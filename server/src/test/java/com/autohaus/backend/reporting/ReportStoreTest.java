package com.autohaus.backend.reporting;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ReportStoreTest {

    @Test
    void startsEmpty() {
        ReportStore store = new ReportStore();
        assertFalse(store.hasReport());
        assertNull(store.fragment());
        assertNull(store.generatedAt());
    }

    @Test
    void holdsLatestReport() {
        ReportStore store = new ReportStore();
        Instant at = Instant.parse("2026-07-09T03:00:00Z");
        store.update("<section class=\"ov\">x</section>", at);
        assertTrue(store.hasReport());
        assertEquals("<section class=\"ov\">x</section>", store.fragment());
        assertEquals(at, store.generatedAt());
    }

    @Test
    void overwritesPreviousReport() {
        ReportStore store = new ReportStore();
        store.update("<old>", Instant.parse("2026-07-08T03:00:00Z"));
        store.update("<new>", Instant.parse("2026-07-09T03:00:00Z"));
        assertEquals("<new>", store.fragment());
    }
}
