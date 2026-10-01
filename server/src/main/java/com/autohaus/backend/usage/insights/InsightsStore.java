package com.autohaus.backend.usage.insights;

import org.springframework.stereotype.Component;

/**
 * Holds the precomputed insights view in memory (REQ-038 BR-5, §14.1) / hält die vorberechnete
 * Insights-Sicht im Speicher. Always present as a bean (independent of the switches); the request
 * path reads only this {@code volatile} snapshot — no KQL, no blob, no SoftNet call (AC-7). /
 * Immer vorhanden; der Request-Pfad liest nur diesen Snapshot.
 *
 * <p>Filled exclusively by {@code InsightsScheduler} (startup, own run, 00:05) and — in the
 * {@code e2e-seed} profile only — by {@code StatisticsE2eSeedLoader}. / Nur Scheduler bzw.
 * Seed-Loader befüllen.
 *
 * <p><b>Atomic reads (§14.1):</b> view and history live together in <b>one</b> immutable
 * {@link Snapshot} behind a single {@code volatile} reference. {@link #snapshot()} therefore always
 * returns a view together with the history it was published with — a reader can never combine a
 * new view with an old history (or vice versa) while the scheduler publishes. / Sicht und Historie
 * in einem Holder — konsistent gelesen.
 */
@Component
public class InsightsStore {

    private volatile Snapshot state = Snapshot.EMPTY;

    /**
     * View and history as one consistent pair / Sicht und Historie als konsistentes Paar.
     *
     * @return never {@code null}; before the first build {@code viewModel} is {@code null} and
     *     {@code history} is {@link InsightsHistory#empty()}
     */
    public Snapshot snapshot() {
        return state;
    }

    /**
     * The current view, {@code null} until the first build / aktuelle Sicht, {@code null} bis zum
     * ersten Aufbau. The controller then renders „Historie derzeit nicht verfügbar" for the active
     * sources (E4, AC-32) — never zeros. / Vor dem ersten Aufbau nie Nullen.
     */
    public InsightsViewModel current() {
        return state.viewModel();
    }

    /** The last loaded history, {@link InsightsHistory#empty()} until the first load. */
    public InsightsHistory history() {
        return state.history();
    }

    /** Whether a view was ever stored / ob je eine Sicht gespeichert wurde. */
    public boolean hasViewModel() {
        return state.viewModel() != null;
    }

    /**
     * Replaces view and history in one step / ersetzt Sicht und Historie in einem Schritt. A
     * {@code null} argument keeps the previous value (a {@code null} history keeps the last loaded
     * one — the view may carry a warning instead). / {@code null} behält den bisherigen Wert.
     */
    public synchronized void update(InsightsViewModel viewModel, InsightsHistory history) {
        Snapshot previous = state;
        state = new Snapshot(viewModel != null ? viewModel : previous.viewModel(),
            history != null ? history : previous.history());
    }

    /**
     * One published state / ein veröffentlichter Stand.
     *
     * @param viewModel the suppressed view, {@code null} until the first build / Sicht
     * @param history the history the view was built from, never {@code null} / Historie
     */
    public record Snapshot(InsightsViewModel viewModel, InsightsHistory history) {

        /** Nothing published yet / noch nichts veröffentlicht. */
        static final Snapshot EMPTY = new Snapshot(null, null);

        /** A missing history is the empty one / fehlende Historie = leere Historie. */
        public Snapshot {
            history = history == null ? InsightsHistory.empty() : history;
        }
    }
}
