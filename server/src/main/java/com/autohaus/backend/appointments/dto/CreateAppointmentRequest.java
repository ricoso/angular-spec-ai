package com.autohaus.backend.appointments.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record CreateAppointmentRequest(
    @NotBlank String snapId,
    @NotBlank String branchId,
    String variant,
    @Valid CustomerDto customer,
    @Valid VehicleDto vehicle,
    String note,
    String rentCategoryId,
    Boolean callbackRequested,
    NotificationsDto notifications
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

    /** REQ-027: SoftNet notifications channel ({ SMS, SMTP }). */
    public record NotificationsDto(Boolean SMS, Boolean SMTP) {}
}
