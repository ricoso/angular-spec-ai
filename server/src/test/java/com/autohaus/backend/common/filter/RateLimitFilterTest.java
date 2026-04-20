package com.autohaus.backend.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(objectMapper);
    }

    @Test
    void shouldNotFilter_nonApiPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/index.html");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldFilter_apiPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void allowsRequestsWithinLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.setRemoteAddr("1.2.3.4");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "test-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
