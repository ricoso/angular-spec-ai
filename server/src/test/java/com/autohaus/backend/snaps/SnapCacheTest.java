package com.autohaus.backend.snaps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SnapCacheTest {

    private SnapCache cache;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        cache = new SnapCache(objectMapper);
    }

    @Test
    void cacheAndRetrieveSnap() {
        ObjectNode snap = objectMapper.createObjectNode();
        snap.put("internal", "snap-123");
        snap.put("checkInTime", "2026-04-15T14:30:00Z");

        cache.cacheSnaps(List.of(snap));

        JsonNode retrieved = cache.getRawSnap("snap-123");
        assertNotNull(retrieved);
        assertEquals("snap-123", retrieved.path("internal").asText());
    }

    @Test
    void getRawSnap_notCached_returnsNull() {
        assertNull(cache.getRawSnap("nonexistent"));
    }

    @Test
    void getRawSnap_returnsDeepCopy() {
        ObjectNode snap = objectMapper.createObjectNode();
        snap.put("internal", "snap-456");
        cache.cacheSnaps(List.of(snap));

        JsonNode copy1 = cache.getRawSnap("snap-456");
        JsonNode copy2 = cache.getRawSnap("snap-456");
        assertNotSame(copy1, copy2);
    }

    @Test
    void cacheSnaps_ignoresSnapsWithoutInternal() {
        ObjectNode snap = objectMapper.createObjectNode();
        snap.put("checkInTime", "2026-04-15T14:30:00Z");
        // no "internal" field

        cache.cacheSnaps(List.of(snap));
        // Should not throw, just skip
    }
}
