package com.autohaus.backend.cars.dto;

import jakarta.validation.constraints.*;

public record CreateCarRequest(
    @NotBlank @Pattern(regexp = "^[A-ZÄÖÜ]{1,3}[\\s\\-]?[A-Z]{1,2}[\\s\\-]?\\d{1,4}[A-Z]?$") String licensePlate,
    @Min(0) @Max(999999) int mileage,
    @NotBlank @Size(min = 17, max = 17) @Pattern(regexp = "^[A-HJ-NPR-Z0-9]{17}$") String vin,
    String brandId,
    @NotBlank String branchId
) {}
