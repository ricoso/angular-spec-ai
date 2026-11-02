package com.autohaus.backend.reporting;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Aggregated report input for HTML generation and the LLM prompt (REQ-030).
 *
 * <p>Iteration 2: the single booking overview became a per-category status overview —
 * {@code snaps} (appointment search, POST /snaps) and {@code appointments} (booking
 * completion, POST /appointments) each carry started/succeeded/failed counters, and
 * {@code errorsByBrand}/{@code errorsByLocation} hold the deterministic error distribution
 * (insertion-ordered, most errors first) used for the "Marken"/"Standorte" traffic-light tiles.
 *
 * <p>{@code summary} may be null / a fallback when the LLM is unavailable.
 */
public record ReportModel(
    Instant generatedAt,
    BookingStats snaps,
    BookingStats appointments,
    Map<String, Long> errorsByBrand,
    Map<String, Long> errorsByLocation,
    List<ErrorEntry> errors,
    AiErrorReportSummary summary
) {}
