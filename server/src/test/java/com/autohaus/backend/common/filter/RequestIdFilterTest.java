package com.autohaus.backend.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void generatesRequestIdWhenNotProvided() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertNotNull(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
        assertNotNull(response.getHeader("X-Request-Id"));
        verify(chain).doFilter(request, response);
    }

    @Test
    void usesProvidedRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "custom-id-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertEquals("custom-id-123", request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
        assertEquals("custom-id-123", response.getHeader("X-Request-Id"));
    }
}
