package com.autohaus.backend.reporting;

/**
 * Overview counters for bookings (POST /appointments) over the report window (REQ-030).
 */
public record BookingStats(
    long started,     // total booking attempts
    long succeeded,   // 2xx
    long failed       // 4xx + 5xx
) {}
