package com.autohaus.backend.snaps.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-039 BR-M6: {@code brandName} ends up in a WARN log line of SoftnetBrandResolver when the
 * brand is unknown — the whitelist keeps line breaks, control/bidi characters and quotes out.
 */
class SnapRequestDtoValidationTest {

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

    private static SnapRequestDto requestWithBrand(String brandName) {
        return new SnapRequestDto("branch-1", List.of("job-1"), "2026-10-09", "2026-10-23",
            "next-available", brandName, null, Boolean.TRUE, "default", null);
    }

    private Set<ConstraintViolation<SnapRequestDto>> brandViolations(String brandName) {
        Set<ConstraintViolation<SnapRequestDto>> violations = validator.validate(requestWithBrand(brandName));
        violations.forEach(v -> assertEquals("brandName", v.getPropertyPath().toString()));
        return violations;
    }

    @ParameterizedTest
    @ValueSource(strings = {"audi", "Volkswagen", "Škoda", "Mercedes-Benz", "Lynk & Co", "Citroën", "DS 7", ""})
    void brandName_plainNames_areValid(String brandName) {
        assertTrue(brandViolations(brandName).isEmpty(), brandName);
    }

    @Test
    void brandName_missing_isValid() {
        assertTrue(brandViolations(null).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Audi\nWARN fake entry", "Audi\r", "Audi‮", "Audi\"", "<script>", "audi_1", "Audi\t"})
    void brandName_logInjectionCharacters_areRejected(String brandName) {
        Set<ConstraintViolation<SnapRequestDto>> violations = brandViolations(brandName);
        assertEquals(1, violations.size(), brandName);
        assertEquals("Pattern",
            violations.iterator().next().getConstraintDescriptor().getAnnotation().annotationType().getSimpleName());
    }

    @Test
    void brandName_longerThan50_isRejected() {
        Set<ConstraintViolation<SnapRequestDto>> violations = brandViolations("a".repeat(51));
        assertEquals(1, violations.size());
        assertEquals("Size",
            violations.iterator().next().getConstraintDescriptor().getAnnotation().annotationType().getSimpleName());
    }
}
