package com.autohaus.backend.branches;

import com.autohaus.backend.branches.dto.BranchResponseDto;
import com.autohaus.backend.config.SoftnetProperties;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class BranchesService {

    private final SoftnetApiClient softnetApi;
    private final String groupId;

    public BranchesService(SoftnetApiClient softnetApi, SoftnetProperties properties) {
        this.softnetApi = softnetApi;
        this.groupId = properties.groupId();
    }

    public List<BranchResponseDto> getBranches() {
        List<JsonNode> groups = softnetApi.getGroups();
        List<JsonNode> branches = softnetApi.getBranches();

        JsonNode group = findGroup(groups);
        if (group == null) return List.of();

        Set<String> groupBranchIds = extractBranchIds(group);
        List<String> brands = extractBrands(group);

        return branches.stream()
            .filter(b -> groupBranchIds.contains(b.path("id").asText()))
            .map(b -> new BranchResponseDto(
                b.path("id").asText(),
                b.path("name").asText(),
                getDescription(b),
                brands
            ))
            .toList();
    }

    public BranchResponseDto getBranch(String branchId) {
        JsonNode branch = softnetApi.getBranch(branchId);
        List<JsonNode> groups = softnetApi.getGroups();
        JsonNode group = findGroup(groups);

        List<String> brands = group != null ? extractBrands(group) : List.of();

        return new BranchResponseDto(
            branch.path("id").asText(),
            branch.path("name").asText(),
            getDescription(branch),
            brands
        );
    }

    private JsonNode findGroup(List<JsonNode> groups) {
        return groups.stream()
            .filter(g -> groupId != null && groupId.equals(g.path("id").asText()))
            .findFirst()
            .orElse(null);
    }

    private Set<String> extractBranchIds(JsonNode group) {
        Set<String> ids = new HashSet<>();
        JsonNode branchesNode = group.path("branches");
        if (branchesNode.isArray()) {
            for (JsonNode b : branchesNode) {
                if (b.isTextual()) {
                    ids.add(b.asText());
                } else {
                    ids.add(b.path("id").asText());
                }
            }
        }
        return ids;
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

    private String getDescription(JsonNode branch) {
        String label = branch.path("label").asText("");
        if (!label.isEmpty()) return label;
        String company = branch.path("company").asText("");
        return company.isEmpty() ? "" : company;
    }
}
