package com.autohaus.backend.snaps;

import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** REQ-039 BR-M6 / TC-M3: internal brand id → SoftNet spelling. */
@ExtendWith(MockitoExtension.class)
class SoftnetBrandResolverTest {

    @Mock private SoftnetApiClient softnetApi;
    private final ObjectMapper om = new ObjectMapper();
    private SoftnetBrandResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SoftnetBrandResolver(softnetApi);
    }

    private List<JsonNode> brands(String... names) {
        return java.util.Arrays.stream(names)
            .map(name -> (JsonNode) om.createObjectNode().put("id", "id-" + name).put("name", name))
            .toList();
    }

    @Test
    void resolve_matchesIgnoringCase() {
        when(softnetApi.getBrands("group-a")).thenReturn(brands("Abarth", "Audi", "Volkswagen"));

        assertEquals("Audi", resolver.resolve("group-a", "audi"));
        assertEquals("Volkswagen", resolver.resolve("group-a", "volkswagen"));
    }

    @Test
    void resolve_cachesBrandListPerGroup() {
        when(softnetApi.getBrands("group-a")).thenReturn(brands("Audi"));

        resolver.resolve("group-a", "audi");
        resolver.resolve("group-a", "AUDI");

        verify(softnetApi, times(1)).getBrands("group-a");
    }

    @Test
    void resolve_unknownBrand_returnsInputUnchanged() {
        when(softnetApi.getBrands("group-a")).thenReturn(brands("Audi"));

        assertEquals("skoda", resolver.resolve("group-a", "skoda"));
    }

    @Test
    void resolve_lookupFails_returnsInputAndRetriesNextTime() {
        when(softnetApi.getBrands("group-a"))
            .thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY))
            .thenReturn(brands("Audi"));

        assertEquals("audi", resolver.resolve("group-a", "audi"));
        assertEquals("Audi", resolver.resolve("group-a", "audi"));
    }

    @Test
    void resolve_blankOrNullBrand_skipsLookup() {
        assertNull(resolver.resolve("group-a", null));
        assertEquals("", resolver.resolve("group-a", ""));
        verify(softnetApi, never()).getBrands("group-a");
    }
}
