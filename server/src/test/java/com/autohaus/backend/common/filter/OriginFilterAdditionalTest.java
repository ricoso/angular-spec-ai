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

class OriginFilterAdditionalTest {

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
    void blocksNonLocalRequest_withoutOriginOrReferer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        request.setRemoteAddr("10.0.0.1");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "test-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void allowsLocalRequest_ipv6Loopback() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        request.setRemoteAddr("::1");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "test-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void allowsLocalRequest_ipv6Long() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        request.setRemoteAddr("0:0:0:0:0:0:0:1");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "test-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void blocksInvalidReferer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        request.addHeader("Referer", "http://evil.com/page");
        request.setRemoteAddr("10.0.0.1");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "test-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertEquals(403, response.getStatus());
    }

    @Test
    void blocksGarbageReferer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        request.addHeader("Referer", "not-a-url");
        request.setRemoteAddr("10.0.0.1");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "test-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertEquals(403, response.getStatus());
    }

    @Test
    void allowsRefererWithPort() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        request.addHeader("Referer", "http://localhost:3000/some/page");
        request.setRemoteAddr("10.0.0.1");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "test-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldFilter_apiPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    void blocksRequest_withoutRequestId_usesUnknown() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        request.addHeader("Origin", "http://evil.com");
        request.setRemoteAddr("10.0.0.1");
        // No request ID attribute
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("unknown"));
    }

    @Test
    void emptyAllowedOrigins_constructor() {
        AppProperties.SessionProperties session = new AppProperties.SessionProperties("secret", 20);
        AppProperties.CorsProperties cors = new AppProperties.CorsProperties("");
        AppProperties appProperties = new AppProperties(session, cors);
        OriginFilter emptyFilter = new OriginFilter(appProperties, objectMapper);
        assertNotNull(emptyFilter);
    }

    @Test
    void nullCors_constructor() {
        AppProperties.SessionProperties session = new AppProperties.SessionProperties("secret", 20);
        AppProperties appProperties = new AppProperties(session, null);
        OriginFilter nullCorsFilter = new OriginFilter(appProperties, objectMapper);
        assertNotNull(nullCorsFilter);
    }
}
