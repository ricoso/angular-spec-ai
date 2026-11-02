package com.autohaus.backend.usage.insights;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Deserialized {@code usage-insights.json} (REQ-038 §10.3, §10.6) / deserialisierte
 * Insights-Historie. Read tolerantly (unknown fields ignored, {@code schemaVersion ≤ CURRENT},
 * missing counters {@code null}) and written idempotently with {@code If-Match} (BR-18). The
 * presence of a {@code branchMonths} key means the month is compacted (frozen) — its days are
 * never recomputed (§5.8). / Tolerant gelesen, idempotent geschrieben; verdichtete Monate sind
 * eingefroren.
 *
 * @param schemaVersion file schema, at most {@link #CURRENT_SCHEMA_VERSION}
 * @param updatedAt write timestamp / Schreibzeitpunkt
 * @param collectingSince first counted day of K2 (BR-19), {@code null} until the first rollup
 * @param funnelCollectingSince first counted day of the funnel, {@code null} while never active
 * @param dropoffCollectingSince first counted day of the drop-off scenarios (N1), {@code null}
 *     while never active
 * @param days global day rows / globale Tageszeilen
 * @param branchDays per-branch day rows, kept until the 8th of the following month / Standort-Tage
 * @param branchMonths compacted per-branch months / verdichtete Standort-Monate
 */
public record InsightsHistory(
    int schemaVersion,
    Instant updatedAt,
    LocalDate collectingSince,
    LocalDate funnelCollectingSince,
    LocalDate dropoffCollectingSince,
    SortedMap<LocalDate, InsightsDay> days,
    SortedMap<LocalDate, java.util.Map<String, BranchCounts>> branchDays,
    SortedMap<YearMonth, java.util.Map<String, BranchCounts>> branchMonths
) {
    /** Current blob schema version (BR-18) / aktuelle Schema-Version. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** An empty history — the first-run state (§5.2) / leere Historie für den Erstlauf. */
    public static InsightsHistory empty() {
        return new InsightsHistory(CURRENT_SCHEMA_VERSION, null, null, null, null,
            Collections.unmodifiableSortedMap(new TreeMap<>()),
            Collections.unmodifiableSortedMap(new TreeMap<>()),
            Collections.unmodifiableSortedMap(new TreeMap<>()));
    }
}
