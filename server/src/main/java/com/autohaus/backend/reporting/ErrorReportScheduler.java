package com.autohaus.backend.reporting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates report generation (REQ-030): query → aggregate → LLM summary → HTML → store.
 * Runs nightly (cron) and once on startup (bridges container restarts). Created as a bean
 * only when the feature toggle is on (see {@code ErrorReportingConfiguration}).
 */
public class ErrorReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(ErrorReportScheduler.class);

    private final ReportAggregator aggregator;
    private final LlmSummaryService llmSummaryService;
    private final ReportHtmlGenerator htmlGenerator;
    private final ReportStore store;

    public ErrorReportScheduler(ReportAggregator aggregator, LlmSummaryService llmSummaryService,
                                ReportHtmlGenerator htmlGenerator, ReportStore store) {
        this.aggregator = aggregator;
        this.llmSummaryService = llmSummaryService;
        this.htmlGenerator = htmlGenerator;
        this.store = store;
    }

    @Scheduled(cron = "${reporting.cron}", zone = "${reporting.zone}")
    public void scheduledRun() {
        generate();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("REQ-030 report feature active — generating initial report on startup");
        generate();
    }

    /** Generates a fresh report and stores it. Degrades gracefully on any failure. */
    public void generate() {
        try {
            ReportModel base = aggregator.aggregate();
            AiErrorReportSummary summary = llmSummaryService.summarize(base);
            ReportModel full = new ReportModel(base.generatedAt(), base.snaps(), base.appointments(),
                base.errorsByBrand(), base.errorsByLocation(), base.errors(), summary);
            store.update(htmlGenerator.generate(full), full.generatedAt());
            log.info("REQ-030 report generated: {} snap searches, {} bookings, {} errors",
                base.snaps().started(), base.appointments().started(), base.errors().size());
        } catch (LogAnalyticsClient.LogAnalyticsUnavailableException e) {
            log.warn("Log Analytics unavailable — storing fallback report: {}", e.getMessage());
            ReportModel fallback = new ReportModel(
                Instant.now(),
                new BookingStats(0, 0, 0),
                new BookingStats(0, 0, 0),
                Map.of(),
                Map.of(),
                List.of(),
                new AiErrorReportSummary(
                    "Datenquelle derzeit nicht erreichbar",
                    "Die Log-Analytics-Abfrage ist momentan nicht möglich. Sobald die Verbindung "
                        + "wieder besteht, wird beim nächsten Lauf ein vollständiger Report erstellt.",
                    List.of(),
                    List.of()));
            store.update(htmlGenerator.generate(fallback), fallback.generatedAt());
        } catch (RuntimeException e) {
            log.error("REQ-030 report generation failed unexpectedly: {}", e.getMessage(), e);
        }
    }
}
