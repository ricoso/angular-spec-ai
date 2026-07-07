package com.autohaus.backend.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void replacesInvalidRequestIdWithGeneratedUuid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "not valid! {injection}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        assertNotEquals("not valid! {injection}", requestId);
        assertTrue(requestId.matches("^[A-Za-z0-9_-]{1,64}$"));
        assertEquals(requestId, response.getHeader("X-Request-Id"));
    }

    @Test
    void rejectsOverlongRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "a".repeat(65));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        assertNotEquals("a".repeat(65), requestId);
    }

    @Test
    void putsTraceIdIntoMdcDuringChainAndClearsAfterwards() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "trace-42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();
        doAnswer(invocation -> {
            mdcDuringChain.set(MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY));
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);

        assertEquals("trace-42", mdcDuringChain.get());
        assertNull(MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY));
    }

    @Test
    void clearsMdcWhenChainThrows() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doThrow(new ServletException("boom")).when(chain).doFilter(request, response);

        assertThrows(ServletException.class, () -> filter.doFilterInternal(request, response, chain));

        assertNull(MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY));
    }
}
