package com.autohaus.backend.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitFilterAdditionalTest {

    private RateLimitFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(objectMapper);
    }

    @Test
    void exceedsRateLimit_returns429() throws Exception {
        String ip = "99.99.99.99";
        FilterChain chain = mock(FilterChain.class);

        // Exhaust the bucket (500 requests — bucket capacity per IP)
        for (int i = 0; i < 500; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
            request.setRemoteAddr(ip);
            request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-" + i);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, chain);
        }

        // Next request should be rate limited
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.setRemoteAddr(ip);
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-limited");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertEquals(429, response.getStatus());
        assertTrue(response.getContentAsString().contains("error.rateLimitExceeded"));
    }

    @Test
    void exceedsRateLimit_withoutRequestId_returns429() throws Exception {
        String ip = "88.88.88.88";
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 500; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
            request.setRemoteAddr(ip);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, chain);
        }

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.setRemoteAddr(ip);
        // No requestId attribute set
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertEquals(429, response.getStatus());
    }

    @Test
    void differentIps_haveIndependentBuckets() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        // Exhaust bucket for IP1
        for (int i = 0; i < 500; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
            request.setRemoteAddr("1.1.1.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(request, response, chain);
        }

        // IP2 should still be allowed
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.setRemoteAddr("2.2.2.2");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-ok");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain, atLeast(1)).doFilter(any(), any());
    }
}
