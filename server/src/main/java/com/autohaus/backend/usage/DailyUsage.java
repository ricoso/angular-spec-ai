package com.autohaus.backend.usage;

/**
 * One calendar day's counters (Europe/Berlin) / Kennzahlen eines Kalendertages. Persisted unit in
 * the blob — contains integer counters only, no session ids, no IP addresses, no user agents, no
 * individual timestamps (REQ-037 AC-14, NFR-Datenschutz-4). / Persistierte Einheit im Blob.
 *
 * <p><b>No {@code day} component on purpose / bewusst KEIN Feld „day":</b> the calendar day is
 * exclusively the map key in {@link UsageHistory#days()} (§10.3). An additional record component
 * would either come back {@code null} from the Jackson round-trip or write a {@code "day"} field
 * the schema does not provide for. In Java the day is carried as
 * {@code Map.Entry<LocalDate, DailyUsage>} resp. {@code SortedMap<LocalDate, DailyUsage>} after
 * parsing — summation, gap detection and the retention purge all work on the key.
 *
 * @param visits distinct session ids per device class / eindeutige Session-IDs je Geräteklasse
 * @param searchRequests POST /api/v1/snaps, total / gesamt
 * @param searchRequestsOk thereof {@code statusCode < 400} / davon erfolgreich
 * @param bookingRequests POST /api/v1/appointments, total / gesamt
 * @param bookingRequestsOk thereof {@code statusCode < 400} / davon erfolgreich
 * @param sessionsWithSearch sessions with at least one successful search / Sitzungen mit Suche
 * @param sessionsWithBooking sessions with at least one successful booking / Sitzungen mit Buchung
 */
public record DailyUsage(
    DeviceBreakdown visits,
    long searchRequests,
    long searchRequestsOk,
    long bookingRequests,
    long bookingRequestsOk,
    long sessionsWithSearch,
    long sessionsWithBooking
) {

    /** A day without any usage — all counters zero / Tag ohne Nutzung, alle Zähler auf 0. */
    public static DailyUsage empty() {
        return new DailyUsage(new DeviceBreakdown(0L, 0L, 0L, 0L), 0L, 0L, 0L, 0L, 0L, 0L);
    }

    /**
     * Field-wise sum of two days / feldweise Summe zweier Tage — the basis for month, year and the
     * monthly series (BR-14). Note that session-based counters are summed across days by design: a
     * session that spans midnight counts on both days (BR-4). / Sitzungen über Mitternacht zählen
     * an beiden Tagen.
     *
     * @param other the day to add, may be {@code null} / zu addierender Tag, darf {@code null} sein
     */
    public DailyUsage plus(DailyUsage other) {
        if (other == null) {
            return this;
        }
        return new DailyUsage(
            visits.plus(other.visits()),
            searchRequests + other.searchRequests(),
            searchRequestsOk + other.searchRequestsOk(),
            bookingRequests + other.bookingRequests(),
            bookingRequestsOk + other.bookingRequestsOk(),
            sessionsWithSearch + other.sessionsWithSearch(),
            sessionsWithBooking + other.sessionsWithBooking());
    }
}
