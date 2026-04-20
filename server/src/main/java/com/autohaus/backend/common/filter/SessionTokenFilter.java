package com.autohaus.backend.common.filter;

import com.autohaus.backend.common.dto.ErrorResponse;
import com.autohaus.backend.session.SessionTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(10)
public class SessionTokenFilter extends OncePerRequestFilter {

    private final SessionTokenService sessionTokenService;
    private final ObjectMapper objectMapper;

    public SessionTokenFilter(SessionTokenService sessionTokenService, ObjectMapper objectMapper) {
        this.sessionTokenService = sessionTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/")
            || path.equals("/api/v1/session/token")
            || path.equals("/api/v1/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = request.getHeader("X-Session-Token");
        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);

        if (token == null || token.isBlank()) {
            sendError(response, requestId, "Session token is missing");
            return;
        }

        if (!sessionTokenService.validateToken(token)) {
            sendError(response, requestId, "Session token is invalid or expired");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, String requestId, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse error = ErrorResponse.of(403, "error.invalidSessionToken", message, requestId != null ? requestId : "unknown");
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
