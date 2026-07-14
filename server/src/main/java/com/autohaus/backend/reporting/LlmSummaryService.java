package com.autohaus.backend.reporting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Produces the narrative summary via Spring AI (REQ-030). The LLM receives only
 * aggregated numbers + error types + affected brands/locations — never raw customer
 * payloads. The (toggle-gated) {@code ChatClient} bean is pre-configured with a
 * {@code StructuredOutputValidationAdvisor} (self-correcting retry) and a
 * {@code SafeGuardAdvisor} guardrail. Any failure degrades gracefully to a neutral
 * fallback so the report is always rendered.
 *
 * <p>Deliberately not a {@code @Service}: it is created as a bean only when the feature
 * toggle is on (see {@code ErrorReportingConfiguration}).
 */
public class LlmSummaryService {

    private static final Logger log = LoggerFactory.getLogger(LlmSummaryService.class);

    private final ChatClient chatClient;

    public LlmSummaryService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public AiErrorReportSummary summarize(ReportModel model) {
        try {
            AiErrorReportSummary summary = chatClient.prompt()
                .user(buildPrompt(model))
                .call()
                .entity(AiErrorReportSummary.class);
            return summary != null ? summary : AiErrorReportSummary.unavailable();
        } catch (RuntimeException e) {
            log.warn("LLM summary generation failed, using neutral fallback: {}", e.getMessage());
            return AiErrorReportSummary.unavailable();
        }
    }

    /**
     * Builds the prompt from aggregates only — no request/response payloads, no customer data.
     */
    private String buildPrompt(ReportModel model) {
        BookingStats snaps = model.snaps();
        BookingStats appointments = model.appointments();

        Map<String, Integer> byType = new LinkedHashMap<>();
        for (ErrorEntry e : model.errors()) {
            String t = nz(e.errorType());
            byType.put(t, byType.getOrDefault(t, 0) + 1);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Du bist ein technischer Betriebsassistent für ein Autohaus-Terminbuchungssystem. ")
            .append("Erstelle eine sachliche, deutsche Zusammenfassung der Fehlerlage der letzten 24 Stunden. ")
            .append("Nutze ausschließlich die folgenden aggregierten Kennzahlen — keine erfundenen Zahlen.\n\n");
        sb.append("Terminsuchen (POST /snaps): ")
            .append(snaps.started()).append(" gestartet, ")
            .append(snaps.succeeded()).append(" erfolgreich, ")
            .append(snaps.failed()).append(" fehlgeschlagen.\n");
        sb.append("Buchungen (POST /appointments): ")
            .append(appointments.started()).append(" gestartet, ")
            .append(appointments.succeeded()).append(" erfolgreich, ")
            .append(appointments.failed()).append(" fehlgeschlagen.\n");
        sb.append("Fehler gesamt: ").append(model.errors().size()).append("\n");
        sb.append("Fehler nach Typ: ").append(byType).append("\n");
        sb.append("Fehler nach Marke: ").append(model.errorsByBrand()).append("\n");
        sb.append("Fehler nach Standort: ").append(model.errorsByLocation()).append("\n\n");
        sb.append("Liefere: eine kurze Überschrift, eine Gesamtbewertung, die wichtigsten ")
            .append("priorisierten Probleme (mit Schweregrad, betroffenen Marken/Standorten und einer ")
            .append("Ursachenhypothese) sowie konkrete Handlungsempfehlungen.");
        return sb.toString();
    }

    private static String nz(String s) {
        return (s == null || s.isBlank()) ? "unbekannt" : s;
    }
}
