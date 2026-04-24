package com.autohaus.backend.snaps;

import com.autohaus.backend.softnet.SoftnetApiClient;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationIdMapperTest {

    private static final String GROUP = "group-mettmann";
    private static final String ID_INSPEKTION   = "6908807454f4bb2b69b03ffc";
    private static final String ID_OELWECHSEL   = "6908807454f4bb2b69b03ff8";
    private static final String ID_TUEV         = "6908807454f4bb2b69b03ff5";
    private static final String ID_BREMSEN      = "6908807454f4bb2b69b03ff6";
    private static final String ID_BREMS_FLUID  = "6908807454f4bb2b69b04002";

    @Mock private SoftnetApiClient softnetApi;
    private OperationIdMapper mapper;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mapper = new OperationIdMapper(softnetApi);
        lenient().when(softnetApi.getOperations(anyString())).thenReturn(fakeOperations());
    }

    private List<JsonNode> fakeOperations() {
        ArrayNode arr = om.createArrayNode();
        addOp(arr, ID_INSPEKTION,   "Inspektion");
        addOp(arr, ID_OELWECHSEL,   "Ölwechsel-Service");
        addOp(arr, ID_BREMSEN,      "Bremsen prüfen (kostenlos) und bei Bedarf erneuern");
        addOp(arr, ID_TUEV,         "TÜV (Haupt- und Abgasuntersuchung)");
        addOp(arr, ID_BREMS_FLUID,  "Bremsflüssigkeitswechsel");
        addOp(arr, "op-noise",      "Geräusche");
        return List.of(arr.get(0), arr.get(1), arr.get(2), arr.get(3), arr.get(4), arr.get(5));
    }

    private void addOp(ArrayNode arr, String id, String name) {
        ObjectNode n = arr.addObject();
        n.put("id", id);
        n.put("name", name);
    }

    @Test
    void mapKnownFrontendId_resolvesToSoftnetOperationId() {
        List<String> result = mapper.mapToSoftnetOperations(GROUP, List.of("inspection"));
        assertEquals(1, result.size());
        assertEquals(ID_INSPEKTION, result.get(0));
    }

    @Test
    void mapMultipleFrontendIds() {
        List<String> result = mapper.mapToSoftnetOperations(GROUP, List.of("inspection", "oil-change", "tuv"));
        assertEquals(3, result.size());
        assertTrue(result.contains(ID_INSPEKTION));
        assertTrue(result.contains(ID_OELWECHSEL));
        assertTrue(result.contains(ID_TUEV));
    }

    @Test
    void deduplicatesWhenTwoKeysResolveToSameOperation() {
        // brake-check matches "Bremsen prüfen", brake-fluid matches "Bremsflüssigkeitswechsel" — different ops
        // but e.g. diagnostics + repair both resolve to "diagnosearbeiten" → dedup
        List<String> result = mapper.mapToSoftnetOperations(GROUP, List.of("brake-check", "brake-fluid"));
        assertEquals(2, result.size());
    }

    @Test
    void passthrough24HexIds() {
        List<String> result = mapper.mapToSoftnetOperations(GROUP, List.of("6908807454f4bb2b69b03ffc"));
        assertEquals(1, result.size());
        assertEquals("6908807454f4bb2b69b03ffc", result.get(0));
    }

    @Test
    void skipUnknownFrontendKeys() {
        List<String> result = mapper.mapToSoftnetOperations(GROUP, List.of("unknown-service"));
        assertTrue(result.isEmpty());
    }

    @Test
    void skipFrontendKeyWithNoMatchingOperationName() {
        // wiper-blades keyword is "wischerblätter" — not in our fake fixture
        List<String> result = mapper.mapToSoftnetOperations(GROUP, List.of("wiper-blades"));
        assertTrue(result.isEmpty());
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertTrue(mapper.mapToSoftnetOperations(GROUP, List.of()).isEmpty());
        assertTrue(mapper.mapToSoftnetOperations(GROUP, null).isEmpty());
    }

    @Test
    void mixedInput() {
        List<String> result = mapper.mapToSoftnetOperations(
            GROUP, List.of("inspection", "6908807454f4bb2b69b04099", "unknown"));
        assertEquals(2, result.size());
        assertTrue(result.contains(ID_INSPEKTION));
        assertTrue(result.contains("6908807454f4bb2b69b04099"));
    }

    @Test
    void legacyNoGroupOverloadOnlyPassesHexIdsThrough() {
        List<String> result = mapper.mapToSoftnetOperations(List.of("6908807454f4bb2b69b03ffc", "inspection"));
        assertEquals(1, result.size());
        assertEquals("6908807454f4bb2b69b03ffc", result.get(0));
    }

    @Test
    void cachesOperationsPerGroup() {
        mapper.mapToSoftnetOperations(GROUP, List.of("inspection"));
        mapper.mapToSoftnetOperations(GROUP, List.of("oil-change"));
        mapper.mapToSoftnetOperations(GROUP, List.of("tuv"));
        // three calls, same group — should only fetch once
        org.mockito.Mockito.verify(softnetApi, org.mockito.Mockito.times(1)).getOperations(GROUP);
    }
}
