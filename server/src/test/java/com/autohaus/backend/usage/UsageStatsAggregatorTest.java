package com.autohaus.backend.usage;

import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.reporting.LogAnalyticsClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Aggregation of one calendar day out of raw KQL rows (REQ-037 TC-4, TC-5, TC-14) / Aggregation
 * eines Kalendertages aus KQL-Rohzeilen.
 *
 * <p>The aggregator only ever sees pre-aggregated rows, so the exclusions of BR-10
 * ({@code /api/v1/health}, {@code /internal/**}, lines without a {@code sessionId}) can only be
 * asserted where they are actually enforced: in the generated query text. The tests therefore
 * capture every KQL string handed to {@link LogAnalyticsClient} and assert against it. / Die
 * Ausschlüsse wirken in der Query, daher wird der Query-Text geprüft.
 *
 * <p>The HTML-escaping half of TC-14 belongs to the renderer and is covered in
 * {@code UsageSectionRendererTest}. / Die Escaping-Hälfte von TC-14 prüft der Renderer-Test.
 */
@ExtendWith(MockitoExtension.class)
class UsageStatsAggregatorTest {

    /** Europe/Berlin, CEST (UTC+2) — the day of the §10.3 blob example. */
    private static final LocalDate DAY = LocalDate.of(2026, 9, 20);

    @Mock private LogAnalyticsClient logClient;

    private final ObjectMapper om = new ObjectMapper();

    /** Every KQL string the aggregator sent, in call order / alle abgesetzten KQL-Abfragen. */
    private final List<String> queries = new ArrayList<>();

    /** Device rows as {@code [Device, visits]} — the {@code by} column comes first (§12.4). */
    private List<String[]> deviceRows = List.of(
        new String[] {"mobile", "41"},
        new String[] {"desktop", "12"},
        new String[] {"unknown", "1"});

    private UsageStatsAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new UsageStatsAggregator(logClient, properties("Europe/Berlin"));
        // lenient: the unavailability test replaces this stubbing with a throwing one.
        lenient().when(logClient.query(anyString())).thenAnswer(invocation -> {
            String kql = invocation.getArgument(0);
            queries.add(kql);
            if (kql.contains("summarize visits = count() by Device")) {
                return deviceResponse();
            }
            if (kql.contains("fileRequests")) {
                // REQ-043 row: [apiRequests, fileRequests].
                return requestsResponse(1708, 6170);
            }
            // Funnel rows: [requests, requestsOk, sessions, failedSessions] — §10.3 plus REQ-043.
            return kql.contains("/api/v1/snaps")
                ? funnelResponse(61, 58, 27, 2)
                : funnelResponse(11, 10, 9, 1);
        });
    }

    // ---- TC-4: aggregation out of raw KQL rows / Aggregation aus KQL-Rohzeilen ----

    @Test
    void aggregatesOneDayFromKqlRows() {
        Optional<DailyUsage> result = aggregator.aggregateDailyUsage(DAY);

        assertTrue(result.isPresent());
        DailyUsage usage = result.get();
        assertEquals(54L, usage.visits().total());
        assertEquals(41L, usage.visits().mobile());
        assertEquals(0L, usage.visits().tablet());
        assertEquals(12L, usage.visits().desktop());
        assertEquals(1L, usage.visits().unknown());
        assertEquals(61L, usage.searchRequests());
        assertEquals(58L, usage.searchRequestsOk());
        assertEquals(11L, usage.bookingRequests());
        assertEquals(10L, usage.bookingRequestsOk());
        assertEquals(27L, usage.sessionsWithSearch());
        assertEquals(9L, usage.sessionsWithBooking());
        assertEquals(1708L, usage.apiRequests());
        assertEquals(6170L, usage.fileRequests());
        assertEquals(2L, usage.searchSessionsFailed());
        assertEquals(1L, usage.bookingSessionsFailed());
    }

    /**
     * REQ-043 K-3: a failed session has no successful call at all — sessions without id are not
     * customers and never count. / Sitzung ganz ohne Erfolg, ohne Session-ID zählt nicht.
     */
    @Test
    void funnelQueryCountsSessionsWithoutAnySuccessAsFailed() {
        aggregator.aggregateDailyUsage(DAY);

        String funnel = queries.stream().filter(q -> q.contains("/api/v1/snaps")).findFirst().orElseThrow();
        assertTrue(funnel.contains("failedSessions = countif(okHits == 0 and isnotempty(SessionId))"));
    }

    /**
     * BR-1/BR-2: an empty {@code Device} (log line from before the deploy, i.e. the whole 25-day
     * catch-up) and a foreign value both land in {@code unknown}, so the four classes stay
     * exhaustive and their sum still equals the visit count (AC-4). / Leerstring und fremder Wert
     * landen beide in {@code unknown}.
     */
    @Test
    void mapsEmptyAndForeignDeviceValuesToUnknown() {
        deviceRows = List.of(
            new String[] {"mobile", "41"},
            new String[] {"desktop", "12"},
            new String[] {"unknown", "1"},
            new String[] {"", "5"},
            new String[] {"bot", "3"});

        DailyUsage usage = aggregator.aggregateDailyUsage(DAY).orElseThrow();

        assertEquals(9L, usage.visits().unknown());
        assertEquals(41L, usage.visits().mobile());
        assertEquals(0L, usage.visits().tablet());
        assertEquals(12L, usage.visits().desktop());
        // 41 + 0 + 12 + 9 — the sum of the four classes is still the visit count (AC-4).
        assertEquals(62L, usage.visits().total());
    }

    // ---- TC-5: day boundaries in Europe/Berlin, handed over as UTC / Grenzen in Europe/Berlin ----

    /**
     * TC-5: the KQL lower bound of 01.10.2026 is {@code 2026-09-30T22:00:00Z} (CEST, UTC+2) and
     * <b>not</b> {@code 2026-10-01T00:00:00Z}. {@code startofmonth(now())} and friends are UTC
     * based and would cut the day two hours late (BR-7). / Untergrenze in UTC, nicht in Ortszeit.
     *
     * <p>The period half of TC-5 — the month block holding only 01.10. while the year block holds
     * September and October — is asserted in {@code UsageStatsServiceTest}. / Die Perioden-Hälfte
     * prüft der Service-Test.
     */
    @Test
    void translatesBerlinDayBoundariesToUtc() {
        aggregator.aggregateDailyUsage(LocalDate.of(2026, 10, 1));

        assertFalse(queries.isEmpty());
        for (String kql : queries) {
            assertTrue(kql.contains("datetime(2026-09-30T22:00:00Z)"),
                "lower bound must be the UTC instant of 01.10.2026 00:00 Europe/Berlin: " + kql);
            assertTrue(kql.contains("datetime(2026-10-01T22:00:00Z)"),
                "upper bound must be the UTC instant of 02.10.2026 00:00 Europe/Berlin: " + kql);
            assertFalse(kql.contains("datetime(2026-10-01T00:00:00Z)"),
                "local midnight must never reach the query: " + kql);
            assertFalse(kql.contains("startofmonth"),
                "startofmonth() is UTC based and excluded (BR-7): " + kql);
        }

        // Same rule in winter time (CET, UTC+1): 15.01.2026 starts at 14.01.2026T23:00:00Z.
        queries.clear();
        aggregator.aggregateDailyUsage(LocalDate.of(2026, 1, 15));
        for (String kql : queries) {
            assertTrue(kql.contains("datetime(2026-01-14T23:00:00Z)"), kql);
            assertTrue(kql.contains("datetime(2026-01-15T23:00:00Z)"), kql);
        }
    }

    // ---- TC-14: exclusions / Ausschlüsse (BR-10) ----

    /**
     * TC-14/BR-10: {@code /api/v1/health} (≈ 1 440 ping requests a day), every {@code /internal/**}
     * path and every line without a {@code sessionId} are excluded from <b>all</b> counters; all
     * queries additionally pin the container app, because the workspace is the resource group's
     * shared default workspace. / Health, {@code /internal/**} und Zeilen ohne Session-ID fließen
     * in keine Kennzahl ein.
     */
    @Test
    void excludesHealthInternalAndSessionlessLines() {
        aggregator.aggregateDailyUsage(DAY);

        assertEquals(4, queries.size(),
            "one device query, one funnel query per endpoint and the REQ-043 request query");
        for (String kql : queries) {
            assertTrue(kql.contains("ContainerAppName_s == 'gs-terminbuchungs-app'"),
                "the shared default workspace must be filtered by container app: " + kql);
        }

        String visits = queryContaining("summarize visits = count() by Device");
        assertTrue(visits.contains("Url startswith \"/api/\""),
            "/internal/** is excluded by only counting /api/ urls");
        assertTrue(visits.contains("Url != \"/api/v1/health\""), "the health ping is excluded");
        assertTrue(visits.contains("isnotempty(SessionId)"),
            "lines without a sessionId are excluded (BR-4/BR-10)");

        // The funnel counts a session only when it carries a sessionId; a 4xx/5xx attempt counts
        // towards the request number but never towards the funnel (BR-5).
        String search = queryContaining("/api/v1/snaps");
        assertTrue(search.contains("countif(Status < 400 and isnotempty(SessionId))"), search);
        assertTrue(search.contains("requestsOk = countif(Status < 400)"), search);
        assertNotNull(queryContaining("/api/v1/appointments"));

        // REQ-043: the load figure counts every status and sessionless lines, but never the
        // health ping or the status page itself.
        String requests = queryContaining("fileRequests");
        assertTrue(requests.contains("Url != \"/api/v1/health\""), requests);
        assertTrue(requests.contains("not(Url startswith \"/internal\")"), requests);
        assertFalse(requests.contains("SessionId"), "sessionless requests are load too");
        assertFalse(requests.contains("Status"), "failed requests are load too");
    }

    /**
     * TC-14: the aggregator builds its queries from constants and ISO instants only — there is no
     * free-text input that could break out of the KQL. / Keine Freitext-Eingabe in der Query.
     */
    @Test
    void buildsQueriesFromConstantsAndIsoInstantsOnly() {
        aggregator.aggregateDailyUsage(DAY);

        for (String kql : queries) {
            assertTrue(kql.matches("(?s).*datetime\\(2026-09-19T22:00:00Z\\).*"), kql);
            assertTrue(kql.matches("(?s).*datetime\\(2026-09-20T22:00:00Z\\).*"), kql);
        }
        String search = queryContaining("/api/v1/snaps");
        assertFalse(search.contains("/api/v1/appointments"),
            "each funnel query carries exactly one endpoint constant");
    }

    // ---- BR-4: exact counting, no HyperLogLog estimate / exakt zählen, nicht schätzen ----

    /**
     * BR-4: {@code dcount()}/{@code dcountif()} are HyperLogLog estimators (≈ 1.6 % error) and are
     * excluded without discussion — the session counts are numerator and denominator of all three
     * conversion rates (BR-6), which are specified to one decimal place. / {@code dcount} ist in
     * keiner Query zulässig.
     */
    @Test
    void neverUsesDcountEstimators() {
        aggregator.aggregateDailyUsage(DAY);

        assertFalse(queries.isEmpty());
        for (String kql : queries) {
            assertFalse(kql.contains("dcount"),
                "dcount()/dcountif() are HyperLogLog estimators and excluded (BR-4): " + kql);
            assertFalse(kql.contains("dcountif"), kql);
        }
        // The exact two-stage form of §12.4 is what replaces them.
        assertTrue(queryContaining("summarize visits = count() by Device")
            .contains("summarize Device = take_any(Device) by SessionId"));
    }

    // ---- E1: Log Analytics unavailable / Log Analytics nicht erreichbar ----

    /**
     * E1: an unreachable Log Analytics yields {@link Optional#empty()} instead of an exception —
     * the caller keeps the previous snapshot and the page reports „Live-Daten derzeit nicht
     * verfügbar" (TC-8, asserted end to end in {@code UsageStatsSchedulerTest}). / Leeres
     * {@link Optional} statt Exception.
     */
    @Test
    void returnsEmptyWhenLogAnalyticsIsUnavailable() {
        when(logClient.query(anyString()))
            .thenThrow(new LogAnalyticsClient.LogAnalyticsUnavailableException("no identity endpoint"));

        assertEquals(Optional.empty(), aggregator.aggregateDailyUsage(DAY));
    }

    /** A day without any traffic is „0 Besuche", not „keine Daten" (AC-12). */
    @Test
    void aDayWithoutTrafficIsZeroNotMissing() {
        deviceRows = List.of();

        DailyUsage usage = aggregator.aggregateDailyUsage(DAY).orElseThrow();

        assertEquals(0L, usage.visits().total());
        assertEquals(61L, usage.searchRequests());
    }

    // ---- helpers ----

    private String queryContaining(String needle) {
        return queries.stream()
            .filter(kql -> kql.contains(needle))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no query containing: " + needle));
    }

    /** Log Analytics response with the device rows {@code [Device, visits]}. */
    private JsonNode deviceResponse() {
        ObjectNode root = om.createObjectNode();
        ArrayNode rows = root.putArray("tables").addObject().putArray("rows");
        for (String[] deviceRow : deviceRows) {
            ArrayNode row = rows.addArray();
            row.add(deviceRow[0]);
            row.add(deviceRow[1]);
        }
        return root;
    }

    /** REQ-043: Log Analytics response with the single row {@code [apiRequests, fileRequests]}. */
    private JsonNode requestsResponse(long apiRequests, long fileRequests) {
        ObjectNode root = om.createObjectNode();
        ArrayNode row = root.putArray("tables").addObject().putArray("rows").addArray();
        row.add(String.valueOf(apiRequests));
        row.add(String.valueOf(fileRequests));
        return root;
    }

    /**
     * Log Analytics response with the single funnel row
     * {@code [requests, requestsOk, sessions, failedSessions]}.
     */
    private JsonNode funnelResponse(long requests, long requestsOk, long sessions, long failedSessions) {
        ObjectNode root = om.createObjectNode();
        ArrayNode rows = root.putArray("tables").addObject().putArray("rows");
        ArrayNode row = rows.addArray();
        row.add(String.valueOf(requests));
        row.add(String.valueOf(requestsOk));
        row.add(String.valueOf(sessions));
        row.add(String.valueOf(failedSessions));
        return root;
    }

    private static UsageStatsProperties properties(String zone) {
        return new UsageStatsProperties(true, "0 */15 * * * *", "0 30 3 * * *", zone, null, 24, 60,
            new UsageStatsProperties.Storage("", "usage-stats", "usage-daily.json"));
    }
}
