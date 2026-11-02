package com.autohaus.backend.usage;

import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Holds the most recent usage snapshot in memory (REQ-037 AC-7) / hält den aktuellen
 * Nutzungs-Snapshot im Speicher.
 *
 * <p>{@code GET /internal/report} and {@code GET /internal/report/usage.json} read this snapshot
 * only — no Log Analytics and no blob call happens in the request path (response time &lt; 200 ms).
 * Aggregation runs exclusively in {@link UsageStatsScheduler}. / Keine Aggregation im Request-Pfad.
 *
 * <p>The store is always present as a bean and <b>never returns {@code null}</b>: until the first
 * successful run {@link #current()} answers with {@link UsageStats#unavailable()}, which renders as
 * „keine Daten" (—) rather than as zeros (AC-12). / Liefert nie {@code null}.
 *
 * <p>{@code liveAsOf} survives a failed live refresh on purpose (E1): the page then shows
 * „Live-Daten derzeit nicht verfügbar — Stand: &lt;letzter erfolgreicher Abruf&gt;" instead of
 * silently displaying stale numbers as current. / Zeitstempel des letzten erfolgreichen Abrufs
 * bleibt erhalten.
 */
@Component
public class UsageStatsStore {

    private volatile UsageStats stats;
    private volatile Instant liveAsOf;
    private volatile boolean rollupStale;

    /**
     * Replaces the snapshot / ersetzt den Snapshot.
     *
     * <p>A {@code null} argument is ignored (the store keeps its last good state, E1). The
     * {@code liveAsOf} timestamp is only advanced when the new snapshot carries one — a refresh
     * that could not reach Log Analytics must not reset it. / Ein fehlender Zeitstempel überschreibt
     * den letzten erfolgreichen nicht.
     *
     * @param stats the new snapshot, may be {@code null} / neuer Snapshot, darf {@code null} sein
     */
    public synchronized void update(UsageStats stats) {
        if (stats == null) {
            return;
        }
        this.stats = stats;
        if (stats.liveAsOf() != null) {
            this.liveAsOf = stats.liveAsOf();
        }
    }

    /**
     * The current snapshot / der aktuelle Snapshot — never {@code null}: the empty state is
     * {@link UsageStats#unavailable()} (AC-11, AC-12). / Nie {@code null}.
     */
    public UsageStats current() {
        UsageStats current = this.stats;
        return current == null ? UsageStats.unavailable() : current;
    }

    /**
     * Timestamp of the last successful Log Analytics refresh / Zeitstempel des letzten
     * erfolgreichen Abrufs, {@code null} when there has not been one yet.
     */
    public Instant liveAsOf() {
        return liveAsOf;
    }

    /** Whether a snapshot has ever been stored / ob je ein Snapshot gespeichert wurde. */
    public boolean hasStats() {
        return stats != null;
    }

    /**
     * Observability flag for a stale rollup (§9) / Observability-Flag für einen veralteten Rollup:
     * {@code true} when the most recent day in the blob is more than three days old. The visible
     * hint on the page is derived from {@link UsageStats#lastRollupDay()}; this flag additionally
     * lets the scheduler log the {@code WARN usage rollup stale} exactly once per state change
     * instead of on every 15-minute cycle. / Verhindert WARN-Spam bei jedem Refresh.
     *
     * @param stale the new state / neuer Zustand
     */
    public synchronized void markRollupStale(boolean stale) {
        this.rollupStale = stale;
    }

    /** Whether the rollup is currently considered stale / ob der Rollup derzeit veraltet ist. */
    public boolean rollupStale() {
        return rollupStale;
    }
}
