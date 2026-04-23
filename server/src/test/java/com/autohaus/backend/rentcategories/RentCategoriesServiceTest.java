package com.autohaus.backend.rentcategories;

import com.autohaus.backend.rentcategories.dto.RentCategoryResponseDto;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RentCategoriesServiceTest {

    @Mock private SoftnetApiClient softnetApi;
    private final ObjectMapper om = new ObjectMapper();
    private RentCategoriesService service;

    @BeforeEach
    void setUp() {
        service = new RentCategoriesService(softnetApi);
    }

    @Test
    void getRentCategories_returnsMappedDtos() {
        ObjectNode node = om.createObjectNode();
        node.put("id", "rc-1");
        node.put("groupid", "grp-1");
        node.put("name", "Kleinwagen");
        node.put("description", "Kompakte Fahrzeuge");
        ArrayNode brands = node.putArray("brand");
        brands.add("VW"); brands.add("Audi");
        ArrayNode branches = node.putArray("branches");
        branches.add("branch-1");

        when(softnetApi.getRentCategories()).thenReturn(List.of(node));

        List<RentCategoryResponseDto> result = service.getRentCategories();

        assertEquals(1, result.size());
        RentCategoryResponseDto dto = result.get(0);
        assertEquals("rc-1", dto.id());
        assertEquals("grp-1", dto.groupId());
        assertEquals("Kleinwagen", dto.name());
        assertEquals("Kompakte Fahrzeuge", dto.description());
        assertEquals(List.of("VW", "Audi"), dto.brands());
        assertEquals(List.of("branch-1"), dto.branches());
    }

    @Test
    void getRentCategories_emptyDescription_defaultsToEmptyString() {
        ObjectNode node = om.createObjectNode();
        node.put("id", "rc-2");
        node.put("groupid", "grp-1");
        node.put("name", "Mittelklasse");

        when(softnetApi.getRentCategories()).thenReturn(List.of(node));

        List<RentCategoryResponseDto> result = service.getRentCategories();
        assertEquals("", result.get(0).description());
        assertTrue(result.get(0).brands().isEmpty());
        assertTrue(result.get(0).branches().isEmpty());
    }

    @Test
    void getRentCategories_empty_returnsEmptyList() {
        when(softnetApi.getRentCategories()).thenReturn(List.of());

        List<RentCategoryResponseDto> result = service.getRentCategories();
        assertTrue(result.isEmpty());
    }
}
