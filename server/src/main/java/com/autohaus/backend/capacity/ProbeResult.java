package com.autohaus.backend.capacity;

import java.time.LocalDate;

/**
 * Result of one branch probe (REQ-038 §10.4) / Ergebnis einer Standort-Messung. Persisted per
 * branch inside {@code scans[D].branches} — no {@code checkInResource}, no operation names, no
 * customer data (BR-13). / Ohne Personen- und Beraterdaten.
 *
 * @param status probe outcome / Mess-Ergebnis
 * @param reason failure reason, {@code null} unless {@code status == ERROR}
 * @param firstFreeDate earliest free slot day (Europe/Berlin), {@code null} unless {@code OK}
 * @param workdaysToFirstFree Mo–Sa days d with targetDay ≤ d &lt; firstFreeDate; 0 = free on the
 *     target day / Werktage bis zum ersten freien Termin
 * @param freeSlotsTargetDay total distinct {@code checkInTime} on the target day (N2: no daypart
 *     split), {@code null} unless {@code OK} / Gesamtzahl freier Termine am Zieltag
 */
public record ProbeResult(
    ProbeStatus status,
    ProbeReason reason,
    LocalDate firstFreeDate,
    Integer workdaysToFirstFree,
    Integer freeSlotsTargetDay
) {}
