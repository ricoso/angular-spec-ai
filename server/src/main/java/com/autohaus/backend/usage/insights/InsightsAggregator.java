package com.autohaus.backend.usage.insights;

import com.autohaus.backend.common.logging.DeviceClassifier;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.config.setup.BranchSetup;
import com.autohaus.backend.config.setup.GroupEntry;
import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.reporting.LogAnalyticsClient;
import com.autohaus.backend.usage.UsageZone;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Aggregates one Berlin calendar day for the booking insights (K2) and the global funnel (K3) out
 * of Azure Log Analytics (REQ-038 §12.8, Step 3) / aggregiert einen Berliner Kalendertag für die
 * Buchungs-Insights und den globalen Trichter.
 *
 * <p><b>At most three queries per day / höchstens drei Abfragen je Tag:</b> {@link #BOOKINGS_KQL}
 * (KQL 1) and {@link #SERVICE_VIEWS_KQL} (KQL 2) only while the bookings source is active,
 * {@link #FUNNEL_KQL} (KQL 3a) while only the funnel is active, {@link #DROPOFF_KQL} (KQL 3b)
 * <b>instead of</b> 3a while the drop-off scenarios are active too (N1) — funnel and scenarios then
 * come from one result, so Σ scenarios = erreicht(1) − erreicht(5) holds by construction (BR-27).
 * Only the funnel → exactly one query; only K2 → exactly two (TC-46). / Nur Trichter: eine
 * Abfrage; nur K2: zwei.
 *
 * <p><b>Queries verbatim (§12.8) / Abfragen wörtlich:</b> only the two UTC boundaries (and, in
 * KQL 1, the ISO day) are placeholders — Java computes the Berlin day boundaries and passes
 * explicit UTC instants (BR-12, pattern {@code UsageStatsAggregator}); 23- and 25-hour days around
 * the DST switch come out right. The two trailing {@code // ab hier …} comments of §12.8 are left
 * out: they contain the literal {@code Log_s} after the {@code project}, which the BR-7 string test
 * (TC-17) forbids. /
 * Die zwei Kommentare aus §12.8 entfallen, weil sie {@code Log_s} nach dem {@code project} enthalten.
 *
 * <p><b>Booking lines are assigned by their {@code day} (§10.2) / Zuordnung über {@code day}:</b>
 * {@code UsageEventLogger} writes the lines in a daily shuffled flush at 23:59:59 (and on
 * shutdown), decoupled from the booking request, so KQL 1 reads
 * {@value #FLUSH_GRACE_MINUTES} minutes past the day end and keeps a line when its {@code day}
 * equals the aggregated day; a line without {@code day} (format
 * {@value UsageBookingEvent#LEGACY_FORMAT_VERSION}) only inside the day window, as before. KQL 1
 * is anchored on the log prefix {@code " - job.usagebooking: USAGE_BOOKING "} — the first
 * {@code " - <traceId>: "} of the line must be the flush marker, which no client request can carry.
 * / Die Zeilen entstehen im Tagesflush; KQL 1 liest 10 Minuten über das Tagesende hinaus, verankert
 * auf das Präfix des Flushs.
 *
 * <p><b>Second whitelist while reading (E11) / zweite Whitelist beim Einlesen:</b> a booking row
 * whose {@code branch} is not a 24-hex id from {@code setup.json}, whose {@code v} is neither
 * {@value UsageBookingEvent#FORMAT_VERSION} (with an ISO {@code day} equal to the aggregated day)
 * nor {@value UsageBookingEvent#LEGACY_FORMAT_VERSION} (without {@code day}), or whose
 * {@code bookingDaypart} is foreign is discarded; foreign slot/lead-time values become
 * {@code unknown}; {@code ops} keeps at most {@value UsageBookingEvent#MAX_OPS} 24-hex ids. A group that does not belong to the branch in
 * {@code setup.json} is never sent to SoftNet — its operations count as {@code unknown}. Funnel
 * rows are re-checked against the device whitelist, drop-off rows against the scenario whitelist
 * ({@link DropoffScenario#fromKey}); a foreign scenario and an inconsistent {@code booked}
 * (⇔ MaxStep 5) are discarded with a {@code WARN} <b>without values</b>. A query reaching its
 * {@code take} limit ({@value #TAKE_LIMIT} rows) makes the day incomplete: it is not written. /
 * Unbekannte Werte werden verworfen bzw. {@code unknown}; am {@code take}-Limit fällt der Tag aus.
 *
 * <p><b>Privacy by construction (BR-7, BR-30) / Datenschutz durch Konstruktion:</b> no query
 * extracts {@code sessionId} together with a branch id; the per-session scenario × stage × device
 * tuple is never materialised — only the two margins {@code byScenarioStep} and
 * {@code byScenarioDevice} leave this class, both nested like the blob (§10.6). Raw rows are never
 * logged. / Rohzeilen werden nie geloggt.
 */
@Component
public class InsightsAggregator {

    private static final Logger log = LoggerFactory.getLogger(InsightsAggregator.class);

    /** {@code take} limit of KQL 1 — reaching it makes the day incomplete (E11 Nr. 2). */
    public static final int TAKE_LIMIT = 5000;

    /**
     * Minutes KQL 1 reads past the day end: the 23:59:59 flush may reach Log Analytics after
     * midnight (§12.8) / Minuten über das Tagesende hinaus, weil der Flush nach Mitternacht
     * ankommen kann.
     */
    public static final int FLUSH_GRACE_MINUTES = 10;

    /** Scenario key of a booked session in KQL 3b — never persisted / Schlüssel „gebucht". */
    public static final String BOOKED = "booked";

    /** Highest funnel stage „Gebucht" (BR-8) / höchste Trichterstufe. */
    public static final int STAGE_BOOKED = 5;

    /** Device classes in display order (BR-1 REQ-037) / Geräteklassen in Anzeigereihenfolge. */
    public static final List<String> DEVICES = List.of(
        DeviceClassifier.MOBILE, DeviceClassifier.TABLET, DeviceClassifier.DESKTOP,
        DeviceClassifier.UNKNOWN);

    /** Stage keys of {@code byScenarioStep} (§10.6) / Stufen-Schlüssel. */
    public static final List<String> DROPOFF_STAGES = List.of("1", "2", "3", "4");

    private static final Pattern HEX_24 = Pattern.compile("^[a-f0-9]{24}$");

    /** Parses the {@code Ops} cell ({@code tostring(J.ops)} → JSON array text) / nur für {@code ops}. */
    private static final ObjectMapper OPS_MAPPER = new ObjectMapper();

    /**
     * KQL 1 — bookings ({@code USAGE_BOOKING} of the daily flush), no {@code sessionId}, no device
     * (§12.8, verbatim apart from the placeholders) / Buchungen ohne Sitzung und Gerät.
     * Placeholders: {@code %1$s} day start (UTC), {@code %2$s} day end (UTC), {@code %3$s} ISO day.
     * Anchored on the prefix of the flush: the first {@code " - <traceId>: "} of the line must be
     * {@code job.usagebooking} (no client can set it), the JSON must follow it directly. Window up
     * to {@value #FLUSH_GRACE_MINUTES} minutes past the day end; a line counts for the day of its
     * {@code day} field, a line without {@code day} (v1) only inside the day window. Row layout:
     * {@code [V, Branch, Grp, Variant, Ops, BookingDaypart, SlotWeekday, SlotDaypart, LeadTime,
     * Mobility, Day]} — {@code Day} last, so the first ten columns keep their v1 positions. /
     * Verankert auf das Flush-Präfix; Zuordnung über {@code day}, ohne {@code day} über das
     * Tagesfenster.
     *
     * <p><b>{@code Day} comes from the raw JSON text, never from {@code J.day}:</b>
     * {@code parse_json} turns the ISO date string into a {@code datetime}, and {@code tostring}
     * then yields {@code 2026-09-27T00:00:00.0000000Z}, which never equals {@code '2026-09-27'} —
     * every booking was silently dropped (production 26.–28.09.2026). / {@code parse_json} macht
     * aus dem Datum ein {@code datetime}; der Tag wird daher per Regex aus dem Rohtext gelesen.
     */
    public static final String BOOKINGS_KQL = """
        ContainerAppConsoleLogs_CL
        | where ContainerAppName_s == 'gs-terminbuchungs-app'
        | where TimeGenerated >= datetime(%1$s) and TimeGenerated < datetime(%2$s) + 10m
        | where Log_s has 'USAGE_BOOKING' and Log_s contains_cs ' - job.usagebooking: USAGE_BOOKING '
        | where extract(@' - ([A-Za-z0-9_.-]{0,64}): ', 1, Log_s) == 'job.usagebooking'
        | extend Raw = extract(@' - job\\.usagebooking: USAGE_BOOKING (\\{.*\\})\\s*$', 1, Log_s)
        | extend J = parse_json(Raw)
        | where isnotnull(J)
        | extend Day = extract(@'"day":"(\\d{4}-\\d{2}-\\d{2})"', 1, Raw)
        | where Day == '%3$s' or (isempty(Day) and TimeGenerated < datetime(%2$s))
        | project V = toint(J.v), Branch = tostring(J.branch), Grp = tostring(J.group), Variant = tostring(J.variant),
                  Ops = tostring(J.ops), BookingDaypart = tostring(J.bookingDaypart), SlotWeekday = tostring(J.slotWeekday),
                  SlotDaypart = tostring(J.slotDaypart), LeadTime = tostring(J.leadTime), Mobility = tobool(J.mobility),
                  Day
        | take 5000
        """;

    /**
     * KQL 2 — service-selection views per branch (R-3), without {@code sessionId} (§12.8) /
     * Leistungsaufrufe je Standort ohne Sitzung. Row layout: {@code [BranchId, serviceViews]}.
     */
    public static final String SERVICE_VIEWS_KQL = """
        ContainerAppConsoleLogs_CL
        | where ContainerAppName_s == 'gs-terminbuchungs-app'
        | where TimeGenerated >= datetime(%s) and TimeGenerated < datetime(%s)
        | where Log_s has 'INFO  HTTP' and Log_s has '"method":"GET"' and Log_s has '/operations'
        | extend Url = extract('"url":"([^"]*)"', 1, Log_s), Status = toint(extract('"statusCode":([0-9]+)', 1, Log_s))
        | extend BranchId = extract(@'^/api/v1/branches/([0-9a-f]{24})/operations$', 1, Url)
        | where Status > 0 and Status < 400 and isnotempty(BranchId)
        | summarize serviceViews = count() by BranchId
        """;

    /**
     * KQL 3a {@code FUNNEL_KQL} — global funnel with {@code sessionId}, without any branch
     * extraction (§12.8, BR-7/BR-8) / globaler Trichter ohne Standort. Only while the funnel is
     * active and the drop-off scenarios are off. Row layout: {@code [MaxStep, Device, sessions]}.
     */
    public static final String FUNNEL_KQL = """
        ContainerAppConsoleLogs_CL
        | where ContainerAppName_s == 'gs-terminbuchungs-app'
        | where TimeGenerated >= datetime(%s) and TimeGenerated < datetime(%s)
        | where Log_s has 'INFO  HTTP' and Log_s contains '"url":"/api/'
              and Log_s !contains '"url":"/api/v1/health"' and Log_s !contains '"url":"/internal'
        | extend SessionId = extract('"sessionId":"([0-9a-f]{16})"', 1, Log_s),
                 Status = toint(extract('"statusCode":([0-9]{3})', 1, Log_s)),
                 Device = extract('"device":"(mobile|tablet|desktop|unknown)"', 1, Log_s),
                 Kind   = case(
                   Log_s has '"method":"POST"' and Log_s contains '"url":"/api/v1/appointments"', 'book',
                   Log_s has '"method":"POST"' and Log_s contains '"url":"/api/v1/snaps"', 'search',
                   Log_s matches regex @'"url":"/api/v1/branches/[0-9a-f]{24}/rentcategories"', 'notes',
                   Log_s matches regex @'"url":"/api/v1/branches/[0-9a-f]{24}/operations"', 'services',
                   'start')
        | where isnotempty(SessionId) and isnotnull(Status)
        | project SessionId, Status, Kind, Device = iff(isempty(Device), 'unknown', Device)
        | extend Step = iff(Status >= 400, 1, case(Kind=='book',5, Kind=='search',4, Kind=='notes',3, Kind=='services',2, 1))
        | summarize MaxStep = max(Step), Device = take_any(Device) by SessionId
        | summarize sessions = count() by MaxStep, Device
        """;

    /**
     * KQL 3b {@code DROPOFF_KQL} — replaces 3a while the drop-off scenarios are active (N1,
     * §12.8, BR-27/BR-28): one primary scenario per session with MaxStep &lt; 5, the first matching
     * catalogue row wins (§8); funnel counters from the same result. No time difference between
     * requests, no {@code Url} column, no join. Row layout: {@code [Scenario, MaxStep, Device,
     * sessions]}. / Ersetzt 3a; genau ein Szenario je Sitzung.
     */
    public static final String DROPOFF_KQL = """
        let From = datetime(%s); let To = datetime(%s);
        ContainerAppConsoleLogs_CL
        | where ContainerAppName_s == 'gs-terminbuchungs-app'
        | where TimeGenerated >= From and TimeGenerated < To
        | where Log_s has 'INFO  HTTP' and Log_s contains '"url":"/api/'
              and Log_s !contains '"url":"/api/v1/health"' and Log_s !contains '"url":"/internal'
        | extend SessionId = extract('"sessionId":"([0-9a-f]{16})"', 1, Log_s),
                 Seq    = toint(extract('"correlationId":"[0-9a-f]{16}-([0-9]{1,6})"', 1, Log_s)),
                 Status = toint(extract('"statusCode":([0-9]{3})', 1, Log_s)),
                 DurMs  = toint(extract('"duration":"([0-9]{1,7})ms"', 1, Log_s)),
                 Device = extract('"device":"(mobile|tablet|desktop|unknown)"', 1, Log_s),
                 Hits   = extract('"hits":"(none|few|many)"', 1, Log_s),
                 Kind   = case(
                   Log_s has '"method":"POST"' and Log_s contains '"url":"/api/v1/appointments"', 'book',
                   Log_s has '"method":"POST"' and Log_s contains '"url":"/api/v1/snaps"', 'search',
                   Log_s matches regex @'"url":"/api/v1/branches/[0-9a-f]{24}/rentcategories"', 'notes',
                   Log_s matches regex @'"url":"/api/v1/branches/[0-9a-f]{24}/operations"', 'services',
                   'start')
        | where isnotempty(SessionId) and isnotnull(Seq) and isnotnull(Status)
        | project SessionId, Seq, Status, DurMs, Hits, Kind,
                  Device = iff(isempty(Device), 'unknown', Device)
        | extend Step = iff(Status >= 400, 1, case(Kind=='book',5, Kind=='search',4, Kind=='notes',3, Kind=='services',2, 1)),
                 SearchSeq = iff(Kind == 'search' and Status < 400, Seq, -1)
        | summarize MaxStep = max(Step), Device = take_any(Device), Seen = count(),
                    MinSeq = min(Seq), MaxSeq = max(Seq),
                    (LastSeq, LastStatus, LastKind, LastDurMs) = arg_max(Seq, Status, Kind, DurMs),
                    (LastSearchSeq, LastHits) = arg_max(SearchSeq, Hits),
                    Searches  = countif(SearchSeq > 0),
                    Expired   = countif(Kind == 'book' and Status == 404),
                    Conflicts = countif(Kind == 'book' and Status == 410),
                    Errors    = countif(Status >= 500 or Status in (403, 429))
                  by SessionId
        | extend Scenario = case(
            MaxStep == 5, 'booked',
            LastStatus >= 500, 'server-error',
            LastStatus in (403, 429), 'access-denied',
            Expired > 0, 'slot-expired',
            Conflicts > 0, 'slot-conflict',
            LastKind == 'book' and LastStatus between (400 .. 499), 'booking-rejected',
            LastKind == 'search' and LastStatus between (400 .. 499), 'search-rejected',
            Errors > 0, 'error-then-quit',
            MaxSeq - MinSeq + 1 > Seen, 'requests-lost',
            LastSearchSeq > 0 and LastHits == 'none', 'no-slots',
            LastSearchSeq > 0 and LastHits == 'few', 'few-slots',
            LastDurMs >= 8000, 'slow-response',
            Searches >= 4, 'many-searches',
            strcat('quiet-', tostring(MaxStep)))
        | summarize sessions = count() by Scenario, MaxStep, Device
        """;

    private final LogAnalyticsClient logClient;
    private final SetupService setupService;
    private final ServiceGroupClassifier classifier;
    private final ZoneId zone;

    public InsightsAggregator(LogAnalyticsClient logClient, SetupService setupService,
                              ServiceGroupClassifier classifier, UsageStatsProperties properties) {
        this.logClient = logClient;
        this.setupService = setupService;
        this.classifier = classifier;
        this.zone = UsageZone.resolve(properties);
    }

    /** The zone all day boundaries are computed in (BR-12) / Zone der Tagesgrenzen. */
    public ZoneId zone() {
        return zone;
    }

    /**
     * Aggregates one calendar day / aggregiert einen Kalendertag.
     *
     * <p>Never throws for Log Analytics trouble: unavailable (E3), a query at its {@code take}
     * limit (E11 Nr. 2) or a transient service-group failure (E9) yield {@link Optional#empty()}
     * — the caller then writes <b>nothing</b> for this day (no partial day). A source that is not
     * requested stays {@code null} in the result („nicht erfasst" → „—", never 0). / Leeres
     * Ergebnis heißt: Tag nicht schreiben; nicht angefragte Quellen bleiben {@code null}.
     *
     * @param day the Berlin calendar day / Berliner Kalendertag
     * @param bookings run KQL 1 + 2 (K2) / Buchungs-Abfragen ausführen
     * @param funnel run the funnel query (K3) / Trichter-Abfrage ausführen
     * @param dropoff use the drop-off variant 3b instead of 3a (only with {@code funnel}, N1)
     * @return the day's aggregate, or empty when the day must not be written
     */
    public Optional<AggregatedDay> aggregateDay(LocalDate day, boolean bookings, boolean funnel,
                                                boolean dropoff) {
        Instant from = day.atStartOfDay(zone).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(zone).toInstant();
        try {
            BookingPart bookingPart = bookings ? queryBookings(day, from, to) : null;
            if (bookings && bookingPart == null) {
                return Optional.empty();
            }
            FunnelDay funnelDay = null;
            if (funnel) {
                Optional<FunnelDay> result = dropoff ? queryDropoff(day, from, to) : queryFunnel(day, from, to);
                if (result.isEmpty()) {
                    return Optional.empty();
                }
                funnelDay = result.orElseThrow();
            }
            InsightsDay global = bookingPart == null
                ? new InsightsDay(null, null, null, null, null, null, null, null, funnelDay)
                : bookingPart.global(funnelDay);
            Map<String, BranchCounts> branches = bookingPart == null ? Map.of() : bookingPart.branches();
            return Optional.of(new AggregatedDay(global, branches));
        } catch (LogAnalyticsClient.LogAnalyticsUnavailableException e) {
            log.warn("usage insights aggregation for {} skipped — Log Analytics unavailable: {}",
                day, e.getMessage());
            return Optional.empty();
        } catch (ServiceGroupClassifier.CatalogUnavailableException e) {
            log.warn("usage insights aggregation for {} skipped — service groups not loadable (E9)", day);
            return Optional.empty();
        }
    }

    // ---- KQL 1 + 2: bookings and service views / Buchungen und Leistungsaufrufe ----

    /** Returns {@code null} when the day is incomplete (take limit) / {@code null} bei Kappung. */
    private BookingPart queryBookings(LocalDate day, Instant from, Instant to) {
        Map<String, Set<String>> groupsByBranch = branchWhitelist();
        List<List<String>> bookingRows = query(BOOKINGS_KQL.formatted(utc(from), utc(to),
            DateTimeFormatter.ISO_LOCAL_DATE.format(day)));
        if (atTakeLimit(bookingRows, "KQL 1", day)) {
            return null;
        }
        List<List<String>> viewRows = run(SERVICE_VIEWS_KQL, from, to);
        if (atTakeLimit(viewRows, "KQL 2", day)) {
            return null;
        }

        GlobalAcc global = new GlobalAcc();
        Map<String, BranchAcc> branches = new LinkedHashMap<>();
        groupsByBranch.keySet().forEach(id -> branches.put(id, new BranchAcc()));
        boolean weekend = isWeekend(day);
        int discarded = 0;
        for (List<String> row : bookingRows) {
            BookingRow booking = parseBookingRow(row, groupsByBranch, day);
            if (booking == null) {
                discarded++;
                continue;
            }
            Set<ServiceGroup> groups = booking.groupKnown()
                ? classifier.classify(booking.group(), booking.ops(), booking.variant())
                : unknownGroups(booking.variant());
            boolean offHours = booking.bookingDaypart() != BookingDaypart.DAY || weekend;
            global.add(booking, groups);
            branches.get(booking.branch()).add(booking, groups, offHours);
        }
        if (discarded > 0) {
            // E11: count only — the rejected values themselves are never logged.
            log.warn("usage insights {}: {} booking row(s) discarded by the whitelist (E11)", day, discarded);
        }

        long serviceViews = 0L;
        int discardedViews = 0;
        for (List<String> row : viewRows) {
            String branchId = row.isEmpty() ? "" : row.get(0).trim();
            Long views = row.size() < 2 ? null : parseCount(row.get(1));
            if (!groupsByBranch.containsKey(branchId) || views == null) {
                discardedViews++;
                continue;
            }
            branches.get(branchId).serviceViews += views;
            serviceViews += views;
        }
        if (discardedViews > 0) {
            log.warn("usage insights {}: {} service-view row(s) discarded by the whitelist (E11)",
                day, discardedViews);
        }
        global.serviceViews = serviceViews;

        Map<String, BranchCounts> branchCounts = new LinkedHashMap<>();
        branches.forEach((id, acc) -> branchCounts.put(id, acc.toCounts()));
        return new BookingPart(global, Collections.unmodifiableMap(branchCounts));
    }

    /**
     * One KQL 1 row through the whitelist (E11), {@code null} = discard / eine Zeile durch die
     * Whitelist.
     */
    private static BookingRow parseBookingRow(List<String> row, Map<String, Set<String>> groupsByBranch,
                                              LocalDate day) {
        if (row.size() < 10) {
            return null;
        }
        if (!belongsToDay(parseInt(row.get(0)), row.size() > 10 ? row.get(10).trim() : "", day)) {
            return null;
        }
        String branch = row.get(1).trim();
        if (!HEX_24.matcher(branch).matches() || !groupsByBranch.containsKey(branch)) {
            return null;
        }
        BookingDaypart bookingDaypart = BookingDaypart.fromKey(row.get(5).trim());
        if (bookingDaypart == null) {
            // No UNKNOWN bucket exists for the booking moment — counting the booking without it
            // would break "Σ T-1 = bookings" that the secondary suppression relies on (BR-9 Nr. 4a).
            return null;
        }
        String group = row.get(2).trim();
        boolean groupKnown = HEX_24.matcher(group).matches()
            && groupsByBranch.get(branch).contains(group);
        String variant = ServiceGroupClassifier.TIRE_CHANGE_VARIANT.equals(row.get(3).trim())
            ? ServiceGroupClassifier.TIRE_CHANGE_VARIANT : "default";
        return new BookingRow(branch, group, groupKnown, variant, parseOps(row.get(4)),
            bookingDaypart,
            SlotWeekday.fromKey(row.get(6).trim()),
            SlotDaypart.fromKey(row.get(7).trim()),
            LeadTimeBucket.fromKey(row.get(8).trim()),
            "true".equalsIgnoreCase(row.get(9).trim()));
    }

    /**
     * Version and {@code day} of a booking row (E11, §10.2) / Version und Tag einer Buchungszeile:
     * a present {@code day} must be an ISO date equal to the aggregated day — KQL 1 already filters
     * on it, Java checks again; without {@code day} only format
     * {@value UsageBookingEvent#LEGACY_FORMAT_VERSION} is accepted (KQL 1 kept it inside the day
     * window by {@code TimeGenerated}); format {@value UsageBookingEvent#FORMAT_VERSION} requires
     * {@code day}; every other version is discarded. / Vorhandener Tag muss exakt passen; ohne Tag
     * nur Version 1.
     */
    static boolean belongsToDay(Integer version, String rowDay, LocalDate day) {
        if (version == null) {
            return false;
        }
        if (rowDay == null || rowDay.isEmpty()) {
            return version == UsageBookingEvent.LEGACY_FORMAT_VERSION;
        }
        if (version != UsageBookingEvent.FORMAT_VERSION && version != UsageBookingEvent.LEGACY_FORMAT_VERSION) {
            return false;
        }
        try {
            return LocalDate.parse(rowDay, DateTimeFormatter.ISO_LOCAL_DATE).equals(day);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** {@code ops}: JSON array of 24-hex ids, at most {@value UsageBookingEvent#MAX_OPS} (E11). */
    static List<String> parseOps(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        JsonNode node;
        try {
            node = OPS_MAPPER.readTree(raw);
        } catch (Exception e) {
            return List.of();
        }
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> ops = new ArrayList<>();
        for (JsonNode element : node) {
            if (ops.size() >= UsageBookingEvent.MAX_OPS) {
                break;
            }
            String id = element.isTextual() ? element.asText() : element.path("id").asText("");
            if (HEX_24.matcher(id).matches()) {
                ops.add(id);
            }
        }
        return List.copyOf(ops);
    }

    /** Groups of a booking whose group is not in {@code setup.json} (never sent to SoftNet). */
    private static Set<ServiceGroup> unknownGroups(String variant) {
        return ServiceGroupClassifier.TIRE_CHANGE_VARIANT.equals(variant)
            ? Set.of(ServiceGroup.UNKNOWN, ServiceGroup.TIRE_CHANGE)
            : Set.of(ServiceGroup.UNKNOWN);
    }

    /** branchId → its group ids from {@code setup.json} — the whitelist of E11. */
    private Map<String, Set<String>> branchWhitelist() {
        Map<String, Set<String>> whitelist = new LinkedHashMap<>();
        List<BranchSetup> branches = setupService == null ? List.of() : setupService.getAllBranches();
        for (BranchSetup branch : branches) {
            if (branch == null || branch.branchId() == null || !HEX_24.matcher(branch.branchId()).matches()) {
                continue;
            }
            Set<String> groups = new java.util.HashSet<>();
            if (branch.groups() != null) {
                for (GroupEntry group : branch.groups()) {
                    if (group != null && group.groupId() != null) {
                        groups.add(group.groupId());
                    }
                }
            }
            whitelist.put(branch.branchId(), Set.copyOf(groups));
        }
        return whitelist;
    }

    // ---- KQL 3a / 3b: funnel and drop-off / Trichter und Absprung ----

    private Optional<FunnelDay> queryFunnel(LocalDate day, Instant from, Instant to) {
        List<List<String>> rows = run(FUNNEL_KQL, from, to);
        if (atTakeLimit(rows, "KQL 3a", day)) {
            return Optional.empty();
        }
        FunnelAcc funnel = new FunnelAcc();
        int discarded = 0;
        for (List<String> row : rows) {
            Integer maxStep = row.size() < 3 ? null : parseStage(row.get(0));
            Long sessions = row.size() < 3 ? null : parseCount(row.get(2));
            if (maxStep == null || sessions == null) {
                discarded++;
                continue;
            }
            funnel.add(maxStep, normalizeDevice(row.get(1)), sessions);
        }
        if (discarded > 0) {
            log.warn("usage insights {}: {} funnel row(s) discarded by the whitelist (E11)", day, discarded);
        }
        return Optional.of(new FunnelDay(funnel.all(), funnel.byDevice(), null));
    }

    private Optional<FunnelDay> queryDropoff(LocalDate day, Instant from, Instant to) {
        List<List<String>> rows = run(DROPOFF_KQL, from, to);
        if (atTakeLimit(rows, "KQL 3b", day)) {
            return Optional.empty();
        }
        FunnelAcc funnel = new FunnelAcc();
        Map<DropoffScenario, long[]> byStep = new EnumMap<>(DropoffScenario.class);
        Map<DropoffScenario, Map<String, Long>> byDevice = new EnumMap<>(DropoffScenario.class);
        int malformed = 0;
        int unknownScenario = 0;
        int inconsistentBooked = 0;
        int unknownDevice = 0;
        for (List<String> row : rows) {
            if (row.size() < 4) {
                malformed++;
                continue;
            }
            String scenarioKey = row.get(0).trim();
            Integer maxStep = parseStage(row.get(1));
            Long sessions = parseCount(row.get(3));
            if (maxStep == null || sessions == null) {
                malformed++;
                continue;
            }
            String device = row.get(2).trim();
            if (!DEVICES.contains(device)) {
                unknownDevice++;
                continue;
            }
            boolean booked = BOOKED.equals(scenarioKey);
            if (booked != (maxStep == STAGE_BOOKED)) {
                inconsistentBooked++;
                continue;
            }
            DropoffScenario scenario = booked ? null : DropoffScenario.fromKey(scenarioKey);
            if (!booked && scenario == null) {
                unknownScenario++;
                continue;
            }
            funnel.add(maxStep, device, sessions);
            if (scenario != null) {
                byStep.computeIfAbsent(scenario, s -> new long[DROPOFF_STAGES.size()])[maxStep - 1] += sessions;
                byDevice.computeIfAbsent(scenario, s -> zeroDevices()).merge(device, sessions, Long::sum);
            }
        }
        // E11: a WARN per kind of violation, never with the offending values.
        if (unknownScenario > 0) {
            log.warn("usage insights {}: {} drop-off row(s) with an unknown scenario discarded (E11)",
                day, unknownScenario);
        }
        if (inconsistentBooked > 0) {
            log.warn("usage insights {}: {} drop-off row(s) with inconsistent 'booked' / MaxStep "
                + "discarded (E11)", day, inconsistentBooked);
        }
        if (unknownDevice > 0) {
            log.warn("usage insights {}: {} drop-off row(s) with an unknown device class discarded (E11)",
                day, unknownDevice);
        }
        if (malformed > 0) {
            log.warn("usage insights {}: {} malformed drop-off row(s) discarded (E11)", day, malformed);
        }

        Map<String, Map<String, Long>> scenarioStep = new LinkedHashMap<>();
        Map<String, Map<String, Long>> scenarioDevice = new LinkedHashMap<>();
        long scenarioSum = 0L;
        for (DropoffScenario scenario : DropoffScenario.values()) {
            long[] steps = byStep.get(scenario);
            if (steps == null) {
                continue;
            }
            Map<String, Long> stages = new LinkedHashMap<>();
            for (int i = 0; i < DROPOFF_STAGES.size(); i++) {
                stages.put(DROPOFF_STAGES.get(i), steps[i]);
                scenarioSum += steps[i];
            }
            scenarioStep.put(scenario.key(), Collections.unmodifiableMap(stages));
            scenarioDevice.put(scenario.key(),
                Collections.unmodifiableMap(new LinkedHashMap<>(byDevice.get(scenario))));
        }
        FunnelCounts all = funnel.all();
        long dropOffs = all.visits() - all.booking();
        if (scenarioSum != dropOffs) {
            // BR-27 / AC-33 plausibility check — values deliberately not logged.
            log.warn("usage insights {}: drop-off invariant violated — Σ scenarios differs from "
                + "erreicht(1) − erreicht(5) (BR-27)", day);
        }
        DropoffDay dropoff = new DropoffDay(Collections.unmodifiableMap(scenarioStep),
            Collections.unmodifiableMap(scenarioDevice));
        return Optional.of(new FunnelDay(all, funnel.byDevice(), dropoff));
    }

    private static Map<String, Long> zeroDevices() {
        Map<String, Long> devices = new LinkedHashMap<>();
        DEVICES.forEach(device -> devices.put(device, 0L));
        return devices;
    }

    // ---- helpers / Hilfsfunktionen ----

    private List<List<String>> run(String template, Instant from, Instant to) {
        return query(template.formatted(utc(from), utc(to)));
    }

    private List<List<String>> query(String kql) {
        return LogAnalyticsClient.rows(logClient.query(kql));
    }

    private static boolean atTakeLimit(List<List<String>> rows, String query, LocalDate day) {
        if (rows.size() >= TAKE_LIMIT) {
            log.warn("usage insights {}: {} reached its take limit of {} rows — the day is "
                + "incomplete and not written (E11)", day, query, TAKE_LIMIT);
            return true;
        }
        return false;
    }

    private static boolean isWeekend(LocalDate day) {
        DayOfWeek weekday = day.getDayOfWeek();
        return weekday == DayOfWeek.SATURDAY || weekday == DayOfWeek.SUNDAY;
    }

    /** Device whitelist of REQ-037 — every foreign value becomes {@code unknown} (3a). */
    private static String normalizeDevice(String raw) {
        String device = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return DEVICES.contains(device) ? device : DeviceClassifier.UNKNOWN;
    }

    /** Funnel stage 1–5, {@code null} otherwise / Trichterstufe 1–5. */
    private static Integer parseStage(String raw) {
        Integer stage = parseInt(raw);
        return stage != null && stage >= 1 && stage <= STAGE_BOOKED ? stage : null;
    }

    private static Integer parseInt(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Non-negative count, {@code null} for anything else / nicht-negativer Zähler. */
    private static Long parseCount(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            long value = Long.parseLong(raw.trim());
            return value < 0 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** KQL literal for {@code datetime(...)}: second-precision ISO-8601 UTC. */
    private static String utc(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.SECONDS));
    }

    // ---- accumulators / Akkumulatoren ----

    /** One whitelisted booking row / eine geprüfte Buchungszeile. */
    private record BookingRow(String branch, String group, boolean groupKnown, String variant,
                              List<String> ops, BookingDaypart bookingDaypart, SlotWeekday slotWeekday,
                              SlotDaypart slotDaypart, LeadTimeBucket leadTime, boolean mobility) {}

    private record BookingPart(GlobalAcc acc, Map<String, BranchCounts> branches) {
        InsightsDay global(FunnelDay funnel) {
            return acc.toDay(funnel);
        }
    }

    /** Group mentions in {@link ServiceGroup} order, zero keys left out (§10.6). */
    private static Map<String, Long> groupMap(Map<ServiceGroup, Long> counts) {
        Map<String, Long> groups = new LinkedHashMap<>();
        for (ServiceGroup group : ServiceGroup.values()) {
            Long count = counts.get(group);
            if (count != null && count > 0) {
                groups.put(group.key(), count);
            }
        }
        return Collections.unmodifiableMap(groups);
    }

    private static final class GlobalAcc {
        private long bookings;
        private final Map<BookingDaypart, Long> bookingDaypart = new EnumMap<>(BookingDaypart.class);
        private final Map<SlotWeekday, Map<SlotDaypart, Long>> slotGrid = new EnumMap<>(SlotWeekday.class);
        private long slotUnknown;
        private final Map<LeadTimeBucket, Long> leadTime = new EnumMap<>(LeadTimeBucket.class);
        private final Map<ServiceGroup, Long> groups = new EnumMap<>(ServiceGroup.class);
        private long mobility;
        private long serviceViews;

        void add(BookingRow booking, Set<ServiceGroup> bookingGroups) {
            bookings++;
            bookingDaypart.merge(booking.bookingDaypart(), 1L, Long::sum);
            if (booking.slotWeekday() == SlotWeekday.UNKNOWN || booking.slotDaypart() == SlotDaypart.UNKNOWN) {
                slotUnknown++;
            } else {
                slotGrid.computeIfAbsent(booking.slotWeekday(), w -> new EnumMap<>(SlotDaypart.class))
                    .merge(booking.slotDaypart(), 1L, Long::sum);
            }
            leadTime.merge(booking.leadTime(), 1L, Long::sum);
            bookingGroups.forEach(group -> groups.merge(group, 1L, Long::sum));
            if (booking.mobility()) {
                mobility++;
            }
        }

        InsightsDay toDay(FunnelDay funnel) {
            Map<String, Long> dayparts = new LinkedHashMap<>();
            for (BookingDaypart part : BookingDaypart.values()) {
                dayparts.put(part.key(), bookingDaypart.getOrDefault(part, 0L));
            }
            Map<String, Map<String, Long>> grid = new LinkedHashMap<>();
            for (SlotWeekday weekday : SlotWeekday.values()) {
                Map<SlotDaypart, Long> cells = slotGrid.get(weekday);
                if (weekday == SlotWeekday.UNKNOWN || cells == null) {
                    continue;
                }
                Map<String, Long> row = new LinkedHashMap<>();
                for (SlotDaypart part : SlotDaypart.values()) {
                    if (part != SlotDaypart.UNKNOWN) {
                        row.put(part.key(), cells.getOrDefault(part, 0L));
                    }
                }
                grid.put(weekday.key(), Collections.unmodifiableMap(row));
            }
            Map<String, Long> lead = new LinkedHashMap<>();
            for (LeadTimeBucket bucket : LeadTimeBucket.values()) {
                lead.put(bucket.key(), leadTime.getOrDefault(bucket, 0L));
            }
            return new InsightsDay(bookings, Collections.unmodifiableMap(dayparts),
                Collections.unmodifiableMap(grid), slotUnknown, Collections.unmodifiableMap(lead),
                groupMap(groups), mobility, serviceViews, funnel);
        }
    }

    private static final class BranchAcc {
        private long bookings;
        private long serviceViews;
        private final Map<ServiceGroup, Long> groups = new EnumMap<>(ServiceGroup.class);
        private long mobility;
        private long offHours;
        private long slotLate;

        void add(BookingRow booking, Set<ServiceGroup> bookingGroups, boolean outsideHours) {
            bookings++;
            bookingGroups.forEach(group -> groups.merge(group, 1L, Long::sum));
            if (booking.mobility()) {
                mobility++;
            }
            if (outsideHours) {
                offHours++;
            }
            if (booking.slotDaypart() == SlotDaypart.LATE) {
                slotLate++;
            }
        }

        BranchCounts toCounts() {
            return new BranchCounts(bookings, serviceViews, groupMap(groups), mobility, offHours, slotLate);
        }
    }

    /**
     * Cumulative funnel counters / kumulierte Trichterzähler: erreicht(k) = Σ sessions with
     * MaxStep ≥ k, over all sessions and per device class (BR-8). Exact sums, never
     * {@code dcount()}. / Exakt gezählt.
     */
    private static final class FunnelAcc {
        private final long[] all = new long[STAGE_BOOKED];
        private final Map<String, long[]> byDevice = new HashMap<>();

        void add(int maxStep, String device, long sessions) {
            long[] perDevice = byDevice.computeIfAbsent(device, d -> new long[STAGE_BOOKED]);
            for (int stage = 1; stage <= maxStep; stage++) {
                all[stage - 1] += sessions;
                perDevice[stage - 1] += sessions;
            }
        }

        FunnelCounts all() {
            return counts(all);
        }

        Map<String, FunnelCounts> byDevice() {
            Map<String, FunnelCounts> devices = new LinkedHashMap<>();
            for (String device : DEVICES) {
                devices.put(device, counts(byDevice.getOrDefault(device, new long[STAGE_BOOKED])));
            }
            return Collections.unmodifiableMap(devices);
        }

        private static FunnelCounts counts(long[] reached) {
            return new FunnelCounts(reached[0], reached[1], reached[2], reached[3], reached[4]);
        }
    }
}
