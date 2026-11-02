package com.autohaus.backend.cars;

import com.autohaus.backend.cars.dto.CarResponseDto;
import com.autohaus.backend.cars.dto.CreateCarRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cars")
public class CarsController {

    private final CarsService carsService;

    public CarsController(CarsService carsService) {
        this.carsService = carsService;
    }

    @PostMapping
    public CarResponseDto createCar(@Valid @RequestBody CreateCarRequest request) {
        return carsService.createCar(request);
    }
}
