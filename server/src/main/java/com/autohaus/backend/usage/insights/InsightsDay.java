package com.autohaus.backend.usage.insights;

import java.util.Map;

/**
 * Global counters of one calendar day — no location dimension (REQ-038 §10.3) / globale Zähler
 * eines Kalendertags, ohne Standort-Dimension. {@code null} = source off / not collected → „—",
 * never 0 (BR-19); a missing key inside a present map means 0. / Fehlender Zähler ist „—", nie 0.
 *
 * @param bookings bookings of the day / Buchungen des Tages
 * @param bookingDaypart bookings per {@code day|evening|night} (T-1)
 * @param slotGrid bookings per slot weekday {@code MO..SU} × daypart {@code early..late} (T-3)
 * @param slotUnknown bookings without a usable {@code checkInTime} (E12)
 * @param leadTime bookings per lead-time bucket {@code 0-1..31+,unknown} (T-5)
 * @param groups service-group mentions per {@link ServiceGroup} key (G-1, BR-11)
 * @param mobility bookings with a rental car (G-3)
 * @param serviceViews sum of R-3 over all branches / Leistungsaufrufe gesamt
 * @param funnel funnel of the day, {@code null} while {@code usage.funnel.enabled=false}
 * @param variants REQ-042: bookings per entry variant {@code default|tireChange|recall} (G-4);
 *                 {@code null} = not collected (older blobs) → „—", a missing key means 0
 */
public record InsightsDay(
    Long bookings,
    Map<String, Long> bookingDaypart,
    Map<String, Map<String, Long>> slotGrid,
    Long slotUnknown,
    Map<String, Long> leadTime,
    Map<String, Long> groups,
    Long mobility,
    Long serviceViews,
    FunnelDay funnel,
    Map<String, Long> variants
) {
    /** REQ-042 compatibility constructor without the entry-variant distribution ({@code variants = null}). */
    public InsightsDay(Long bookings, Map<String, Long> bookingDaypart,
            Map<String, Map<String, Long>> slotGrid, Long slotUnknown, Map<String, Long> leadTime,
            Map<String, Long> groups, Long mobility, Long serviceViews, FunnelDay funnel) {
        this(bookings, bookingDaypart, slotGrid, slotUnknown, leadTime, groups, mobility,
            serviceViews, funnel, null);
    }
}
