package com.autohaus.backend.config.setup;

import com.autohaus.backend.config.FeatureProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Loads the branch catalog from setup.json at startup and offers Branch→Group lookups.
 * Applies the Radwechsel-Saison feature toggle: when disabled, tireChange groups are
 * filtered out at load time.
 */
@Service
public class SetupService {

    private static final Logger log = LoggerFactory.getLogger(SetupService.class);
    private static final String TIRE_CHANGE_VARIANT = "tireChange";

    private final ObjectMapper objectMapper;
    private final FeatureProperties featureProperties;
    private final String setupJsonInline;
    private final String setupJsonPath;

    private Map<String, BranchSetup> branchesById = Map.of();

    public SetupService(
        ObjectMapper objectMapper,
        FeatureProperties featureProperties,
        @Value("${setup.json:}") String setupJsonInline,
        @Value("${setup.json-path:server/config/setup.json}") String setupJsonPath
    ) {
        this.objectMapper = objectMapper;
        this.featureProperties = featureProperties;
        this.setupJsonInline = setupJsonInline;
        this.setupJsonPath = setupJsonPath;
    }

    /** REQ-025 (Iter.3): setup.json wird beim App-Start einmal geladen. */
    @PostConstruct
    public void init() {
        String json = resolveJson();
        if (json == null || json.isBlank()) {
            throw new IllegalStateException(
                "No setup configuration found. Set SETUP_JSON (inline content) or SETUP_JSON_PATH."
            );
        }
        loadBranches(json);
        log.info("Radwechsel-Saison: enabled={} ({} branches loaded)",
            featureProperties.radwechselEnabled(), branchesById.size());
    }

    private String resolveJson() {
        if (setupJsonInline != null && !setupJsonInline.isBlank()) {
            log.info("Loading setup from SETUP_JSON env variable (inline content)");
            return setupJsonInline;
        }
        try {
            Path path = Path.of(setupJsonPath).toAbsolutePath();
            log.info("Loading setup from file: {}", path);
            return Files.readString(path);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to read setup.json from " + setupJsonPath + ": " + e.getMessage(), e
            );
        }
    }

    private void loadBranches(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode branchesNode = root.path("branches");
            if (!branchesNode.isArray()) {
                throw new IllegalStateException("setup.json must have 'branches' array");
            }
            List<BranchSetup> parsed = objectMapper.convertValue(
                branchesNode,
                new TypeReference<List<BranchSetup>>() {}
            );
            this.branchesById = parsed.stream()
                .map(this::applyFeatureToggle)
                .filter(b -> !b.groups().isEmpty())
                .collect(Collectors.toUnmodifiableMap(BranchSetup::branchId, b -> b));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse setup.json: " + e.getMessage(), e);
        }
    }

    private BranchSetup applyFeatureToggle(BranchSetup branch) {
        if (featureProperties.radwechselEnabled()) {
            return branch;
        }
        List<GroupEntry> filtered = branch.groups().stream()
            .filter(g -> !TIRE_CHANGE_VARIANT.equals(g.variant()))
            .toList();
        return new BranchSetup(branch.branchId(), branch.name(), branch.address(), filtered);
    }

    public List<BranchSetup> getAllBranches() {
        return branchesById.values().stream()
            .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
            .toList();
    }

    public BranchSetup findByBranchId(String branchId) {
        BranchSetup branch = branchesById.get(branchId);
        if (branch == null) {
            throw new BranchNotFoundException(branchId);
        }
        return branch;
    }

    public String resolveGroupId(String branchId, String variant) {
        BranchSetup branch = findByBranchId(branchId);
        String effectiveVariant = variant == null || variant.isBlank() ? "default" : variant;
        var direct = branch.groups().stream()
            .filter(g -> g.variant().equals(effectiveVariant))
            .map(GroupEntry::groupId)
            .findFirst();
        if (direct.isPresent()) {
            return direct.get();
        }
        // Fallback auf default: z. B. wenn tireChange angefragt wird, aber Radwechsel-Saison
        // ausgeschaltet ist, soll die Buchung in der Default-Gruppe landen statt zu crashen.
        var fallback = branch.groups().stream()
            .filter(g -> "default".equals(g.variant()))
            .map(GroupEntry::groupId)
            .findFirst();
        if (fallback.isPresent()) {
            log.warn("Variant '{}' not available for branch '{}', falling back to 'default'",
                effectiveVariant, branchId);
            return fallback.get();
        }
        throw new VariantNotFoundException(
            branchId,
            effectiveVariant,
            branch.groups().stream().map(GroupEntry::variant).toList()
        );
    }

    public boolean isTireChangeEnabled() {
        return featureProperties.radwechselEnabled();
    }
}
