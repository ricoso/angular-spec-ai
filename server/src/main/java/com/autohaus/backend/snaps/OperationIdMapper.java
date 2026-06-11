package com.autohaus.backend.snaps;

import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Resolves frontend service keys (e.g. "inspection") to the real SoftNet
 * operation IDs of the target branch's group.
 *
 * Operation IDs differ per SoftNet group. This component fetches operations
 * from SoftNet per group and matches them to frontend keys by keyword lookup
 * in the operation's German name. Results are cached per group (10 min TTL)
 * to avoid hammering SoftNet on every snap request.
 */
@Component
public class OperationIdMapper {

    private static final Logger log = LoggerFactory.getLogger(OperationIdMapper.class);
    private static final Pattern HEX_24 = Pattern.compile("^[a-f0-9]{24}$");
    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;

    /**
     * Frontend service key -> list of keywords (case-insensitive substring match
     * against the German operation name in SoftNet). Order matters: first
     * matching operation wins.
     */
    private static final Map<String, List<String>> FRONTEND_TO_KEYWORDS = Map.ofEntries(
        Map.entry("inspection",              List.of("inspektion")),
        Map.entry("oil-change",              List.of("ölwechsel")),
        Map.entry("brake-check",             List.of("bremsen prüfen")),
        Map.entry("wiper-blades",            List.of("wischerblätter")),
        Map.entry("tuv",                     List.of("tüv")),
        Map.entry("tire-change",             List.of("radwechsel ohne einlagerung")),
        Map.entry("actions-checks",          List.of("sicherheits", "carcheck")),
        Map.entry("repair",                  List.of("diagnosearbeiten")),
        Map.entry("brake-fluid",             List.of("bremsflüssigkeit")),
        Map.entry("bodywork",                List.of("unfall", "kostenvoranschlag")),
        Map.entry("dialog-acceptance",       List.of("dialogannahme")),
        Map.entry("maintenance-contract",    List.of("wartungsvertrag")),
        Map.entry("emergency-oil",           List.of("notfallöl")),
        Map.entry("uvv-inspection",          List.of("uvv")),
        Map.entry("ac-cleaning",             List.of("klimaanlagenreinigung")),
        Map.entry("bring-own-tires",         List.of("radwechsel ohne einlagerung")),
        Map.entry("stored-tires",            List.of("radwechsel mit einlagerung")),
        Map.entry("pickup-delivery",         List.of("hol")),
        Map.entry("store-tires",             List.of("radwechsel mit einlagerung")),
        Map.entry("warranty-check",          List.of("herstellergarantie")),
        Map.entry("safety-check",            List.of("sicherheits")),
        Map.entry("fluid-check",             List.of("flüssigkeitsstände")),
        Map.entry("battery-check",           List.of("batterie")),
        Map.entry("diagnostics",             List.of("diagnosearbeiten")),
        Map.entry("noises",                  List.of("geräusche")),
        Map.entry("ac-malfunction",          List.of("klimaanlage ohne")),
        Map.entry("cost-estimate",           List.of("kostenvoranschlag")),
        Map.entry("windshield-replacement",  List.of("windschutzscheibe"))
    );

    private final SoftnetApiClient softnetApi;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public OperationIdMapper(SoftnetApiClient softnetApi) {
        this.softnetApi = softnetApi;
    }

    /**
     * Resolves frontend service keys to real SoftNet operation IDs for the
     * given group. Hex-24 IDs in the input are passed through unchanged (for
     * direct-ID callers / tests).
     */
    public List<String> mapToSoftnetOperations(String groupId, List<String> frontendIds) {
        if (frontendIds == null || frontendIds.isEmpty()) return List.of();

        Map<String, String> nameToId = loadOperationsForGroup(groupId);
        Set<String> result = new LinkedHashSet<>();

        for (String frontendId : frontendIds) {
            if (frontendId == null || frontendId.isBlank()) continue;

            if (HEX_24.matcher(frontendId).matches()) {
                result.add(frontendId);
                continue;
            }

            List<String> keywords = FRONTEND_TO_KEYWORDS.get(frontendId);
            if (keywords == null) {
                log.warn("Unknown frontend service key \"{}\" — skipping", frontendId);
                continue;
            }

            String opId = findOperationByKeywords(nameToId, keywords);
            if (opId != null) {
                result.add(opId);
            } else {
                log.warn("No SoftNet operation matches frontend key \"{}\" (keywords: {}) in group {}",
                    frontendId, keywords, groupId);
            }
        }

        return new ArrayList<>(result);
    }

    /**
     * Back-compat overload — pre-existing callers (and tests) may still pass
     * only frontend IDs. Without a group context no real lookup is possible,
     * so we return the input unchanged for hex IDs and log a warning for keys.
     * New code MUST use the {@code (groupId, ids)} variant.
     */
    public List<String> mapToSoftnetOperations(List<String> frontendIds) {
        if (frontendIds == null || frontendIds.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (String id : frontendIds) {
            if (id != null && HEX_24.matcher(id).matches()) {
                result.add(id);
            } else {
                log.warn("mapToSoftnetOperations called without groupId for key \"{}\" — cannot resolve", id);
            }
        }
        return result;
    }

    private Map<String, String> loadOperationsForGroup(String groupId) {
        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(groupId);
        if (cached != null && cached.expiresAt > now) {
            return cached.nameToId;
        }

        List<JsonNode> ops = softnetApi.getOperations(groupId);
        Map<String, String> nameToId = new LinkedHashMap<>();
        for (JsonNode op : ops) {
            String name = op.path("name").asText("").toLowerCase(Locale.ROOT).trim();
            String id = op.path("id").asText("");
            if (!name.isEmpty() && !id.isEmpty()) {
                nameToId.put(name, id);
            }
        }
        cache.put(groupId, new CacheEntry(nameToId, now + CACHE_TTL_MS));
        log.debug("Loaded {} operations for group {}", nameToId.size(), groupId);
        return nameToId;
    }

    private String findOperationByKeywords(Map<String, String> nameToId, List<String> keywords) {
        for (Map.Entry<String, String> entry : nameToId.entrySet()) {
            String name = entry.getKey();
            boolean allMatch = keywords.stream().allMatch(kw -> name.contains(kw.toLowerCase(Locale.ROOT)));
            if (allMatch) return entry.getValue();
        }
        return null;
    }

    /** For tests: clear the per-group cache. */
    public void invalidateCache() {
        cache.clear();
    }

    private record CacheEntry(Map<String, String> nameToId, long expiresAt) {}
}
