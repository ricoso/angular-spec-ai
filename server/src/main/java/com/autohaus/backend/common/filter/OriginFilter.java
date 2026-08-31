package com.autohaus.backend.common.filter;

import com.autohaus.backend.common.dto.ErrorResponse;
import com.autohaus.backend.common.logging.LogSanitizer;
import com.autohaus.backend.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(5)
public class OriginFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OriginFilter.class);
    private final Set<String> allowedOrigins;
    private final ObjectMapper objectMapper;

    public OriginFilter(AppProperties appProperties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        String origins = appProperties.cors() != null ? appProperties.cors().allowedOrigins() : "";
        this.allowedOrigins = origins != null && !origins.isBlank()
            ? Arrays.stream(origins.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet())
            : Set.of();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || path.equals("/api/v1/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        boolean isSameOrigin = (origin == null || origin.isBlank()) && (referer == null || referer.isBlank());

        if (isSameOrigin && isLocalRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (origin != null && !origin.isBlank() && allowedOrigins.contains(origin.toLowerCase())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (referer != null && !referer.isBlank()) {
            String refererOrigin = extractOrigin(referer);
            if (refererOrigin != null && allowedOrigins.contains(refererOrigin)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        // REQ-034: mask the client IP (personal data under GDPR) and sanitize the referer
        // (may carry query-string PII). Origin is host-only, path excludes the query string.
        log.warn("Blocked request from unauthorized origin: origin=\"{}\" referer=\"{}\" path=\"{}\" ip=\"{}\"",
            origin, LogSanitizer.redact(referer), request.getRequestURI(), LogSanitizer.maskIp(request.getRemoteAddr()));

        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse error = ErrorResponse.of(403, "error.unauthorizedOrigin", "Request origin is not authorized",
            requestId != null ? requestId : "unknown");
        objectMapper.writeValue(response.getOutputStream(), error);
    }

    private String extractOrigin(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            if (scheme == null || host == null) return null;
            String origin = scheme + "://" + host;
            if (port > 0 && port != 80 && port != 443) {
                origin += ":" + port;
            }
            return origin.toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isLocalRequest(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return "127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
    }
}
