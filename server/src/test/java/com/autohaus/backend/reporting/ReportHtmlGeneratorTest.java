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
}
