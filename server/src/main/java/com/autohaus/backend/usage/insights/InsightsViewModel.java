package com.autohaus.backend.usage.insights;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The suppressed insights view (REQ-038 §10.5) — the <b>single</b> place BR-9 is applied; renderers
 * and JSON only ever see {@link Cell} objects and must never touch {@link InsightsHistory} (§19).
 * / Die unterdrückte Insights-Sicht; BR-9 wird ausschließlich hier angewandt.
 *
 * <p>Built by the scheduler on startup, after each own run and daily at 00:05 — never in the
 * request path (BR-5). / Wird nie im Request-Pfad gebaut.
 *
 * @param asOf „yesterday" the model was built for / der Stichtag „gestern"
 * @param bookingsEnabled {@code usage.insights.enabled}; {@code false} → ranking..matrix are null
 * @param funnelEnabled {@code usage.enabled && usage.funnel.enabled}; {@code false} → funnel null
 * @param collectingSince first counted day of K2 (BR-19)
 * @param funnelCollectingSince first counted day of the funnel
 * @param closedMonth the previous month, {@code null} before the first closed month
 * @param closedMonthPartialSince {@code collectingSince} when it lies after the 1st of
 *     {@code closedMonth}, else {@code null} (BR-19)
 * @param closedMonthIncomplete whether days are missing in a compacted month (§5.8)
 * @param missingDays how many days are missing there / fehlende Tage
 * @param ranking S2 rows with ≥ 5 bookings, rank descending / Rangliste
 * @param belowThreshold S2 group „Unter 5 Buchungen", sorted by name (Collator DE) / unter 5
 * @param month global metrics „aktueller Monat bis gestern" / Monat
 * @param year global metrics „laufendes Jahr bis gestern" / Jahr
 * @param matrix S3 G-2/G-3 for the closed month / Matrix
 * @param closedMonthOffHoursShare global T-2 share of the closed month (I-4, BR-21) — n ≥ 20,
 *     never with numerator or complement 1–4 (BR-9 Nr. 5) / globaler T-2-Anteil des Vormonats
 * @param funnel S6, {@code null} while switched off / Trichter
 * @param warnings visible {@code .st-warn} texts (stale, pending day, …) / Warnhinweise
 */
public record InsightsViewModel(
    LocalDate asOf,
    boolean bookingsEnabled,
    boolean funnelEnabled,
    LocalDate collectingSince,
    LocalDate funnelCollectingSince,
    YearMonth closedMonth,
    LocalDate closedMonthPartialSince,
    boolean closedMonthIncomplete,
    int missingDays,
    List<BranchRankingRow> ranking,
    List<BranchRankingRow> belowThreshold,
    Period month,
    Period year,
    GroupMatrix matrix,
    Cell closedMonthOffHoursShare,
    FunnelView funnel,
    List<String> warnings
) {
    /**
     * Warning shown while no history could ever be loaded (E4, AC-32, §17
     * {@code WARN_HISTORY_UNAVAILABLE}) — appended by the scheduler via
     * {@code withAdditionalWarnings(...)}; renderers show every warning as {@code .st-warn}. /
     * Hinweis, solange nie ein Stand geladen wurde.
     */
    public static final String WARN_HISTORY_UNAVAILABLE = "Historie derzeit nicht verfügbar";

    /** Cell values 1–4 render as „&lt; 5" (BR-9 Nr. 1) / Schwelle der Primärunterdrückung. */
    public static final int K_MIN = 5;

    /** Shares appear from this denominator on (BR-9 Nr. 2) / Mindest-Nenner für Anteile. */
    public static final int N_SHARE_MIN = 20;

    /** Own matrix row from this booking count on (BR-9 Nr. 3) / Mindestbuchungen je Matrixzeile. */
    public static final int MIN_BOOKINGS_MATRIX = 20;

    /** Branches with bookings &gt; 0 the collective row needs (BR-9 Nr. 3) / Mindest-Beiträger. */
    public static final int MIN_BRANCHES_OTHER_ROW = 3;

    /** Sessions a device class needs for an own funnel column (BR-9 Nr. 7) / Mindest-Sitzungen. */
    public static final int MIN_SESSIONS_DEVICE = 20;

    /** Drop-offs a device class needs for an own drop-off column (BR-31) / Mindest-Abbrüche. */
    public static final int MIN_DROPOFFS_DEVICE = 20;

    /**
     * Builds the suppressed view (BR-9, BR-10, BR-19) / baut die unterdrückte Sicht. Thin delegate
     * — the logic lives in {@link InsightsViewModelBuilder} to keep this record a pure data type.
     *
     * @param history the loaded blob history / geladene Historie
     * @param branchNames display names keyed by branchId (from {@code setup.json}) / Anzeigenamen
     * @param today the Berlin calendar day of the build / Berliner Kalendertag des Aufbaus
     * @param bookingsEnabled {@code usage.insights.enabled}
     * @param funnelEnabled {@code usage.enabled && usage.funnel.enabled}
     * @param dropoffEnabled {@code usage.dropoff.enabled} (only effective with the funnel, N1)
     */
    public static InsightsViewModel build(InsightsHistory history, Map<String, String> branchNames,
                                          LocalDate today, boolean bookingsEnabled,
                                          boolean funnelEnabled, boolean dropoffEnabled) {
        return InsightsViewModelBuilder.build(history, branchNames, today,
            bookingsEnabled, funnelEnabled, dropoffEnabled);
    }

    /**
     * Copy with additional {@code .st-warn} texts appended (e.g. {@code WARN_HISTORY_UNAVAILABLE}
     * from the scheduler, E4) / Kopie mit zusätzlichen Warnhinweisen. Duplicates are skipped. /
     * Doppelte Texte werden übersprungen.
     */
    public InsightsViewModel withAdditionalWarnings(List<String> additional) {
        if (additional == null || additional.isEmpty()) {
            return this;
        }
        List<String> merged = new ArrayList<>(warnings == null ? List.of() : warnings);
        for (String warning : additional) {
            if (warning != null && !merged.contains(warning)) {
                merged.add(warning);
            }
        }
        return new InsightsViewModel(asOf, bookingsEnabled, funnelEnabled, collectingSince,
            funnelCollectingSince, closedMonth, closedMonthPartialSince, closedMonthIncomplete,
            missingDays, ranking, belowThreshold, month, year, matrix, closedMonthOffHoursShare, funnel,
            List.copyOf(merged));
    }
}
