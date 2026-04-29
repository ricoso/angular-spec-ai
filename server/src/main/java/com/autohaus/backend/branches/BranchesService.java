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

    /**
     * REQ-025 (i) Iter. 3: Adresse direkt aus setup.json (strukturierter Record).
     * Name + Marken kommen weiterhin aus SoftNet (über Group-Lookup).
     */
    private BranchResponseDto toDto(BranchSetup branch, Map<String, JsonNode> groupsById) {
        String defaultGroupId = branch.groups().stream()
            .filter(g -> "default".equals(g.variant()))
            .findFirst()
            .map(GroupEntry::groupId)
            .orElse(branch.groups().isEmpty() ? null : branch.groups().get(0).groupId());

        JsonNode group = defaultGroupId != null ? groupsById.get(defaultGroupId) : null;
        List<String> brands = group != null ? extractBrands(group) : List.of();

        BranchSetup.BranchAddressSetup addr = branch.address();
        BranchResponseDto.AddressDto address = addr != null
            ? new BranchResponseDto.AddressDto(addr.street(), addr.zip(), addr.city(), addr.country())
            : null;

        return new BranchResponseDto(
            branch.branchId(),
            branch.name(),
            composeDisplayAddress(address),
            address,
            brands
        );
    }

    private static String composeDisplayAddress(BranchResponseDto.AddressDto a) {
        if (a == null) return "";
        StringBuilder sb = new StringBuilder();
        if (a.street() != null && !a.street().isBlank()) {
            sb.append(a.street());
        }
        String zipCity = "";
        if (a.zip() != null && !a.zip().isBlank()) {
            zipCity = a.zip();
        }
        if (a.city() != null && !a.city().isBlank()) {
            zipCity = zipCity.isEmpty() ? a.city() : zipCity + " " + a.city();
        }
        if (!zipCity.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(zipCity);
        }
        return sb.toString();
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
