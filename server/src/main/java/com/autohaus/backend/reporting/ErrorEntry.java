package com.autohaus.backend.reporting;

import java.time.Instant;
import java.util.List;

/**
 * One {@code SOFTNET_ERROR} log line, deterministically parsed from Log Analytics (REQ-030).
 * {@code operations} holds resolved plain-text service names (never raw operation IDs).
 */
public record ErrorEntry(
    Instant timestamp,
    String traceId,
    String brand,
    String location,
    List<String> operations,
    String errorType,
    String endpoint,
    String requestBody,
    String responseBody
) {}
