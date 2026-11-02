package com.autohaus.backend.reporting;

import java.util.List;

/**
 * Typed structured output the LLM must return (REQ-030). Validated by Spring AI's
 * {@code StructuredOutputValidationAdvisor}; schema violations trigger a self-correcting
 * retry. The LLM only ever receives aggregated stats + error types — never raw customer
 * payloads.
 */
public record AiErrorReportSummary(
    String headline,
    String overallAssessment,
    List<PrioritizedIssue> topIssues,
    List<String> recommendations
) {

    public record PrioritizedIssue(
        String title,
        String severity,          // "low" | "medium" | "high" | "critical"
        String affectedBrands,
        String affectedLocations,
        String rootCauseHypothesis
    ) {}

    /** Neutral fallback used when the LLM is unavailable or disabled. */
    public static AiErrorReportSummary unavailable() {
        return new AiErrorReportSummary(
            "KI-Zusammenfassung nicht verfügbar",
            "Die automatische Auswertung konnte nicht erstellt werden. "
                + "Die Kennzahlen und die Fehlerliste unten sind dennoch vollständig.",
            List.of(),
            List.of()
        );
    }
}
