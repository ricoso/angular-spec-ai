package com.autohaus.backend.rentcategories;

import com.autohaus.backend.rentcategories.dto.RentCategoryResponseDto;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RentCategoriesService {

    private final SoftnetApiClient softnetApi;

    public RentCategoriesService(SoftnetApiClient softnetApi) {
        this.softnetApi = softnetApi;
    }

    public List<RentCategoryResponseDto> getRentCategories() {
        List<JsonNode> categories = softnetApi.getRentCategories();
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
            toStringList(node.path("brand")),
            toStringList(node.path("branches"))
        );
    }

    private List<String> toStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> result.add(n.asText()));
        }
        return result;
    }
}
