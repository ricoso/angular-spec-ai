package com.autohaus.backend.brands;

import com.autohaus.backend.brands.dto.BrandResponseDto;
import com.autohaus.backend.config.SoftnetProperties;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class BrandsServiceTest {

    @Mock private SoftnetApiClient softnetApi;
    private final ObjectMapper om = new ObjectMapper();
    private BrandsService service;

    @BeforeEach
    void setUp() {
        SoftnetProperties props = new SoftnetProperties("http://test", "http://auth", "/fp", 10000, "group-1", "branch-1");
        service = new BrandsService(softnetApi, props);
    }

    @Test
    void getBrandsByBranch_filtersToGroupBrands() {
        ObjectNode group = om.createObjectNode();
        group.put("id", "group-1");
        group.putArray("brand").add("BMW").add("Audi");

        ObjectNode bmw = om.createObjectNode();
        bmw.put("id", "b1");
        bmw.put("name", "BMW");
        ObjectNode toyota = om.createObjectNode();
        toyota.put("id", "b2");
        toyota.put("name", "Toyota");

        when(softnetApi.getGroups()).thenReturn(List.of(group));
        when(softnetApi.getBrands()).thenReturn(List.of(bmw, toyota));

        List<BrandResponseDto> result = service.getBrandsByBranch("any-branch");
        assertEquals(1, result.size());
        assertEquals("BMW", result.get(0).name());
    }

    @Test
    void getBrandsByBranch_caseInsensitiveMatch() {
        ObjectNode group = om.createObjectNode();
        group.put("id", "group-1");
        group.putArray("brand").add("bmw");

        ObjectNode bmw = om.createObjectNode();
        bmw.put("id", "b1");
        bmw.put("name", "BMW");

        when(softnetApi.getGroups()).thenReturn(List.of(group));
        when(softnetApi.getBrands()).thenReturn(List.of(bmw));

        List<BrandResponseDto> result = service.getBrandsByBranch("any");
        assertEquals(1, result.size());
    }

    @Test
    void getBrandsByBranch_groupNotFound_returnsEmpty() {
        when(softnetApi.getGroups()).thenReturn(List.of());
        when(softnetApi.getBrands()).thenReturn(List.of());

        List<BrandResponseDto> result = service.getBrandsByBranch("any");
        assertTrue(result.isEmpty());
    }
}
