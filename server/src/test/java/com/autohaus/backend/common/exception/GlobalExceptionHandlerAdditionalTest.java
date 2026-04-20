package com.autohaus.backend.common.exception;

import com.autohaus.backend.common.dto.ErrorResponse;
import com.autohaus.backend.common.filter.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.MethodParameter;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerAdditionalTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest createRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "test-req-id");
        return request;
    }

    private MockHttpServletRequest createRequestWithoutId() {
        return new MockHttpServletRequest();
    }

    // === handleValidation ===

    @Test
    void handleValidation_returnsBadRequest() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "email", "must not be blank"));
        bindingResult.addError(new FieldError("target", "firstName", "size must be between 2 and 50"));

        MethodParameter param = new MethodParameter(
            GlobalExceptionHandlerAdditionalTest.class.getDeclaredMethod("createRequest"), -1
        );
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, createRequest());

        assertEquals(400, response.getStatusCode().value());
        assertEquals("error.validationFailed", response.getBody().messageKey());
        assertNotNull(response.getBody().details());
        assertEquals(2, response.getBody().details().size());
        assertEquals("email", response.getBody().details().get(0).field());
    }

    // === handleClientError ===

    @Test
    void handleClientError_401_returns503() {
        HttpClientErrorException ex = HttpClientErrorException.create(
            HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, null, null
        );

        ResponseEntity<ErrorResponse> response = handler.handleClientError(ex, createRequest());

        assertEquals(503, response.getStatusCode().value());
        assertEquals("error.serviceUnavailable", response.getBody().messageKey());
        assertEquals("SoftNet API configuration error", response.getBody().message());
    }

    @Test
    void handleClientError_403_returns503() {
        HttpClientErrorException ex = HttpClientErrorException.create(
            HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY, null, null
        );

        ResponseEntity<ErrorResponse> response = handler.handleClientError(ex, createRequest());

        assertEquals(503, response.getStatusCode().value());
        assertEquals("error.serviceUnavailable", response.getBody().messageKey());
    }

    @Test
    void handleClientError_400_returns400() {
        HttpClientErrorException ex = HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, null, null
        );

        ResponseEntity<ErrorResponse> response = handler.handleClientError(ex, createRequest());

        assertEquals(400, response.getStatusCode().value());
        assertEquals("error.validationFailed", response.getBody().messageKey());
        assertEquals("Invalid request to upstream API", response.getBody().message());
    }

    @Test
    void handleClientError_422_returns400() {
        HttpClientErrorException ex = HttpClientErrorException.create(
            HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable", HttpHeaders.EMPTY, null, null
        );

        ResponseEntity<ErrorResponse> response = handler.handleClientError(ex, createRequest());

        assertEquals(400, response.getStatusCode().value());
    }

    // === handleResponseStatus with various status codes ===

    @Test
    void handleResponseStatus_400_usesValidationKey() {
        ResponseEntity<ErrorResponse> response = handler.handleResponseStatus(
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad input"), createRequest()
        );
        assertEquals(400, response.getStatusCode().value());
        assertEquals("error.validationFailed", response.getBody().messageKey());
    }

    @Test
    void handleResponseStatus_403_usesSessionKey() {
        ResponseEntity<ErrorResponse> response = handler.handleResponseStatus(
            new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden"), createRequest()
        );
        assertEquals(403, response.getStatusCode().value());
        assertEquals("error.invalidSessionToken", response.getBody().messageKey());
    }

    @Test
    void handleResponseStatus_429_usesRateLimitKey() {
        ResponseEntity<ErrorResponse> response = handler.handleResponseStatus(
            new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limited"), createRequest()
        );
        assertEquals(429, response.getStatusCode().value());
        assertEquals("error.rateLimitExceeded", response.getBody().messageKey());
    }

    @Test
    void handleResponseStatus_408_usesTimeoutKey() {
        ResponseEntity<ErrorResponse> response = handler.handleResponseStatus(
            new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "Timeout"), createRequest()
        );
        assertEquals(408, response.getStatusCode().value());
        assertEquals("error.requestTimeout", response.getBody().messageKey());
    }

    @Test
    void handleResponseStatus_502_usesUpstreamKey() {
        ResponseEntity<ErrorResponse> response = handler.handleResponseStatus(
            new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Bad gateway"), createRequest()
        );
        assertEquals(502, response.getStatusCode().value());
        assertEquals("error.upstreamError", response.getBody().messageKey());
    }

    @Test
    void handleResponseStatus_503_usesServiceUnavailableKey() {
        ResponseEntity<ErrorResponse> response = handler.handleResponseStatus(
            new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unavailable"), createRequest()
        );
        assertEquals(503, response.getStatusCode().value());
        assertEquals("error.serviceUnavailable", response.getBody().messageKey());
    }

    @Test
    void handleResponseStatus_504_usesUpstreamTimeoutKey() {
        ResponseEntity<ErrorResponse> response = handler.handleResponseStatus(
            new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Gateway timeout"), createRequest()
        );
        assertEquals(504, response.getStatusCode().value());
        assertEquals("error.upstreamTimeout", response.getBody().messageKey());
    }

    @Test
    void handleResponseStatus_withNullReason_usesDefaultMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleResponseStatus(
            new ResponseStatusException(HttpStatus.NOT_FOUND), createRequest()
        );
        assertEquals(404, response.getStatusCode().value());
        assertEquals("An error occurred", response.getBody().message());
    }

    @Test
    void handleResponseStatus_unmappedStatus_usesInternalErrorKey() {
        ResponseEntity<ErrorResponse> response = handler.handleResponseStatus(
            new ResponseStatusException(HttpStatus.CONFLICT, "Conflict"), createRequest()
        );
        assertEquals(409, response.getStatusCode().value());
        assertEquals("error.internalError", response.getBody().messageKey());
    }

    // === Missing requestId ===

    @Test
    void handleGeneral_withoutRequestId_usesUnknown() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneral(
            new RuntimeException("err"), createRequestWithoutId()
        );
        assertEquals("unknown", response.getBody().meta().requestId());
    }

    @Test
    void handleValidation_withoutRequestId() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "name", "required"));

        MethodParameter param = new MethodParameter(
            GlobalExceptionHandlerAdditionalTest.class.getDeclaredMethod("createRequest"), -1
        );
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, createRequestWithoutId());
        assertEquals(400, response.getStatusCode().value());
        assertEquals("unknown", response.getBody().meta().requestId());
    }
}
