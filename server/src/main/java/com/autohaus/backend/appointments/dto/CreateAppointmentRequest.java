package com.autohaus.backend.appointments.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record CreateAppointmentRequest(
    @NotBlank String snapId,
    @NotBlank String branchId,
    String variant,
    @Valid CustomerDto customer,
    @Valid VehicleDto vehicle,
    // SoftNet spec: wishlist.note holds at most 2000 chars (REQ-039: also guards composeNote).
    @Size(max = 2000) String note,
    // REQ-039 BR-M5: wish id is resolved against SoftNet's categories; length re-checked in the service.
    @Size(max = 24) String rentCategoryId,
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
        // Kennzeichen: nur leer / reines Leerzeichen ist ungültig, jeder andere Wert ist erlaubt.
        // @NotBlank leistet genau das (trimmt und lehnt Whitespace-only ab).
        // License plate: only blank / whitespace-only is invalid, any other value is accepted.
        @NotBlank String licensePlate,
        // REQ-031: Integer statt int — fehlender JSON-Wert wird nicht still 0,
        // sondern von @NotNull abgefangen; SoftNet wertet 0 als "nicht gesetzt"
        @NotNull @Min(10) Integer mileage,
        // REQ-039 BR-9: VIN is optional; if sent it must be 17 letters/digits (null = not given).
        @Pattern(regexp = "^[A-Za-z0-9]{17}$") String vin,
        String model,
        String firstRegMonth,
        String firstRegYear
    ) {}

    /** REQ-027: SoftNet notifications channel ({ SMS, SMTP }). */
    public record NotificationsDto(Boolean SMS, Boolean SMTP) {}
}
