package com.autohaus.backend.reporting;

import com.autohaus.backend.usage.UsageSectionRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Report generation writes a <b>fragment</b> into the store (REQ-037 §14.2) / die Reporterzeugung
 * legt ein Fragment im Store ab.
 *
 * <p>TC-17 guards the fallback path in particular: if it stored a full
 * {@code <!doctype html>…</html>} document, {@code ReportHtmlGenerator.renderPage(...)} would embed
 * it as a fragment and produce nested {@code html}/{@code head}/{@code body} elements — exactly
 * during an outage, when the page matters most. / Gerade der Störungsfall darf keine
 * verschachtelte Seite erzeugen.
 */
@ExtendWith(MockitoExtension.class)
class ErrorReportSchedulerTest {

    @Mock
    private ReportAggregator aggregator;

    @Mock
    private LlmSummaryService llmSummaryService;

    private final ReportHtmlGenerator htmlGenerator = new ReportHtmlGenerator(new UsageSectionRenderer());
    private final ReportStore store = new ReportStore();

    private ErrorReportScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ErrorReportScheduler(aggregator, llmSummaryService, htmlGenerator, store);
    }

    /** TC-17: the Log-Analytics fallback path stores a fragment, not a whole document. */
    @Test
    void fallbackPathStoresAFragmentWithoutDocumentFrame() {
        when(aggregator.aggregate())
            .thenThrow(new LogAnalyticsClient.LogAnalyticsUnavailableException("no managed identity"));

        scheduler.generate();

        assertTrue(store.hasReport(), "the outage must still leave something on the page");
        String fragment = store.fragment();
        assertFalse(fragment.contains("<!doctype"), "TC-17: the fallback must not store a document");
        assertFalse(fragment.contains("<html"), "TC-17: the fallback must not store <html>");
        assertTrue(fragment.startsWith("<section class=\"ai\""), "a fragment starts with the AI section");
        assertTrue(fragment.contains("Datenquelle derzeit nicht erreichbar"), "the outage is named");
        verifyNoInteractions(llmSummaryService);
    }

    /** The fragment of the fallback embeds cleanly into the page frame (REQ-037 §14.2). */
    @Test
    void fallbackFragmentEmbedsIntoThePageWithoutNesting() {
        when(aggregator.aggregate())
            .thenThrow(new LogAnalyticsClient.LogAnalyticsUnavailableException("network"));

        scheduler.generate();
        String html = htmlGenerator.renderPage(null, store.fragment());

        assertEquals(1, countOccurrences(html, "<!doctype"), "exactly one document declaration");
        assertEquals(1, countOccurrences(html, "<html"), "exactly one <html> element");
        assertEquals(1, countOccurrences(html, "<body>"), "exactly one <body> element");
    }

    /** The normal path stores a fragment too — the frame is added by the controller. */
    @Test
    void normalPathStoresAFragmentWithoutDocumentFrame() {
        ReportModel base = new ReportModel(
            Instant.parse("2026-09-21T03:00:00Z"),
            new BookingStats(200, 190, 10),
            new BookingStats(100, 95, 5),
            Map.of(), Map.of(), List.of(), null);
        when(aggregator.aggregate()).thenReturn(base);
        when(llmSummaryService.summarize(any(ReportModel.class)))
            .thenReturn(AiErrorReportSummary.unavailable());

        scheduler.generate();

        String fragment = store.fragment();
        assertNotNull(fragment);
        assertFalse(fragment.contains("<!doctype"));
        assertFalse(fragment.contains("<html"));
        assertTrue(fragment.contains("<section class=\"ov\">"), "the status overview is part of the fragment");
        assertEquals(base.generatedAt(), store.generatedAt());
    }

    /** An unexpected failure leaves the previous fragment untouched (REQ-030 degradation). */
    @Test
    void unexpectedFailureKeepsThePreviousFragment() {
        store.update("<section class=\"ai\">previous</section>", Instant.parse("2026-09-20T03:00:00Z"));
        when(aggregator.aggregate()).thenThrow(new IllegalStateException("boom"));

        scheduler.generate();

        assertEquals("<section class=\"ai\">previous</section>", store.fragment());
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i > -1; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
