package com.autohaus.backend.usage.insights;

/**
 * One row of the S2 branch ranking (REQ-038 §10.5, R-1..R-4, T-2, T-4) / eine Zeile der
 * Standort-Rangliste. Rows with 0–4 bookings carry {@code rank == null} and live in
 * {@code belowThreshold} („Unter 5 Buchungen", AC-15). / Zeilen unter 5 Buchungen sind ohne Rang.
 *
 * @param branchName display name from {@code setup.json} / Anzeigename
 * @param rank 1-based rank, {@code null} in the below-threshold group
 * @param rankMove {@code up} | {@code down} | {@code same} | {@code new}, {@code null} without a
 *     previous month / Rangänderung. The JSON carries these keys; the {@code "="} in the §12.5
 *     example is the rendered symbol of {@code same} (§17 {@code RANK_SAME}). / Im JSON stehen die
 *     Schlüssel, {@code "="} ist nur das angezeigte Symbol.
 * @param previousRank rank in the month before the previous month, {@code null} when new
 * @param bookings R-1 / Online-Buchungen
 * @param share R-2 / Anteil am Online-Volumen
 * @param bookingsYear year up to the end of the previous month / Jahr bis Vormonat
 * @param serviceViews R-3 / Leistungsaufrufe
 * @param bookingsPer100Views R-4 (Näherung) / Buchungen je 100 Aufrufe
 * @param offHoursShare T-2 per branch / außerhalb Mo–Fr 07–18 Uhr
 * @param lateShare T-4 per branch / späte Termine ab 16 Uhr
 * @param barWidth bar width 0–100; {@code 0.0} unless {@code bookings.state == VALUE} (BR-9 Nr. 5)
 */
public record BranchRankingRow(
    String branchName,
    Integer rank,
    String rankMove,
    Integer previousRank,
    Cell bookings,
    Cell share,
    Cell bookingsYear,
    Cell serviceViews,
    Cell bookingsPer100Views,
    Cell offHoursShare,
    Cell lateShare,
    double barWidth
) {}
