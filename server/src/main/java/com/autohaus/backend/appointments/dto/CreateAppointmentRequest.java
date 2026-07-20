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
        // REQ-031: Integer statt int — fehlender JSON-Wert wird nicht still 0,
        // sondern von @NotNull abgefangen; SoftNet wertet 0 als "nicht gesetzt"
        @NotNull @Min(10) Integer mileage,
        String vin,
        String model,
        String firstRegMonth,
        String firstRegYear
    ) {}

    /** REQ-027: SoftNet notifications channel ({ SMS, SMTP }). */
    public record NotificationsDto(Boolean SMS, Boolean SMTP) {}
}
