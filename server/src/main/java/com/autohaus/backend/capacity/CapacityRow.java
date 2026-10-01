package com.autohaus.backend.capacity;

import java.time.LocalDate;
import java.util.List;

/**
 * One branch row of S1 (REQ-038 §10.5, N2) / eine Standort-Zeile der Kapazitäts-Sektion. Rows are
 * sorted by classification (mostly-free → mixed → mostly-booked → too-few-days), then
 * {@code freeDays} descending, then the level of the most recent scan, ties by name (BR-26). /
 * Sortierung nach BR-26.
 *
 * @param branchName display name from {@code setup.json} / Anzeigename
 * @param level text level of the most recent shown scan (K-1) / Textstufe
 * @param levelLabel the label, built relative to the day of retrieval (BR-25) / Beschriftung
 * @param workdays workdays to the first free slot, {@code null} unless measurable / Werktage
 * @param reasonKey failure reason key, {@code null} unless {@code NOT_MEASURABLE} / Grund
 * @param freeSlotsTargetDay K-2 total, {@code null} unless {@code CAPACITY_SHOW_SLOT_COUNT} (N2)
 * @param last10 10 cell states aligned to {@code CapacityViewModel.last10ScanDays} (BR-26)
 * @param last10Reasons reason key per {@code NOT_MEASURABLE} cell, else {@code null} (title/
 *     footnote) / Grund je „—"-Zelle
 * @param freeDays count of {@code FREE} cells / freie Tage
 * @param measurableDays count of measurable cells (not-measurable days do <b>not</b> count) /
 *     messbare Tage
 * @param classification the 10-day classification (BR-26) / Einordnung
 * @param firstFreeDate Berlin day of the first free slot in the shown scan ({@code .cap-when},
 *     §11.2), {@code null} unless measured / Tag des ersten freien Termins
 */
public record CapacityRow(
    String branchName,
    CapacityLevel level,
    String levelLabel,
    Integer workdays,
    String reasonKey,
    Integer freeSlotsTargetDay,
    List<CapacityDayState> last10,
    List<String> last10Reasons,
    Integer freeDays,
    Integer measurableDays,
    CapacityTrend classification,
    LocalDate firstFreeDate
) {

    /**
     * Row without the first free date (hand-built views, tests) — the renderer then derives it from
     * the target day and {@code workdays} (Mo–Sa). / Zeile ohne Datum; der Renderer leitet es ab.
     */
    public CapacityRow(String branchName, CapacityLevel level, String levelLabel, Integer workdays,
                       String reasonKey, Integer freeSlotsTargetDay, List<CapacityDayState> last10,
                       List<String> last10Reasons, Integer freeDays, Integer measurableDays,
                       CapacityTrend classification) {
        this(branchName, level, levelLabel, workdays, reasonKey, freeSlotsTargetDay, last10,
            last10Reasons, freeDays, measurableDays, classification, null);
    }
}
