package com.autohaus.backend.usage.insights;

import java.util.Map;

/**
 * Per-branch counters (REQ-038 §10.3) — the only place the location dimension exists (BR-7) /
 * Zähler je Standort, einzige Stelle mit Standort-Dimension. Lives in {@code branchDays}
 * (until the 8th of the following month) and {@code branchMonths} (24 months). / Tagesgenau nur
 * bis zur Verdichtung.
 *
 * @param bookings online bookings (R-1)
 * @param serviceViews service-selection page views (R-3, requests without {@code sessionId})
 * @param groups service-group mentions (G-2)
 * @param mobility bookings with a rental car (G-3)
 * @param offHours bookings outside Mo–Fr 07–18 Uhr (T-2)
 * @param slotLate bookings with a slot daypart {@code late} (T-4)
 */
public record BranchCounts(
    Long bookings,
    Long serviceViews,
    Map<String, Long> groups,
    Long mobility,
    Long offHours,
    Long slotLate
) {}
