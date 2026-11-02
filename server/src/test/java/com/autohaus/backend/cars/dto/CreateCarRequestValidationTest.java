package com.autohaus.backend.cars.dto;

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
 * REQ-031: Bean-Validation tests for CreateCarRequest.mileage (@Min(10) @Max(999999)).
 */
class CreateCarRequestValidationTest {

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

    private CreateCarRequest requestWithMileage(int mileage) {
        return new CreateCarRequest("B-MS1234", mileage, "WVWZZZ3CZWE123456", null, "branch-1");
    }

    @Test
    void mileage_5_violatesMin() {
        Set<ConstraintViolation<CreateCarRequest>> violations = validator.validate(requestWithMileage(5));

        assertEquals(1, violations.size());
        ConstraintViolation<CreateCarRequest> violation = violations.iterator().next();
        assertEquals("mileage", violation.getPropertyPath().toString());
        assertEquals("Min", violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName());
    }

    @Test
    void mileage_9_violatesMin() {
        Set<ConstraintViolation<CreateCarRequest>> violations = validator.validate(requestWithMileage(9));

        assertEquals(1, violations.size());
        assertEquals("mileage", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void mileage_10_isValid() {
        Set<ConstraintViolation<CreateCarRequest>> violations = validator.validate(requestWithMileage(10));

        assertTrue(violations.isEmpty());
    }

    @Test
    void mileage_999999_isValid() {
        Set<ConstraintViolation<CreateCarRequest>> violations = validator.validate(requestWithMileage(999999));

        assertTrue(violations.isEmpty());
    }

    @Test
    void mileage_1000000_violatesMax() {
        Set<ConstraintViolation<CreateCarRequest>> violations = validator.validate(requestWithMileage(1000000));

        assertEquals(1, violations.size());
        ConstraintViolation<CreateCarRequest> violation = violations.iterator().next();
        assertEquals("mileage", violation.getPropertyPath().toString());
        assertEquals("Max", violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName());
    }
}
