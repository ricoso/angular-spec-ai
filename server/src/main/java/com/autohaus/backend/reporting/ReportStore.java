package com.autohaus.backend.reporting;

import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Holds the most recently generated report HTML in memory (REQ-030). No persistence —
 * the scheduler regenerates on startup and nightly, which bridges container restarts.
 * Always present as a bean; empty until the (toggle-gated) scheduler has run once.
 */
@Component
public class ReportStore {

    private volatile String html;
    private volatile Instant generatedAt;

    public synchronized void update(String html, Instant generatedAt) {
        this.html = html;
        this.generatedAt = generatedAt;
    }

    public String html() {
        return html;
    }

    public Instant generatedAt() {
        return generatedAt;
    }

    public boolean hasReport() {
        return html != null;
    }
}
