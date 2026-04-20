package com.autohaus.backend.mobility;

import com.autohaus.backend.mobility.dto.MobilityAlternativeResponseDto;
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
class MobilityServiceTest {

    @Mock private SoftnetApiClient softnetApi;
    private final ObjectMapper om = new ObjectMapper();
    private MobilityService service;

    @BeforeEach
    void setUp() {
        service = new MobilityService(softnetApi);
    }

    @Test
    void getMobilityAlternatives_returnsMapped() {
        ObjectNode alt = om.createObjectNode();
        alt.put("id", "mob-1");
        alt.put("name", "Leihwagen");

        when(softnetApi.getMobilityAlternatives()).thenReturn(List.of(alt));

        List<MobilityAlternativeResponseDto> result = service.getMobilityAlternatives("any-branch");
        assertEquals(1, result.size());
        assertEquals("mob-1", result.get(0).id());
        assertEquals("Leihwagen", result.get(0).name());
    }

    @Test
    void getMobilityAlternatives_emptyResponse() {
        when(softnetApi.getMobilityAlternatives()).thenReturn(List.of());

        List<MobilityAlternativeResponseDto> result = service.getMobilityAlternatives("any");
        assertTrue(result.isEmpty());
    }
}
