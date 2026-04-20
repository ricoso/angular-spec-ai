package com.autohaus.backend.common.dto;

import com.autohaus.backend.common.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice(basePackages = "com.autohaus.backend")
public class ResponseWrapperAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Don't wrap ErrorResponse or already-wrapped ApiResponse
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request, ServerHttpResponse response) {

        // Don't wrap if already an ApiResponse or ErrorResponse
        if (body instanceof ApiResponse || body instanceof ErrorResponse) {
            return body;
        }

        String requestId = "unknown";
        if (request instanceof ServletServerHttpRequest servletRequest) {
            Object id = servletRequest.getServletRequest().getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
            if (id != null) requestId = id.toString();
        }

        return ApiResponse.of(body, requestId);
    }
}
