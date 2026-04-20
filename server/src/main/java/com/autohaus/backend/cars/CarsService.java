package com.autohaus.backend.cars;

import com.autohaus.backend.cars.dto.CarResponseDto;
import com.autohaus.backend.cars.dto.CreateCarRequest;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class CarsService {

    private final SoftnetApiClient softnetApi;

    public CarsService(SoftnetApiClient softnetApi) {
        this.softnetApi = softnetApi;
    }

    public CarResponseDto createCar(CreateCarRequest dto) {
        Map<String, Object> data = new HashMap<>();
        data.put("licensePlate", dto.licensePlate());
        data.put("mileage", dto.mileage());
        data.put("vin", dto.vin());

        JsonNode result = softnetApi.createCar(data);
        String plate = result.path("licensePlate").asText(null);
        return new CarResponseDto(result.path("id").asText(), plate != null ? plate : dto.licensePlate());
    }
}
