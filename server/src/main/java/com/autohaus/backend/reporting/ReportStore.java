package com.autohaus.backend.reporting;

import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Holds the most recently generated report <b>fragment</b> in memory (REQ-030, REQ-037 §14.2). No
 * persistence — the scheduler regenerates on startup and nightly, which bridges container restarts.
 * Always present as a bean; empty until the (toggle-gated) scheduler has run once.
 *
 * <p>Since REQ-037 the stored string is no longer a complete HTML document but only the report
 * fragment ({@code .ai} + {@code .ov} + {@code .err}, produced by
 * {@link ReportHtmlGenerator#reportFragment(ReportModel)}). {@code ReportController} embeds it into
 * the page via {@code renderPage(usage, fragment)} so that the usage statistics can be placed above
 * it. Storing a full document here would produce nested {@code html}/{@code head}/{@code body}
 * elements (TC-17). / Gespeichert wird seit REQ-037 ein Fragment, keine vollständige Seite.
 */
@Component
public class ReportStore {

    private volatile String fragment;
    private volatile Instant generatedAt;

    /**
     * Replaces the stored report fragment / ersetzt das gespeicherte Report-Fragment.
     *
     * @param fragment the report fragment — no {@code <!doctype>}, no {@code <html>} (TC-17) /
     *     Report-Fragment ohne Dokumentrahmen
     * @param generatedAt time the report was generated / Erzeugungszeitpunkt
     */
    public synchronized void update(String fragment, Instant generatedAt) {
        this.fragment = fragment;
        this.generatedAt = generatedAt;
    }

    /** The current report fragment, {@code null} until the first run / aktuelles Fragment. */
    public String fragment() {
        return fragment;
    }

    public Instant generatedAt() {
        return generatedAt;
    }

    public boolean hasReport() {
        return fragment != null;
    }
}
