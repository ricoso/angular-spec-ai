package com.autohaus.backend.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/** The shared production sleeper of the REQ-038 jobs / der gemeinsame Produktions-Sleeper. */
class SleeperTest {

    @Test
    @DisplayName("THREAD returns at once for zero and negative durations and sleeps a short one")
    void sleepsWithoutFailingOnNonPositiveDurations() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertDoesNotThrow(() -> Sleeper.THREAD.sleep(Duration.ZERO));
            assertDoesNotThrow(() -> Sleeper.THREAD.sleep(Duration.ofSeconds(-30)));
            assertDoesNotThrow(() -> Sleeper.THREAD.sleep(Duration.ofMillis(5)));
        });
    }

    @Test
    @DisplayName("THREAD propagates an interrupt as InterruptedException")
    void propagatesTheInterrupt() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> Sleeper.THREAD.sleep(Duration.ofSeconds(10)));
        } finally {
            Thread.interrupted();
        }
    }
}
