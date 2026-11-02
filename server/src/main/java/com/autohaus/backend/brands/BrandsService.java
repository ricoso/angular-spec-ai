package com.autohaus.backend.brands;

import com.autohaus.backend.brands.dto.BrandResponseDto;
import com.autohaus.backend.config.setup.GroupEntry;
import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class BrandsService {

    private final SoftnetApiClient softnetApi;
    private final SetupService setupService;

    public BrandsService(SoftnetApiClient softnetApi, SetupService setupService) {
        this.softnetApi = softnetApi;
        this.setupService = setupService;
    }

    public List<BrandResponseDto> getBrandsByBranch(String branchId) {
        String groupId = setupService.resolveGroupId(branchId, "default");
        List<JsonNode> allBrands = softnetApi.getBrands(groupId);
        List<JsonNode> groups = softnetApi.getGroups();

        JsonNode group = groups.stream()
            .filter(g -> groupId.equals(g.path("id").asText()))
            .findFirst().orElse(null);

        if (group == null) return List.of();

        Set<String> groupBrandNames = StreamSupport.stream(group.path("brand").spliterator(), false)
            .map(b -> b.asText().toLowerCase())
            .collect(Collectors.toSet());

        return allBrands.stream()
            .filter(b -> groupBrandNames.contains(b.path("name").asText().toLowerCase()))
            .map(b -> new BrandResponseDto(b.path("id").asText(), b.path("name").asText()))
            .toList();
    }
}
