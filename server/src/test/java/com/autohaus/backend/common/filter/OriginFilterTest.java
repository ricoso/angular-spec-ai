package com.autohaus.backend.common.filter;

import com.autohaus.backend.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OriginFilterTest {

    private OriginFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AppProperties.SessionProperties session = new AppProperties.SessionProperties("secret-32chars-minimum-secret!!", 20);
        AppProperties.CorsProperties cors = new AppProperties.CorsProperties("http://localhost:3000,http://localhost:4200");
        AppProperties appProperties = new AppProperties(session, cors);
        filter = new OriginFilter(appProperties, objectMapper);
    }

    @Test
    void shouldNotFilter_nonApiPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/index.html");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void shouldNotFilter_healthEndpoint() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void allowsLocalRequest_withoutOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        request.setRemoteAddr("127.0.0.1");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "test-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void allowsAllowedOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        request.addHeader("Origin", "http://localhost:3000");
        request.setRemoteAddr("10.0.0.1");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "test-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void blocksUnauthorizedOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        request.addHeader("Origin", "http://evil.com");
        request.setRemoteAddr("10.0.0.1");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "test-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void allowsRefererFromAllowedOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        request.addHeader("Referer", "http://localhost:4200/home/brand");
        request.setRemoteAddr("10.0.0.1");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "test-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
