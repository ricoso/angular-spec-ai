package com.autohaus.backend.reporting;

import java.time.Instant;
import java.util.List;

/**
 * Aggregated report input for HTML generation and the LLM prompt (REQ-030).
 * {@code summary} may be null / a fallback when the LLM is unavailable.
 */
public record ReportModel(
    Instant generatedAt,
    BookingStats stats,
    List<ErrorEntry> errors,
    AiErrorReportSummary summary
) {}
