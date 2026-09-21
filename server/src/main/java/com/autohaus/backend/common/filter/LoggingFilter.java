package com.autohaus.backend.common.filter;

import com.autohaus.backend.common.logging.DeviceClassifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class LoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("HTTP");

    /** Access log line as before REQ-037 / Access-Zeile wie vor REQ-037. */
    private static final String ACCESS_PATTERN =
        "{\"correlationId\":\"{}\",\"sessionId\":\"{}\",\"method\":\"{}\",\"url\":\"{}\",\"statusCode\":{},\"duration\":\"{}ms\"}";

    /**
     * Same line plus the anonymous device class (REQ-037 AC-1) / dieselbe Zeile plus anonyme
     * Geräteklasse.
     */
    private static final String ACCESS_PATTERN_WITH_DEVICE =
        "{\"correlationId\":\"{}\",\"sessionId\":\"{}\",\"method\":\"{}\",\"url\":\"{}\",\"statusCode\":{},\"duration\":\"{}ms\",\"device\":\"{}\"}";

    /**
     * REQ-037 kill switch {@code USAGE_STATS_ENABLED} (BR-13): with {@code false} neither the
     * {@code User-Agent} nor {@code Sec-CH-UA-Mobile} header is read and the access line stays
     * byte-identical to the state before REQ-037 — no empty {@code "device":""} field. Nothing is
     * collected "just in case" (purpose limitation). / Kill-Switch: bei {@code false} werden die
     * beiden Header gar nicht erst gelesen, die Access-Zeile bleibt unverändert.
     */
    private final boolean usageStatsEnabled;

    public LoggingFilter(@Value("${usage.enabled:true}") boolean usageStatsEnabled) {
        this.usageStatsEnabled = usageStatsEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            String correlationId = request.getHeader("X-Correlation-Id");
            String sessionId = request.getHeader("X-Session-Id");

            if (usageStatsEnabled) {
                // REQ-037 BR-3: the raw User-Agent is read and discarded within the very same
                // expression — no local variable, no debug line, no exception message ever carries
                // it (TC-3). Only the resulting class (mobile|tablet|desktop|unknown) is logged.
                // Der rohe User-Agent wird im selben Ausdruck verworfen; geloggt wird nur die Klasse.
                String device = DeviceClassifier.classifyDevice(
                    request.getHeader("User-Agent"),
                    request.getHeader("Sec-CH-UA-Mobile"));

                log.info(ACCESS_PATTERN_WITH_DEVICE,
                    correlationId != null ? correlationId : "",
                    sessionId != null ? sessionId : "",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration,
                    device);
            } else {
                log.info(ACCESS_PATTERN,
                    correlationId != null ? correlationId : "",
                    sessionId != null ? sessionId : "",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration);
            }
        }
    }
}
