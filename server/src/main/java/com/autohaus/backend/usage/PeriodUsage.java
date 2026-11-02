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
 * @param apiRequests REQ-043 K-1: API requests of the recorded days (server load, since K-3 only
 *     in {@code usage.json}, no longer on the page) / API-Aufrufe; {@code null} = nicht erfasst
 * @param fileRequests REQ-043: file requests (app code, images) of the recorded days / Datei-Abrufe
 * @param requestsFrom first day of the period that carries the counters, {@code null} when none —
 *     later than {@code rangeFrom} means the figures are partial / erster erfasster Tag
 * @param searchSessionsFailed REQ-043 K-3: sessions whose every search failed, summed over the
 *     recorded days; {@code null} → not recorded / Sitzungen ohne Suchergebnis
 * @param bookingSessionsFailed REQ-043 K-3: sessions whose every booking attempt failed /
 *     Sitzungen ohne erfolgreiche Buchung trotz Versuch
 * @param sessionsFailedFrom first day of the period that carries both failure counters,
 *     {@code null} when none / erster Tag mit Fehler-Zählern
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
    Double closeRate,
    Long apiRequests,
    Long fileRequests,
    LocalDate requestsFrom,
    Long searchSessionsFailed,
    Long bookingSessionsFailed,
    LocalDate sessionsFailedFrom
) {

    /** Pre-REQ-043 shape without the request counter / Form vor REQ-043. */
    public PeriodUsage(String label, String rangeLabel, LocalDate rangeFrom, LocalDate rangeTo,
                       boolean hasData, DeviceBreakdown visits, long visitCount, long searchRequests,
                       long searchRequestsOk, long bookingRequests, long bookingRequestsOk,
                       long sessionsWithSearch, long sessionsWithBooking, Double searchRate,
                       Double bookingRate, Double closeRate) {
        this(label, rangeLabel, rangeFrom, rangeTo, hasData, visits, visitCount, searchRequests,
            searchRequestsOk, bookingRequests, bookingRequestsOk, sessionsWithSearch,
            sessionsWithBooking, searchRate, bookingRate, closeRate, null, null, null, null, null,
            null);
    }

    /**
     * REQ-043 K-3: sessions that searched — {@code ok} plus failed. Without the failure counter
     * only the successful sessions are known. / Sitzungen mit Terminsuche, ok + Fehler.
     */
    public long searchSessions() {
        return sessionsWithSearch + (searchSessionsFailed == null ? 0L : searchSessionsFailed);
    }

    /** REQ-043 K-3: sessions that tried to book — {@code ok} plus failed. / ok + Fehler. */
    public long bookingSessions() {
        return sessionsWithBooking + (bookingSessionsFailed == null ? 0L : bookingSessionsFailed);
    }
}
