package com.autohaus.backend.capacity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The in-memory capacity store (REQ-038 §14.1, BR-5) / der Kapazitäts-Speicher: view and history
 * are published together and read together through {@link CapacityStore#snapshot()}. / Sicht und
 * Historie werden gemeinsam veröffentlicht und gelesen.
 */
class CapacityStoreTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    @Test
    @DisplayName("§14.1: before the first build the snapshot has no view and the empty history")
    void startsEmpty() {
        CapacityStore store = new CapacityStore();

        assertNull(store.snapshot().viewModel());
        assertTrue(store.snapshot().history().scans().isEmpty());
        assertNull(store.current());
        assertTrue(store.history().scans().isEmpty());
        assertFalse(store.hasViewModel());
    }

    @Test
    @DisplayName("§14.1: update publishes view and history as one pair; null keeps the previous value")
    void publishesViewAndHistoryTogether() {
        CapacityStore store = new CapacityStore();
        CapacityHistory first = CapacityHistory.empty();
        CapacityViewModel firstView = view();

        store.update(firstView, first);
        CapacityStore.Snapshot published = store.snapshot();
        assertSame(firstView, published.viewModel());
        assertSame(first, published.history());
        assertTrue(store.hasViewModel());

        CapacityViewModel relabelled = view();
        store.update(relabelled, null);
        assertSame(relabelled, store.current());
        assertSame(first, store.history(), "a null history keeps the last loaded one");

        CapacityHistory second = CapacityHistory.empty();
        store.update(null, second);
        assertSame(relabelled, store.snapshot().viewModel(), "a null view keeps the previous one");
        assertSame(second, store.snapshot().history());
        assertSame(firstView, published.viewModel(), "an earlier snapshot never changes");
    }

    @Test
    @DisplayName("§14.1: a snapshot never pairs the view of one publication with the history of another")
    void readersNeverSeeAMixedPair() throws Exception {
        CapacityStore store = new CapacityStore();
        CapacityViewModel viewA = view();
        CapacityViewModel viewB = view();
        CapacityHistory historyA = CapacityHistory.empty();
        CapacityHistory historyB = CapacityHistory.empty();
        store.update(viewA, historyA);

        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicBoolean mixed = new AtomicBoolean(false);
        CountDownLatch readerStarted = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> reader = pool.submit(() -> {
                readerStarted.countDown();
                while (!stop.get()) {
                    CapacityStore.Snapshot snapshot = store.snapshot();
                    boolean pairA = snapshot.viewModel() == viewA && snapshot.history() == historyA;
                    boolean pairB = snapshot.viewModel() == viewB && snapshot.history() == historyB;
                    if (!pairA && !pairB) {
                        mixed.set(true);
                    }
                }
            });
            assertTrue(readerStarted.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 20_000; i++) {
                store.update(i % 2 == 0 ? viewB : viewA, i % 2 == 0 ? historyB : historyA);
            }
            stop.set(true);
            reader.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertFalse(mixed.get(), "view and history always belong to the same publication");
    }

    private static CapacityViewModel view() {
        return CapacityViewModel.build(CapacityHistory.empty(), Map.of(), false, TODAY, null);
    }
}
