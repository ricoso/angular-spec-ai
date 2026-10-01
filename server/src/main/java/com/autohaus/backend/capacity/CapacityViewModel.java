package com.autohaus.backend.capacity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The precomputed capacity view of S1 (REQ-038 §10.5, N2) / die vorberechnete Kapazitäts-Sicht.
 * Built by the scheduler on startup, after each scan and daily at 00:05 (label relative to the day
 * of retrieval, BR-25) — never in the request path (BR-5). Capacity values are operational data
 * without personal reference; the „&lt; 5" rule does not apply here (BR-26). / Betriebsdaten ohne
 * Personenbezug; nie im Request-Pfad gebaut.
 *
 * @param scanDay day of the shown scan / Tag des angezeigten Scans
 * @param targetDay its target day / dessen Zieltag
 * @param state state of the shown scan / Zustand
 * @param showingFallbackScan latest scan failed → the last {@code complete|partial} scan is shown
 *     (BR-14, AC-10) / Fallback-Anzeige
 * @param fallbackState state of the shown fallback scan, {@code null} otherwise
 * @param runningSince own replica's scan in progress (after the claim), else {@code null} (§5.2)
 * @param collectingSince first scan day (BR-19) / Erfassungsbeginn
 * @param last10ScanDays column heads of the 10-day grid, oldest first (N2) / Spaltenköpfe
 * @param rows branch rows sorted per BR-26 / Standort-Zeilen
 * @param warnings visible {@code .st-warn} texts / Warnhinweise
 * @param scanStartedAt start of the shown scan — „{from}" of §17 {@code CAPACITY_RANGE} and the
 *     time of {@code META_STATISTICS}; {@code null} without a shown scan / Start des gezeigten Scans
 * @param scanFinishedAt end of the shown scan — „{to}" of {@code CAPACITY_RANGE}; {@code null}
 *     without one / Ende des gezeigten Scans
 * @param latestScanDay day of the most recent final scan ({@code complete|partial|failed}); differs
 *     from {@code scanDay} only while a failed scan is bridged by the fallback — the first date of
 *     §17 {@code META_SCAN_FAILED} / Tag des jüngsten (ggf. fehlgeschlagenen) Scans
 */
public record CapacityViewModel(
    LocalDate scanDay,
    LocalDate targetDay,
    ScanState state,
    boolean showingFallbackScan,
    ScanState fallbackState,
    Instant runningSince,
    LocalDate collectingSince,
    List<LocalDate> last10ScanDays,
    List<CapacityRow> rows,
    List<String> warnings,
    Instant scanStartedAt,
    Instant scanFinishedAt,
    LocalDate latestScanDay
) {

    /**
     * View without scan times and latest scan day (hand-built views, tests) / Sicht ohne
     * Scan-Zeiten: the renderers then leave the clock times out. / Die Renderer lassen die Uhrzeiten
     * dann weg.
     */
    public CapacityViewModel(LocalDate scanDay, LocalDate targetDay, ScanState state,
                             boolean showingFallbackScan, ScanState fallbackState, Instant runningSince,
                             LocalDate collectingSince, List<LocalDate> last10ScanDays,
                             List<CapacityRow> rows, List<String> warnings) {
        this(scanDay, targetDay, state, showingFallbackScan, fallbackState, runningSince, collectingSince,
            last10ScanDays, rows, warnings, null, null, null);
    }

    /**
     * Warning shown while no history could ever be loaded (E4, AC-32, §17
     * {@code WARN_HISTORY_UNAVAILABLE}) — appended by the scheduler via
     * {@code withAdditionalWarnings(...)}; renderers show every warning as {@code .st-warn}. /
     * Hinweis, solange nie ein Stand geladen wurde.
     */
    public static final String WARN_HISTORY_UNAVAILABLE = "Historie derzeit nicht verfügbar";

    /** Scan days shown in the 10-day grid (BR-26, N2) / Scan-Tage im Raster. */
    public static final int LAST_SCAN_DAYS = 10;

    /** Measurable days a classification needs (BR-26) / messbare Tage für eine Einordnung. */
    public static final int MIN_MEASURABLE_DAYS = 5;

    /** Free-day ratio from which a branch is „meist frei" (BR-26) / Schwelle „meist frei". */
    public static final double MOSTLY_FREE_MIN = 0.7;

    /** Free-day ratio up to which a branch is „meist belegt" (BR-26) / Schwelle „meist belegt". */
    public static final double MOSTLY_BOOKED_MAX = 0.2;

    /**
     * Builds the suppression-free but precomputed view (BR-25, BR-26) / baut die vorberechnete
     * Sicht. Thin delegate — the logic lives in {@link CapacityViewModelBuilder}.
     *
     * @param history the loaded blob history / geladene Historie
     * @param branchNames display names keyed by branchId / Anzeigenamen
     * @param showSlotCount {@code CAPACITY_SHOW_SLOT_COUNT} (K-2)
     * @param today the Berlin calendar day of the build (BR-25) / Kalendertag des Aufbaus
     * @param runningSince own replica's running scan, else {@code null}
     */
    public static CapacityViewModel build(CapacityHistory history, Map<String, String> branchNames,
                                          boolean showSlotCount, LocalDate today,
                                          Instant runningSince) {
        return CapacityViewModelBuilder.build(history, branchNames, showSlotCount, today,
            runningSince);
    }

    /**
     * Copy with additional {@code .st-warn} texts appended (e.g. {@code WARN_HISTORY_UNAVAILABLE}
     * from the scheduler, E4) / Kopie mit zusätzlichen Warnhinweisen. Duplicates are skipped.
     */
    public CapacityViewModel withAdditionalWarnings(List<String> additional) {
        if (additional == null || additional.isEmpty()) {
            return this;
        }
        List<String> merged = new ArrayList<>(warnings == null ? List.of() : warnings);
        for (String warning : additional) {
            if (warning != null && !merged.contains(warning)) {
                merged.add(warning);
            }
        }
        return new CapacityViewModel(scanDay, targetDay, state, showingFallbackScan, fallbackState,
            runningSince, collectingSince, last10ScanDays, rows, List.copyOf(merged), scanStartedAt,
            scanFinishedAt, latestScanDay);
    }
}
