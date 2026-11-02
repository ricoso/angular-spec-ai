package com.autohaus.backend.appointments.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REQ-031: Bean-Validation tests for VehicleDto.mileage
 * (@NotNull @Min(10) Integer — missing JSON value must not silently become 0,
 * and SoftNet treats 0 as "not set").
 */
class CreateAppointmentRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private CreateAppointmentRequest.VehicleDto vehicleWithMileage(Integer mileage) {
        return new CreateAppointmentRequest.VehicleDto("B-MS1234", mileage, null, null, null, null);
    }

    // === VehicleDto.mileage ===

    @Test
    void vehicleMileage_null_violatesNotNull() {
        Set<ConstraintViolation<CreateAppointmentRequest.VehicleDto>> violations =
            validator.validate(vehicleWithMileage(null));

        assertEquals(1, violations.size());
        ConstraintViolation<CreateAppointmentRequest.VehicleDto> violation = violations.iterator().next();
        assertEquals("mileage", violation.getPropertyPath().toString());
        assertEquals("NotNull", violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName());
    }

    @Test
    void vehicleMileage_5_violatesMin() {
        Set<ConstraintViolation<CreateAppointmentRequest.VehicleDto>> violations =
            validator.validate(vehicleWithMileage(5));

        assertEquals(1, violations.size());
        ConstraintViolation<CreateAppointmentRequest.VehicleDto> violation = violations.iterator().next();
        assertEquals("mileage", violation.getPropertyPath().toString());
        assertEquals("Min", violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName());
    }

    @Test
    void vehicleMileage_9_violatesMin() {
        Set<ConstraintViolation<CreateAppointmentRequest.VehicleDto>> violations =
            validator.validate(vehicleWithMileage(9));

        assertEquals(1, violations.size());
        assertEquals("mileage", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void vehicleMileage_10_isValid() {
        Set<ConstraintViolation<CreateAppointmentRequest.VehicleDto>> violations =
            validator.validate(vehicleWithMileage(10));

        assertTrue(violations.isEmpty());
    }

    @Test
    void vehicleMileage_50000_isValid() {
        Set<ConstraintViolation<CreateAppointmentRequest.VehicleDto>> violations =
            validator.validate(vehicleWithMileage(50000));

        assertTrue(violations.isEmpty());
    }

    // === Cascading: mileage violation surfaces as "vehicle.mileage" on the full request ===

    @Test
    void createAppointmentRequest_invalidVehicleMileage_reportsNestedPath() {
        CreateAppointmentRequest request = new CreateAppointmentRequest(
            "snap-1",
            "branch-1",
            null,
            new CreateAppointmentRequest.CustomerDto(
                "test@test.de", "Max", "Muster", "0170", null,
                "Str 1", "12345", "Berlin", null, null, null),
            vehicleWithMileage(5),
            null, null, null, null
        );

        Set<ConstraintViolation<CreateAppointmentRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("vehicle.mileage", violations.iterator().next().getPropertyPath().toString());
    }

    // === VehicleDto.licensePlate (@NotBlank: only blank / whitespace-only is rejected) ===

    private CreateAppointmentRequest.VehicleDto vehicleWithPlate(String plate) {
        return new CreateAppointmentRequest.VehicleDto(plate, 50000, null, null, null, null);
    }

    private long plateViolations(String plate) {
        return validator.validate(vehicleWithPlate(plate)).stream()
            .filter(v -> v.getPropertyPath().toString().equals("licensePlate"))
            .count();
    }

    @Test
    void licensePlate_singleSpace_isInvalid() {
        assertTrue(plateViolations(" ") > 0, "a lone space must be rejected");
    }

    @Test
    void licensePlate_empty_isInvalid() {
        assertTrue(plateViolations("") > 0);
    }

    @Test
    void licensePlate_anyNonBlankValue_isValid() {
        // Per requirement: only a lone/blank space is forbidden — everything else is accepted.
        assertEquals(0, plateViolations("M AB 1234"));
        assertEquals(0, plateViolations("B-MS1234"));
        assertEquals(0, plateViolations("aa"));
    }

    @Test
    void licensePlate_over20Chars_violatesSize() {
        // REQ-043 B-1: SoftNet rejects car.licenseNr > 20 chars with an HTTP 500.
        assertEquals(0, plateViolations("x".repeat(CreateAppointmentRequest.LICENSE_PLATE_MAX_LENGTH)));
        assertEquals(1, plateViolations("x".repeat(CreateAppointmentRequest.LICENSE_PLATE_MAX_LENGTH + 1)));
    }

    @Test
    void createAppointmentRequest_validVehicleMileage_hasNoViolations() {
        CreateAppointmentRequest request = new CreateAppointmentRequest(
            "snap-1",
            "branch-1",
            null,
            new CreateAppointmentRequest.CustomerDto(
                "test@test.de", "Max", "Muster", "0170", null,
                "Str 1", "12345", "Berlin", null, null, null),
            vehicleWithMileage(50000),
            null, null, null, null
        );

        Set<ConstraintViolation<CreateAppointmentRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    // === REQ-039: optional VIN, note and wish limits ===

    private CreateAppointmentRequest.VehicleDto vehicleWithVin(String vin) {
        return new CreateAppointmentRequest.VehicleDto("B-MS1234", 50000, vin, null, null, null);
    }

    @Test
    void vehicleVin_missing_isValid() {
        assertTrue(validator.validate(vehicleWithVin(null)).isEmpty());
    }

    @Test
    void vehicleVin_17Alphanumerics_isValid() {
        assertTrue(validator.validate(vehicleWithVin("WVWZZZ1JZ3W386752")).isEmpty());
    }

    @Test
    void vehicleVin_wrongLengthOrCharacters_violatesPattern() {
        assertEquals(1, validator.validate(vehicleWithVin("WVWZZZ1JZ3")).size());
        assertEquals(1, validator.validate(vehicleWithVin("WVW-ZZZ1JZ3W38675")).size());
    }

    private CreateAppointmentRequest requestWith(String note, String rentCategoryId) {
        return new CreateAppointmentRequest(
            "snap-1", "branch-1", null,
            new CreateAppointmentRequest.CustomerDto(
                "test@test.de", "Max", "Muster", "0170", null,
                "Str 1", "12345", "Berlin", null, null, null),
            vehicleWithMileage(50000),
            note, rentCategoryId, null, null);
    }

    @Test
    void note_over2000Chars_violatesSize() {
        assertTrue(validator.validate(requestWith("x".repeat(2000), null)).isEmpty());
        assertEquals(1, validator.validate(requestWith("x".repeat(2001), null)).size());
    }

    @Test
    void rentCategoryId_over24Chars_violatesSize() {
        assertTrue(validator.validate(requestWith(null, "6908808e54f4bb2b69b0538b")).isEmpty());
        assertEquals(1, validator.validate(requestWith(null, "x".repeat(25))).size());
    }
}
