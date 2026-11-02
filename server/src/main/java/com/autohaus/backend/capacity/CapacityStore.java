package com.autohaus.backend.capacity;

import org.springframework.stereotype.Component;

/**
 * Holds the precomputed capacity view in memory (REQ-038 BR-5, §14.1) / hält die vorberechnete
 * Kapazitäts-Sicht im Speicher. Always present as a bean; filled only by
 * {@code CapacityScanScheduler} on startup and after each scan — without the switch it stays empty
 * (and S1 is not rendered at all, BR-4). In the {@code e2e-seed} profile the seed loader fills it.
 * / Ohne Schalter bleibt er leer.
 *
 * <p><b>Atomic reads (§14.1):</b> view and history live together in <b>one</b> immutable
 * {@link Snapshot} behind a single {@code volatile} reference, so {@link #snapshot()} never pairs a
 * new view with an old history. / Sicht und Historie in einem Holder — konsistent gelesen.
 */
@Component
public class CapacityStore {

    private volatile Snapshot state = Snapshot.EMPTY;

    /**
     * View and history as one consistent pair / Sicht und Historie als konsistentes Paar.
     *
     * @return never {@code null}; before the first build {@code viewModel} is {@code null} and
     *     {@code history} is {@link CapacityHistory#empty()}
     */
    public Snapshot snapshot() {
        return state;
    }

    /** The current view, {@code null} until the first build / {@code null} bis zum ersten Aufbau. */
    public CapacityViewModel current() {
        return state.viewModel();
    }

    /** The last loaded history, {@link CapacityHistory#empty()} until the first load. */
    public CapacityHistory history() {
        return state.history();
    }

    /** Whether a view was ever stored / ob je eine Sicht gespeichert wurde. */
    public boolean hasViewModel() {
        return state.viewModel() != null;
    }

    /** Replaces view and history in one step; {@code null} arguments keep the previous value. */
    public synchronized void update(CapacityViewModel viewModel, CapacityHistory history) {
        Snapshot previous = state;
        state = new Snapshot(viewModel != null ? viewModel : previous.viewModel(),
            history != null ? history : previous.history());
    }

    /**
     * One published state / ein veröffentlichter Stand.
     *
     * @param viewModel the view, {@code null} until the first build / Sicht
     * @param history the history the view was built from, never {@code null} / Historie
     */
    public record Snapshot(CapacityViewModel viewModel, CapacityHistory history) {

        /** Nothing published yet / noch nichts veröffentlicht. */
        static final Snapshot EMPTY = new Snapshot(null, null);

        /** A missing history is the empty one / fehlende Historie = leere Historie. */
        public Snapshot {
            history = history == null ? CapacityHistory.empty() : history;
        }
    }
}
