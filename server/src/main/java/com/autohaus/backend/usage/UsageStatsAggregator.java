package com.autohaus.backend.usage;

import com.autohaus.backend.common.logging.DeviceClassifier;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.reporting.LogAnalyticsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Aggregates one calendar day from the app's own access log in Azure Log Analytics (REQ-037
 * §12.4) / Aggregiert einen Kalendertag aus dem Access-Log in Azure Log Analytics.
 *
 * <p>Four KQL queries per day — visits per device class, the {@code /api/v1/snaps} funnel, the
 * {@code /api/v1/appointments} funnel and (REQ-043) all server requests — are combined into a
 * single {@link DailyUsage}. The queries run in the scheduler, never in a request path (§9
 * Performance). / Vier KQL-Abfragen je Tag, ausschließlich im Scheduler.
 *
 * <p><b>Time boundaries (BR-7) / Zeitgrenzen:</b> {@code TimeGenerated} is UTC in Log Analytics,
 * the display calendar is {@code Europe/Berlin}. Java therefore computes the day boundaries as
 * {@code ZonedDateTime} in the configured zone and inserts them as explicit UTC instants
 * ({@code datetime(2026-09-20T22:00:00Z)}). {@code startofmonth(now())} and friends are
 * deliberately <b>not</b> used — they are UTC based and would cut the day two hours late. / Java
 * rechnet die Grenzen und übergibt explizite UTC-Instants.
 *
 * <p><b>Exact counting, no estimates (BR-4) / exakt zählen, nicht schätzen:</b> session counts use
 * {@code summarize by SessionId} + {@code count()} resp. {@code countif(...)}.
 * {@code dcount()}/{@code dcountif()} are HyperLogLog estimators (≈ 1.6 % error) and are excluded
 * — the session counts are numerator and denominator of all three conversion rates (BR-6), which
 * are specified to one decimal place. / {@code dcount()} ist ausgeschlossen.
 *
 * <p><b>Exclusions (BR-10) / Ausschlüsse:</b> {@code /api/v1/health} (the Azure URL ping, ≈ 1 440
 * requests/day), every {@code /internal/**} path (covered by {@code Url startswith "/api/"}) and
 * every line without a {@code sessionId}. All queries additionally filter on
 * {@code ContainerAppName_s == 'gs-terminbuchungs-app'}, because the workspace is the resource
 * group's default workspace and potentially shared. / Health, {@code /internal/**} und Zeilen ohne
 * Session-ID fließen in keine Kennzahl ein.
 *
 * <p>The bean only exists while the feature is switched on (BR-13) — with
 * {@code usage.enabled=false} no KQL query runs at all. / Bean existiert nur bei aktivem Feature.
 */
@Component
@ConditionalOnProperty(name = "usage.enabled", havingValue = "true", matchIfMissing = true)
public class UsageStatsAggregator {

    private static final Logger log = LoggerFactory.getLogger(UsageStatsAggregator.class);

    /** Fallback when the property is unset — same zone as the rest of the feature (BR-7). */
    private static final String DEFAULT_ZONE = "Europe/Berlin";

    /** The four permitted device classes (BR-1) — everything else becomes {@code unknown}. */
    private static final Set<String> DEVICE_WHITELIST = Set.of(
        DeviceClassifier.MOBILE, DeviceClassifier.TABLET,
        DeviceClassifier.DESKTOP, DeviceClassifier.UNKNOWN);

    /** Search = {@code POST /api/v1/snaps} (BR-5). / Terminsuche. */
    private static final String SEARCH_PATH = "/api/v1/snaps";

    /** Booking = {@code POST /api/v1/appointments} (BR-5). / Buchung. */
    private static final String BOOKING_PATH = "/api/v1/appointments";

    /**
     * Visits per device class for one calendar day (§12.4, verbatim apart from the two UTC
     * boundaries) / Besuche je Geräteklasse für einen Kalendertag.
     *
     * <p>The {@code extend} line with the whitelist is mandatory (BR-1/BR-2): for log lines from
     * <b>before</b> the deploy — i.e. for the whole 25-day catch-up (AC-9) — {@code extract(...)}
     * yields an empty string that would otherwise show up as a bucket of its own and get lost in
     * the four-class distribution; the sum of the device shares would then no longer match the
     * visit count (AC-4). {@link #normalizeDevice(String)} applies the same mapping again while
     * reading the rows, so a changed query cannot let anything slip through. / Die Whitelist-Zeile
     * ist zwingend und wird in Java noch einmal angewandt.
     */
    private static final String VISITS_KQL = """
        ContainerAppConsoleLogs_CL
        | where ContainerAppName_s == 'gs-terminbuchungs-app'
        | where TimeGenerated >= datetime(%s) and TimeGenerated < datetime(%s)
        | where Log_s has 'INFO  HTTP'
        | extend SessionId = extract('"sessionId":"([^"]+)"', 1, Log_s),
                 Device    = extract('"device":"([a-z]+)"', 1, Log_s),
                 Url       = extract('"url":"([^"]*)"', 1, Log_s)
        | extend Device = iff(isempty(Device) or Device !in ('mobile','tablet','desktop','unknown'), 'unknown', Device)
        | where isnotempty(SessionId) and Url startswith "/api/" and Url != "/api/v1/health"
        | summarize Device = take_any(Device) by SessionId
        | summarize visits = count() by Device
        """;

    /**
     * Funnel counters for one endpoint and one calendar day (§12.4) / Funnel-Zähler je Endpoint.
     *
     * <p>Two-stage {@code summarize}: the first stage groups by {@code SessionId} and marks
     * sessions with at least one successful call, the second stage sums the request counters and
     * counts the marked sessions exactly (BR-4, BR-5). Failed attempts (4xx/5xx) count towards
     * {@code requests} but never towards the funnel. / Zweistufiges {@code summarize}; Fehlversuche
     * zählen in die Request-Zahl, nicht in den Funnel.
     *
     * <p>REQ-043 K-3: {@code failedSessions} counts the sessions without a single successful call
     * — the customers who got stuck. A session that failed once and then succeeded is
     * {@code ok}, never both. / Sitzungen ganz ohne Erfolg; ein späterer Erfolg zählt als ok.
     */
    private static final String FUNNEL_KQL = """
        ContainerAppConsoleLogs_CL
        | where ContainerAppName_s == 'gs-terminbuchungs-app'
        | where TimeGenerated >= datetime(%s) and TimeGenerated < datetime(%s)
        | where Log_s has 'INFO  HTTP' and Log_s has '"method":"POST"' and Log_s has '%s'
        | extend Status    = toint(extract('"statusCode":([0-9]+)', 1, Log_s)),
                 SessionId = extract('"sessionId":"([^"]+)"', 1, Log_s)
        | where Status > 0
        | summarize requests = count(), requestsOk = countif(Status < 400),
                    okHits = countif(Status < 400 and isnotempty(SessionId)) by SessionId
        | summarize requests = sum(requests), requestsOk = sum(requestsOk),
                    sessions = countif(okHits > 0),
                    failedSessions = countif(okHits == 0 and isnotempty(SessionId))
        """;

    /**
     * REQ-043: every request the server answered on one calendar day („Anfragen", the load figure),
     * split into API calls and file requests (app code, styles, images) / alle Anfragen eines
     * Kalendertages, getrennt nach API- und Datei-Abrufen.
     *
     * <p>Deliberately broader than the funnel: every status (errors and 429 are load too) and lines
     * without a session id. Excluded: the Azure health ping (≈ 1 440/day, platform, not usage) and
     * {@code /internal/**} (the status page itself). / Bewusst breiter als der Funnel; Health-Ping
     * und {@code /internal/**} bleiben draußen.
     */
    private static final String REQUESTS_KQL = """
        ContainerAppConsoleLogs_CL
        | where ContainerAppName_s == 'gs-terminbuchungs-app'
        | where TimeGenerated >= datetime(%s) and TimeGenerated < datetime(%s)
        | where Log_s has 'INFO  HTTP'
        | extend Url = extract('"url":"([^"]*)"', 1, Log_s)
        | where isnotempty(Url) and Url != "/api/v1/health" and not(Url startswith "/internal")
        | summarize apiRequests = countif(Url startswith "/api/"),
                    fileRequests = countif(not(Url startswith "/api/"))
        """;

    private final LogAnalyticsClient logClient;
    private final ZoneId zone;

    public UsageStatsAggregator(LogAnalyticsClient logClient, UsageStatsProperties properties) {
        this.logClient = logClient;
        this.zone = resolveZone(properties);
    }

    /**
     * Aggregates one calendar day (in the configured zone) into a {@link DailyUsage} /
     * aggregiert einen Kalendertag zu einer Tageszeile.
     *
     * <p>Never throws: when Log Analytics is unavailable — which is the normal case outside Azure,
     * because there is no managed identity endpoint — the result is {@link Optional#empty()} and
     * the caller keeps the previous snapshot and reports {@code liveAvailable == false} (E1, TC-8).
     * A day without any traffic yields {@code Optional.of(DailyUsage.empty())}, which is something
     * else entirely: "no data" is not the same as "zeros" (AC-12). / Wirft nie; leeres
     * {@link Optional} heißt „Live-Daten nicht verfügbar", nicht „null Besuche".
     *
     * @param day the calendar day in the configured zone / Kalendertag in der konfigurierten Zone
     * @return the day's counters, or empty when Log Analytics could not be queried
     */
    public Optional<DailyUsage> aggregateDailyUsage(LocalDate day) {
        Instant from = day.atStartOfDay(zone).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(zone).toInstant();
        try {
            DeviceBreakdown visits = queryVisits(from, to);
            FunnelCounts search = queryFunnel(from, to, SEARCH_PATH);
            FunnelCounts booking = queryFunnel(from, to, BOOKING_PATH);
            long[] requests = queryRequests(from, to);
            return Optional.of(new DailyUsage(
                visits,
                search.requests(), search.requestsOk(),
                booking.requests(), booking.requestsOk(),
                search.sessions(), booking.sessions(),
                requests[0], requests[1],
                search.failedSessions(), booking.failedSessions()));
        } catch (LogAnalyticsClient.LogAnalyticsUnavailableException e) {
            log.warn("Usage aggregation for {} skipped — Log Analytics unavailable: {}",
                day, e.getMessage());
            return Optional.empty();
        }
    }

    /** The zone all day boundaries are computed in (BR-7). / Zone der Tagesgrenzen. */
    public ZoneId zone() {
        return zone;
    }

    /**
     * Runs the device query and folds the rows into the four classes / faltet die Zeilen in die
     * vier Klassen. Row layout: {@code [Device, visits]} — the {@code by} column comes first.
     */
    private DeviceBreakdown queryVisits(Instant from, Instant to) {
        List<List<String>> rows = LogAnalyticsClient.rows(
            logClient.query(VISITS_KQL.formatted(utc(from), utc(to))));
        long mobile = 0L;
        long tablet = 0L;
        long desktop = 0L;
        long unknown = 0L;
        for (List<String> row : rows) {
            if (row.size() < 2) {
                continue;
            }
            long visits = parseLong(row.get(1));
            switch (normalizeDevice(row.get(0))) {
                case DeviceClassifier.MOBILE -> mobile += visits;
                case DeviceClassifier.TABLET -> tablet += visits;
                case DeviceClassifier.DESKTOP -> desktop += visits;
                // Empty, truncated or foreign values land here — the four classes stay exhaustive
                // (BR-1), so visits.total() always equals the visit count (AC-4).
                default -> unknown += visits;
            }
        }
        return new DeviceBreakdown(mobile, tablet, desktop, unknown);
    }

    /**
     * Runs the funnel query for one endpoint / führt die Funnel-Abfrage aus. Row layout:
     * {@code [requests, requestsOk, sessions, failedSessions]}; an empty result means "no such
     * request that day".
     */
    private FunnelCounts queryFunnel(Instant from, Instant to, String path) {
        List<List<String>> rows = LogAnalyticsClient.rows(
            logClient.query(FUNNEL_KQL.formatted(utc(from), utc(to), path)));
        if (rows.isEmpty() || rows.get(0).size() < 4) {
            return new FunnelCounts(0L, 0L, 0L, 0L);
        }
        List<String> row = rows.get(0);
        return new FunnelCounts(parseLong(row.get(0)), parseLong(row.get(1)), parseLong(row.get(2)),
            parseLong(row.get(3)));
    }

    /**
     * REQ-043: server requests of the day / Anfragen des Tages. Row layout:
     * {@code [apiRequests, fileRequests]}; returned in the same order.
     */
    private long[] queryRequests(Instant from, Instant to) {
        List<List<String>> rows = LogAnalyticsClient.rows(
            logClient.query(REQUESTS_KQL.formatted(utc(from), utc(to))));
        if (rows.isEmpty() || rows.get(0).size() < 2) {
            return new long[]{0L, 0L};
        }
        return new long[]{parseLong(rows.get(0).get(0)), parseLong(rows.get(0).get(1))};
    }

    /**
     * Second line of defence for BR-1 / zweite Verteidigungslinie: every value that is not exactly
     * one of the four literals — empty string, truncated, foreign, differently cased — becomes
     * {@code unknown}.
     */
    private static String normalizeDevice(String raw) {
        if (raw == null) {
            return DeviceClassifier.UNKNOWN;
        }
        String device = raw.trim().toLowerCase(Locale.ROOT);
        return DEVICE_WHITELIST.contains(device) ? device : DeviceClassifier.UNKNOWN;
    }

    /** KQL literal for {@code datetime(...)}: second-precision ISO-8601 UTC, e.g. {@code 2026-09-20T22:00:00Z}. */
    private static String utc(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.SECONDS));
    }

    /** Lenient row parsing — a non-numeric cell must not fail the whole day. */
    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    /** Configured zone, with a safe default so a missing property cannot break the aggregation. */
    private static ZoneId resolveZone(UsageStatsProperties properties) {
        String configured = properties == null ? null : properties.zone();
        if (configured == null || configured.isBlank()) {
            return ZoneId.of(DEFAULT_ZONE);
        }
        try {
            return ZoneId.of(configured.trim());
        } catch (RuntimeException e) {
            log.warn("Unknown usage.zone '{}' — falling back to {}", configured, DEFAULT_ZONE);
            return ZoneId.of(DEFAULT_ZONE);
        }
    }

    /**
     * One endpoint's funnel counters for one day / Funnel-Zähler eines Endpoints für einen Tag.
     *
     * @param requests all POST requests / alle POST-Requests
     * @param requestsOk thereof {@code statusCode < 400} / davon erfolgreich
     * @param sessions sessions with at least one successful call / Sitzungen mit Erfolg
     * @param failedSessions sessions without any successful call / Sitzungen ganz ohne Erfolg
     */
    private record FunnelCounts(long requests, long requestsOk, long sessions, long failedSessions) {}
}
