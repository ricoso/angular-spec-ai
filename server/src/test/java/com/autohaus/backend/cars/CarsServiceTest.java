package com.autohaus.backend.cars;

import com.autohaus.backend.cars.dto.CarResponseDto;
import com.autohaus.backend.cars.dto.CreateCarRequest;
import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarsServiceTest {

    @Mock private SoftnetApiClient softnetApi;
    @Mock private SetupService setupService;
    private final ObjectMapper om = new ObjectMapper();
    private CarsService service;

    @BeforeEach
    void setUp() {
        when(setupService.resolveGroupId(anyString(), any())).thenReturn("mock-group-id");
        service = new CarsService(softnetApi, setupService);
    }

    @Test
    void createCar_returnsMappedResponse() {
        ObjectNode result = om.createObjectNode();
        result.put("id", "car-1");
        result.put("licensePlate", "B-MS1234");

        when(softnetApi.createCar(any(), eq("mock-group-id"))).thenReturn(result);

        CreateCarRequest dto = new CreateCarRequest("B-MS1234", 50000, "WVWZZZ3CZWE123456", null, "branch-1");

        CarResponseDto response = service.createCar(dto);
        assertEquals("car-1", response.id());
        assertEquals("B-MS1234", response.licensePlate());
    }

    @Test
    void createCar_fallbackToRequestPlate() {
        ObjectNode result = om.createObjectNode();
        result.put("id", "car-2");

        when(softnetApi.createCar(any(), eq("mock-group-id"))).thenReturn(result);

        CreateCarRequest dto = new CreateCarRequest("M-AB123", 30000, "WVWZZZ3CZWE123457", null, "branch-1");

        CarResponseDto response = service.createCar(dto);
        assertEquals("M-AB123", response.licensePlate());
    }
}
