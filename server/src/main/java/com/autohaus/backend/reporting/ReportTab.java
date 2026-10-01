package com.autohaus.backend.reporting;

/**
 * Tabs of the status page (REQ-038 BR-1, BR-2, §10.1) / Tabs der Statusseite. Each tab is a real
 * page behind its own path — the navigation consists of plain links with
 * {@code aria-current="page"}, because the CSP of the internal chain forbids every script. /
 * Echte Seiten statt Skript-Tabs.
 */
public enum ReportTab {
    STATISTICS("/internal/report/statistics", "Statistik", "Statusseite – Statistik"),
    BUGS("/internal/report/bugs", "Bugs", "Statusseite – Bugs");

    private final String path;
    private final String label;
    private final String title;

    ReportTab(String path, String label, String title) {
        this.path = path;
        this.label = label;
        this.title = title;
    }

    /** Canonical link target of the navigation / kanonisches Linkziel der Navigation. */
    public String path() {
        return path;
    }

    /** Visible tab label, German literal (§17) / sichtbare Tab-Beschriftung. */
    public String label() {
        return label;
    }

    /** {@code <title>} of the document (WCAG 2.4.2) / Dokumenttitel. */
    public String title() {
        return title;
    }
}
