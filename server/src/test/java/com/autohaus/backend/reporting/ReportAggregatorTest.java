package com.autohaus.backend.reporting;

import com.autohaus.backend.config.setup.BranchSetup;
import com.autohaus.backend.snaps.OperationIdMapper;
import com.autohaus.backend.config.setup.SetupService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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

    @BeforeEach
    void setUp() {
        aggregator = new ReportAggregator(logClient, setupService, operationIdMapper, om, new PiiRedactor(om));
        when(logClient.query(anyString())).thenAnswer(inv -> {
            String kql = inv.getArgument(0);
            return kql.contains("summarize") ? statsResponse() : errorsResponse();
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

    private JsonNode statsResponse() {
        ObjectNode root = om.createObjectNode();
        ArrayNode rows = root.putArray("tables").addObject().putArray("rows");
        ArrayNode row = rows.addArray();
        row.add("100");
        row.add("80");
        row.add("20");
        return root;
    }

    private JsonNode errorsResponse() {
        ObjectNode root = om.createObjectNode();
        ArrayNode rows = root.putArray("tables").addObject().putArray("rows");
        ArrayNode row = rows.addArray();
        row.add("2026-07-07T05:14:08.061Z");
        row.add(errorLogLine);
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
