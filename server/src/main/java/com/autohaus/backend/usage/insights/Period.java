package com.autohaus.backend.usage.insights;

import java.util.Map;

/**
 * Global metrics of one display period (REQ-038 §10.5) — „aktueller Monat bis gestern" or
 * „laufendes Jahr bis gestern" (BR-10) / globale Kennzahlen eines Anzeigezeitraums.
 *
 * @param label period label (§17 {@code PERIOD_MONTH}/{@code PERIOD_YEAR}) / Beschriftung
 * @param rangeLabel formatted date range / formatierter Zeitraum
 * @param bookings bookings of the period / Buchungen
 * @param groups G-1 mentions per {@link ServiceGroup} key / Servicegruppen
 * @param bookedWhen T-1: weekday {@code MO..SU} → daypart {@code day|evening|night} (heat cells)
 * @param slotGrid T-3: weekday {@code MO..SU} → slot daypart {@code early..late} (heat cells)
 * @param leadTime T-5 per bucket / Vorlauf
 * @param offHoursShare T-2 / außerhalb der Geschäftszeit
 * @param slotUnknown bookings without a slot time (E12), {@code null} = none to report
 * @param bookedWhenLegend runtime legend of T-1 / Laufzeit-Legende
 * @param slotLegend runtime legend of T-3 / Laufzeit-Legende
 */
public record Period(
    String label,
    String rangeLabel,
    Cell bookings,
    Map<String, Cell> groups,
    Map<String, Map<String, Cell>> bookedWhen,
    Map<String, Map<String, Cell>> slotGrid,
    Map<String, Cell> leadTime,
    Cell offHoursShare,
    Cell slotUnknown,
    HeatLegend bookedWhenLegend,
    HeatLegend slotLegend
) {}
