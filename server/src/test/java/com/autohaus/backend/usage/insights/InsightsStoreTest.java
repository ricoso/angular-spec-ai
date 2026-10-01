package com.autohaus.backend.usage.insights;

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
 * The in-memory insights store (REQ-038 §14.1, BR-5) / der Insights-Speicher: view and history are
 * published together and read together through {@link InsightsStore#snapshot()}; the older
 * accessors keep their contract. / Sicht und Historie werden gemeinsam veröffentlicht und gelesen.
 */
class InsightsStoreTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    @Test
    @DisplayName("§14.1: before the first build the snapshot has no view and the empty history")
    void startsEmpty() {
        InsightsStore store = new InsightsStore();

        InsightsStore.Snapshot snapshot = store.snapshot();
        assertNull(snapshot.viewModel());
        assertNotNull(snapshot.history());
        assertTrue(snapshot.history().days().isEmpty());
        assertNull(store.current());
        assertTrue(store.history().days().isEmpty());
        assertFalse(store.hasViewModel());
    }

    @Test
    @DisplayName("§14.1: update publishes view and history as one pair; null keeps the previous value")
    void publishesViewAndHistoryTogether() {
        InsightsStore store = new InsightsStore();
        InsightsHistory first = InsightsHistory.empty();
        InsightsViewModel firstView = view();

        store.update(firstView, first);
        InsightsStore.Snapshot published = store.snapshot();
        assertSame(firstView, published.viewModel());
        assertSame(first, published.history());
        assertSame(firstView, store.current());
        assertSame(first, store.history());
        assertTrue(store.hasViewModel());

        InsightsViewModel rebuilt = view();
        store.update(rebuilt, null);
        assertSame(rebuilt, store.snapshot().viewModel());
        assertSame(first, store.snapshot().history(), "a null history keeps the last loaded one");

        InsightsHistory second = InsightsHistory.empty();
        store.update(null, second);
        assertSame(rebuilt, store.snapshot().viewModel(), "a null view keeps the previous one");
        assertSame(second, store.snapshot().history());

        assertSame(firstView, published.viewModel(), "an earlier snapshot never changes");
        assertSame(first, published.history());
    }

    @Test
    @DisplayName("§14.1: a snapshot never pairs the view of one publication with the history of another")
    void readersNeverSeeAMixedPair() throws Exception {
        InsightsStore store = new InsightsStore();
        InsightsViewModel viewA = view();
        InsightsViewModel viewB = view();
        InsightsHistory historyA = InsightsHistory.empty();
        InsightsHistory historyB = InsightsHistory.empty();
        store.update(viewA, historyA);

        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicBoolean mixed = new AtomicBoolean(false);
        CountDownLatch readersStarted = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Runnable reader = () -> {
                readersStarted.countDown();
                while (!stop.get()) {
                    InsightsStore.Snapshot snapshot = store.snapshot();
                    boolean pairA = snapshot.viewModel() == viewA && snapshot.history() == historyA;
                    boolean pairB = snapshot.viewModel() == viewB && snapshot.history() == historyB;
                    if (!pairA && !pairB) {
                        mixed.set(true);
                    }
                }
            };
            Future<?> first = pool.submit(reader);
            Future<?> second = pool.submit(reader);
            assertTrue(readersStarted.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 20_000; i++) {
                if (i % 2 == 0) {
                    store.update(viewB, historyB);
                } else {
                    store.update(viewA, historyA);
                }
            }
            stop.set(true);
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertFalse(mixed.get(), "view and history always belong to the same publication");
    }

    private static InsightsViewModel view() {
        return InsightsViewModel.build(InsightsHistory.empty(), Map.of(), TODAY, true, false, false);
    }
}
