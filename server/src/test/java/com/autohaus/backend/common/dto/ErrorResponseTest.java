package com.autohaus.backend.common.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ErrorResponseTest {

    @Test
    void of_createsBasicError() {
        ErrorResponse error = ErrorResponse.of(400, "error.validationFailed", "Bad request", "req-1");
        assertEquals(400, error.statusCode());
        assertEquals("error.validationFailed", error.messageKey());
        assertEquals("Bad request", error.message());
        assertNull(error.details());
        assertEquals("req-1", error.meta().requestId());
    }

    @Test
    void withDetails_includesValidationErrors() {
        List<ErrorResponse.ValidationError> details = List.of(
            new ErrorResponse.ValidationError("email", "error.required", "Email is required")
        );
        ErrorResponse error = ErrorResponse.withDetails(400, "error.validationFailed", "Validation failed", details, "req-2");
        assertNotNull(error.details());
        assertEquals(1, error.details().size());
        assertEquals("email", error.details().get(0).field());
    }
}
