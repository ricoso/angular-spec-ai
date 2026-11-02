package com.autohaus.backend.common.dto;

import com.autohaus.backend.common.filter.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResponseWrapperAdviceTest {

    private final ResponseWrapperAdvice advice = new ResponseWrapperAdvice();

    @Test
    void supports_alwaysReturnsTrue() {
        assertTrue(advice.supports(null, null));
    }

    @Test
    void beforeBodyWrite_wrapsStringInApiResponse() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-123");
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());

        Object result = advice.beforeBodyWrite("test-data", null, MediaType.APPLICATION_JSON,
            MappingJackson2HttpMessageConverter.class, request, response);

        assertInstanceOf(ApiResponse.class, result);
        ApiResponse<?> apiResponse = (ApiResponse<?>) result;
        assertEquals("test-data", apiResponse.data());
        assertEquals("req-123", apiResponse.meta().requestId());
        assertNotNull(apiResponse.meta().timestamp());
    }

    @Test
    void beforeBodyWrite_wrapsListInApiResponse() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-456");
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());

        List<String> data = List.of("a", "b", "c");
        Object result = advice.beforeBodyWrite(data, null, MediaType.APPLICATION_JSON,
            MappingJackson2HttpMessageConverter.class, request, response);

        assertInstanceOf(ApiResponse.class, result);
        ApiResponse<?> apiResponse = (ApiResponse<?>) result;
        assertEquals(data, apiResponse.data());
    }

    @Test
    void beforeBodyWrite_wrapsMapInApiResponse() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-789");
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());

        Map<String, String> data = Map.of("key", "value");
        Object result = advice.beforeBodyWrite(data, null, MediaType.APPLICATION_JSON,
            MappingJackson2HttpMessageConverter.class, request, response);

        assertInstanceOf(ApiResponse.class, result);
    }

    @Test
    void beforeBodyWrite_doesNotWrapApiResponse() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-123");
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());

        ApiResponse<String> existing = ApiResponse.of("data", "id");
        Object result = advice.beforeBodyWrite(existing, null, MediaType.APPLICATION_JSON,
            MappingJackson2HttpMessageConverter.class, request, response);

        assertSame(existing, result);
    }

    @Test
    void beforeBodyWrite_doesNotWrapErrorResponse() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-123");
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());

        ErrorResponse error = ErrorResponse.of(400, "err", "msg", "id");
        Object result = advice.beforeBodyWrite(error, null, MediaType.APPLICATION_JSON,
            MappingJackson2HttpMessageConverter.class, request, response);

        assertSame(error, result);
    }

    @Test
    void beforeBodyWrite_withoutRequestId_usesUnknown() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        // Not setting the requestId attribute
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());

        Object result = advice.beforeBodyWrite("data", null, MediaType.APPLICATION_JSON,
            MappingJackson2HttpMessageConverter.class, request, response);

        assertInstanceOf(ApiResponse.class, result);
        ApiResponse<?> apiResponse = (ApiResponse<?>) result;
        assertEquals("unknown", apiResponse.meta().requestId());
    }

    @Test
    void beforeBodyWrite_withNullBody_wrapsNull() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "req-null");
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());

        Object result = advice.beforeBodyWrite(null, null, MediaType.APPLICATION_JSON,
            MappingJackson2HttpMessageConverter.class, request, response);

        assertInstanceOf(ApiResponse.class, result);
        ApiResponse<?> apiResponse = (ApiResponse<?>) result;
        assertNull(apiResponse.data());
    }
}
