package com.autohaus.backend.common.exception;

import com.autohaus.backend.common.dto.ErrorResponse;
import com.autohaus.backend.common.filter.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest createRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "test-request-id");
        return request;
    }

    @Test
    void handleResponseStatus_404() {
        ResponseEntity<ErrorResponse> response = handler.handleResponseStatus(
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"), createRequest());
        assertEquals(404, response.getStatusCode().value());
        assertEquals("error.notFound", response.getBody().messageKey());
    }

    @Test
    void handleTimeout() {
        ResponseEntity<ErrorResponse> response = handler.handleTimeout(
            new ResourceAccessException("timeout"), createRequest());
        assertEquals(504, response.getStatusCode().value());
        assertEquals("error.upstreamTimeout", response.getBody().messageKey());
    }

    @Test
    void handleServerError() {
        ResponseEntity<ErrorResponse> response = handler.handleServerError(
            HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "err", null, null, null), createRequest());
        assertEquals(502, response.getStatusCode().value());
        assertEquals("error.upstreamError", response.getBody().messageKey());
    }

    @Test
    void handleGeneral() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneral(
            new RuntimeException("unexpected"), createRequest());
        assertEquals(500, response.getStatusCode().value());
        assertEquals("error.internalError", response.getBody().messageKey());
    }

    @Test
    void includesRequestIdInMeta() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneral(
            new RuntimeException("test"), createRequest());
        assertEquals("test-request-id", response.getBody().meta().requestId());
    }
}
