package com.autohaus.backend.snaps;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OperationIdMapperTest {

    private OperationIdMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new OperationIdMapper();
    }

    @Test
    void mapKnownFrontendId() {
        List<String> result = mapper.mapToSoftnetOperations(List.of("inspection"));
        assertEquals(1, result.size());
        assertEquals("698b34c24dd8eea4d6d4219e", result.get(0));
    }

    @Test
    void mapMultipleFrontendIds() {
        List<String> result = mapper.mapToSoftnetOperations(List.of("inspection", "oil-change", "tuv"));
        assertEquals(3, result.size());
    }

    @Test
    void deduplicatesMappedIds() {
        // brake-check and brake-fluid both map to the same SoftNet ID
        List<String> result = mapper.mapToSoftnetOperations(List.of("brake-check", "brake-fluid"));
        assertEquals(1, result.size());
        assertEquals("698b34c24dd8eea4d6d4219b", result.get(0));
    }

    @Test
    void passthrough24HexIds() {
        List<String> result = mapper.mapToSoftnetOperations(List.of("698b34c24dd8eea4d6d4219e"));
        assertEquals(1, result.size());
        assertEquals("698b34c24dd8eea4d6d4219e", result.get(0));
    }

    @Test
    void skipUnknownIds() {
        List<String> result = mapper.mapToSoftnetOperations(List.of("unknown-service"));
        assertTrue(result.isEmpty());
    }

    @Test
    void emptyInput_returnsEmpty() {
        List<String> result = mapper.mapToSoftnetOperations(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void mixedInput() {
        List<String> result = mapper.mapToSoftnetOperations(
            List.of("inspection", "698b34c24dd8eea4d6d4219a", "unknown"));
        assertEquals(2, result.size());
    }
}
