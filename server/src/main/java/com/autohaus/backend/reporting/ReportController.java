package com.autohaus.backend.reporting;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the daily error report as HTML (REQ-030), behind Basic Auth (see SecurityConfig,
 * {@code /internal/**}). Always available; shows a placeholder until the (toggle-gated)
 * scheduler has produced a report. The refresh endpoint only works when the feature is on.
 */
@RestController
public class ReportController {

    private final ReportStore store;
    private final ObjectProvider<ErrorReportScheduler> scheduler;

    public ReportController(ReportStore store, ObjectProvider<ErrorReportScheduler> scheduler) {
        this.store = store;
        this.scheduler = scheduler;
    }

    @GetMapping(value = "/internal/report", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> report() {
        String body = store.hasReport() ? store.html() : placeholder();
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(body);
    }

    @PostMapping(value = "/internal/report/refresh", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> refresh() {
        ErrorReportScheduler active = scheduler.getIfAvailable();
        if (active == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Report-Feature nicht konfiguriert (LLM_API_KEY / LLM_MODEL fehlen).");
        }
        active.generate();
        return ResponseEntity.ok("Report neu erzeugt.");
    }

    private String placeholder() {
        return "<!doctype html><html lang=\"de\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
            + "<title>Fehler-Report</title></head>"
            + "<body style=\"font-family:system-ui,sans-serif;max-width:40rem;margin:3rem auto;padding:0 1rem;color:#1a2b3c\">"
            + "<h1>Fehler-Report</h1><p>Es liegt noch kein Report vor. Das Feature erstellt automatisch "
            + "einen Report, sobald die Secrets <code>LLM_API_KEY</code> und <code>LLM_MODEL</code> gesetzt "
            + "sind und der nächtliche Lauf (bzw. der Start) erfolgt ist.</p></body></html>";
    }
}
