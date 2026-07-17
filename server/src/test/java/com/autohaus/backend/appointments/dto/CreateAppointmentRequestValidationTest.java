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
}
