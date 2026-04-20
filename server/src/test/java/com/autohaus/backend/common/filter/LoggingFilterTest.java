package com.autohaus.backend.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoggingFilterTest {

    private final LoggingFilter filter = new LoggingFilter();

    @Test
    void logsRequestWithAllHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/branches");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "test-req-id");
        request.addHeader("X-Correlation-Id", "corr-123");
        request.addHeader("X-Session-Id", "sess-456");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void logsRequestWithoutOptionalHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/customers");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void logsRequestWhenFilterChainThrows() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-err");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doThrow(new ServletException("boom")).when(chain).doFilter(request, response);

        assertThrows(ServletException.class, () -> filter.doFilterInternal(request, response, chain));
    }

    @Test
    void logsRequestWithNullRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/something");
        // requestId attribute is not set
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
