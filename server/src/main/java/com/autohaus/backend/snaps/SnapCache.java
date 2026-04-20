package com.autohaus.backend.snaps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SnapCache {

    private static final long TTL_MS = 30 * 60 * 1000L;

    private final Map<String, CachedSnap> cache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SnapCache(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void cacheSnaps(List<JsonNode> snaps) {
        long expiresAt = System.currentTimeMillis() + TTL_MS;
        for (JsonNode snap : snaps) {
            String internal = snap.path("internal").asText(null);
            if (internal != null) {
                cache.put(internal, new CachedSnap(snap.deepCopy(), expiresAt));
            }
        }
        cleanExpired();
    }

    public JsonNode getRawSnap(String snapId) {
        CachedSnap entry = cache.get(snapId);
        if (entry == null) return null;
        if (System.currentTimeMillis() > entry.expiresAt) {
            cache.remove(snapId);
            return null;
        }
        return entry.snap.deepCopy();
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> now > e.getValue().expiresAt);
    }

    private record CachedSnap(JsonNode snap, long expiresAt) {}
}
