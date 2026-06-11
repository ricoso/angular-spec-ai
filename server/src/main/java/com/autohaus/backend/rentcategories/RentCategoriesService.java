package com.autohaus.backend.rentcategories;

import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.rentcategories.dto.RentCategoryResponseDto;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RentCategoriesService {

    private final SoftnetApiClient softnetApi;
    private final SetupService setupService;

    public RentCategoriesService(SoftnetApiClient softnetApi, SetupService setupService) {
        this.softnetApi = softnetApi;
        this.setupService = setupService;
    }

    public List<RentCategoryResponseDto> getRentCategories(String branchId) {
        String groupId = setupService.resolveGroupId(branchId, "default");
        List<JsonNode> categories = softnetApi.getRentCategories(groupId);
        return categories.stream()
            .map(this::toDto)
            .toList();
    }

    private RentCategoryResponseDto toDto(JsonNode node) {
        return new RentCategoryResponseDto(
            node.path("id").asText(),
            node.path("groupid").asText(),
            node.path("name").asText(),
            node.path("description").asText(""),
            extractPrice(node),
            nonBlankOrNull(node.path("currency").asText("")),
            nonBlankOrNull(node.path("priceUnit").asText("")),
            toStringList(node.path("brand")),
            toStringList(node.path("branches"))
        );
    }

    private java.math.BigDecimal extractPrice(JsonNode node) {
        JsonNode priceNode = node.path("price");
        if (priceNode.isMissingNode() || priceNode.isNull()) {
            return null;
        }
        if (priceNode.isNumber()) {
            return priceNode.decimalValue();
        }
        String text = priceNode.asText("").trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new java.math.BigDecimal(text.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String nonBlankOrNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private List<String> toStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> result.add(n.asText()));
        }
        return result;
    }
}
