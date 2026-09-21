package com.autohaus.backend.usage;

import java.time.LocalDate;

/**
 * One display period's aggregate (month or year) / Aggregat eines Anzeigezeitraums (Monat oder
 * Jahr). {@code hasData == false} means "no data" (rendered as „—") and is NOT the same as all
 * zeros (AC-12). / „keine Daten" ist nicht dasselbe wie lauter Nullen.
 *
 * <p>The record components are the JSON field names of {@code GET /internal/report/usage.json}
 * (§12.2) — {@code rangeLabel}, not {@code range}. / Die Feldnamen entsprechen exakt den
 * Record-Komponenten.
 *
 * @param label display label / Anzeigename, z. B. „Aktueller Monat (September 2026)",
 *     „Jahresübersicht 2026" oder „September"
 * @param rangeLabel display-only range incl. time zone / reine Anzeige,
 *     „01.09.2026 – 21.09.2026 · Europe/Berlin"
 * @param rangeFrom machine readable start, ISO in JSON / maschinenlesbar: „2026-09-01"
 * @param rangeTo machine readable end, ISO in JSON / maschinenlesbar: „2026-09-21"
 * @param hasData whether the period has any day rows at all / ob überhaupt Tageszeilen vorliegen
 * @param visits visits per device class / Besuche je Geräteklasse
 * @param visitCount total visits — always equals {@code visits.total()} (BR-1, AC-4)
 * @param searchRequests POST /api/v1/snaps, total / gesamt
 * @param searchRequestsOk thereof {@code statusCode < 400} / davon erfolgreich
 * @param bookingRequests POST /api/v1/appointments, total / gesamt
 * @param bookingRequestsOk thereof {@code statusCode < 400} / davon erfolgreich
 * @param sessionsWithSearch sessions with at least one successful search / Sitzungen mit Suche
 * @param sessionsWithBooking sessions with at least one successful booking / Sitzungen mit Buchung
 * @param searchRate {@code sessionsWithSearch / visits}; {@code null} → „—" when the divisor is 0,
 *     otherwise one decimal (BR-6)
 * @param bookingRate total conversion {@code sessionsWithBooking / visits} / Buchungsquote
 * @param closeRate {@code sessionsWithBooking / sessionsWithSearch} / Abschlussquote
 */
public record PeriodUsage(
    String label,
    String rangeLabel,
    LocalDate rangeFrom,
    LocalDate rangeTo,
    boolean hasData,
    DeviceBreakdown visits,
    long visitCount,
    long searchRequests,
    long searchRequestsOk,
    long bookingRequests,
    long bookingRequestsOk,
    long sessionsWithSearch,
    long sessionsWithBooking,
    Double searchRate,
    Double bookingRate,
    Double closeRate
) {}
