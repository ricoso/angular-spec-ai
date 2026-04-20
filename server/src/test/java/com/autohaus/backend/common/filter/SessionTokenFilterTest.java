package com.autohaus.backend.common.filter;

import com.autohaus.backend.session.SessionTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionTokenFilterTest {

    @Mock private SessionTokenService sessionTokenService;
    @Mock private FilterChain filterChain;
    private SessionTokenFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        filter = new SessionTokenFilter(sessionTokenService, objectMapper);
    }

    @Test
    void shouldNotFilter_nonApiPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/index.html");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilter_sessionEndpoint() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/session/token");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilter_healthEndpoint() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldFilter_apiEndpoints() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void missingToken_returns403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "test-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void invalidToken_returns403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        request.addHeader("X-Session-Token", "invalid-token");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "test-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(sessionTokenService.validateToken("invalid-token")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(403, response.getStatus());
    }

    @Test
    void validToken_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        request.addHeader("X-Session-Token", "valid-token");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "test-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(sessionTokenService.validateToken("valid-token")).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
