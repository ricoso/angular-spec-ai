package com.autohaus.backend.common.logging;

import com.autohaus.backend.common.filter.RequestIdFilter;
import org.slf4j.MDC;

/**
 * MDC job markers of the REQ-038 background runs (BR-15, §19 Nr. 8) / MDC-Job-Marker der
 * REQ-038-Hintergrundläufe.
 *
 * <p>Sets the {@code traceId} of a job ({@code job.capacityprobe}, {@code job.insightsrollup},
 * {@code job.usagebooking}) and restores the previous value afterwards — {@code null} removes the
 * key, so a scheduler thread is left clean. The same three steps were previously copied into every
 * job; the call sites keep their own {@code try/finally}, so nothing changes in the order of logging
 * and restoring. / Setzt den Job-Marker und stellt den vorherigen Wert wieder her; die Aufrufer
 * behalten ihr eigenes {@code try/finally}.
 *
 * <pre>{@code
 * String previous = JobTrace.enter(TRACE_ID);
 * try {
 *     ...
 * } finally {
 *     JobTrace.restore(previous);
 * }
 * }</pre>
 */
public final class JobTrace {

    private JobTrace() {
    }

    /**
     * Sets the job marker / setzt den Job-Marker.
     *
     * @param traceId the job marker / Job-Marker
     * @return the previous {@code traceId} or {@code null} / vorheriger Wert oder {@code null}
     */
    public static String enter(String traceId) {
        String previous = MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY);
        MDC.put(RequestIdFilter.TRACE_ID_MDC_KEY, traceId);
        return previous;
    }

    /**
     * Sets the job marker only when no {@code traceId} is present — a nested call keeps the marker
     * of its run / setzt den Marker nur, wenn noch keiner gesetzt ist.
     *
     * @param traceId the job marker / Job-Marker
     * @return the previous {@code traceId} or {@code null} / vorheriger Wert oder {@code null}
     */
    public static String enterIfAbsent(String traceId) {
        String previous = MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY);
        if (previous == null) {
            MDC.put(RequestIdFilter.TRACE_ID_MDC_KEY, traceId);
        }
        return previous;
    }

    /**
     * Restores the value returned by {@link #enter} or {@link #enterIfAbsent} / stellt den
     * vorherigen Wert wieder her.
     *
     * @param previous the previous {@code traceId}; {@code null} removes the key / {@code null}
     *     entfernt den Schlüssel
     */
    public static void restore(String previous) {
        if (previous == null) {
            MDC.remove(RequestIdFilter.TRACE_ID_MDC_KEY);
        } else {
            MDC.put(RequestIdFilter.TRACE_ID_MDC_KEY, previous);
        }
    }
}
