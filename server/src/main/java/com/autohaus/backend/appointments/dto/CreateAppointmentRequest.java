package com.autohaus.backend.appointments.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record CreateAppointmentRequest(
    @NotBlank String snapId,
    @Valid CustomerDto customer,
    @Valid VehicleDto vehicle,
    String note,
    String mobilityOption
) {
    public record CustomerDto(
        @NotBlank String email,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String mobile,
        String phone,
        @NotBlank String address,
        @NotBlank String zipCode,
        @NotBlank String city,
        String country,
        String language,
        String salutation
    ) {}

    public record VehicleDto(
        @NotBlank String licensePlate,
        @Min(0) int mileage,
        String vin,
        String model,
        String firstRegMonth,
        String firstRegYear
    ) {}
}
