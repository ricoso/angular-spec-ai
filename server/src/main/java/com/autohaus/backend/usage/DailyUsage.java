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
 * @param apiRequests REQ-043: every {@code /api/**} request of the day except the health ping, any
 *     status, with or without session; {@code null} = not recorded (day rows from before REQ-043) /
 *     alle API-Aufrufe; {@code null} = nicht erfasst
 * @param fileRequests REQ-043: every other request the server answered — app code, styles,
 *     images, logos — except {@code /internal/**}; together with {@code apiRequests} the server
 *     load (since K-3 only in {@code usage.json}) / Datei-Abrufe (App, Bilder)
 * @param searchSessionsFailed REQ-043 K-3: sessions that searched but never got a result — every
 *     {@code /api/v1/snaps} call of the session failed; together with {@code sessionsWithSearch}
 *     the „Terminsuchen" tile ({@code null} = nicht erfasst) / Sitzungen, deren jede Suche scheiterte
 * @param bookingSessionsFailed REQ-043 K-3: sessions whose every booking attempt failed — the
 *     customers who got stuck / Sitzungen, deren jeder Buchungsversuch scheiterte
 */
public record DailyUsage(
    DeviceBreakdown visits,
    long searchRequests,
    long searchRequestsOk,
    long bookingRequests,
    long bookingRequestsOk,
    long sessionsWithSearch,
    long sessionsWithBooking,
    Long apiRequests,
    Long fileRequests,
    Long searchSessionsFailed,
    Long bookingSessionsFailed
) {

    /**
     * Pre-REQ-043 shape without the request counters (→ {@code null}, „nicht erfasst") / Form vor
     * REQ-043.
     */
    public DailyUsage(DeviceBreakdown visits, long searchRequests, long searchRequestsOk,
                      long bookingRequests, long bookingRequestsOk, long sessionsWithSearch,
                      long sessionsWithBooking) {
        this(visits, searchRequests, searchRequestsOk, bookingRequests, bookingRequestsOk,
            sessionsWithSearch, sessionsWithBooking, null, null, null, null);
    }

    /** A day without any usage — all counters zero / Tag ohne Nutzung, alle Zähler auf 0. */
    public static DailyUsage empty() {
        return new DailyUsage(new DeviceBreakdown(0L, 0L, 0L, 0L), 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
            0L, 0L);
    }

    /**
     * REQ-043: whether the row carries every counter added after REQ-037 — rows from before are
     * recomputed once while Log Analytics still holds their day (see {@code UsageStatsScheduler}).
     * / ob die Zeile alle nachträglich eingeführten Zähler trägt.
     */
    public boolean hasAllCounters() {
        return apiRequests != null && fileRequests != null
            && searchSessionsFailed != null && bookingSessionsFailed != null;
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
            sessionsWithBooking + other.sessionsWithBooking(),
            plusNullable(apiRequests, other.apiRequests()),
            plusNullable(fileRequests, other.fileRequests()),
            plusNullable(searchSessionsFailed, other.searchSessionsFailed()),
            plusNullable(bookingSessionsFailed, other.bookingSessionsFailed()));
    }

    /** {@code null} + {@code null} stays „nicht erfasst"; otherwise the recorded values add up. */
    private static Long plusNullable(Long a, Long b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : a + b;
    }
}
