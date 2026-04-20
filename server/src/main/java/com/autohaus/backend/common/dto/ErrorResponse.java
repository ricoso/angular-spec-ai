package com.autohaus.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    int statusCode,
    String messageKey,
    String message,
    List<ValidationError> details,
    ErrorMeta meta
) {
    public record ValidationError(String field, String messageKey, String message) {}
    public record ErrorMeta(String timestamp, String requestId) {}

    public static ErrorResponse of(int statusCode, String messageKey, String message, String requestId) {
        return new ErrorResponse(statusCode, messageKey, message, null,
            new ErrorMeta(Instant.now().toString(), requestId));
    }

    public static ErrorResponse withDetails(int statusCode, String messageKey, String message, List<ValidationError> details, String requestId) {
        return new ErrorResponse(statusCode, messageKey, message, details,
            new ErrorMeta(Instant.now().toString(), requestId));
    }
}
