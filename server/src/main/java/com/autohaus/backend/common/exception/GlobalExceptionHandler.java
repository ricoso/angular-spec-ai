package com.autohaus.backend.common.exception;

import com.autohaus.backend.common.dto.ErrorResponse;
import com.autohaus.backend.common.filter.RequestIdFilter;
import com.autohaus.backend.common.logging.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger("ExceptionHandler");

    private static final Map<Integer, String> MESSAGE_KEY_MAP = Map.of(
        400, "error.validationFailed",
        403, "error.invalidSessionToken",
        404, "error.notFound",
        408, "error.requestTimeout",
        410, "error.snapGone",
        429, "error.rateLimitExceeded",
        502, "error.upstreamError",
        503, "error.serviceUnavailable",
        504, "error.upstreamTimeout"
    );

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String requestId = getRequestId(request);
        List<ErrorResponse.ValidationError> details = ex.getBindingResult().getFieldErrors().stream()
            .map(err -> new ErrorResponse.ValidationError(err.getField(), "error.validationFailed", err.getDefaultMessage()))
            .toList();
        logError(400, "Validation failed", ex);
        return ResponseEntity.badRequest().body(
            ErrorResponse.withDetails(400, "error.validationFailed", "Validation failed", details, requestId));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        String requestId = getRequestId(request);
        int status = ex.getStatusCode().value();
        String messageKey = MESSAGE_KEY_MAP.getOrDefault(status, "error.internalError");
        logError(status, ex.getReason(), ex);
        return ResponseEntity.status(status).body(
            ErrorResponse.of(status, messageKey, ex.getReason() != null ? ex.getReason() : "An error occurred", requestId));
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorResponse> handleTimeout(ResourceAccessException ex, HttpServletRequest request) {
        String requestId = getRequestId(request);
        logError(504, "SoftNet API did not respond in time", ex);
        return ResponseEntity.status(504).body(
            ErrorResponse.of(504, "error.upstreamTimeout", "SoftNet API did not respond in time", requestId));
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ErrorResponse> handleClientError(HttpClientErrorException ex, HttpServletRequest request) {
        String requestId = getRequestId(request);
        int softnetStatus = ex.getStatusCode().value();
        int status;
        String messageKey;
        String message;

        if (softnetStatus == 410) {
            // REQ-031: snap was taken in the meantime — pass 410 through so the
            // frontend can trigger the reselect flow instead of a generic error
            status = 410;
            messageKey = "error.snapGone";
            message = "Requested time slot is no longer available";
        } else if (softnetStatus == 401 || softnetStatus == 403) {
            status = 503;
            messageKey = "error.serviceUnavailable";
            message = "SoftNet API configuration error";
        } else {
            status = 400;
            messageKey = "error.validationFailed";
            message = "Invalid request to upstream API";
        }

        logError(status, message, ex);
        return ResponseEntity.status(status).body(ErrorResponse.of(status, messageKey, message, requestId));
    }

    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<ErrorResponse> handleServerError(HttpServerErrorException ex, HttpServletRequest request) {
        String requestId = getRequestId(request);
        logError(502, "SoftNet API returned an error", ex);
        return ResponseEntity.status(502).body(
            ErrorResponse.of(502, "error.upstreamError", "SoftNet API returned an error", requestId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request) {
        String requestId = getRequestId(request);
        logError(500, ex.getMessage(), ex);
        return ResponseEntity.status(500).body(
            ErrorResponse.of(500, "error.internalError", "An unexpected error occurred", requestId));
    }

    private String getRequestId(HttpServletRequest request) {
        Object id = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return id != null ? id.toString() : "unknown";
    }

    private void logError(int status, String message, Exception ex) {
        // REQ-034: sanitize the message — third-party exceptions may embed PII (e.g. an upstream
        // response body). Static messages contain no PII and pass through unchanged.
        log.error("{\"statusCode\":{},\"message\":\"{}\",\"exception\":\"{}\"}",
            status, LogSanitizer.redact(message), ex.getClass().getSimpleName());
    }
}
