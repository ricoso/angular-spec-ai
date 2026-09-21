package com.autohaus.backend.reporting;

import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.usage.UsageStats;
import com.autohaus.backend.usage.UsageStatsScheduler;
import com.autohaus.backend.usage.UsageStatsStore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Serves the internal status page as HTML (REQ-030, REQ-037), behind Basic Auth (see
 * {@code SecurityConfig}, {@code /internal/**}). The page always renders: the usage statistics come
 * from the in-memory snapshot and are independent of the LLM toggle (AC-6), the error report is
 * added as a fragment as soon as the (toggle-gated) scheduler has produced one.
 *
 * <p><b>No aggregation in the request path (AC-7):</b> every endpoint here reads
 * {@link UsageStatsStore} only — no Log Analytics query, no blob access. Those run exclusively in
 * {@link UsageStatsScheduler} (startup, every 15 min, nightly rollup). / Keine KQL-/Blob-Abfrage im
 * Request-Pfad.
 *
 * <p>With {@code usage.enabled=false} the controller passes {@code null} as the usage model, which
 * makes {@code renderPage(...)} drop the section without replacement, and
 * {@code usage.json} answers {@code 404} (AC-22, BR-13). / Kill-Switch entfernt Sektion und
 * Endpoint ersatzlos.
 */
@RestController
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    /**
     * Own mapper for {@code usage.json} (REQ-037 §12.2) / eigener Mapper für {@code usage.json}.
     *
     * <p>Two reasons it cannot be the injected bean: it is a bare {@code new ObjectMapper()}
     * without {@code JavaTimeModule} ({@code config/WebConfig}), and the application sets
     * {@code spring.jackson.default-property-inclusion: non_null} globally — which would silently
     * drop {@code searchRate}/{@code bookingRate}/{@code closeRate}, {@code liveAsOf},
     * {@code collectingSince} and {@code lastRollupDay} although §12.2 shows them explicitly as
     * {@code null}. {@code ALWAYS} keeps the contract stable for monitoring consumers; the global
     * setting stays untouched so the existing API responses do not change. / Nullwerte müssen
     * erhalten bleiben, deshalb ein eigener Mapper.
     */
    private static final ObjectMapper USAGE_MAPPER = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .serializationInclusion(JsonInclude.Include.ALWAYS)
        .build();

    /**
     * Hint shown instead of the report sections while no report exists (REQ-030 wording kept). It
     * is a <b>fragment</b>, the page frame comes from {@code renderPage(...)}; the statistics are
     * rendered above it (AC-6). / Bisheriger Hinweistext, jetzt als Fragment.
     */
    private static final String NO_REPORT_HINT =
        "<section class=\"nr\"><h2>Fehler-Report</h2>"
            + "<p class=\"empty\">Es liegt noch kein Report vor. Das Feature erstellt automatisch "
            + "einen Report, sobald die Secrets <code>LLM_API_KEY</code> und <code>LLM_MODEL</code> gesetzt "
            + "sind und der nächtliche Lauf (bzw. der Start) erfolgt ist.</p></section>";

    private static final String NO_LLM_MESSAGE = "Report-Feature nicht konfiguriert (LLM_API_KEY / LLM_MODEL fehlen).";

    private final ReportStore store;
    private final ReportHtmlGenerator htmlGenerator;
    private final UsageStatsStore usageStatsStore;
    private final UsageStatsProperties usageProperties;
    private final ObjectProvider<ErrorReportScheduler> scheduler;
    private final ObjectProvider<UsageStatsScheduler> usageScheduler;

    public ReportController(ReportStore store,
                            ReportHtmlGenerator htmlGenerator,
                            UsageStatsStore usageStatsStore,
                            UsageStatsProperties usageProperties,
                            ObjectProvider<ErrorReportScheduler> scheduler,
                            ObjectProvider<UsageStatsScheduler> usageScheduler) {
        this.store = store;
        this.htmlGenerator = htmlGenerator;
        this.usageStatsStore = usageStatsStore;
        this.usageProperties = usageProperties;
        this.scheduler = scheduler;
        this.usageScheduler = usageScheduler;
    }

    /**
     * Status page: header → usage statistics → error report fragment (or the hint above) → footer
     * (§12.1, AC-2, AC-6). Reads in-memory state only (AC-7). / Statusseite, rein aus dem Speicher.
     */
    @GetMapping(value = "/internal/report", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> report() {
        UsageStats usage = usageEnabled() ? usageStatsStore.current() : null;
        String fragment = store.hasReport() ? store.fragment() : NO_REPORT_HINT;
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(htmlGenerator.renderPage(usage, fragment));
    }

    /**
     * Machine-readable aggregate for verification/monitoring (§12.2, AC-16) — the same snapshot the
     * page renders, serialized directly from {@link UsageStats}. {@code 404} when the feature is
     * switched off (AC-22). / Dasselbe Aggregat als JSON.
     */
    @GetMapping(value = "/internal/report/usage.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> usageJson() {
        if (!usageEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        try {
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(USAGE_MAPPER.writeValueAsString(usageStatsStore.current()));
        } catch (JsonProcessingException e) {
            log.error("REQ-037: usage snapshot not serializable: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"usage stats not serializable\"}");
        }
    }

    /**
     * Manual refresh (§12.3): updates the statistics whenever the {@link UsageStatsScheduler} bean
     * exists ({@code usage.enabled=true}) and the error report whenever its own scheduler bean
     * exists. Only when <b>both</b> are absent the endpoint still answers {@code 503}.
     *
     * <p>Throttled server-side to one run per {@code usage.refresh-min-interval-seconds} (AC-21):
     * the second call inside that window gets {@code 429} <b>before</b> any KQL or blob request is
     * issued — {@code /internal/**} is publicly reachable and every run costs 3-9 Log Analytics
     * queries. The throttle clock is stamped by the scheduler itself, so a cron run seconds earlier
     * blocks a manual call too. / Gedrosselt, ohne Backend-Abfrage bei Ablehnung.
     */
    @PostMapping(value = "/internal/report/refresh", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> refresh() {
        UsageStatsScheduler activeUsage = usageScheduler.getIfAvailable();
        ErrorReportScheduler activeReport = scheduler.getIfAvailable();

        if (activeUsage == null && activeReport == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(NO_LLM_MESSAGE);
        }

        List<String> messages = new ArrayList<>(2);
        if (activeUsage != null) {
            if (!activeUsage.refreshIfAllowed()) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Zu früh — frühestens " + refreshMinIntervalSeconds() + " s nach dem letzten Lauf.");
            }
            messages.add("Statistik neu erzeugt.");
        }
        if (activeReport != null) {
            activeReport.generate();
            messages.add("Report neu erzeugt.");
        } else {
            messages.add(NO_LLM_MESSAGE);
        }
        return ResponseEntity.ok(String.join(" ", messages));
    }

    /** Kill switch {@code usage.enabled} (BR-13) — defensive against a missing properties bean. */
    private boolean usageEnabled() {
        return usageProperties != null && usageProperties.enabled();
    }

    private int refreshMinIntervalSeconds() {
        return usageProperties != null ? usageProperties.refreshMinIntervalSeconds() : 0;
    }
}
