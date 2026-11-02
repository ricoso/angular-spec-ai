package com.autohaus.backend.reporting;

import com.autohaus.backend.config.setup.BranchSetup;
import com.autohaus.backend.snaps.OperationIdMapper;
import com.autohaus.backend.config.setup.SetupService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportAggregatorTest {

    private static final String LOG_LINE =
        "2026-07-07 05:14:08,061 [http-nio-3000-exec-8] ERROR c.a.backend.softnet.SoftnetApiClient "
            + "- e936f6ca-4fec-4d8d-9556-54617f21f60f: SOFTNET_ERROR POST /api/planning/snaps failed after 336ms: "
            + "500 Internal Server Error: \"...\" "
            + "| requestBody={\"groupid\":\"g1\",\"brand\":\"volkswagen\",\"operations\":[\"op1\"],\"preferredBranch\":\"b1\"} "
            + "| responseBody={\"code\":\"InternalServerError\",\"original\":{\"message\":\"No match could be found\"}}";

    @Mock private LogAnalyticsClient logClient;
    @Mock private SetupService setupService;
    @Mock private OperationIdMapper operationIdMapper;

    private final ObjectMapper om = new ObjectMapper();
    private ReportAggregator aggregator;

    /** The error log line returned by the mocked LogAnalytics client; overridable per test. */
    private String errorLogLine = LOG_LINE;

    /** REQ-038 TC-36: several error lines instead of {@link #errorLogLine}; {@code null} = unused. */
    private List<String> errorLogLines;

    /** REQ-038 TC-36: the last error query sent to Log Analytics / die letzte Fehlerabfrage. */
    private String lastErrorsKql;

    /**
     * The trace-id {@code extract} of {@code ERRORS_KQL} — the mock applies the regex taken from the
     * query itself with the KQL semantics (first match, empty string without match), so the anchor
     * is under test (TC-36). / Der Mock wendet die Regex aus der Abfrage wie Log Analytics an.
     */
    private static final Pattern TRACE_EXTRACT =
        Pattern.compile("\\| extend TraceId = extract\\(@'([^']*)', 1, Log_s\\)");

    /** The case-sensitive job exclusion on that trace id / der case-sensitive Job-Ausschluss. */
    private static final Pattern NOT_STARTSWITH_CS =
        Pattern.compile("\\| where TraceId !startswith_cs '([^']*)'");

    /** Trace id of a genuine customer request (RequestIdFilter UUID) / echte Kunden-Trace-ID. */
    private static final String CUSTOMER_TRACE_ID = "5b1e0c2d-7a3f-4c1e-9d2b-6f8e0a4c2d11";

    @BeforeEach
    void setUp() {
        aggregator = new ReportAggregator(logClient, setupService, operationIdMapper, om, new PiiRedactor(om));
        when(logClient.query(anyString())).thenAnswer(inv -> {
            String kql = inv.getArgument(0);
            return kql.contains("summarize") ? statsResponse() : errorsResponse(kql);
        });
        BranchSetup branch = mock(BranchSetup.class);
        lenient().when(branch.name()).thenReturn("Volkswagen Essen-Kray");
        lenient().when(setupService.findByBranchId("b1")).thenReturn(branch);
        lenient().when(operationIdMapper.resolveOperationNames(eq("g1"), eq(List.of("op1"))))
            .thenReturn(List.of("Inspektion"));
    }

    @Test
    void aggregatesBookingStats() {
        ReportModel model = aggregator.aggregate();
        assertEquals(100, model.appointments().started());
        assertEquals(80, model.appointments().succeeded());
        assertEquals(20, model.appointments().failed());
        // snaps stats use the same summarize-mock → same counters
        assertEquals(100, model.snaps().started());
    }

    @Test
    void aggregatesErrorDistributionByBrandAndLocation() {
        ReportModel model = aggregator.aggregate();
        assertEquals(1L, model.errorsByBrand().get("volkswagen"));
        assertEquals(1L, model.errorsByLocation().get("Volkswagen Essen-Kray"));
    }

    @Test
    void parsesErrorLineWithResolvedLocationAndOperations() {
        ReportModel model = aggregator.aggregate();
        assertEquals(1, model.errors().size());
        ErrorEntry e = model.errors().get(0);
        assertEquals("e936f6ca-4fec-4d8d-9556-54617f21f60f", e.traceId());
        assertEquals("volkswagen", e.brand());
        assertEquals("Volkswagen Essen-Kray", e.location());
        assertEquals(List.of("Inspektion"), e.operations());
        assertEquals("No match (Operation/Marke)", e.errorType());
        assertEquals("/api/planning/snaps", e.endpoint());
        assertTrue(e.requestBody().contains("volkswagen"));
        assertTrue(e.responseBody().contains("No match"));
    }

    @Test
    void summaryIsNullBeforeLlmStep() {
        assertNull(aggregator.aggregate().summary());
    }

    @Test
    void unresolvedOperationIdBecomesPlaceholderInsteadOfRawId() {
        String hexId = "6908807454f4bb2b69b03ffc";
        errorLogLine = errorLine("g1", "[\"" + hexId + "\"]");
        // Simulates OperationIdMapper's per-ID fallback (unknown ID returned unchanged).
        when(operationIdMapper.resolveOperationNames("g1", List.of(hexId))).thenReturn(List.of(hexId));

        ErrorEntry e = aggregator.aggregate().errors().get(0);
        assertEquals(List.of("Service (unbekannt)"), e.operations());
    }

    @Test
    void blankGroupIdYieldsPlaceholderInsteadOfRawId() {
        String hexId = "6908807454f4bb2b69b03ffc";
        errorLogLine = errorLine("", "[\"" + hexId + "\"]");
        // resolveOperationNames("", ...) returns an empty list → aggregator falls back to raw IDs.

        ErrorEntry e = aggregator.aggregate().errors().get(0);
        assertEquals(List.of("Service (unbekannt)"), e.operations());
    }

    @Test
    void resolutionFailureYieldsPlaceholderInsteadOfRawId() {
        String hexId = "6908807454f4bb2b69b03ffc";
        errorLogLine = errorLine("g1", "[\"" + hexId + "\"]");
        when(operationIdMapper.resolveOperationNames("g1", List.of(hexId)))
            .thenThrow(new RuntimeException("SoftNet unreachable"));

        ErrorEntry e = aggregator.aggregate().errors().get(0);
        assertEquals(List.of("Service (unbekannt)"), e.operations());
    }

    @Test
    void mixedResolvedAndUnresolvedKeepsNamesAndCollapsesPlaceholder() {
        String hexId1 = "6908807454f4bb2b69b03ffc";
        String hexId2 = "6908807454f4bb2b69b03ff8";
        errorLogLine = errorLine("g1", "[\"" + hexId1 + "\",\"" + hexId2 + "\"]");
        when(operationIdMapper.resolveOperationNames("g1", List.of(hexId1, hexId2)))
            .thenReturn(List.of("Inspektion", hexId2)); // second ID stays unresolved

        ErrorEntry e = aggregator.aggregate().errors().get(0);
        assertEquals(List.of("Inspektion", "Service (unbekannt)"), e.operations());
    }

    @Test
    void snapErrorCarriesSoftnetCodeAndMessage() {
        ErrorEntry e = aggregator.aggregate().errors().get(0);
        assertEquals("InternalServerError — No match could be found", e.errorDetail());
    }

    @Test
    @DisplayName("Booking errors: brand, location and services come from the patched snap")
    void bookingErrorReadsMetadataFromPatchedSnap() {
        String hexId = "6908807454f4bb2b69b03ffc";
        errorLogLine = bookingErrorLine(
            "{\"branch\":{\"id\":\"b1\",\"name\":\"Essen\"},\"wishlist\":{\"car\":{\"brand\":\"audi\",\"license\":\"E-AB 123\"},"
                + "\"operations\":[{\"id\":\"" + hexId + "\"}],\"client\":{\"email\":\"max@example.com\"}}}",
            "{\"code\":\"GoneError\",\"message\":\"Access to the target resource is no longer available \"}");
        when(setupService.resolveGroupId("b1", null)).thenReturn("g1");
        when(operationIdMapper.resolveOperationNames("g1", List.of(hexId))).thenReturn(List.of("Inspektion"));

        ErrorEntry e = aggregator.aggregate().errors().get(0);
        assertEquals("audi", e.brand());
        assertEquals("Volkswagen Essen-Kray", e.location());
        assertEquals(List.of("Inspektion"), e.operations());
        assertEquals("Termin nicht mehr verfügbar", e.errorType());
        assertEquals("GoneError — Access to the target resource is no longer available", e.errorDetail());
        assertFalse(e.requestBody().contains("max@example.com"), "PII stays masked");
    }

    @Test
    void bookingErrorWithTextualBranchAndUnknownGroupFallsBackToPlaceholder() {
        String hexId = "6908807454f4bb2b69b03ffc";
        errorLogLine = bookingErrorLine(
            "{\"branch\":\"b1\",\"wishlist\":{\"operations\":[\"" + hexId + "\"]}}",
            "{\"code\":\"BadRequest\",\"message\":\"apptMaxCount per user reached\"}");
        when(setupService.resolveGroupId("b1", null)).thenThrow(new RuntimeException("no default group"));

        ErrorEntry e = aggregator.aggregate().errors().get(0);
        assertEquals("", e.brand());
        assertEquals("Volkswagen Essen-Kray", e.location());
        assertEquals(List.of("Service (unbekannt)"), e.operations());
        assertEquals("Buchungslimit erreicht", e.errorType());
        assertEquals("BadRequest — apptMaxCount per user reached", e.errorDetail());
    }

    @Test
    void nonJsonResponseYieldsEmptyDetail() {
        errorLogLine = bookingErrorLine("{\"branch\":\"b1\"}", "upstream timed out");
        ErrorEntry e = aggregator.aggregate().errors().get(0);
        assertEquals("", e.errorDetail());
        assertEquals("Timeout", e.errorType());
    }

    private static String bookingErrorLine(String requestBody, String responseBody) {
        return "2026-10-22 09:14:08,061 [http-nio-3000-exec-8] ERROR c.a.backend.softnet.SoftnetApiClient "
            + "- " + CUSTOMER_TRACE_ID + ": SOFTNET_ERROR POST /api/planning/appointments failed after 336ms: "
            + "410 Gone: \"...\" | requestBody=" + requestBody + " | responseBody=" + responseBody;
    }

    // ─── REQ-038 BR-15 / TC-36: the bugs tab stays clean ───

    @Test
    @DisplayName("TC-36: ERRORS_KQL excludes every job trace id of the real log prefix right after the SOFTNET_ERROR filter")
    void errorsQueryExcludesTheBackgroundJobPrefixes() {
        aggregator.aggregate();

        List<String> lines = lastErrorsKql.lines().map(String::trim).toList();
        int softnetFilter = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).equals("| where Log_s has 'SOFTNET_ERROR' and Log_s has 'requestBody'")) {
                softnetFilter = i;
            }
        }
        assertTrue(softnetFilter >= 0, lastErrorsKql);
        assertEquals("| extend TraceId = extract(@' - ([A-Za-z0-9_.-]{0,64}): SOFTNET_ERROR', 1, Log_s)",
            lines.get(softnetFilter + 1), "the trace id of the prefix directly in front of SOFTNET_ERROR");
        assertEquals("| where TraceId !startswith_cs 'job.'", lines.get(softnetFilter + 2),
            "the exclusion follows the SOFTNET_ERROR filter directly");
        assertEquals(ReportAggregator.ERRORS_KQL, lastErrorsKql);
        assertFalse(lastErrorsKql.contains("contains_cs"), "no substring test anywhere in the line");
    }

    /**
     * BR-15: every MDC marker of a background job starts with {@code job.} and fits the trace-id
     * class of the extract — a new job is covered without touching {@code ERRORS_KQL}. / Jeder
     * Job-Marker beginnt mit {@code job.} und passt in die Zeichenklasse.
     */
    @Test
    @DisplayName("TC-36 / BR-15: the excluded prefix covers the markers of scan, rollup and booking flush")
    void excludesExactlyTheMarkersTheJobsSet() {
        aggregator.aggregate();

        assertEquals(com.autohaus.backend.capacity.CapacityProbe.TRACE_ID,
            com.autohaus.backend.capacity.CapacityScanScheduler.TRACE_ID, "scan and probe share one marker");
        Matcher extract = TRACE_EXTRACT.matcher(lastErrorsKql);
        assertTrue(extract.find(), lastErrorsKql);
        Pattern traceClass = Pattern.compile(extract.group(1).replace(": SOFTNET_ERROR", ": "));
        for (String marker : List.of(com.autohaus.backend.capacity.CapacityScanScheduler.TRACE_ID,
                com.autohaus.backend.usage.insights.InsightsScheduler.TRACE_ID,
                com.autohaus.backend.usage.insights.UsageEventLogger.TRACE_ID)) {
            assertTrue(marker.startsWith("job."), marker);
            Matcher m = traceClass.matcher("x - " + marker + ": ");
            assertTrue(m.find() && marker.equals(m.group(1)), "the trace-id class captures " + marker);
        }
        assertTrue(lastErrorsKql.contains("!startswith_cs 'job.'"), lastErrorsKql);
    }

    @Test
    @DisplayName("TC-36: job errors are hidden, normal errors and a customer request 'capacityprobe' stay")
    void jobErrorsNeverReachTheErrorListButCustomerErrorsAlwaysDo() {
        errorLogLines = List.of(
            LOG_LINE,
            lineWithPrefix("capacityprobe", "audi"),              // customer: X-Request-Id: capacityprobe
            lineWithPrefix("job.capacityprobe", "skoda"),         // capacity scan (BR-15)
            lineWithPrefix("job.insightsrollup", "cupra"));       // insights rollup (BR-15)

        ReportModel model = aggregator.aggregate();

        assertEquals(2, model.errors().size(), "normal line and customer line stay in the list");
        assertEquals("e936f6ca-4fec-4d8d-9556-54617f21f60f", model.errors().get(0).traceId());
        assertEquals("audi", model.errors().get(1).brand());
        assertEquals(1L, model.errorsByBrand().get("volkswagen"));
        assertEquals(1L, model.errorsByBrand().get("audi"));
        assertFalse(model.errorsByBrand().containsKey("skoda"), "no job error in the brand tile");
        assertFalse(model.errorsByBrand().containsKey("cupra"), "no job error in the brand tile");
        assertEquals(2L, model.errorsByLocation().get("Volkswagen Essen-Kray"), "the location tile counts 2");
    }

    /**
     * Security finding (BR-15): customer fields such as {@code car.model} reach the SoftNet request
     * body unredacted. A job prefix inside them must never hide the customer's real error — the old
     * substring test {@code !contains_cs '- job.capacityprobe:'} did. / Ein Job-Präfix in einem
     * Kundenfeld blendet den echten Kundenfehler nie aus.
     */
    @Test
    @DisplayName("TC-36 (security): a job prefix inside a customer field never hides the real customer error")
    void aJobPrefixInsideTheRequestBodyNeverHidesACustomerError() {
        String plain = customerLineWithModel(CUSTOMER_TRACE_ID, "seat", "x - job.capacityprobe: y");
        String withMarker = customerLineWithModel(CUSTOMER_TRACE_ID, "opel",
            "x - job.insightsrollup: SOFTNET_ERROR");
        String withoutMdc = customerLineWithModel("system", "fiat", "x - job.capacityprobe: SOFTNET_ERROR");
        String emptyTrace = customerLineWithModel("", "kia", "x - job.usagebooking: SOFTNET_ERROR");
        for (String line : List.of(plain, withMarker, withoutMdc)) {
            assertTrue(line.contains("- job.capacityprobe:") || line.contains("- job.insightsrollup:"),
                "the pre-fix substring exclusion would have hidden this line: " + line);
        }
        errorLogLines = List.of(plain, withMarker, withoutMdc, emptyTrace,
            lineWithPrefix("job.capacityprobe", "skoda"));

        ReportModel model = aggregator.aggregate();

        assertEquals(List.of("seat", "opel", "fiat", "kia"), model.errors().stream().map(ErrorEntry::brand).toList(),
            "every customer error stays, the genuine job error does not");
        assertEquals(CUSTOMER_TRACE_ID, model.errors().get(0).traceId(), "the real trace id of the prefix");
        assertEquals(CUSTOMER_TRACE_ID, model.errors().get(1).traceId());
        assertFalse(model.errorsByBrand().containsKey("skoda"));
    }

    /**
     * A customer SOFTNET_ERROR line whose request body carries {@code car.model} verbatim (as
     * {@code SoftnetApiClient} logs it) / Kundenzeile mit unredigiertem {@code car.model}.
     */
    private static String customerLineWithModel(String traceId, String brand, String model) {
        return "2026-07-07 05:14:08,061 [http-nio-3000-exec-2] ERROR c.a.backend.softnet.SoftnetApiClient "
            + "- " + traceId + ": SOFTNET_ERROR POST /api/planning/appointments failed after 212ms: "
            + "400 Bad Request: \"...\" "
            + "| requestBody={\"groupid\":\"g1\",\"brand\":\"" + brand + "\",\"car\":{\"model\":\"" + model
            + "\"},\"operations\":[\"op1\"],\"preferredBranch\":\"b1\"} "
            + "| responseBody={\"code\":\"BadRequest\",\"original\":{\"message\":\"Save to database validation failed\"}}";
    }

    /** SOFTNET_ERROR line with the given MDC trace id in the log prefix (application.yml pattern). */
    private static String lineWithPrefix(String traceId, String brand) {
        return "2026-07-07 05:14:08,061 [scheduling-1] ERROR c.a.backend.softnet.SoftnetApiClient "
            + "- " + traceId + ": SOFTNET_ERROR POST /api/planning/snaps failed after 336ms: "
            + "500 Internal Server Error: \"...\" "
            + "| requestBody={\"groupid\":\"g1\",\"brand\":\"" + brand + "\",\"operations\":[\"op1\"],\"preferredBranch\":\"b1\"} "
            + "| responseBody={\"code\":\"InternalServerError\",\"original\":{\"message\":\"No match could be found\"}}";
    }

    private JsonNode statsResponse() {
        ObjectNode root = om.createObjectNode();
        ArrayNode rows = root.putArray("tables").addObject().putArray("rows");
        ArrayNode row = rows.addArray();
        row.add("100");
        row.add("80");
        row.add("20");
        return root;
    }

    /**
     * Log Analytics for {@code ERRORS_KQL}: {@code TraceId = extract(regex, 1, Log_s)} is the first
     * match (empty without one), {@code TraceId !startswith_cs prefix} is case-sensitive — both read
     * from the query. / Emuliert Extract (erstes Vorkommen) und den case-sensitiven Ausschluss.
     */
    private JsonNode errorsResponse(String kql) {
        lastErrorsKql = kql;
        Matcher extract = TRACE_EXTRACT.matcher(kql);
        Matcher exclusion = NOT_STARTSWITH_CS.matcher(kql);
        Pattern traceId = extract.find() ? Pattern.compile(extract.group(1)) : null;
        String excludedPrefix = exclusion.find() ? exclusion.group(1) : null;
        ObjectNode root = om.createObjectNode();
        ArrayNode rows = root.putArray("tables").addObject().putArray("rows");
        for (String line : errorLogLines != null ? errorLogLines : List.of(errorLogLine)) {
            if (traceId != null && excludedPrefix != null) {
                Matcher first = traceId.matcher(line);
                String extracted = first.find() ? first.group(1) : "";
                if (extracted.startsWith(excludedPrefix)) {
                    continue;   // filtered by Log Analytics / von Log Analytics gefiltert
                }
            }
            ArrayNode row = rows.addArray();
            row.add("2026-07-07T05:14:08.061Z");
            row.add(line);
        }
        return root;
    }

    /** Builds a SOFTNET_ERROR log line for /snaps with the given groupid + operations JSON array. */
    private static String errorLine(String groupId, String operationsJson) {
        return "2026-07-07 05:14:08,061 [http-nio-3000-exec-8] ERROR c.a.backend.softnet.SoftnetApiClient "
            + "- e936f6ca-4fec-4d8d-9556-54617f21f60f: SOFTNET_ERROR POST /api/planning/snaps failed after 336ms: "
            + "500 Internal Server Error: \"...\" "
            + "| requestBody={\"groupid\":\"" + groupId + "\",\"brand\":\"volkswagen\",\"operations\":"
            + operationsJson + ",\"preferredBranch\":\"b1\"} "
            + "| responseBody={\"code\":\"InternalServerError\",\"original\":{\"message\":\"No match could be found\"}}";
    }
}
