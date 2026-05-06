package com.autohaus.backend.jobtypes;

import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.jobtypes.dto.CategoryDto;
import com.autohaus.backend.jobtypes.dto.JobTypeResponseDto;
import com.autohaus.backend.jobtypes.dto.OperationDto;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class JobTypesService {

    private static final String TIRE_CHANGE_NAME_FRAGMENT = "reifen";

    /**
     * REQ-025 (b) Iter. 3: stabiler Marker für die "Räderwechsel"-Kategorie.
     * Tag mit rank=3 ist der TireChange-Tag. Frontend prüft denselben Wert.
     */
    public static final int TIRE_CHANGE_TAG_RANK = 3;

    private final SoftnetApiClient softnetApi;
    private final SetupService setupService;

    public JobTypesService(SoftnetApiClient softnetApi, SetupService setupService) {
        this.softnetApi = softnetApi;
        this.setupService = setupService;
    }

    public List<JobTypeResponseDto> getJobTypesByBranch(String branchId) {
        return getJobTypesByBranch(branchId, null);
    }

    public List<JobTypeResponseDto> getJobTypesByBranch(String branchId, String groupIdOverride) {
        String groupId = resolveGroupId(branchId, groupIdOverride);
        List<JsonNode> jobTypes = softnetApi.getJobTypes(groupId);
        List<JsonNode> operations = softnetApi.getOperations(groupId);

        List<JobTypeResponseDto.OperationDto> allOps = operations.stream()
            .map(op -> new JobTypeResponseDto.OperationDto(op.path("id").asText(), op.path("name").asText()))
            .toList();

        boolean tireChangeEnabled = setupService.isTireChangeEnabled();

        return jobTypes.stream()
            .filter(jt -> tireChangeEnabled || !isTireChangeJobType(jt))
            .map(jt -> new JobTypeResponseDto(
                jt.path("id").asText(),
                jt.path("name").asText(),
                jt.path("name").asText(),
                jt.path("hint").isMissingNode() || jt.path("hint").isNull() ? null : jt.path("hint").asText(),
                isTireChangeJobType(jt) ? "tireChange" : nonBlankOrNull(jt.path("category").asText("")),
                nonBlankOrNull(jt.path("categoryName").asText("")),
                nonBlankOrNull(jt.path("iconUrl").asText("")),
                allOps
            ))
            .toList();
    }

    /**
     * REQ-025 (b) Iter. 3: Kategorien (= Tags aus SoftNet), sortiert nach `rank`.
     * Cards auf der Services-Page rendern 1:1 diese Liste.
     */
    public List<CategoryDto> getCategoriesByBranch(String branchId) {
        return getCategoriesByBranch(branchId, null);
    }

    public List<CategoryDto> getCategoriesByBranch(String branchId, String groupIdOverride) {
        String groupId = resolveGroupId(branchId, groupIdOverride);
        List<JsonNode> tags = softnetApi.getList("/api/planning/tags", groupId);
        return tags.stream()
            .map(t -> new CategoryDto(
                t.path("id").asText(),
                t.path("name").asText(),
                t.path("rank").asInt(0)
            ))
            .sorted(Comparator.comparingInt(CategoryDto::rank))
            .toList();
    }

    /**
     * REQ-025 (b) Iter. 3: Operations (= Services) mit description, tagIds, order, imageId.
     * Frontend gruppiert per `tagIds` unter den jeweiligen Cards und sortiert nach `order`.
     */
    public List<OperationDto> getOperationsByBranch(String branchId) {
        return getOperationsByBranch(branchId, null);
    }

    public List<OperationDto> getOperationsByBranch(String branchId, String groupIdOverride) {
        String groupId = resolveGroupId(branchId, groupIdOverride);
        List<JsonNode> operations = softnetApi.getOperations(groupId);
        return operations.stream()
            .map(op -> {
                List<String> tagIds = new ArrayList<>();
                JsonNode tagsNode = op.path("tags");
                if (tagsNode.isArray()) {
                    for (JsonNode t : tagsNode) {
                        tagIds.add(t.asText());
                    }
                }
                Double order = op.has("order") && !op.path("order").isNull()
                    ? op.path("order").asDouble()
                    : null;
                return new OperationDto(
                    op.path("id").asText(),
                    op.path("name").asText(),
                    nonBlankOrNull(op.path("description").asText("")),
                    tagIds,
                    order,
                    nonBlankOrNull(op.path("image").asText(""))
                );
            })
            .sorted(Comparator.comparing(
                (OperationDto o) -> o.order() != null ? o.order() : Double.MAX_VALUE
            ))
            .toList();
    }

    /**
     * Wenn das Frontend per Deep-Link eine konkrete {@code groupId} mitschickt
     * (z. B. die "tireChange"-Group für Boxenstopp-Tage), wird diese gegen die
     * in setup.json hinterlegten Groups der Branch validiert und direkt verwendet.
     * Sonst → Default-Group der Branch.
     */
    private String resolveGroupId(String branchId, String groupIdOverride) {
        if (groupIdOverride != null && !groupIdOverride.isBlank()) {
            return setupService.validateGroupIdForBranch(branchId, groupIdOverride);
        }
        return setupService.resolveGroupId(branchId, "default");
    }

    private String nonBlankOrNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private boolean isTireChangeJobType(JsonNode jobType) {
        String name = jobType.path("name").asText("").toLowerCase(Locale.ROOT);
        return name.contains(TIRE_CHANGE_NAME_FRAGMENT);
    }
}
