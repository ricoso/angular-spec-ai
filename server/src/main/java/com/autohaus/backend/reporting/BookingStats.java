package com.autohaus.backend.reporting;

/**
 * Overview counters for one funnel category over the report window (REQ-030): appointment
 * searches (POST /snaps) or booking completions (POST /appointments).
 */
public record BookingStats(
    long started,     // total attempts
    long succeeded,   // 2xx
    long failed       // 4xx + 5xx
) {}
