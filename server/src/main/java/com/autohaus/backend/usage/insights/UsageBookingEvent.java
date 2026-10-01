package com.autohaus.backend.usage.insights;

import java.util.List;

/**
 * One booking as an anonymous event — whitelist values only (REQ-038 §10.2, BR-6) / eine Buchung
 * als anonymes Ereignis, ausschließlich Whitelist-Werte.
 *
 * <p>Deliberately absent: appointment id, snap id, slot date, any time of day, brand, model,
 * device, {@code sessionId}, {@code checkInResource}, customer data, free text (BR-24). The only
 * date is the Berlin calendar {@code day} of the booking — the day the line is counted for; it was
 * implicit in the log timestamp before and is coarser than it. / Bewusst ohne jede identifizierende
 * Dimension; das einzige Datum ist der Buchungstag ohne Uhrzeit.
 *
 * <p>{@code UsageEventLogger} only <b>buffers</b> the event after a successful SoftNet booking
 * (and only while {@code usage.insights.enabled}, AC-13); the line {@code USAGE_BOOKING {json}} is
 * written by the daily, shuffled flush under MDC {@code traceId=job.usagebooking}, so it can no
 * longer be linked to the booking request (trace id, millisecond, thread). / Das Ereignis wird nur
 * gepuffert und täglich gemischt geschrieben — ohne Bezug zum Buchungs-Request.
 *
 * <p><b>Format version / Formatversion:</b> {@value #FORMAT_VERSION} since the batch flush — the
 * meaning of the line changed: its timestamp no longer marks the booking, {@code day} is the
 * mandatory assignment key. Readers dispatch on {@code v}: {@value #LEGACY_FORMAT_VERSION} = no
 * {@code day}, assigned by {@code TimeGenerated}; {@value #FORMAT_VERSION} = assigned by
 * {@code day} only. / Seit dem Tagesflush Version 2: Zuordnung über {@code day} statt über den
 * Zeitstempel; Version 1 wird weiter über {@code TimeGenerated} gelesen.
 *
 * @param v format version, always {@link #FORMAT_VERSION} when written / Formatversion
 * @param day Berlin calendar day of the booking, ISO {@code yyyy-MM-dd}, no time / Buchungstag
 * @param branch branchId from {@code setup.json}, 24 hex / Standort
 * @param group resolved groupId, 24 hex / aufgelöste Group
 * @param variant variant of the resolved group: {@code default} or {@code tireChange} (§5.5)
 * @param ops 0–{@value #MAX_OPS} operation ids, 24 hex each; invalid entries dropped / Operationen
 * @param bookingDaypart daypart of the booking moment / Tagesabschnitt der Buchung
 * @param slotWeekday weekday of the booked slot / Wochentag des Termins
 * @param slotDaypart daypart of the booked slot / Tagesabschnitt des Termins
 * @param leadTime lead-time bucket / Vorlauf-Bucket
 * @param mobility whether a rental car was booked ({@code rentCategoryid} set, REQ-036) / Mietwagen
 */
public record UsageBookingEvent(
    int v,
    String day,
    String branch,
    String group,
    String variant,
    List<String> ops,
    BookingDaypart bookingDaypart,
    SlotWeekday slotWeekday,
    SlotDaypart slotDaypart,
    LeadTimeBucket leadTime,
    boolean mobility
) {
    /** Format version of the written line (§10.2) / Formatversion der geschriebenen Zeile. */
    public static final int FORMAT_VERSION = 2;

    /**
     * Version of lines written synchronously in the booking request, without {@code day} — still
     * readable, assigned by {@code TimeGenerated} / Version der früheren Request-Zeilen ohne
     * {@code day}; weiterhin lesbar.
     */
    public static final int LEGACY_FORMAT_VERSION = 1;

    /** Upper bound of logged operation ids (§16) / Obergrenze der Operation-IDs. */
    public static final int MAX_OPS = 20;
}
