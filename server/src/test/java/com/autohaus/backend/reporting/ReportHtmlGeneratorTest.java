package com.autohaus.backend.reporting;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReportHtmlGeneratorTest {

    private final ReportHtmlGenerator generator = new ReportHtmlGenerator();

    private ReportModel sampleModel(String requestBody) {
        BookingStats appointments = new BookingStats(100, 80, 20);
        BookingStats snaps = new BookingStats(200, 130, 70);
        ErrorEntry error = new ErrorEntry(
            Instant.parse("2026-07-09T02:10:00Z"),
            "trace-123",
            "volkswagen",
            "Volkswagen Essen-Kray",
            List.of("Inspektion", "Ölwechsel"),
            "No match (Operation/Marke)",
            "/api/planning/snaps",
            requestBody,
            "{\"code\":\"InternalServerError\"}");
        AiErrorReportSummary summary = new AiErrorReportSummary(
            "Alles im grünen Bereich",
            "Die Buchungsstrecke ist stabil.",
            List.of(new AiErrorReportSummary.PrioritizedIssue(
                "VW No-match", "high", "volkswagen", "Essen-Kray", "SoftNet-Katalog")),
            List.of("SoftNet-Ticket eröffnen"));
        return new ReportModel(Instant.parse("2026-07-09T03:00:00Z"), snaps, appointments,
            Map.of("volkswagen", 3L), Map.of("Volkswagen Essen-Kray", 3L), List.of(error), summary);
    }

    @Test
    void rendersSummaryStatsAndErrorFields() {
        String html = generator.generate(sampleModel("{\"brand\":\"volkswagen\"}"));
        assertTrue(html.contains("Alles im grünen Bereich"));
        assertTrue(html.contains("VW No-match"));
        assertTrue(html.contains("SoftNet-Ticket eröffnen"));
        assertTrue(html.contains("volkswagen"));
        assertTrue(html.contains("Volkswagen Essen-Kray"));
        assertTrue(html.contains("Inspektion, Ölwechsel"));
        assertTrue(html.contains("100"));
        assertTrue(html.contains("80"));
        assertTrue(html.contains("20"));
        assertTrue(html.contains("trace-123"));
        // Iteration 2: category status overview + PII note
        assertTrue(html.contains("Status-Übersicht"));
        assertTrue(html.contains("Terminsuchen"));
        assertTrue(html.contains("Buchungen"));
        assertTrue(html.contains("Marken"));
        assertTrue(html.contains("Standorte"));
        assertTrue(html.contains("Payloads PII-maskiert"));
    }

    @Test
    void rendersErrorFilterBarWithCountsAndDataAttributes() {
        String html = generator.generate(sampleModel("{\"brand\":\"volkswagen\"}"));
        // filter bar + radios present
        assertTrue(html.contains("class=\"filters\""), "filter bar must be rendered");
        assertTrue(html.contains("id=\"flt-all\""));
        assertTrue(html.contains("Alle (1)"));
        assertTrue(html.contains("id=\"flt-ep-snaps\""));
        assertTrue(html.contains("Terminsuchen (1)"));
        // cards carry the filterable endpoint attribute
        assertTrue(html.contains("data-ep=\"snaps\""));
        // no appointment filter button (sample has no appointment error)
        assertFalse(html.contains("id=\"flt-ep-appointments\""));
        // no error-type filter buttons (filter by endpoint only)
        assertFalse(html.contains("id=\"flt-ty-"));
    }

    @Test
    void escapesHtmlInPayloads() {
        String html = generator.generate(sampleModel("{\"note\":\"<script>alert(1)</script>\"}"));
        assertTrue(html.contains("&lt;script&gt;"), "payload angle brackets must be escaped");
        assertFalse(html.contains("<script>alert(1)</script>"), "raw script tag must not appear");
    }

    @Test
    void rendersFallbackSummaryAndEmptyErrorList() {
        ReportModel model = new ReportModel(
            Instant.parse("2026-07-09T03:00:00Z"),
            new BookingStats(0, 0, 0),
            new BookingStats(0, 0, 0),
            Map.of(),
            Map.of(),
            List.of(),
            AiErrorReportSummary.unavailable());
        String html = generator.generate(model);
        assertTrue(html.contains("KI-Zusammenfassung nicht verfügbar"));
        assertTrue(html.contains("Keine Fehler im Auswertungszeitraum."));
    }

    @Test
    void toleratesNullSummary() {
        ReportModel model = new ReportModel(
            Instant.parse("2026-07-09T03:00:00Z"), new BookingStats(1, 1, 0), new BookingStats(1, 1, 0),
            Map.of(), Map.of(), List.of(), null);
        String html = generator.generate(model);
        assertNotNull(html);
        assertTrue(html.contains("<!doctype html>"));
    }

    // -------------------------------------------------------------------------------------
    // REQ-037 §14.2: page frame and report fragment are separate / Seitenrahmen und Fragment
    // sind getrennt. The store holds a fragment, renderPage(...) adds the frame — otherwise a
    // full document would end up nested inside the page (TC-17).
    // -------------------------------------------------------------------------------------

    @Test
    void reportFragmentCarriesNoDocumentFrame() {
        ReportModel model = new ReportModel(
            Instant.parse("2026-07-09T03:00:00Z"),
            new BookingStats(0, 0, 0), new BookingStats(0, 0, 0),
            Map.of(), Map.of(), List.of(), AiErrorReportSummary.unavailable());

        String fragment = generator.reportFragment(model);

        assertFalse(fragment.contains("<!doctype"), "a fragment must not open a document");
        assertFalse(fragment.contains("<html"), "a fragment must not open <html>");
        assertFalse(fragment.contains("<style"), "the page stylesheet belongs to renderPage(...)");
        assertTrue(fragment.startsWith("<section class=\"ai\""), "the fragment starts with the AI section");
    }

    @Test
    void reportFragmentWithErrorsStillCarriesNoDocumentFrame() {
        String fragment = generator.reportFragment(sampleModel("{\"brand\":\"volkswagen\"}"));

        assertFalse(fragment.contains("<!doctype"));
        assertFalse(fragment.contains("<html"));
        // The only <style> a fragment may carry is the per-report CSS-only error filter kept from
        // REQ-030 (one selector per filter button); the page stylesheet is never part of it. /
        // Nur der Filter-Style des Fehler-Reports, nie das Seiten-CSS.
        assertFalse(fragment.contains(":root{--ink:"), "the page stylesheet must stay in renderPage(...)");
    }

    @Test
    void renderPagePlacesUsageStatisticsBeforeTheAiSection() {
        String fragment = generator.reportFragment(sampleModel("{\"brand\":\"volkswagen\"}"));

        String html = generator.renderPage(com.autohaus.backend.usage.UsageStats.unavailable(), fragment);

        int header = html.indexOf("<header class=\"hd\">");
        int usage = html.indexOf("class=\"us\"");
        int ai = html.indexOf("<section class=\"ai\"");
        assertTrue(header > -1, "page header must be rendered");
        assertTrue(usage > -1, "AC-2: the usage section must be rendered");
        assertTrue(ai > -1, "the report fragment must be embedded");
        assertTrue(header < usage, "AC-2: the statistics follow the page header");
        assertTrue(usage < ai, "AC-2: the statistics precede the KI-Zusammenfassung");
        assertTrue(html.startsWith("<!doctype html>"), "renderPage(...) still produces a full document");
    }

    @Test
    void renderPageDropsTheUsageSectionWhenTheFeatureIsOff() {
        String html = generator.renderPage(null, "<section class=\"ai\">x</section>");

        assertFalse(html.contains("class=\"us\""), "AC-22: the section is dropped");
        assertFalse(html.contains("nicht verf\u00fcgbar"), "AC-22: without any replacement hint");
        assertTrue(html.contains("<section class=\"ai\">x</section>"), "the fragment is still embedded");
    }
}
