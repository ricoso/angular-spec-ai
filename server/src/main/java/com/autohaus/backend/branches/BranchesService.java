package com.autohaus.backend.branches;

import com.autohaus.backend.branches.dto.BranchResponseDto;
import com.autohaus.backend.config.setup.BranchSetup;
import com.autohaus.backend.config.setup.GroupEntry;
import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BranchesService {

    private final SoftnetApiClient softnetApi;
    private final SetupService setupService;

    public BranchesService(SoftnetApiClient softnetApi, SetupService setupService) {
        this.softnetApi = softnetApi;
        this.setupService = setupService;
    }

    public List<BranchResponseDto> getBranches() {
        List<BranchSetup> setupBranches = setupService.getAllBranches();
        if (setupBranches.isEmpty()) {
            return List.of();
        }

        Map<String, JsonNode> groupsById = loadGroupsById();

        return setupBranches.stream()
            .map(b -> toDto(b, groupsById))
            .toList();
    }

    public BranchResponseDto getBranch(String branchId) {
        BranchSetup branch = setupService.findByBranchId(branchId);
        Map<String, JsonNode> groupsById = loadGroupsById();
        return toDto(branch, groupsById);
    }

    private Map<String, JsonNode> loadGroupsById() {
        return softnetApi.getGroups().stream()
            .collect(Collectors.toUnmodifiableMap(
                g -> g.path("id").asText(),
                g -> g,
                (a, b) -> a
            ));
    }

    private BranchResponseDto toDto(BranchSetup branch, Map<String, JsonNode> groupsById) {
        String defaultGroupId = branch.groups().stream()
            .filter(g -> "default".equals(g.variant()))
            .findFirst()
            .map(GroupEntry::groupId)
            .orElse(branch.groups().isEmpty() ? null : branch.groups().get(0).groupId());

        JsonNode group = defaultGroupId != null ? groupsById.get(defaultGroupId) : null;
        List<String> brands = group != null ? extractBrands(group) : List.of();

        return new BranchResponseDto(
            branch.branchId(),
            branch.name(),
            branch.address(),
            brands
        );
    }

    private List<String> extractBrands(JsonNode group) {
        List<String> brands = new ArrayList<>();
        JsonNode brandNode = group.path("brand");
        if (brandNode.isArray()) {
            for (JsonNode b : brandNode) {
                brands.add(b.asText());
            }
        }
        return brands;
    }
}
