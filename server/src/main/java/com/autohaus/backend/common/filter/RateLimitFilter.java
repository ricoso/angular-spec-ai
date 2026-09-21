package com.autohaus.backend.common.filter;

import com.autohaus.backend.common.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(3)
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    /**
     * Requests per window and IP / Requests pro Zeitfenster und IP. Production default is
     * unchanged (500 per 15 min). Made configurable because the Playwright suite drives several
     * hundred API calls from a single IP within minutes and would otherwise hit 429 mid-run —
     * {@code scripts/test-local.sh} raises it for local E2E only. / Nur konfigurierbar gemacht,
     * damit die E2E-Suite nicht in das Limit läuft; der Produktionswert bleibt unverändert.
     */
    private final long capacity;
    private final long refillMinutes;

    public RateLimitFilter(ObjectMapper objectMapper,
                           @Value("${app.rate-limit.capacity:500}") long capacity,
                           @Value("${app.rate-limit.refill-minutes:15}") long refillMinutes) {
        this.objectMapper = objectMapper;
        this.capacity = capacity;
        this.refillMinutes = refillMinutes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Only rate-limit API endpoints, exclude health check
        return !path.startsWith("/api/") || path.equals("/api/v1/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        Bucket bucket = buckets.computeIfAbsent(ip, k -> createBucket());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorResponse error = ErrorResponse.of(429, "error.rateLimitExceeded", "Too many requests",
                requestId != null ? requestId : "unknown");
            objectMapper.writeValue(response.getOutputStream(), error);
        }
    }

    private Bucket createBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.builder().capacity(capacity)
                .refillGreedy(capacity, Duration.ofMinutes(refillMinutes)).build())
            .build();
    }
}
