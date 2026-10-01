package com.autohaus.backend.common.util;

import java.time.Duration;

/**
 * Injectable wait of the REQ-038 background jobs (jitter, pause, backoff, retry delay) so tests never
 * really wait / austauschbares Warten der REQ-038-Hintergrundjobs, damit Tests nie wirklich warten.
 *
 * <p>Replaces the two identical nested interfaces {@code ServiceGroupClassifier.Sleeper} and
 * {@code CapacityScanScheduler.Sleeper}. Tests pass a lambda or a method reference such as
 * {@code sleeps::add}. / Ersetzt die zwei gleichen verschachtelten Interfaces.
 */
@FunctionalInterface
public interface Sleeper {

    /**
     * Production implementation: {@link Thread#sleep(long)}, a negative duration does not wait /
     * Produktions-Implementierung, negative Dauer wartet nicht.
     */
    Sleeper THREAD = duration -> Thread.sleep(Math.max(0L, duration.toMillis()));

    /**
     * Waits for the given duration / wartet die angegebene Dauer.
     *
     * @param duration how long / wie lange
     * @throws InterruptedException when the thread is interrupted / bei Unterbrechung
     */
    void sleep(Duration duration) throws InterruptedException;
}
