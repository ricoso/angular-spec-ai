package com.autohaus.backend.usage.insights;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.config.setup.BranchSetup;
import com.autohaus.backend.config.setup.GroupEntry;
import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.reporting.LogAnalyticsClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One Berlin day out of hand-built Log Analytics rows (REQ-038 TC-17, TC-18, TC-19, TC-46, TC-62) /
 * ein Berliner Tag aus handgebauten Log-Analytics-Zeilen.
 *
 * <p>The query texts are asserted where BR-7 is enforced — in the constants and in every string
 * actually handed to {@link LogAnalyticsClient}; the row processing (whitelist, cumulative funnel,
 * drop-off margins) is asserted on the resulting {@link AggregatedDay}. Pattern
 * {@code UsageStatsAggregatorTest}. / Query-Text und Zeilenverarbeitung werden getrennt geprüft.
 *
 * <p>KQL 1 additionally runs against raw log records through {@link #emulateBookingsKql}: the time
 * window, the prefix anchor, the JSON regex and the day clause are taken from the query text
 * itself, so the literals of the daily flush (§10.2, §12.8) are under test. / KQL 1 läuft zusätzlich
 * gegen Roh-Logzeilen; Fenster, Präfix-Anker, Regex und Tagesbedingung stammen aus dem Query-Text.
 */
@ExtendWith(MockitoExtension.class)
class InsightsAggregatorTest {

    /** Thursday 22.10.2026 (CEST) — the §12.8 example day. */
    private static final LocalDate DAY = LocalDate.of(2026, 10, 22);

    private static final String BRANCH_A = "68f0a1b2c3d4e5f6a7b8c9d0";
    private static final String BRANCH_B = "68f0a1b2c3d4e5f6a7b8c9d4";
    private static final String FOREIGN_BRANCH = "68f0a1b2c3d4e5f6a7b8c9ee";
    private static final String GROUP_A = "6908a1b2c3d4e5f6a7b8c9d1";
    private static final String GROUP_A_TIRE = "6908a1b2c3d4e5f6a7b8c9d2";
    private static final String GROUP_B = "6908a1b2c3d4e5f6a7b8c9d3";
    private static final String OP_1 = "6908a1b2c3d4e5f6a7b8c9e2";
    private static final String OP_2 = "6908a1b2c3d4e5f6a7b8c9e3";

    private static final ObjectMapper OM = new ObjectMapper();

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /** Log time of the console pattern {@code %d{ISO8601}} / Zeitstempel des Log-Musters. */
    private static final DateTimeFormatter LOG_TIME =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS").withZone(BERLIN);

    /** Logger column of the flush line ({@code %logger{36}}) / Logger-Spalte der Flush-Zeile. */
    private static final String FLUSH_LOGGER = "c.a.b.u.insights.UsageEventLogger";

    // ---- KQL 1 clauses read back from the query text / aus dem Query-Text gelesene Klauseln ----
    private static final Pattern KQL_WINDOW = Pattern.compile(
        "TimeGenerated >= datetime\\(([^)]+)\\) and TimeGenerated < datetime\\(([^)]+)\\) \\+ (\\d+)m");
    private static final Pattern KQL_HAS = Pattern.compile("Log_s has '([^']*)'");
    private static final Pattern KQL_CONTAINS_CS = Pattern.compile("Log_s contains_cs '([^']*)'");
    private static final Pattern KQL_PREFIX = Pattern.compile(
        "where extract\\(@'([^']*)', 1, Log_s\\) == '([^']*)'");
    private static final Pattern KQL_JSON = Pattern.compile("extend Raw = extract\\(@'([^']*)', 1, Log_s\\)");
    private static final Pattern KQL_DAY_EXTRACT = Pattern.compile("extend Day = extract\\(@'([^']*)', 1, Raw\\)");
    private static final Pattern KQL_DAY = Pattern.compile(
        "where Day == '([^']*)' or \\(isempty\\(Day\\) and TimeGenerated < datetime\\(([^)]+)\\)\\)");

    /** Placeholders of the query templates ({@code %s}, {@code %1$s}) / Platzhalter. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?s");

    @Mock private LogAnalyticsClient logClient;
    @Mock private SetupService setupService;
    @Mock private ServiceGroupClassifier classifier;

    /** Every KQL string sent, in call order / alle abgesetzten Abfragen. */
    private final List<String> queries = new ArrayList<>();

    private List<List<Object>> bookingRows = new ArrayList<>();
    private List<List<Object>> viewRows = new ArrayList<>();
    private List<List<Object>> funnelRows = new ArrayList<>();
    private List<List<Object>> dropoffRows = new ArrayList<>();

    /** Raw records KQL 1 is emulated on; {@code null} = answer with {@link #bookingRows}. */
    private List<LogRecord> bookingLog;

    private ListAppender<ILoggingEvent> logs;
    private InsightsAggregator aggregator;

    @BeforeEach
    void setUp() {
        lenient().when(setupService.getAllBranches()).thenReturn(List.of(
            new BranchSetup(BRANCH_A, "Autohaus Nord", null, List.of(
                new GroupEntry("default", GROUP_A, "Nord"), new GroupEntry("tireChange", GROUP_A_TIRE, "Nord R"))),
            new BranchSetup(BRANCH_B, "Autohaus Süd", null, List.of(new GroupEntry("default", GROUP_B, "Süd")))));
        lenient().when(classifier.classify(anyString(), anyList(), anyString()))
            .thenReturn(Set.of(ServiceGroup.INSPECTION));
        lenient().when(logClient.query(anyString())).thenAnswer(invocation -> {
            String kql = invocation.getArgument(0);
            queries.add(kql);
            if (kql.contains("USAGE_BOOKING")) {
                return response(bookingLog == null ? bookingRows : emulateBookingsKql(kql, bookingLog));
            }
            if (kql.contains("summarize serviceViews")) {
                return response(viewRows);
            }
            if (kql.contains("let From")) {
                return response(dropoffRows);
            }
            return response(funnelRows);
        });
        aggregator = new InsightsAggregator(logClient, setupService, classifier, properties("Europe/Berlin"));
        Logger logger = (Logger) LoggerFactory.getLogger(InsightsAggregator.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(InsightsAggregator.class)).detachAppender(logs);
    }

    // ---- TC-17: BR-7 string test / String-Prüfung ----

    @Test
    @DisplayName("TC-17: no query contains join or traceId, none SessionId together with a branch id")
    void noQueryCombinesSessionIdWithABranchOrJoins() {
        for (String kql : allQueries()) {
            assertFalse(kql.contains("join"), kql);
            assertFalse(kql.contains("traceId"), kql);
            assertFalse(kql.contains("dcount"), "exact counting, never dcount (BR-8): " + kql);
            assertTrue(kql.contains("where ContainerAppName_s == 'gs-terminbuchungs-app'"), kql);
            boolean session = kql.contains("SessionId") || kql.contains("sessionId");
            boolean branch = kql.contains("BranchId") || kql.contains("([0-9a-f]{24})");
            assertFalse(session && branch, "sessionId and a branch id in one query: " + kql);
        }
    }

    @Test
    @DisplayName("TC-17: FUNNEL_KQL/DROPOFF_KQL have no capture group, no \"url\":\"(, no Url, no Log_s after project")
    void theSessionQueriesNeverExposeAUrlOrTheRawLine() {
        for (String kql : List.of(InsightsAggregator.FUNNEL_KQL, InsightsAggregator.DROPOFF_KQL)) {
            assertFalse(kql.contains("([0-9a-f]{24})"), kql);
            assertFalse(kql.contains("\"url\":\"("), kql);
            assertFalse(kql.contains("Url"), kql);
            int project = kql.indexOf("| project");
            assertTrue(project > 0, kql);
            assertEquals(project, kql.lastIndexOf("| project"), "exactly one project: " + kql);
            assertFalse(kql.substring(project).contains("Log_s"), "Log_s after the project: " + kql);
        }
    }

    @Test
    @DisplayName("TC-17: the bookings query never reads sessionId or device; KQL 1 is capped with take 5000")
    void theBookingQueriesCarryNoSessionAndNoDevice() {
        for (String kql : List.of(InsightsAggregator.BOOKINGS_KQL, InsightsAggregator.SERVICE_VIEWS_KQL)) {
            assertFalse(kql.contains("sessionId"), kql);
            assertFalse(kql.contains("device"), kql);
        }
        assertTrue(InsightsAggregator.BOOKINGS_KQL.contains("| take 5000"));
        assertEquals(5000, InsightsAggregator.TAKE_LIMIT);
        assertTrue(InsightsAggregator.BOOKINGS_KQL.contains(
                "extract(@' - job\\.usagebooking: USAGE_BOOKING (\\{.*\\})\\s*$', 1, Log_s)"),
            "the regex survives the Java text block verbatim: " + InsightsAggregator.BOOKINGS_KQL);
    }

    @Test
    @DisplayName("§12.8: KQL 1 is anchored on the flush prefix ' - job.usagebooking: USAGE_BOOKING ', not just on the marker")
    void theBookingQueryIsAnchoredOnTheFlushPrefix() {
        String kql = InsightsAggregator.BOOKINGS_KQL;
        assertTrue(kql.contains("| where Log_s has 'USAGE_BOOKING' and Log_s contains_cs ' - "
            + UsageEventLogger.TRACE_ID + ": " + UsageEventLogger.MARKER + " '"), kql);
        assertTrue(kql.contains("| where extract(@' - ([A-Za-z0-9_.-]{0,64}): ', 1, Log_s) == '"
            + UsageEventLogger.TRACE_ID + "'"), "the FIRST prefix of the line is the flush marker: " + kql);
        assertTrue(kql.contains("TimeGenerated < datetime(%2$s) + " + InsightsAggregator.FLUSH_GRACE_MINUTES + "m"),
            "the window reaches past midnight: " + kql);
        assertTrue(kql.contains("| where Day == '%3$s' or (isempty(Day) and TimeGenerated < datetime(%2$s))"),
            "assigned by day; without day by the day window: " + kql);
        assertTrue(kql.contains("| extend Day = extract(@'\"day\":\"(\\d{4}-\\d{2}-\\d{2})\"', 1, Raw)"),
            "Day is read from the raw JSON text: " + kql);
        assertFalse(kql.contains("J.day"),
            "parse_json turns the ISO day into a datetime — tostring(J.day) never equals '2026-09-27': " + kql);
        assertEquals(10, InsightsAggregator.FLUSH_GRACE_MINUTES);
        assertTrue(Pattern.compile("Mobility = tobool\\(J\\.mobility\\),\\s+Day\\s+\\| take 5000").matcher(kql).find(),
            "Day is the last column, the first ten keep their positions: " + kql);
    }

    @Test
    @DisplayName("TC-17/BR-12: the Berlin day is passed as explicit UTC instants (also on the 25-h day)")
    void passesTheBerlinDayAsUtcInstants() {
        aggregator.aggregateDay(DAY, true, true, false);
        for (String kql : queries) {
            assertTrue(kql.contains("datetime(2026-10-21T22:00:00Z)"), kql);
            assertTrue(kql.contains("datetime(2026-10-22T22:00:00Z)"), kql);
            assertFalse(PLACEHOLDER.matcher(kql).find(), kql);
        }
        assertTrue(queries.get(0).contains("TimeGenerated < datetime(2026-10-22T22:00:00Z) + 10m"), queries.get(0));
        assertTrue(queries.get(0).contains("| where Day == '2026-10-22' or (isempty(Day) and "
            + "TimeGenerated < datetime(2026-10-22T22:00:00Z))"), queries.get(0));

        queries.clear();
        aggregator.aggregateDay(LocalDate.of(2026, 10, 25), false, true, true);
        assertEquals(1, queries.size());
        assertTrue(queries.get(0).startsWith(
            "let From = datetime(2026-10-24T22:00:00Z); let To = datetime(2026-10-25T23:00:00Z);"),
            "DST end: 25-hour day (BR-12): " + queries.get(0));
    }

    // ---- TC-46 / TC-62: number of queries / Anzahl der Abfragen ----

    @Test
    @DisplayName("TC-46: only K2 → exactly two queries (KQL 1 + 2), no funnel query")
    void onlyBookingsRunsExactlyTwoQueries() {
        AggregatedDay result = aggregator.aggregateDay(DAY, true, false, false).orElseThrow();

        assertEquals(List.of(InsightsAggregator.BOOKINGS_KQL, InsightsAggregator.SERVICE_VIEWS_KQL),
            templatesOf(queries));
        assertNull(result.global().funnel(), "funnel: null while the funnel is off (§5.4)");
    }

    @Test
    @DisplayName("TC-46: only the funnel → exactly one query FUNNEL_KQL, bookings stay null")
    void onlyTheFunnelRunsExactlyOneQuery() {
        funnelRows = List.of(row(1, "mobile", 10));

        AggregatedDay result = aggregator.aggregateDay(DAY, false, true, false).orElseThrow();

        assertEquals(List.of(InsightsAggregator.FUNNEL_KQL), templatesOf(queries));
        assertNull(result.global().bookings(), "not collected → „—\", never 0 (BR-19)");
        assertNull(result.global().serviceViews());
        assertTrue(result.branches().isEmpty());
        assertNull(result.global().funnel().dropoff(), "dropoff: null while switched off (AC-35)");
    }

    @Test
    @DisplayName("TC-62: drop-off on → DROPOFF_KQL replaces FUNNEL_KQL; with K2 at most three queries")
    void theDropoffQueryReplacesTheFunnelQuery() {
        aggregator.aggregateDay(DAY, false, true, true);
        assertEquals(List.of(InsightsAggregator.DROPOFF_KQL), templatesOf(queries));

        queries.clear();
        aggregator.aggregateDay(DAY, true, true, true);
        assertEquals(List.of(InsightsAggregator.BOOKINGS_KQL, InsightsAggregator.SERVICE_VIEWS_KQL,
            InsightsAggregator.DROPOFF_KQL), templatesOf(queries));

        queries.clear();
        aggregator.aggregateDay(DAY, true, true, false);
        assertEquals(List.of(InsightsAggregator.BOOKINGS_KQL, InsightsAggregator.SERVICE_VIEWS_KQL,
            InsightsAggregator.FUNNEL_KQL), templatesOf(queries));
    }

    @Test
    @DisplayName("TC-46: nothing requested → no query and an all-null day")
    void nothingRequestedRunsNoQuery() {
        AggregatedDay result = aggregator.aggregateDay(DAY, false, false, true).orElseThrow();

        assertTrue(queries.isEmpty());
        assertNull(result.global().bookings());
        assertNull(result.global().funnel());
    }

    // ---- bookings / Buchungen ----

    @Test
    @DisplayName("TC-18/§10.3: global and per-branch counters of one day")
    void aggregatesBookingsGloballyAndPerBranch() {
        when(classifier.classify(eq(GROUP_A), anyList(), eq("default")))
            .thenReturn(Set.of(ServiceGroup.INSPECTION, ServiceGroup.EXTRAS));
        when(classifier.classify(eq(GROUP_A_TIRE), anyList(), eq("tireChange")))
            .thenReturn(Set.of(ServiceGroup.TIRE_CHANGE));
        bookingRows = List.of(
            booking(BRANCH_A, GROUP_A, "default", "[\"" + OP_1 + "\"]", "day", "MO", "morning", "2-3", false),
            booking(BRANCH_A, GROUP_A, "default", "[\"" + OP_1 + "\"]", "evening", "MO", "late", "0-1", true),
            booking(BRANCH_A, GROUP_A_TIRE, "tireChange", "[\"" + OP_2 + "\"]", "night", "SA", "early", "31+", false),
            booking(BRANCH_B, GROUP_B, "default", "[]", "day", "unknown", "unknown", "unknown", true));
        viewRows = List.of(row(BRANCH_A, 19), row(BRANCH_B, 7), row(FOREIGN_BRANCH, 99), row("not-hex", 5));

        AggregatedDay result = aggregator.aggregateDay(DAY, true, false, false).orElseThrow();

        InsightsDay global = result.global();
        assertEquals(4L, global.bookings());
        assertEquals(Map.of("day", 2L, "evening", 1L, "night", 1L), global.bookingDaypart());
        assertEquals(List.of("day", "evening", "night"), List.copyOf(global.bookingDaypart().keySet()));
        assertEquals(Map.of("early", 0L, "morning", 1L, "midday", 0L, "afternoon", 0L, "late", 1L),
            global.slotGrid().get("MO"));
        assertEquals(1L, global.slotGrid().get("SA").get("early"));
        assertFalse(global.slotGrid().containsKey("TU"), "weekdays without bookings are left out");
        assertEquals(1L, global.slotUnknown(), "E12: booking without usable slot time");
        assertEquals(7, global.leadTime().size());
        assertEquals(1L, global.leadTime().get("0-1"));
        assertEquals(1L, global.leadTime().get("2-3"));
        assertEquals(1L, global.leadTime().get("31+"));
        assertEquals(1L, global.leadTime().get("unknown"));
        assertEquals(0L, global.leadTime().get("4-7"));
        assertEquals(Map.of("inspection", 3L, "tire-change", 1L, "extras", 2L), global.groups());
        assertEquals(2L, global.mobility());
        assertEquals(26L, global.serviceViews(), "foreign and malformed branch ids are dropped (E11)");

        BranchCounts a = result.branches().get(BRANCH_A);
        assertEquals(3L, a.bookings());
        assertEquals(19L, a.serviceViews());
        assertEquals(Map.of("inspection", 2L, "tire-change", 1L, "extras", 2L), a.groups());
        assertEquals(1L, a.mobility());
        assertEquals(2L, a.offHours(), "evening and night count outside Mo–Fr 07–18 (T-2)");
        assertEquals(1L, a.slotLate());

        BranchCounts b = result.branches().get(BRANCH_B);
        assertEquals(1L, b.bookings());
        assertEquals(7L, b.serviceViews());
        assertEquals(0L, b.offHours());
        assertEquals(0L, b.slotLate());
        assertEquals(Set.of(BRANCH_A, BRANCH_B), result.branches().keySet(),
            "every setup.json branch, no foreign one");
    }

    @Test
    @DisplayName("T-2: on a Saturday every booking counts as outside the business hours")
    void weekendBookingsCountAsOffHours() {
        bookingRows = List.of(bookingOn(LocalDate.of(2026, 10, 24), BRANCH_A, GROUP_A, "default", "[]", "day",
            "MO", "morning", "2-3", false));

        AggregatedDay result = aggregator.aggregateDay(LocalDate.of(2026, 10, 24), true, false, false)
            .orElseThrow();

        assertEquals(1L, result.branches().get(BRANCH_A).offHours());
    }

    @Test
    @DisplayName("TC-18: foreign branch, v = 3 and a foreign bookingDaypart are discarded; foreign slotDaypart → unknown")
    void discardsRowsOutsideTheWhitelist() {
        bookingRows = new ArrayList<>(List.of(
            booking(FOREIGN_BRANCH, GROUP_A, "default", "[]", "day", "MO", "morning", "2-3", false),
            booking("68F0A1B2C3D4E5F6A7B8C9D0", GROUP_A, "default", "[]", "day", "MO", "morning", "2-3", false),
            booking(BRANCH_A, GROUP_A, "default", "[]", "brunch", "MO", "morning", "2-3", false),
            booking(BRANCH_A, GROUP_A, "default", "[]", "day", "XX", "tea-time", "99", false),
            List.of(1, BRANCH_A, GROUP_A)));
        List<Object> version3 = new ArrayList<>(booking(BRANCH_A, GROUP_A, "default", "[]", "day", "MO",
            "morning", "2-3", false));
        version3.set(0, 3);
        bookingRows.add(version3);
        List<Object> noVersion = new ArrayList<>(version3);
        noVersion.set(0, "");
        bookingRows.add(noVersion);

        AggregatedDay result = aggregator.aggregateDay(DAY, true, false, false).orElseThrow();

        assertEquals(1L, result.global().bookings(), "only the row with foreign enum values survives");
        assertEquals(1L, result.global().slotUnknown(), "slotDaypart tea-time → unknown");
        assertEquals(1L, result.global().leadTime().get("unknown"));
        assertFalse(result.branches().containsKey(FOREIGN_BRANCH));
        List<String> warnings = messages(Level.WARN);
        assertTrue(warnings.stream().anyMatch(m -> m.contains("6 booking row(s) discarded")), warnings.toString());
        assertTrue(warnings.stream().noneMatch(m -> m.contains(FOREIGN_BRANCH) || m.contains("brunch")),
            "no rejected values in the log: " + warnings);
    }

    @Test
    @DisplayName("TC-18: at most 20 operation ids, only 24 hex, handed to the classifier")
    void passesAtMostTwentyValidOperationIdsToTheClassifier() {
        List<String> ops = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            ops.add("\"6908a1b2c3d4e5f6a7b8c%03d\"".formatted(i));
        }
        ops.add(1, "\"NOT-HEX\"");
        bookingRows = List.of(booking(BRANCH_A, GROUP_A, "default", "[" + String.join(",", ops) + "]",
            "day", "MO", "morning", "2-3", false));

        aggregator.aggregateDay(DAY, true, false, false).orElseThrow();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(classifier).classify(eq(GROUP_A), captor.capture(), eq("default"));
        assertEquals(20, captor.getValue().size());
        assertTrue(captor.getValue().stream().allMatch(id -> id.matches("^[a-f0-9]{24}$")));
    }

    @Test
    @DisplayName("E11: ops as {id} objects are read; unparseable ops become an empty list")
    void parsesOperationIdsDefensively() {
        assertEquals(List.of(OP_1), InsightsAggregator.parseOps("[{\"id\":\"" + OP_1 + "\"}, 7, null]"));
        assertEquals(List.of(), InsightsAggregator.parseOps("not json"));
        assertEquals(List.of(), InsightsAggregator.parseOps("{\"id\":\"" + OP_1 + "\"}"));
        assertEquals(List.of(), InsightsAggregator.parseOps(""));
        assertEquals(List.of(), InsightsAggregator.parseOps(null));
    }

    @Test
    @DisplayName("E11: a group outside the branch's setup.json groups is never sent to SoftNet")
    void aForeignGroupIsClassifiedUnknownWithoutSoftnet() {
        bookingRows = List.of(
            booking(BRANCH_B, GROUP_A, "default", "[\"" + OP_1 + "\"]", "day", "MO", "morning", "2-3", false),
            booking(BRANCH_B, "garbage", "tireChange", "[\"" + OP_1 + "\"]", "day", "MO", "morning", "2-3", false));

        AggregatedDay result = aggregator.aggregateDay(DAY, true, false, false).orElseThrow();

        verify(classifier, never()).classify(any(), any(), any());
        assertEquals(Map.of("tire-change", 1L, "unknown", 2L), result.global().groups());
    }

    @Test
    @DisplayName("TC-18/E11: a query at its take limit (5000 rows) makes the day incomplete (WARN)")
    void aQueryAtItsTakeLimitSkipsTheDay() {
        List<List<Object>> rows = new ArrayList<>();
        List<Object> one = booking(BRANCH_A, GROUP_A, "default", "[]", "day", "MO", "morning", "2-3", false);
        for (int i = 0; i < InsightsAggregator.TAKE_LIMIT; i++) {
            rows.add(one);
        }
        bookingRows = rows;

        Optional<AggregatedDay> result = aggregator.aggregateDay(DAY, true, true, false);

        assertTrue(result.isEmpty());
        assertEquals(1, queries.size(), "nothing else is queried for an incomplete day");
        assertTrue(messages(Level.WARN).stream().anyMatch(m -> m.contains("take limit")));
    }

    @Test
    @DisplayName("TC-18/E11: the take limit also guards KQL 2 and the funnel queries")
    void theTakeLimitGuardsEveryQuery() {
        viewRows = Collections.nCopies(InsightsAggregator.TAKE_LIMIT, row(BRANCH_A, 1));
        assertTrue(aggregator.aggregateDay(DAY, true, false, false).isEmpty());

        funnelRows = Collections.nCopies(InsightsAggregator.TAKE_LIMIT, row(1, "mobile", 1));
        assertTrue(aggregator.aggregateDay(DAY, false, true, false).isEmpty());

        dropoffRows = Collections.nCopies(InsightsAggregator.TAKE_LIMIT, row("quiet-1", 1, "mobile", 1));
        assertTrue(aggregator.aggregateDay(DAY, false, true, true).isEmpty());
    }

    @Test
    @DisplayName("E3: Log Analytics unavailable → empty result, never a partial day")
    void logAnalyticsUnavailableYieldsNoDay() {
        when(logClient.query(anyString()))
            .thenThrow(new LogAnalyticsClient.LogAnalyticsUnavailableException("no identity"));

        assertTrue(aggregator.aggregateDay(DAY, true, true, true).isEmpty());
    }

    @Test
    @DisplayName("E9: a transient service-group failure makes the whole day unwritable")
    void aTransientClassificationFailureYieldsNoDay() {
        bookingRows = List.of(booking(BRANCH_A, GROUP_A, "default", "[\"" + OP_1 + "\"]", "day", "MO",
            "morning", "2-3", false));
        when(classifier.classify(anyString(), anyList(), anyString()))
            .thenThrow(new ServiceGroupClassifier.CatalogUnavailableException("500", null));

        assertTrue(aggregator.aggregateDay(DAY, true, false, false).isEmpty());
    }

    @Test
    @DisplayName("§10.3: a day without any booking yields zeros inside present maps, not null")
    void aDayWithoutBookingsYieldsExplicitZeros() {
        AggregatedDay result = aggregator.aggregateDay(DAY, true, false, false).orElseThrow();

        assertEquals(0L, result.global().bookings());
        assertEquals(0L, result.global().serviceViews());
        assertTrue(result.global().slotGrid().isEmpty());
        assertTrue(result.global().groups().isEmpty());
        assertEquals(0L, result.branches().get(BRANCH_A).bookings());
    }

    // ---- §10.2 day assignment of the daily flush / Tageszuordnung des Tagesflushs ----

    @Test
    @DisplayName("E11/§10.2: Java re-checks the day — v2 needs the aggregated ISO day, v1 counts only without day")
    void reChecksTheDayOfEveryBookingRow() {
        List<Object> v2WithoutDay = new ArrayList<>(booking(BRANCH_A, GROUP_A, "default", "[]", "day", "MO",
            "morning", "2-3", false).subList(0, 10));
        bookingRows = List.of(
            booking(BRANCH_A, GROUP_A, "default", "[]", "day", "MO", "morning", "2-3", false),            // counts
            bookingOn(DAY.minusDays(1), BRANCH_A, GROUP_A, "default", "[]", "day", "MO", "morning", "2-3", false),
            bookingOn(DAY.plusDays(1), BRANCH_A, GROUP_A, "default", "[]", "day", "MO", "morning", "2-3", false),
            withDay(booking(BRANCH_A, GROUP_A, "default", "[]", "day", "MO", "morning", "2-3", false), "22.10.2026"),
            withDay(booking(BRANCH_A, GROUP_A, "default", "[]", "day", "MO", "morning", "2-3", false),
                "2026-10-22T23:59:59"),
            withDay(booking(BRANCH_A, GROUP_A, "default", "[]", "day", "MO", "morning", "2-3", false), ""),
            v2WithoutDay,
            legacy(BRANCH_A, GROUP_A, "[]", "evening"),                                                  // counts
            withDay(withVersion(legacy(BRANCH_A, GROUP_A, "[]", "day"), 1), DAY.toString()),              // counts
            withDay(legacy(BRANCH_A, GROUP_A, "[]", "day"), DAY.minusDays(1).toString()),
            withVersion(booking(BRANCH_A, GROUP_A, "default", "[]", "day", "MO", "morning", "2-3", false), 3));

        AggregatedDay result = aggregator.aggregateDay(DAY, true, false, false).orElseThrow();

        assertEquals(3L, result.global().bookings(), "v2 with the day, v1 without day, v1 with the day");
        assertEquals(Map.of("day", 2L, "evening", 1L, "night", 0L), result.global().bookingDaypart());
        List<String> warnings = messages(Level.WARN);
        assertTrue(warnings.stream().anyMatch(m -> m.contains("8 booking row(s) discarded")), warnings.toString());
        assertTrue(warnings.stream().noneMatch(m -> m.contains("2026-10-21") || m.contains("22.10.2026")),
            "no rejected values in the log: " + warnings);
    }

    @Test
    @DisplayName("§10.2: belongsToDay — ISO only, exact day, versions 1 and 2 only")
    void belongsToDayAcceptsOnlyTheExactIsoDay() {
        assertTrue(InsightsAggregator.belongsToDay(2, "2026-10-22", DAY));
        assertTrue(InsightsAggregator.belongsToDay(1, "", DAY));
        assertTrue(InsightsAggregator.belongsToDay(1, null, DAY));
        assertTrue(InsightsAggregator.belongsToDay(1, "2026-10-22", DAY));
        assertFalse(InsightsAggregator.belongsToDay(2, "", DAY), "v2 without day");
        assertFalse(InsightsAggregator.belongsToDay(2, "2026-10-21", DAY));
        assertFalse(InsightsAggregator.belongsToDay(2, "2026-10-22 ", DAY), "trimmed by the caller only");
        assertFalse(InsightsAggregator.belongsToDay(2, "2026-1-22", DAY));
        assertFalse(InsightsAggregator.belongsToDay(3, "2026-10-22", DAY));
        assertFalse(InsightsAggregator.belongsToDay(null, "2026-10-22", DAY));
        assertFalse(InsightsAggregator.belongsToDay(0, "", DAY));
    }

    @Test
    @DisplayName("§12.8 KQL 1 on raw lines: the 23:59:59 flush arriving after midnight counts for its day, not the next")
    void assignsTheFlushLinesToTheirDayAcrossMidnight() {
        Instant dayEnd = Instant.parse("2026-10-22T22:00:00Z");     // 23.10. 00:00 MESZ
        bookingLog = List.of(
            flushLine(Instant.parse("2026-10-22T21:59:59.200Z"), bookingJson(DAY, op(1))),        // A1 day
            flushLine(dayEnd.plusSeconds(3), bookingJson(DAY, op(2))),                          // A2 after midnight
            flushLine(Instant.parse("2026-10-21T22:00:02Z"), bookingJson(DAY.minusDays(1), op(3))), // A3 yesterday
            flushLine(Instant.parse("2026-10-22T08:15:00Z"), bookingJson(DAY, op(4))),            // A4 @PreDestroy
            flushLine(dayEnd.plus(Duration.ofMinutes(11)), bookingJson(DAY, op(5))),             // A5 beyond grace
            flushLine(Instant.parse("2026-10-22T10:00:00Z"), legacyJson(op(8))),                  // A8 v1, in window
            flushLine(dayEnd.plus(Duration.ofMinutes(5)), legacyJson(op(9))));                   // A9 v1, next day

        assertEquals(List.of(op(1), op(2), op(4), op(8)), opsOfDay(DAY));
        assertEquals(List.of(op(9)), opsOfDay(DAY.plusDays(1)),
            "a line without day follows TimeGenerated; the after-midnight flush line of 22.10. is not counted twice");
        assertEquals(List.of(op(3)), opsOfDay(DAY.minusDays(1)), "yesterday's flush arriving after its midnight");

        queries.clear();
        AggregatedDay result = aggregator.aggregateDay(DAY, true, false, false).orElseThrow();
        assertEquals(4L, result.global().bookings());
        assertEquals(4L, result.branches().get(BRANCH_A).bookings());
    }

    @Test
    @DisplayName("§12.8 KQL 1 on raw lines: request lines, forged prefixes and job WARNs are never counted")
    void countsOnlyLinesOfTheFlushItself() {
        Instant at = Instant.parse("2026-10-22T10:00:00Z");
        bookingLog = List.of(
            // Old synchronous line in the booking request (request trace id) — not the flush.
            logLine(at, "http-nio-3000-exec-3", "INFO", FLUSH_LOGGER, "3f2a9c1e-7b44-4d0e-9a51-0c6d2e8f1b77",
                UsageEventLogger.MARKER + " " + bookingJson(DAY, op(6))),
            // A customer value carrying the prefix in the middle of another line.
            logLine(at, "http-nio-3000-exec-4", "INFO", "c.a.b.appointments.AppointmentsService",
                "5b1e0c2d-1111-4d0e-9a51-0c6d2e8f1b77", "note x - " + UsageEventLogger.TRACE_ID + ": "
                    + UsageEventLogger.MARKER + " " + bookingJson(DAY, op(7))),
            // The same with a forged X-Request-Id without the dot (RequestIdFilter whitelist).
            logLine(at, "http-nio-3000-exec-5", "INFO", FLUSH_LOGGER, "job-usagebooking",
                UsageEventLogger.MARKER + " " + bookingJson(DAY, op(10))),
            // The WARN of the flush itself carries no JSON.
            logLine(at, "scheduling-1", "WARN", FLUSH_LOGGER, UsageEventLogger.TRACE_ID,
                UsageEventLogger.MARKER + " flush: 3 event(s) dropped at the buffer limit of 10000"),
            // Case matters: contains_cs / Groß-/Kleinschreibung zählt.
            logLine(at, "scheduling-1", "INFO", FLUSH_LOGGER, "JOB.usagebooking",
                UsageEventLogger.MARKER + " " + bookingJson(DAY, op(11))),
            flushLine(at, bookingJson(DAY, op(12))));

        assertEquals(List.of(op(12)), opsOfDay(DAY), "only the genuine flush line");
    }

    // ---- TC-19: funnel / Trichter ----

    @Test
    @DisplayName("TC-19: erreicht(k) cumulative over all sessions and per device; visits = REQ-037 visits")
    void cumulatesTheFunnelFromMaxStepRows() {
        funnelRows = List.of(
            row(1, "mobile", 30), row(2, "mobile", 12), row(3, "mobile", 8), row(4, "mobile", 6), row(5, "mobile", 4),
            row(1, "desktop", 10), row(4, "desktop", 5), row(5, "desktop", 3),
            row(2, "", 2), row(2, "bot", 1), row("x", "mobile", 5), row(9, "mobile", 5), row(1, "tablet", -3),
            List.of(1));
        long req037Visits = 30 + 12 + 8 + 6 + 4 + 10 + 5 + 3 + 2 + 1;

        FunnelDay funnel = aggregator.aggregateDay(DAY, false, true, false).orElseThrow().global().funnel();

        assertEquals(new FunnelCounts(req037Visits, 41L, 26L, 18L, 7L), funnel.all());
        assertEquals(new FunnelCounts(60L, 30L, 18L, 10L, 4L), funnel.byDevice().get("mobile"));
        assertEquals(new FunnelCounts(18L, 8L, 8L, 8L, 3L), funnel.byDevice().get("desktop"));
        assertEquals(new FunnelCounts(3L, 3L, 0L, 0L, 0L), funnel.byDevice().get("unknown"),
            "empty and foreign device values fold into unknown (REQ-037 whitelist)");
        assertEquals(new FunnelCounts(0L, 0L, 0L, 0L, 0L), funnel.byDevice().get("tablet"));
        assertEquals(List.of("mobile", "tablet", "desktop", "unknown"), List.copyOf(funnel.byDevice().keySet()));
        assertEquals(req037Visits, funnel.all().visits(), "stage 1 equals the REQ-037 visit count");
        assertTrue(messages(Level.WARN).stream().anyMatch(m -> m.contains("4 funnel row(s) discarded")));
    }

    // ---- TC-62: drop-off rows / Absprung-Zeilen ----

    @Test
    @DisplayName("TC-62: drop-off margins, invariant Σ = visits − booking; foo and booked@3 discarded with WARN")
    void foldsDropoffRowsIntoTheTwoMargins() {
        dropoffRows = List.of(
            row("booked", 5, "mobile", 4),
            row("server-error", 1, "mobile", 2), row("server-error", 2, "desktop", 1),
            row("server-error", 4, "mobile", 1),
            row("no-slots", 4, "mobile", 7),
            row("quiet-1", 1, "desktop", 22),
            row("foo", 3, "mobile", 9),
            row("booked", 3, "mobile", 6),
            row("quiet-4", 5, "mobile", 2),
            row("quiet-2", 2, "fridge", 1),
            row("quiet-2", "two", "mobile", 1));

        FunnelDay funnel = aggregator.aggregateDay(DAY, false, true, true).orElseThrow().global().funnel();
        DropoffDay dropoff = funnel.dropoff();

        assertEquals(new FunnelCounts(37L, 13L, 12L, 12L, 4L), funnel.all());
        assertEquals(Map.of("1", 2L, "2", 1L, "3", 0L, "4", 1L), dropoff.byScenarioStep().get("server-error"));
        assertEquals(Map.of("1", 0L, "2", 0L, "3", 0L, "4", 7L), dropoff.byScenarioStep().get("no-slots"));
        assertEquals(Map.of("1", 22L, "2", 0L, "3", 0L, "4", 0L), dropoff.byScenarioStep().get("quiet-1"));
        assertEquals(List.of("server-error", "no-slots", "quiet-1"), List.copyOf(dropoff.byScenarioStep().keySet()),
            "catalogue order; booked never appears");
        assertEquals(3L, dropoff.byScenarioDevice().get("server-error").get("mobile"));
        assertEquals(1L, dropoff.byScenarioDevice().get("server-error").get("desktop"));
        assertEquals(0L, dropoff.byScenarioDevice().get("server-error").get("tablet"));
        assertEquals(22L, dropoff.byScenarioDevice().get("quiet-1").get("desktop"));

        long stepSum = dropoff.byScenarioStep().values().stream()
            .flatMap(m -> m.values().stream()).mapToLong(Long::longValue).sum();
        long deviceSum = dropoff.byScenarioDevice().values().stream()
            .flatMap(m -> m.values().stream()).mapToLong(Long::longValue).sum();
        assertEquals(funnel.all().visits() - funnel.all().booking(), stepSum, "BR-27 invariant");
        assertEquals(stepSum, deviceSum);

        List<String> warnings = messages(Level.WARN);
        assertTrue(warnings.stream().anyMatch(m -> m.contains("1 drop-off row(s) with an unknown scenario")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(m -> m.contains("2 drop-off row(s) with inconsistent 'booked'")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(m -> m.contains("unknown device class")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(m -> m.contains("malformed drop-off row")), warnings.toString());
        assertTrue(warnings.stream().noneMatch(m -> m.contains("foo") || m.contains("fridge")),
            "WARN without values (E11): " + warnings);
        assertTrue(warnings.stream().noneMatch(m -> m.contains("invariant violated")), warnings.toString());
    }

    @Test
    @DisplayName("BR-30: only the two margins leave the aggregator — no scenario × stage × device tuple")
    void neverExposesTheFullTuple() {
        dropoffRows = List.of(row("no-slots", 4, "mobile", 7), row("no-slots", 2, "desktop", 3));

        DropoffDay dropoff = aggregator.aggregateDay(DAY, false, true, true).orElseThrow()
            .global().funnel().dropoff();

        assertEquals(10L, dropoff.byScenarioStep().get("no-slots").values().stream().mapToLong(Long::longValue).sum());
        assertEquals(Set.of("no-slots"), dropoff.byScenarioDevice().keySet(),
            "device margin keyed by scenario …");
        assertEquals(Map.of("mobile", 7L, "tablet", 0L, "desktop", 3L, "unknown", 0L),
            dropoff.byScenarioDevice().get("no-slots"), "… and device class only — no stage");
    }

    @Test
    @DisplayName("TC-62: an empty drop-off result is a present, empty dropoff — not null")
    void anEmptyDropoffResultIsPresentButEmpty() {
        DropoffDay dropoff = aggregator.aggregateDay(DAY, false, true, true).orElseThrow()
            .global().funnel().dropoff();

        assertNotNull(dropoff);
        assertTrue(dropoff.byScenarioStep().isEmpty());
        assertTrue(dropoff.byScenarioDevice().isEmpty());
    }

    @Test
    @DisplayName("zone fallback: a blank or unknown usage.zone falls back to Europe/Berlin")
    void fallsBackToBerlinForAMissingOrUnknownZone() {
        assertEquals("Europe/Berlin", new InsightsAggregator(logClient, setupService, classifier,
            properties(" ")).zone().getId());
        assertEquals("Europe/Berlin", new InsightsAggregator(logClient, setupService, classifier,
            properties("Mars/Olympus")).zone().getId());
        assertEquals("Europe/Berlin", new InsightsAggregator(logClient, null, classifier, null).zone().getId());
    }

    @Test
    @DisplayName("E11: without setup.json branches every booking and view row is discarded")
    void withoutSetupEveryRowIsDiscarded() {
        InsightsAggregator withoutSetup = new InsightsAggregator(logClient, null, classifier, properties("UTC"));
        bookingRows = List.of(booking(BRANCH_A, GROUP_A, "default", "[]", "day", "MO", "morning", "2-3", false));
        viewRows = List.of(row(BRANCH_A, 3));

        AggregatedDay result = withoutSetup.aggregateDay(DAY, true, false, false).orElseThrow();

        assertEquals(0L, result.global().bookings());
        assertTrue(result.branches().isEmpty());
    }

    // ---- fixtures / Testdaten ----

    static List<String> allQueries() {
        return List.of(InsightsAggregator.BOOKINGS_KQL, InsightsAggregator.SERVICE_VIEWS_KQL,
            InsightsAggregator.FUNNEL_KQL, InsightsAggregator.DROPOFF_KQL);
    }

    /**
     * Maps sent queries back to their template constant — every placeholder ({@code %s},
     * {@code %1$s}) stands for a UTC instant or an ISO day / ordnet Abfragen ihrer Konstante zu.
     */
    private static List<String> templatesOf(List<String> sent) {
        List<String> templates = new ArrayList<>();
        for (String kql : sent) {
            for (String template : allQueries()) {
                if (templatePattern(template).matcher(kql).matches()) {
                    templates.add(template);
                }
            }
        }
        return templates;
    }

    private static Pattern templatePattern(String template) {
        StringBuilder regex = new StringBuilder();
        Matcher placeholder = PLACEHOLDER.matcher(template);
        int last = 0;
        while (placeholder.find()) {
            regex.append(Pattern.quote(template.substring(last, placeholder.start()))).append("[0-9TZ:-]{10,20}");
            last = placeholder.end();
        }
        regex.append(Pattern.quote(template.substring(last)));
        return Pattern.compile(regex.toString(), Pattern.DOTALL);
    }

    /** A KQL 1 result row of format v2 for {@link #DAY} / Ergebniszeile v2 für den Testtag. */
    private static List<Object> booking(String branch, String group, String variant, String ops,
                                        String bookingDaypart, String slotWeekday, String slotDaypart,
                                        String leadTime, boolean mobility) {
        return bookingOn(DAY, branch, group, variant, ops, bookingDaypart, slotWeekday, slotDaypart, leadTime,
            mobility);
    }

    /** A KQL 1 result row of format v2 with the given {@code day} (last column) / mit Buchungstag. */
    private static List<Object> bookingOn(LocalDate day, String branch, String group, String variant, String ops,
                                          String bookingDaypart, String slotWeekday, String slotDaypart,
                                          String leadTime, boolean mobility) {
        return List.of(UsageBookingEvent.FORMAT_VERSION, branch, group, variant, ops, bookingDaypart, slotWeekday,
            slotDaypart, leadTime, mobility, day.toString());
    }

    /** A legacy v1 result row without {@code day} (ten columns) / Altzeile v1 ohne Tag. */
    private static List<Object> legacy(String branch, String group, String ops, String bookingDaypart) {
        return List.of(UsageBookingEvent.LEGACY_FORMAT_VERSION, branch, group, "default", ops, bookingDaypart,
            "MO", "morning", "2-3", false);
    }

    private static List<Object> withDay(List<Object> row, String day) {
        List<Object> copy = new ArrayList<>(row.subList(0, 10));
        copy.add(day);
        return copy;
    }

    private static List<Object> withVersion(List<Object> row, int version) {
        List<Object> copy = new ArrayList<>(row);
        copy.set(0, version);
        return copy;
    }

    // ---- raw log lines and the KQL 1 emulation / Roh-Logzeilen und KQL-1-Emulation ----

    /** One raw {@code ContainerAppConsoleLogs_CL} record / ein Roh-Datensatz. */
    record LogRecord(Instant timeGenerated, String log) {}

    /** Operation id no. {@code n} — tells the records apart / unterscheidet die Datensätze. */
    private static String op(int n) {
        return "6908a1b2c3d4e5f6a7b8%04x".formatted(n);
    }

    /** The JSON the flush writes for one booking of {@code day} (real serialiser) / echtes JSON. */
    private static String bookingJson(LocalDate day, String op) {
        return UsageEventLogger.toJson(new UsageBookingEvent(UsageBookingEvent.FORMAT_VERSION, day.toString(),
            BRANCH_A, GROUP_A, "default", List.of(op), BookingDaypart.DAY, SlotWeekday.MO, SlotDaypart.MORNING,
            LeadTimeBucket.D2_3, false));
    }

    /** A line of format v1 (no {@code day}) / eine Zeile im Format v1 ohne Tag. */
    private static String legacyJson(String op) {
        return "{\"v\":1,\"branch\":\"" + BRANCH_A + "\",\"group\":\"" + GROUP_A + "\",\"variant\":\"default\","
            + "\"ops\":[\"" + op + "\"],\"bookingDaypart\":\"day\",\"slotWeekday\":\"MO\","
            + "\"slotDaypart\":\"morning\",\"leadTime\":\"2-3\",\"mobility\":false}";
    }

    /** The INFO line of the flush (scheduler thread, MDC {@code job.usagebooking}) / Flush-Zeile. */
    private static LogRecord flushLine(Instant at, String json) {
        return logLine(at, "scheduling-1", "INFO", FLUSH_LOGGER, UsageEventLogger.TRACE_ID,
            UsageEventLogger.MARKER + " " + json);
    }

    /**
     * A record in the console pattern {@code %d{ISO8601} [%thread] %-5level %logger{36} -
     * %X{traceId}: %msg} (application.yml) / Datensatz im Konsolenmuster.
     */
    private static LogRecord logLine(Instant at, String thread, String level, String logger, String traceId,
                                     String message) {
        return new LogRecord(at, LOG_TIME.format(at) + " [" + thread + "] " + "%-5s".formatted(level) + " "
            + logger + " - " + traceId + ": " + message);
    }

    /** Ops of the rows KQL 1 returns for {@code day} over {@link #bookingLog} / Ops der Tageszeilen. */
    private List<String> opsOfDay(LocalDate day) {
        queries.clear();
        aggregator.aggregateDay(day, true, false, false);
        List<String> ops = new ArrayList<>();
        for (List<Object> row : emulateBookingsKql(queries.getFirst(), bookingLog)) {
            ops.addAll(InsightsAggregator.parseOps(String.valueOf(row.get(4))));
        }
        return ops;
    }

    /**
     * Runs KQL 1 on raw records the way Log Analytics does — window, {@code has},
     * {@code contains_cs}, the first-prefix {@code extract}, the JSON {@code extract} (leftmost match,
     * like RE2) and the day clause are all read from {@code kql} itself. Row layout as the
     * {@code project}. / Emuliert KQL 1; alle Literale stammen aus dem Query-Text.
     */
    static List<List<Object>> emulateBookingsKql(String kql, List<LogRecord> records) {
        Matcher window = find(KQL_WINDOW, kql);
        Instant from = Instant.parse(window.group(1));
        Instant until = Instant.parse(window.group(2)).plus(Duration.ofMinutes(Long.parseLong(window.group(3))));
        String has = find(KQL_HAS, kql).group(1).toLowerCase(Locale.ROOT);
        String containsCs = find(KQL_CONTAINS_CS, kql).group(1);
        Matcher prefix = find(KQL_PREFIX, kql);
        Pattern prefixRegex = Pattern.compile(prefix.group(1));
        String marker = prefix.group(2);
        Pattern jsonRegex = Pattern.compile(find(KQL_JSON, kql).group(1));
        Pattern dayRegex = Pattern.compile(find(KQL_DAY_EXTRACT, kql).group(1));
        Matcher dayClause = find(KQL_DAY, kql);
        String day = dayClause.group(1);
        Instant dayEnd = Instant.parse(dayClause.group(2));

        List<List<Object>> rows = new ArrayList<>();
        for (LogRecord record : records) {
            String line = record.log();
            Instant time = record.timeGenerated();
            if (time.isBefore(from) || !time.isBefore(until)
                || !line.toLowerCase(Locale.ROOT).contains(has) || !line.contains(containsCs)) {
                continue;
            }
            Matcher first = prefixRegex.matcher(line);
            Matcher json = jsonRegex.matcher(line);
            if (!first.find() || !marker.equals(first.group(1)) || !json.find()) {
                continue;
            }
            JsonNode j;
            try {
                j = OM.readTree(json.group(1));
            } catch (Exception e) {
                continue;
            }
            // Like KQL: Day comes from the raw text, not from the parsed JSON (parse_json would make it a datetime).
            Matcher dayMatch = dayRegex.matcher(json.group(1));
            String rowDay = dayMatch.find() ? dayMatch.group(1) : "";
            if (!rowDay.equals(day) && !(rowDay.isEmpty() && time.isBefore(dayEnd))) {
                continue;
            }
            List<Object> row = new ArrayList<>();
            row.add(j.path("v").isInt() ? j.path("v").asInt() : null);
            for (String field : List.of("branch", "group", "variant")) {
                row.add(j.path(field).asText(""));
            }
            row.add(j.path("ops").isMissingNode() ? "" : j.path("ops").toString());
            for (String field : List.of("bookingDaypart", "slotWeekday", "slotDaypart", "leadTime")) {
                row.add(j.path(field).asText(""));
            }
            row.add(j.path("mobility").isBoolean() ? j.path("mobility").asBoolean() : null);
            row.add(rowDay);
            rows.add(row);
        }
        return rows;
    }

    private static Matcher find(Pattern pattern, String kql) {
        Matcher matcher = pattern.matcher(kql);
        assertTrue(matcher.find(), "clause " + pattern + " missing in " + kql);
        return matcher;
    }

    private static List<Object> row(Object... cells) {
        return List.of(cells);
    }

    /** Log Analytics response {@code {"tables":[{"rows":[...]}]}} / Antwortformat. */
    static JsonNode response(List<List<Object>> rows) {
        ObjectNode root = OM.createObjectNode();
        ArrayNode table = root.putArray("tables").addObject().putArray("rows");
        for (List<Object> row : rows) {
            ArrayNode cells = table.addArray();
            for (Object cell : row) {
                if (cell instanceof Integer i) {
                    cells.add(i);
                } else if (cell instanceof Long l) {
                    cells.add(l);
                } else if (cell instanceof Boolean b) {
                    cells.add(b);
                } else if (cell == null) {
                    cells.addNull();
                } else {
                    cells.add(cell.toString());
                }
            }
        }
        return root;
    }

    static UsageStatsProperties properties(String zone) {
        return new UsageStatsProperties(true, "0 */15 * * * *", "0 30 3 * * *", zone, null, 24, 60,
            new UsageStatsProperties.Storage("", "usage-stats", "usage-daily.json"));
    }

    private List<String> messages(Level level) {
        return logs.list.stream()
            .filter(event -> event.getLevel() == level)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }
}
