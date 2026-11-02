package com.autohaus.backend.snaps;

import com.autohaus.backend.softnet.SoftnetApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REQ-039 BR-M6: resolves the internal brand id (e.g. {@code audi}) to the spelling SoftNet
 * expects (e.g. {@code Audi}). SoftNet matches rent categories case-sensitively, so a snap
 * request with {@code replacementPreference} and a lower-case brand fails with HTTP 500
 * („No matching rent category for brand audi"). / Interne Marken-ID → SoftNet-Schreibweise.
 *
 * <p>The brand list of a group is loaded once and cached; a failed lookup is not cached so the
 * next request retries. Unknown brands fall back to the input unchanged (E-3).
 */
@Component
public class SoftnetBrandResolver {

    private static final Logger log = LoggerFactory.getLogger(SoftnetBrandResolver.class);

    private final SoftnetApiClient softnetApi;
    private final Map<String, List<String>> brandNamesByGroup = new ConcurrentHashMap<>();

    public SoftnetBrandResolver(SoftnetApiClient softnetApi) {
        this.softnetApi = softnetApi;
    }

    public String resolve(String groupId, String brandId) {
        if (brandId == null || brandId.isBlank()) {
            return brandId;
        }
        Optional<String> match = brandNames(groupId).stream()
            .filter(name -> name.equalsIgnoreCase(brandId))
            .findFirst();
        if (match.isEmpty()) {
            // Brand id is a fixed internal key (no PII); logged so a missing mapping is visible.
            log.warn("No SoftNet brand matches '{}' in group {} — sending it unchanged", brandId, groupId);
        }
        return match.orElse(brandId);
    }

    private List<String> brandNames(String groupId) {
        List<String> cached = brandNamesByGroup.get(groupId);
        if (cached != null) {
            return cached;
        }
        try {
            List<String> names = softnetApi.getBrands(groupId).stream()
                .map(brand -> brand.path("name").asText(""))
                .filter(name -> !name.isBlank())
                .toList();
            brandNamesByGroup.put(groupId, names);
            return names;
        } catch (RuntimeException e) {
            log.warn("SoftNet brand lookup failed for group {}: {}", groupId, e.getClass().getSimpleName());
            return List.of();
        }
    }
}
