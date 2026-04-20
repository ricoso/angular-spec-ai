package com.autohaus.backend.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class LoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("HTTP");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
            String correlationId = request.getHeader("X-Correlation-Id");
            String sessionId = request.getHeader("X-Session-Id");

            log.info("{\"requestId\":\"{}\",\"correlationId\":\"{}\",\"sessionId\":\"{}\",\"method\":\"{}\",\"url\":\"{}\",\"statusCode\":{},\"duration\":\"{}ms\"}",
                requestId != null ? requestId : "unknown",
                correlationId != null ? correlationId : "",
                sessionId != null ? sessionId : "",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                duration);
        }
    }
}
