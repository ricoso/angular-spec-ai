package com.autohaus.backend.common.filter;

import com.autohaus.backend.common.logging.DeviceClassifier;
import com.autohaus.backend.usage.insights.SearchHits;
import com.autohaus.backend.usage.insights.UsageAccessAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
     * Same line plus the hit level of a successful appointment search at the very end (REQ-038 N1,
     * §10.8, BR-29) / dieselbe Zeile plus Trefferstufe einer erfolgreichen Terminsuche am Zeilenende.
     */
    private static final String ACCESS_PATTERN_WITH_DEVICE_AND_HITS =
        "{\"correlationId\":\"{}\",\"sessionId\":\"{}\",\"method\":\"{}\",\"url\":\"{}\",\"statusCode\":{},\"duration\":\"{}ms\",\"device\":\"{}\",\"hits\":\"{}\"}";

    /** The only request that may carry {@code hits} (§10.8) / die einzige Anfrage mit {@code hits}. */
    private static final String SEARCH_METHOD = "POST";

    /** URI of the appointment search (§10.8) / URI der Terminsuche. */
    private static final String SEARCH_URI = "/api/v1/snaps";

    /** Status from which a search counts as failed (BR-29) / ab diesem Status gilt sie als fehlgeschlagen. */
    private static final int FIRST_ERROR_STATUS = 400;

    /**
     * REQ-037 kill switch {@code USAGE_STATS_ENABLED} (BR-13): with {@code false} neither the
     * {@code User-Agent} nor {@code Sec-CH-UA-Mobile} header is read and the access line stays
     * byte-identical to the state before REQ-037 — no empty {@code "device":""} field. Nothing is
     * collected "just in case" (purpose limitation). / Kill-Switch: bei {@code false} werden die
     * beiden Header gar nicht erst gelesen, die Access-Zeile bleibt unverändert.
     */
    private final boolean usageStatsEnabled;

    /**
     * REQ-038 N1 (BR-29, AC-34): {@code hits} is written only while {@code usage.enabled},
     * {@code usage.funnel.enabled} <b>and</b> {@code usage.dropoff.enabled} are all active — in every
     * other case the access line stays byte-identical to REQ-037. / Das Feld entsteht nur bei allen
     * drei aktiven Schaltern; sonst bleibt die Zeile byte-gleich.
     */
    private final boolean searchHitsEnabled;

    /**
     * Pre-REQ-038 constructor, kept for existing callers and tests / Konstruktor wie vor REQ-038:
     * funnel and drop-off switches off, so never a {@code hits} field. / Nie ein {@code hits}-Feld.
     */
    public LoggingFilter(boolean usageStatsEnabled) {
        this(usageStatsEnabled, false, false);
    }

    @Autowired
    public LoggingFilter(@Value("${usage.enabled:true}") boolean usageStatsEnabled,
                         @Value("${usage.funnel.enabled:false}") boolean funnelEnabled,
                         @Value("${usage.dropoff.enabled:false}") boolean dropoffEnabled) {
        this.usageStatsEnabled = usageStatsEnabled;
        this.searchHitsEnabled = usageStatsEnabled && funnelEnabled && dropoffEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        boolean completed = false;
        try {
            filterChain.doFilter(request, response);
            completed = true;
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

                SearchHits hits = completed ? searchHits(request, response) : null;
                if (hits != null) {
                    log.info(ACCESS_PATTERN_WITH_DEVICE_AND_HITS,
                        correlationId != null ? correlationId : "",
                        sessionId != null ? sessionId : "",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        duration,
                        device,
                        hits.key());
                } else {
                    log.info(ACCESS_PATTERN_WITH_DEVICE,
                        correlationId != null ? correlationId : "",
                        sessionId != null ? sessionId : "",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        duration,
                        device);
                }
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

    /**
     * The hit level to append, or {@code null} for a byte-identical line (BR-29, AC-34) / die
     * anzuhängende Trefferstufe oder {@code null}.
     *
     * <p>Only {@code POST /api/v1/snaps} with status &lt; 400 whose chain completed normally, and
     * only a genuine {@link SearchHits} value set by {@code SnapsController} — any other attribute
     * content (e.g. a string) is ignored, so the field can only ever be {@code none|few|many}
     * (whitelist). / Nur ein echter {@link SearchHits}-Wert; alles andere wird ignoriert.
     */
    private SearchHits searchHits(HttpServletRequest request, HttpServletResponse response) {
        if (!searchHitsEnabled
                || !SEARCH_METHOD.equals(request.getMethod())
                || !SEARCH_URI.equals(request.getRequestURI())
                || response.getStatus() >= FIRST_ERROR_STATUS) {
            return null;
        }
        return request.getAttribute(UsageAccessAttributes.SEARCH_HITS) instanceof SearchHits hits
            ? hits : null;
    }
}
