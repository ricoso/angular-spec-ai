package com.autohaus.backend.common.logging;

import com.autohaus.backend.common.filter.RequestIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * MDC job markers of the REQ-038 background runs (BR-15) / MDC-Job-Marker: set, then restore the
 * previous value or remove the key.
 */
class JobTraceTest {

    private static final String KEY = RequestIdFilter.TRACE_ID_MDC_KEY;

    @AfterEach
    void tearDown() {
        MDC.remove(KEY);
    }

    @Test
    @DisplayName("enter on a clean thread sets the marker; restore removes the key again")
    void entersAndLeavesACleanThread() {
        String previous = JobTrace.enter("job.capacityprobe");

        assertNull(previous);
        assertEquals("job.capacityprobe", MDC.get(KEY));
        JobTrace.restore(previous);
        assertNull(MDC.get(KEY));
    }

    @Test
    @DisplayName("enter over a request traceId replaces it for the run and restores it afterwards")
    void restoresThePreviousTraceId() {
        MDC.put(KEY, "req-4711");

        String previous = JobTrace.enter("job.usagebooking");

        assertEquals("req-4711", previous);
        assertEquals("job.usagebooking", MDC.get(KEY));
        JobTrace.restore(previous);
        assertEquals("req-4711", MDC.get(KEY));
    }

    @Test
    @DisplayName("enterIfAbsent keeps an existing marker and sets one only on a clean thread")
    void entersOnlyWhenAbsent() {
        MDC.put(KEY, "job.insightsrollup");
        String nested = JobTrace.enterIfAbsent("job.other");
        assertEquals("job.insightsrollup", nested);
        assertEquals("job.insightsrollup", MDC.get(KEY));
        JobTrace.restore(nested);
        assertEquals("job.insightsrollup", MDC.get(KEY));

        MDC.remove(KEY);
        String clean = JobTrace.enterIfAbsent("job.insightsrollup");
        assertNull(clean);
        assertEquals("job.insightsrollup", MDC.get(KEY));
        JobTrace.restore(clean);
        assertNull(MDC.get(KEY));
    }
}
