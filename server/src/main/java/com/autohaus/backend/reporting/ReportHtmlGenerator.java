package com.autohaus.backend.reporting;

import com.autohaus.backend.capacity.CapacitySectionRenderer;
import com.autohaus.backend.capacity.CapacityViewModel;
import com.autohaus.backend.common.html.HtmlText;
import com.autohaus.backend.usage.UsageSectionRenderer;
import com.autohaus.backend.usage.UsageStats;
import com.autohaus.backend.usage.insights.BranchRankingSectionRenderer;
import com.autohaus.backend.usage.insights.EntryVariantSectionRenderer;
import com.autohaus.backend.usage.insights.FunnelSectionRenderer;
import com.autohaus.backend.usage.insights.InsightsViewModel;
import com.autohaus.backend.usage.insights.ServiceGroupSectionRenderer;
import com.autohaus.backend.usage.insights.TimingSectionRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the internal status page (REQ-030, extended by REQ-037 and REQ-038). Served behind Basic
 * Auth. All dynamic values (including customer-data payloads) are HTML-escaped. No external
 * resources — inline CSS only. / Rendert die interne Statusseite.
 *
 * <p>The page is assembled in two parts (REQ-037 §14.2): {@link #reportFragment(ReportModel)}
 * produces the error-report sections ({@code .ai}, {@code .ov}, {@code .err}) <b>without</b> a
 * document frame, and the frame methods wrap header, navigation, content and footer into one
 * document. Statistics and error report stay decoupled: the error report depends on the LLM toggle,
 * the statistics do not. / Seite und Fragment sind getrennt.
 *
 * <p><b>REQ-038 — tabs as real pages (BR-1, BR-2):</b> {@link #renderFrame(ReportTab, boolean,
 * String, String)} is the one shared frame: {@code <title>} per tab, neutral H1 „Statusseite",
 * the tab navigation as plain links with {@code aria-current="page"} (the CSP of the internal
 * chain forbids every script), the content in {@code <main id="content">} (never a
 * {@code <section>} wrapper — the global card rule would nest cards) and the unchanged footer. On
 * top of it sit {@link #renderStatisticsPage(StatisticsPage)}, {@link #renderBugsPage(String,
 * boolean)} and {@link #renderNotFoundPage(boolean)}. {@link #renderPage(UsageStats, String)}
 * stays as a delegation with its previous composition (used by {@link #generate(ReportModel)} and
 * the existing tests; the controller no longer calls it). / Tabs sind echte Seiten mit einem
 * gemeinsamen Rahmen; {@code renderPage(...)} bleibt als Delegation.
 */
@Component
public class ReportHtmlGenerator {

    private static final DateTimeFormatter TS =
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'Uhr'").withZone(ZoneId.of("Europe/Berlin"));

    /** Calendar day of the meta line / Kalendertag der Meta-Zeile, z. B. {@code 22.10.2026}. */
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** Clock time of a scan in Berlin / Uhrzeit eines Scans in Berlin, z. B. {@code 22:31}. */
    private static final DateTimeFormatter CLOCK_TIME =
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Berlin"));

    // ---- visible labels of the frame, German literals (REQ-038 §17) / Beschriftungen ----

    /** H1 of every page (§17 {@code PAGE_HEADING}) / Überschrift jeder Seite. */
    static final String PAGE_HEADING = "Statusseite";

    /** {@code <title>} of the 404 page (§17 {@code TITLE_NOT_FOUND}) / Titel der 404-Seite. */
    static final String TITLE_NOT_FOUND = "Statusseite – Seite nicht gefunden";

    /** {@code aria-label} of the tab navigation (§17 {@code NAV_ARIA_LABEL}). */
    static final String NAV_ARIA_LABEL = "Bereiche der Statusseite";

    /**
     * Separator of the meta line parts — the REQ-030 header already used {@code &middot;}. /
     * Trenner der Meta-Zeile wie im Bestand.
     */
    static final String META_SEPARATOR = " &middot; ";

    /** First part of every meta line (§17 {@code META_STATISTICS}/{@code META_BUGS}). */
    static final String META_AS_OF = "Stand: {ts}";

    /** Evaluation part of {@code META_STATISTICS} (§17) / Auswertungsteil der Meta-Zeile. */
    static final String META_EVALUATED_UNTIL = "Auswertung bis gestern ({date})";

    /**
     * Scan part of {@code META_STATISTICS} (§17) with the start time of the shown scan
     * ({@code CapacityViewModel.scanStartedAt}, Europe/Berlin). / Scan-Teil mit Startzeit.
     */
    static final String META_SCAN_OF = "Kapazitäts-Scan vom {date}, {time} Uhr";

    /**
     * {@link #META_SCAN_OF} for a view without scan times (hand-built views) / ohne Startzeit.
     */
    static final String META_SCAN_OF_UNTIMED = "Kapazitäts-Scan vom {date}";

    /** First scan in progress (§17 {@code META_SCAN_RUNNING}) / erster Scan läuft. */
    static final String META_SCAN_RUNNING = "Kapazitäts-Scan: erster Lauf aktiv (seit {time} Uhr)";

    /** Latest scan failed, fallback shown (§17 {@code META_SCAN_FAILED}) / Fallback-Scan. */
    static final String META_SCAN_FAILED =
        "Kapazitäts-Scan vom {date} fehlgeschlagen – angezeigt: Scan vom {shown}";

    /**
     * Variant of {@code META_SCAN_FAILED} when the day of the failed scan cannot be told from the
     * view model / Variante ohne Datum des fehlgeschlagenen Scans.
     */
    static final String META_SCAN_FAILED_UNDATED =
        "Kapazitäts-Scan fehlgeschlagen – angezeigt: Scan vom {shown}";

    /** Evaluation part of the bugs tab (§17 {@code META_BUGS}, REQ-030 wording). */
    static final String META_BUGS = "Fehler-Auswertung: letzte 24 Stunden";

    /** 404 heading (§17 {@code NOT_FOUND_HEADING}) / Überschrift der 404-Seite. */
    static final String NOT_FOUND_HEADING = "Seite nicht gefunden";

    /** 404 text (§17 {@code NOT_FOUND_TEXT}) — never echoes the requested path (§9 Security). */
    static final String NOT_FOUND_TEXT = "Diese Adresse gibt es auf der Statusseite nicht.";

    /** 404 link to the statistics tab (§17 {@code NOT_FOUND_LINK_STATISTICS}). */
    static final String NOT_FOUND_LINK_STATISTICS = "Zur Statistik";

    /** 404 link to the bugs tab (§17 {@code NOT_FOUND_LINK_BUGS}). */
    static final String NOT_FOUND_LINK_BUGS = "Zu den Bugs";

    /** Unchanged footer (REQ-030) / unveränderter Footer. */
    private static final String FOOTER =
        "<footer class=\"ft\">Automatisch generiert &middot; REQ-030 &middot; gs-terminbuchungs-app</footer>";

    /** Renders the usage statistics section (REQ-037) / rendert die Nutzungsstatistik-Sektion. */
    private final UsageSectionRenderer usageRenderer;

    /** Composes the statistics tab body S0 → S7 (REQ-038) / setzt den Statistik-Tab zusammen. */
    private final StatisticsPageRenderer statisticsRenderer;

    /**
     * Spring wiring — the only {@code @Autowired} constructor (REQ-038 §14.2): with three public
     * constructors Spring would otherwise refuse to start (visible only in the smoke test). /
     * Spring-Verdrahtung; einziger {@code @Autowired}-Konstruktor.
     *
     * @param usageRenderer the renderer of the usage section (REQ-037, S5)
     * @param statisticsRenderer the composer of the statistics tab (REQ-038)
     */
    @Autowired
    public ReportHtmlGenerator(UsageSectionRenderer usageRenderer,
                               StatisticsPageRenderer statisticsRenderer) {
        this.usageRenderer = usageRenderer;
        this.statisticsRenderer = statisticsRenderer;
    }

    /**
     * Kept from REQ-037 for tests and direct use (no longer {@code @Autowired}) / aus REQ-037
     * erhalten, nicht mehr {@code @Autowired}.
     *
     * <p>Builds its own {@link StatisticsPageRenderer} from stateless renderers and hands the given
     * usage renderer on, so S5 renders identically in both paths. / Baut den Statistik-Renderer
     * selbst und reicht den Nutzungs-Renderer weiter.
     *
     * @param usageRenderer the renderer of the usage section (REQ-037)
     */
    public ReportHtmlGenerator(UsageSectionRenderer usageRenderer) {
        this(usageRenderer, new StatisticsPageRenderer(new BriefingSectionRenderer(),
            new CapacitySectionRenderer(), new BranchRankingSectionRenderer(),
            new ServiceGroupSectionRenderer(), new EntryVariantSectionRenderer(),
            new TimingSectionRenderer(), usageRenderer,
            new FunnelSectionRenderer(), new LimitsSectionRenderer()));
    }

    /**
     * Convenience constructor for tests and direct use / Bequemlichkeitskonstruktor.
     *
     * <p>Kept so that {@code new ReportHtmlGenerator()} keeps working; the renderer is stateless.
     */
    public ReportHtmlGenerator() {
        this(new UsageSectionRenderer());
    }

    /**
     * Full page for a model alone, without usage statistics / vollständige Seite ohne Statistik.
     *
     * <p>Delegation only (REQ-037 §14.2): the page keeps its {@code <!doctype html>} frame, so the
     * existing REQ-030 behaviour — and its regression test — is unchanged.
     */
    public String generate(ReportModel model) {
        return renderPage(null, reportFragment(model));
    }

    /**
     * Composes the whole page: frame → header → usage statistics → error-report fragment → footer
     * (REQ-037 AC-2). / Komponiert die gesamte Seite.
     *
     * <p><b>REQ-038:</b> kept as a delegation on {@link #renderFrame(ReportTab, boolean, String,
     * String)} <b>without</b> navigation and with the previous composition (§14.2, §19 deviation
     * 7) — used by {@link #generate(ReportModel)} and the existing tests; the controller serves the
     * tabs through the dedicated methods and never puts statistics and error report into one
     * response (BR-3). / Nur noch Delegation, ohne Navigation.
     *
     * <p>{@code usage == null} means the feature is switched off ({@code usage.enabled=false}): the
     * section is then dropped <b>without any replacement</b> — no „not available" hint, no
     * {@code class="us"} anywhere in the document (AC-22). / Sektion entfällt ersatzlos.
     *
     * <p>„Stand" in the header is the time the page was requested. Each data source names its own
     * age: the statistics section carries {@code liveAsOf} and the rollup day, the error report its
     * own 24-hour window. / Jede Quelle nennt ihr eigenes Alter.
     *
     * @param usage the usage snapshot, or {@code null} to omit the section (AC-22)
     * @param reportFragment the error-report fragment or the hint text shown in its place;
     *     {@code null} or blank appends nothing
     * @return the complete HTML document / das vollständige HTML-Dokument
     */
    public String renderPage(UsageStats usage, String reportFragment) {
        StringBuilder body = new StringBuilder(16384);
        // Usage statistics first — the section is the first content element of the page (AC-2).
        if (usage != null) {
            body.append(usageRenderer.render(usage));
        }
        if (reportFragment != null && !reportFragment.isBlank()) {
            body.append(reportFragment);
        }
        return renderFrame(ReportTab.BUGS, false, bugsMetaLine(Instant.now()), body.toString());
    }

    /**
     * The shared page frame of both tabs (REQ-038 §11.1, AC-2, AC-4) / gemeinsamer Seitenrahmen.
     *
     * <p>Order: {@code <!doctype>} → {@code <title>} of {@code active} + stylesheet →
     * {@code header.hd} (H1 „Statusseite", {@code p.meta}) → {@code nav.tabs} (only with
     * {@code showNavigation}) → {@code <main id="content">} → footer. No {@code role="tab*"}, no
     * script, no form, no image (BR-2). / Reihenfolge des Rahmens.
     *
     * @param active the tab the document belongs to (title and {@code aria-current}); {@code null}
     *     is treated as {@link ReportTab#BUGS}
     * @param showNavigation {@code false} drops the tab bar — used while the statistics tab does
     *     not exist (BR-4, §5.1 row 8) / ohne Tab-Leiste
     * @param metaLine the already escaped meta line; {@code null}/blank omits {@code p.meta}
     * @param body the already escaped content of {@code main}; {@code null} renders an empty main
     * @return the complete document / das vollständige Dokument
     */
    public String renderFrame(ReportTab active, boolean showNavigation, String metaLine, String body) {
        ReportTab tab = active != null ? active : ReportTab.BUGS;
        return frame(tab.title(), showNavigation ? tab : null, metaLine, body);
    }

    /**
     * The statistics tab (REQ-038 §12.2, Step 6) / der Statistik-Tab: jump links and sections
     * S0 → S7 from {@link StatisticsPageRenderer}, meta line per §17 {@code META_STATISTICS}, tab
     * bar with {@code aria-current} on „Statistik". Pure string concatenation over in-memory view
     * models (BR-5). / Reine Verkettung über vorberechnete View-Models.
     *
     * @param page the render input; {@code null} renders a page with S7 only
     * @return the complete document / das vollständige Dokument
     */
    public String renderStatisticsPage(StatisticsPage page) {
        StatisticsPage safe = page != null ? page : new StatisticsPage(null, null, null, null);
        return renderFrame(ReportTab.STATISTICS, true, statisticsMetaLine(safe, Instant.now()),
            statisticsRenderer.render(safe));
    }

    /**
     * The bugs tab (REQ-038 §12.3, Step 7) / der Bugs-Tab: the stored fragment <b>byte-identical</b>
     * (including the CSS-only error filter) or the hint shown in its place (BR-3), meta line
     * „Fehler-Auswertung: letzte 24 Stunden". / Fragment byte-gleich.
     *
     * @param fragment the stored fragment or the hint; {@code null}/blank renders an empty main
     * @param showNavigation {@code false} while the statistics tab does not exist (BR-4)
     * @return the complete document / das vollständige Dokument
     */
    public String renderBugsPage(String fragment, boolean showNavigation) {
        String body = fragment == null || fragment.isBlank() ? "" : fragment;
        return renderFrame(ReportTab.BUGS, showNavigation, bugsMetaLine(Instant.now()), body);
    }

    /**
     * The 404 page of the catch-all mapping (REQ-038 §12.4, E7, AC-5) / die 404-Seite. Links to the
     * existing tabs only — „Zur Statistik" only while that tab exists — and <b>never</b> the
     * requested path (no reflected markup, §9 Security). No tab bar. / Ohne Tab-Leiste, ohne Pfad.
     *
     * @param statisticsAvailable whether the statistics tab exists (§5.1)
     * @return the complete document / das vollständige Dokument
     */
    public String renderNotFoundPage(boolean statisticsAvailable) {
        StringBuilder b = new StringBuilder(512);
        b.append("<section class=\"st-notfound\" aria-labelledby=\"notfound-h\">")
            .append("<h2 id=\"notfound-h\">").append(esc(NOT_FOUND_HEADING)).append("</h2>")
            .append("<p>").append(esc(NOT_FOUND_TEXT)).append("</p><ul>");
        if (statisticsAvailable) {
            b.append("<li><a href=\"").append(esc(ReportTab.STATISTICS.path())).append("\">")
                .append(esc(NOT_FOUND_LINK_STATISTICS)).append("</a></li>");
        }
        b.append("<li><a href=\"").append(esc(ReportTab.BUGS.path())).append("\">")
            .append(esc(NOT_FOUND_LINK_BUGS)).append("</a></li>");
        b.append("</ul></section>");
        return frame(TITLE_NOT_FOUND, null, null, b.toString());
    }

    /**
     * Meta line of the statistics tab (§17 {@code META_STATISTICS}, {@code META_SCAN_RUNNING},
     * {@code META_SCAN_FAILED}) / Meta-Zeile des Statistik-Tabs. Each part appears only for an
     * active source: „Auswertung bis gestern" with the insights view, the scan part with the
     * capacity view. / Jeder Teil nur für eine aktive Quelle.
     *
     * @param page the render input / Render-Eingabe
     * @param now the time of the request („Stand") / Abrufzeitpunkt
     * @return the escaped meta line / die maskierte Meta-Zeile
     */
    static String statisticsMetaLine(StatisticsPage page, Instant now) {
        List<String> parts = new ArrayList<>(3);
        parts.add(META_AS_OF.replace("{ts}", esc(TS.format(now))));
        InsightsViewModel insights = page.insights();
        if (insights != null && insights.asOf() != null) {
            parts.add(META_EVALUATED_UNTIL.replace("{date}", esc(DAY.format(insights.asOf()))));
        }
        String scan = scanMetaPart(page.capacity());
        if (scan != null) {
            parts.add(scan);
        }
        return String.join(META_SEPARATOR, parts);
    }

    /**
     * Meta line of the bugs tab — byte-identical to the REQ-030 header text / Meta-Zeile des
     * Bugs-Tabs, wie bisher.
     */
    static String bugsMetaLine(Instant now) {
        return META_AS_OF.replace("{ts}", esc(TS.format(now))) + META_SEPARATOR + esc(META_BUGS);
    }

    /**
     * The capacity part of the meta line / Kapazitätsteil der Meta-Zeile: first scan running
     * (Step 2, TC-55), fallback after a failed scan (AC-10) or the day of the shown scan; nothing
     * while no scan exists yet. / Laufender Erstscan, Fallback oder Scan-Tag.
     */
    private static String scanMetaPart(CapacityViewModel capacity) {
        if (capacity == null) {
            return null;
        }
        if (capacity.scanDay() == null) {
            // No completed scan to show: only the own running first scan is worth a line (Step 2).
            return capacity.runningSince() != null
                ? META_SCAN_RUNNING.replace("{time}", esc(CLOCK_TIME.format(capacity.runningSince())))
                : null;
        }
        String shown = esc(DAY.format(capacity.scanDay()));
        if (capacity.showingFallbackScan()) {
            // The day of the failed scan: the view model names it; a hand-built view without it
            // falls back to the newest grid column (BR-26: a failed day stays a „—" column).
            LocalDate failed = capacity.latestScanDay() != null
                ? capacity.latestScanDay() : newest(capacity.last10ScanDays());
            if (failed != null && failed.isAfter(capacity.scanDay())) {
                return META_SCAN_FAILED.replace("{date}", esc(DAY.format(failed)))
                    .replace("{shown}", shown);
            }
            return META_SCAN_FAILED_UNDATED.replace("{shown}", shown);
        }
        if (capacity.scanStartedAt() != null) {
            return META_SCAN_OF.replace("{date}", shown)
                .replace("{time}", esc(CLOCK_TIME.format(capacity.scanStartedAt())));
        }
        return META_SCAN_OF_UNTIMED.replace("{date}", shown);
    }

    private static LocalDate newest(List<LocalDate> days) {
        if (days == null || days.isEmpty()) {
            return null;
        }
        LocalDate newest = null;
        for (LocalDate day : days) {
            if (day != null && (newest == null || day.isAfter(newest))) {
                newest = day;
            }
        }
        return newest;
    }

    /**
     * Builds the document / baut das Dokument.
     *
     * @param title the {@code <title>} text (WCAG 2.4.2)
     * @param navigationActive the tab marked with {@code aria-current}; {@code null} = no tab bar
     * @param metaLine the escaped meta line or {@code null}
     * @param body the escaped content of {@code main}
     */
    private String frame(String title, ReportTab navigationActive, String metaLine, String body) {
        StringBuilder h = new StringBuilder(32768);
        h.append("<!doctype html><html lang=\"de\"><head><meta charset=\"utf-8\">");
        h.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        h.append("<title>").append(esc(title)).append("</title>").append(css()).append("</head><body>");

        h.append("<header class=\"hd\"><h1>").append(esc(PAGE_HEADING)).append("</h1>");
        if (metaLine != null && !metaLine.isBlank()) {
            h.append("<p class=\"meta\">").append(metaLine).append("</p>");
        }
        h.append("</header>");

        if (navigationActive != null) {
            h.append(navigation(navigationActive));
        }

        // Panel wrapper is <main>, never <section>: the global card rule would nest cards (§11.1).
        h.append("<main id=\"content\">");
        if (body != null) {
            h.append(body);
        }
        h.append("</main>");

        h.append(FOOTER);
        h.append("</body></html>");
        return h.toString();
    }

    /**
     * The tab bar (BR-2, AC-2): exactly two plain links, the active one with
     * {@code aria-current="page"} — no {@code role="tablist"}, because without a script the APG
     * keyboard behaviour would be missing. / Genau zwei Links, der aktive mit {@code aria-current}.
     */
    private static String navigation(ReportTab active) {
        StringBuilder n = new StringBuilder(256);
        n.append("<nav class=\"tabs\" aria-label=\"").append(esc(NAV_ARIA_LABEL)).append("\"><ul>");
        for (ReportTab tab : ReportTab.values()) {
            n.append("<li><a href=\"").append(esc(tab.path())).append('"');
            if (tab == active) {
                n.append(" aria-current=\"page\"");
            }
            n.append('>').append(esc(tab.label())).append("</a></li>");
        }
        n.append("</ul></nav>");
        return n.toString();
    }

    /**
     * Error-report sections only: {@code .ai} + {@code .ov} + {@code .err} (REQ-037 §14.2). /
     * Nur die Fehler-Report-Sektionen.
     *
     * <p>Contains <b>no</b> {@code <!doctype>}, {@code <html>} or page {@code <style>} block — the
     * fragment is stored that way and embedded by {@link #renderPage(UsageStats, String)}. A full
     * document here would nest {@code html}/{@code head}/{@code body} inside the page (TC-17). /
     * Kein Dokumentrahmen.
     *
     * <p><b>One exception, unchanged from REQ-030:</b> when the report lists errors, the error
     * section still carries its own small inline {@code <style>} with the rules of the CSS-only
     * filter. Those rules are generated per report (one selector per filter button) and therefore
     * cannot live in {@link #css()}. The page stylesheet itself is never part of the fragment. /
     * Der Inline-Style des Fehlerfilters bleibt erhalten.
     *
     * @param model the aggregated error report; {@code null} yields an empty fragment
     * @return the fragment, never {@code null} / das Fragment, nie {@code null}
     */
    public String reportFragment(ReportModel model) {
        if (model == null) {
            return "";
        }
        AiErrorReportSummary summary = model.summary() != null ? model.summary() : AiErrorReportSummary.unavailable();

        StringBuilder h = new StringBuilder(8192);

        // AI summary
        h.append("<section class=\"ai\"><h2>KI-Zusammenfassung</h2>");
        h.append("<p class=\"headline\">").append(esc(summary.headline())).append("</p>");
        h.append("<p>").append(esc(summary.overallAssessment())).append("</p>");
        if (summary.topIssues() != null && !summary.topIssues().isEmpty()) {
            h.append("<h3>Priorisierte Probleme</h3><ul class=\"issues\">");
            for (AiErrorReportSummary.PrioritizedIssue i : summary.topIssues()) {
                h.append("<li><span class=\"sev sev-").append(esc(sevClass(i.severity()))).append("\">")
                    .append(esc(i.severity())).append("</span> <b>").append(esc(i.title())).append("</b>")
                    .append("<div class=\"hint\">Marken: ").append(esc(i.affectedBrands()))
                    .append(" &middot; Standorte: ").append(esc(i.affectedLocations())).append("</div>")
                    .append("<div class=\"hint\">Ursache: ").append(esc(i.rootCauseHypothesis())).append("</div></li>");
            }
            h.append("</ul>");
        }
        if (summary.recommendations() != null && !summary.recommendations().isEmpty()) {
            h.append("<h3>Empfehlungen</h3><ul>");
            for (String r : summary.recommendations()) {
                h.append("<li>").append(esc(r)).append("</li>");
            }
            h.append("</ul>");
        }
        h.append("</section>");

        // Category status overview (Iteration 2): traffic-light tiles per funnel stage + dimension
        h.append("<section class=\"ov\"><h2>Status-Übersicht</h2><div class=\"cats\">");
        h.append(catRate("Terminsuchen", "/snaps", model.snaps()));
        h.append(catRate("Buchungen", "/appointments", model.appointments()));
        h.append(catCount("Marken", "mit Fehlern", model.errorsByBrand()));
        h.append(catCount("Standorte", "mit Fehlern", model.errorsByLocation()));
        h.append("</div></section>");

        // Error list with CSS-only client-side filter (Iteration 3).
        h.append(errorSection(model.errors()));

        return h.toString();
    }

    /**
     * Error list with a CSS-only client-side filter (Iteration 3): hidden radios + sibling
     * selectors let the viewer filter the cards by endpoint (Terminsuchen / Buchungen) and by
     * error type — each button shows its count. No JavaScript, so it also works under a strict CSP.
     */
    private static String errorSection(List<ErrorEntry> errors) {
        StringBuilder h = new StringBuilder(4096);
        h.append("<section class=\"err\"><h2>Fehler (").append(errors.size())
            .append(") <span class=\"pii-note\">— Payloads PII-maskiert</span></h2>");
        if (errors.isEmpty()) {
            h.append("<p class=\"empty\">Keine Fehler im Auswertungszeitraum.</p></section>");
            return h.toString();
        }

        // Count by endpoint for the filter buttons (filter by endpoint only).
        Map<String, Long> epCounts = new LinkedHashMap<>();
        for (ErrorEntry e : errors) {
            String ep = epSlug(e.endpoint());
            epCounts.put(ep, epCounts.getOrDefault(ep, 0L) + 1L);
        }

        // Ordered filter list: {id, label, value}. "Alle" first, then endpoints.
        List<String[]> filters = new ArrayList<>();
        filters.add(new String[]{"flt-all", "Alle (" + errors.size() + ")", ""});
        for (String ep : List.of("snaps", "appointments", "other")) {
            Long c = epCounts.get(ep);
            if (c != null) {
                filters.add(new String[]{"flt-ep-" + ep, epLabel(ep) + " (" + c + ")", ep});
            }
        }

        // Hidden radios — must precede .filters and .err-list for the sibling selectors to reach them.
        for (String[] f : filters) {
            h.append("<input type=\"radio\" name=\"errflt\" class=\"flt-radio\" id=\"").append(f[0]).append("\"")
                .append("flt-all".equals(f[0]) ? " checked" : "").append(">");
        }
        // Dynamic rules: hide non-matching cards + highlight the active label.
        h.append("<style>");
        for (String[] f : filters) {
            if (!f[2].isEmpty()) {
                h.append("#").append(f[0]).append(":checked~.err-list .card:not([data-ep=")
                    .append(f[2]).append("]){display:none}");
            }
            h.append("#").append(f[0]).append(":checked~.filters label[for=").append(f[0])
                .append("]{background:var(--accent);color:#fff;border-color:var(--accent)}");
        }
        h.append("</style>");
        // Filter bar.
        h.append("<div class=\"filters\">");
        for (String[] f : filters) {
            h.append("<label for=\"").append(f[0]).append("\">").append(esc(f[1])).append("</label>");
        }
        h.append("</div>");
        // Cards.
        h.append("<div class=\"err-list\">");
        for (ErrorEntry e : errors) {
            h.append("<article class=\"card\" data-ep=\"").append(epSlug(e.endpoint())).append("\">");
            h.append("<div class=\"row\"><span class=\"k\">Marke</span><span>").append(esc(orDash(e.brand()))).append("</span>")
                .append("<span class=\"k\">Standort</span><span>").append(esc(orDash(e.location()))).append("</span></div>");
            h.append("<div class=\"row\"><span class=\"k\">Services</span><span>")
                .append(esc(e.operations() == null || e.operations().isEmpty() ? "—" : String.join(", ", e.operations())))
                .append("</span><span class=\"k\">Fehlertyp</span><span class=\"etype\">").append(esc(orDash(e.errorType()))).append("</span></div>");
            h.append("<div class=\"row\"><span class=\"k\">Trace-ID</span><span class=\"mono\">").append(esc(orDash(e.traceId())))
                .append("</span><span class=\"k\">Endpoint</span><span class=\"mono\">").append(esc(orDash(e.endpoint()))).append("</span></div>");
            h.append("<details><summary>Gesendet (Request)</summary><pre>").append(esc(orDash(e.requestBody()))).append("</pre></details>");
            h.append("<details><summary>Empfangen (Response)</summary><pre>").append(esc(orDash(e.responseBody()))).append("</pre></details>");
            h.append("</article>");
        }
        h.append("</div></section>");
        return h.toString();
    }

    private static String epLabel(String slug) {
        return switch (slug) {
            case "snaps" -> "Terminsuchen";
            case "appointments" -> "Buchungen";
            default -> "Sonstige";
        };
    }

    private static String epSlug(String endpoint) {
        if (endpoint == null) {
            return "other";
        }
        if (endpoint.endsWith("/snaps")) {
            return "snaps";
        }
        if (endpoint.endsWith("/appointments")) {
            return "appointments";
        }
        return "other";
    }

    /** Traffic-light tile for a funnel category (snaps / appointments): started · ok · failed. */
    private static String catRate(String name, String endpoint, BookingStats s) {
        String status = lightByRate(s.started(), s.failed());
        return "<div class=\"cat cat-" + status + "\">"
            + "<div class=\"cat-hd\"><span class=\"dot\"></span><span class=\"cat-name\">" + esc(name)
            + "</span><span class=\"cat-ep\">" + esc(endpoint) + "</span></div>"
            + "<div class=\"cat-val\">" + s.started() + " gestartet &middot; " + s.succeeded()
            + " ok &middot; <b>" + s.failed() + "</b> Fehler</div></div>";
    }

    /** Traffic-light tile for a dimension (brands / locations): count affected + top list. */
    private static String catCount(String name, String unit, Map<String, Long> byKey) {
        int affected = byKey.size();
        StringBuilder c = new StringBuilder();
        c.append("<div class=\"cat cat-").append(lightByCount(affected)).append("\">");
        c.append("<div class=\"cat-hd\"><span class=\"dot\"></span><span class=\"cat-name\">")
            .append(esc(name)).append("</span></div>");
        c.append("<div class=\"cat-val\"><b>").append(affected).append("</b> ").append(esc(unit)).append("</div>");
        if (!byKey.isEmpty()) {
            c.append("<ul class=\"toplist\">");
            int shown = 0;
            for (Map.Entry<String, Long> e : byKey.entrySet()) {
                if (shown++ >= 5) {
                    break;
                }
                c.append("<li><span>").append(esc(orDash(e.getKey()))).append("</span><b>")
                    .append(e.getValue()).append("</b></li>");
            }
            c.append("</ul>");
        }
        c.append("</div>");
        return c.toString();
    }

    /** Green 0 %, yellow < 5 %, red >= 5 % failure rate; grey when there were no requests. */
    private static String lightByRate(long started, long failed) {
        if (started <= 0) {
            return "neutral";
        }
        if (failed <= 0) {
            return "ok";
        }
        return (double) failed / started < 0.05 ? "warn" : "bad";
    }

    /** Green 0, yellow 1, red >= 2 affected values (signals how widely the errors spread). */
    private static String lightByCount(int affected) {
        if (affected <= 0) {
            return "ok";
        }
        return affected == 1 ? "warn" : "bad";
    }

    private static String sevClass(String severity) {
        String v = severity == null ? "" : severity.toLowerCase(java.util.Locale.ROOT);
        if (v.contains("critical") || v.contains("high")) return "high";
        if (v.contains("medium")) return "med";
        return "low";
    }

    private static String orDash(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    /**
     * HTML-escapes a dynamic value / maskiert einen dynamischen Wert.
     *
     * <p>Shared with {@link UsageSectionRenderer} (REQ-037 §14.2, TC-14) so the whole page uses one
     * implementation. §14.2 asks for package-private visibility; the renderer lives in
     * {@code com.autohaus.backend.usage}, so {@code public} is the smallest visibility that
     * actually reaches it. / Gemeinsame Maskierung für Seite und Statistik-Sektion.
     *
     * <p><b>REQ-038:</b> the implementation lives in the neutral {@link HtmlText#esc(String)}, which
     * the statistics sections use directly (no package depends on {@code reporting} for escaping);
     * this method stays as the public delegation for the existing callers. / Delegiert an das
     * neutrale Paket.
     *
     * @param s the raw value; {@code null} yields an empty string
     * @return the escaped value / der maskierte Wert
     */
    public static String esc(String s) {
        return HtmlText.esc(s);
    }

    private static String css() {
        return """
            <style>
            :root{--ink:#1a2b3c;--soft:#5a6b7b;--line:#dbe3ea;--bg:#eef2f6;--surface:#fff;
            --accent:#0e6b7c;--ok:#2c7a4e;--bad:#b83a2c;--warn:#a16a12;--mono:ui-monospace,Menlo,Consolas,monospace;
            --track:#e3eaf0;--bar-tablet:#35808f;--bar-unknown:#6b7a87;--row-alt:#f7fafc;--focus:#005fcc;
            --radius:6px;--radius-sm:3px;--radius-pill:999px;
            --heat-1:#d8ecef;--heat-2:#aed8df;--heat-3:#7fbfca;--heat-4:#4f9dab;--cap-free:#dcefe4;--cap-tight:#f6e7c8;--cap-booked:#f4dcd6;}
            *{box-sizing:border-box}body{margin:0;font-family:system-ui,-apple-system,"Segoe UI",sans-serif;
            color:var(--ink);background:var(--bg);line-height:1.5;padding:1.5rem;max-width:60rem;margin:0 auto}
            h1{font-size:1.6rem;margin:0}h2{font-size:1.1rem;border-bottom:2px solid var(--line);padding-bottom:.3rem}
            h3{font-size:.95rem;margin:1rem 0 .4rem}.meta{color:var(--soft);font-size:.85rem;margin:.3rem 0 0}
            section{background:var(--surface);border:1px solid var(--line);border-radius:6px;padding:1.1rem 1.3rem;margin:1rem 0}
            .ai{border-left:4px solid var(--accent)}.headline{font-weight:650;font-size:1.05rem}
            .issues{list-style:none;padding:0}.issues li{border:1px solid var(--line);border-radius:5px;padding:.6rem .8rem;margin:.4rem 0}
            .hint{color:var(--soft);font-size:.85rem;margin-top:.2rem}
            .sev{display:inline-block;font-size:.72rem;font-weight:700;text-transform:uppercase;padding:.1rem .45rem;border-radius:3px;margin-right:.4rem}
            .sev-high{background:#fae7e4;color:var(--bad)}.sev-med{background:#f7efd9;color:var(--warn)}.sev-low{background:#e7f2ec;color:var(--ok)}
            .cats{display:grid;grid-template-columns:repeat(2,1fr);gap:.8rem}
            .cat{border:1px solid var(--line);border-left-width:4px;border-radius:6px;padding:.7rem .9rem}
            .cat-ok{border-left-color:var(--ok)}.cat-warn{border-left-color:var(--warn)}.cat-bad{border-left-color:var(--bad)}.cat-neutral{border-left-color:var(--soft)}
            .cat-hd{display:flex;align-items:center;gap:.45rem;font-weight:650}
            .dot{width:.7rem;height:.7rem;border-radius:50%;flex:none;background:var(--soft)}
            .cat-ok .dot{background:var(--ok)}.cat-warn .dot{background:var(--warn)}.cat-bad .dot{background:var(--bad)}
            .cat-ep{color:var(--soft);font-size:.76rem;font-family:var(--mono);margin-left:auto}
            .cat-val{margin-top:.35rem;font-size:.92rem;font-variant-numeric:tabular-nums}
            .toplist{list-style:none;padding:0;margin:.45rem 0 0}.toplist li{display:flex;justify-content:space-between;font-size:.82rem;padding:.14rem 0;border-top:1px solid var(--line)}
            .pii-note{font-weight:400;font-size:.78rem;color:var(--soft)}
            .flt-radio{position:absolute;width:1px;height:1px;opacity:0;pointer-events:none}
            .filters{display:flex;flex-wrap:wrap;gap:.4rem;margin:.3rem 0 .9rem}
            .filters label{cursor:pointer;font-size:.8rem;padding:.3rem .75rem;border:1px solid var(--line);border-radius:999px;background:var(--surface);color:var(--soft);user-select:none}
            .filters label:hover{border-color:var(--accent);color:var(--accent)}
            .card{border:1px solid var(--line);border-radius:6px;padding:.8rem 1rem;margin:.7rem 0}
            .row{display:grid;grid-template-columns:auto 1fr auto 1fr;gap:.3rem .7rem;font-size:.9rem;margin-bottom:.4rem}
            .k{color:var(--soft);font-size:.75rem;text-transform:uppercase;letter-spacing:.04em;align-self:center}
            .etype{font-weight:600;color:var(--bad)}.mono{font-family:var(--mono);font-size:.82rem;word-break:break-all}
            details{margin-top:.4rem}summary{cursor:pointer;color:var(--accent);font-size:.85rem}
            pre{background:#0e161b;color:#c9d6dd;font-family:var(--mono);font-size:.78rem;padding:.7rem;border-radius:5px;overflow-x:auto;white-space:pre-wrap;word-break:break-all}
            .empty{color:var(--soft)}.ft{color:var(--soft);font-size:.78rem;text-align:center;margin-top:1.5rem}
            /* REQ-037 usage statistics — classes per §11.2, tokens per :root above, no JavaScript */
            .us{border-left:4px solid var(--accent)}
            .us-intro{color:var(--soft);font-size:.85rem;margin:.4rem 0 0}
            .us-block{margin-top:1.4rem}.us-block:first-of-type{margin-top:1rem}
            .us-hd{display:flex;align-items:center;flex-wrap:wrap;gap:.5rem}
            .us-title{font-size:.95rem;font-weight:650;margin:0}
            .us-badge{display:inline-block;font-size:.7rem;font-weight:700;text-transform:uppercase;letter-spacing:.04em;
            padding:.1rem .5rem;border:1px solid var(--line);border-radius:var(--radius-pill);color:var(--soft);background:var(--surface)}
            .us-badge-live{color:var(--accent);border-color:var(--accent)}
            .us-range{color:var(--soft);font-size:.8rem;margin:.15rem 0 .65rem}
            .us-subtitle{font-size:.9rem;margin:1.1rem 0 .1rem}
            .us-tiles{grid-template-columns:repeat(3,1fr)}
            .cat-info{border-left-color:var(--accent)}.cat-info .dot{background:var(--accent)}
            .cat-ep-plain{color:var(--soft);font-size:.76rem;margin-left:auto}
            .cat-num{margin-top:.3rem;font-size:1.5rem;font-weight:700;line-height:1.15;font-variant-numeric:tabular-nums}
            .cat-sub{color:var(--soft);font-size:.78rem;margin-top:.15rem;font-variant-numeric:tabular-nums}
            .us-rates{display:grid;grid-template-columns:repeat(3,1fr);gap:.8rem;margin-top:.8rem}
            .us-rate{border:1px solid var(--line);border-radius:var(--radius);padding:.6rem .8rem}
            .us-rate-val{font-size:1.15rem;font-weight:700;color:var(--accent);line-height:1.2;font-variant-numeric:tabular-nums}
            .us-rate-lbl{font-weight:650;font-size:.9rem}
            .us-rate-sub{color:var(--soft);font-size:.78rem;margin-top:.15rem;font-variant-numeric:tabular-nums}
            .dev{list-style:none;padding:0;margin:.45rem 0 0}
            .dev-row{display:grid;grid-template-columns:7rem 1fr 11rem;gap:.15rem .7rem;align-items:center;padding:.32rem 0;border-top:1px solid var(--line)}
            .dev-row:first-child{border-top:0}
            .dev-lbl{font-size:.85rem;font-weight:650}
            .dev-val{font-size:.85rem;color:var(--soft);text-align:right;font-variant-numeric:tabular-nums}
            .dev-val b{color:var(--ink)}
            .dev-bar{display:block;height:.6rem;border-radius:var(--radius-pill);background:var(--track);border:1px solid var(--line);overflow:hidden}
            .dev-fill{display:block;height:100%;border-radius:var(--radius-pill);background:var(--soft)}
            .dev-fill-mobile{background:var(--accent)}.dev-fill-tablet{background:var(--bar-tablet)}
            .dev-fill-desktop{background:var(--soft)}.dev-fill-unknown{background:var(--bar-unknown)}
            .us-thin{color:var(--soft);font-size:.85rem;border:1px dashed var(--line);border-radius:var(--radius);padding:.55rem .8rem;margin:.45rem 0 0}
            .us-scroll{overflow-x:auto;margin:.4rem 0 0;border:1px solid var(--line);border-radius:var(--radius)}
            .us-scroll:focus-visible{outline:.15rem solid var(--focus);outline-offset:.15rem}
            .us-months{width:100%;border-collapse:collapse;font-size:.83rem;font-variant-numeric:tabular-nums}
            .us-months th,.us-months td{padding:.36rem .7rem;text-align:right;border-bottom:1px solid var(--line)}
            .us-months thead th{color:var(--soft);font-weight:650;font-size:.74rem;text-transform:uppercase;letter-spacing:.03em;white-space:nowrap}
            .us-months th[scope=row]{text-align:left;font-weight:400}.us-months thead th:first-child{text-align:left}
            .us-months tbody tr:nth-child(even){background:var(--row-alt)}
            .us-months .us-cur th[scope=row]{font-weight:700}.us-months .us-cur td{font-weight:650}
            .us-months .us-nd{color:var(--soft)}
            .us-months tfoot th,.us-months tfoot td{font-weight:700;border-bottom:0;border-top:2px solid var(--line)}
            .us-empty{color:var(--soft);font-size:.88rem;border:1px dashed var(--line);border-radius:var(--radius);padding:.7rem .9rem;margin:.3rem 0 0}
            .us-warn{font-size:.85rem;color:var(--ink);border:1px solid var(--line);border-left:4px solid var(--warn);
            border-radius:var(--radius-sm);padding:.5rem .75rem;margin:0 0 .7rem}
            .sr{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip-path:inset(50%);white-space:nowrap;border:0}
            /* REQ-038 status page tabs and statistics — classes per §11.2/§16, colours only via the tokens in :root, em/rem, mobile-first */
            .tabs{margin:1rem 0 0}
            .tabs ul{display:flex;flex-wrap:wrap;gap:0 .25rem;list-style:none;margin:0;padding:0;border-bottom:.125rem solid var(--line)}
            .tabs li{display:flex;flex:1 1 0;min-width:0}
            .tabs a{display:inline-flex;flex:1;align-items:center;justify-content:center;min-height:2.75em;min-width:2.75em;padding:.35rem 1rem;
            margin-bottom:-.125rem;color:var(--soft);font-weight:500;text-decoration:none;border-bottom:.2em solid transparent}
            .tabs a:hover{color:var(--ink);border-bottom-color:var(--line)}
            .tabs a[aria-current=page]{color:var(--ink);font-weight:700;border-bottom-color:var(--accent)}
            .tabs a:focus-visible{outline:.15rem solid var(--focus);outline-offset:-.15rem}
            .st-toc{display:flex;flex-wrap:wrap;align-items:center;gap:0 .3rem;margin:.5rem 0 0;font-size:.85rem}
            .st-toc-hd{margin:0 .2rem 0 0;color:var(--soft);font-weight:650}
            .st-toc ul{display:flex;flex-wrap:wrap;list-style:none;margin:0;padding:0}
            .st-toc a{display:inline-flex;align-items:center;min-height:2.75rem;padding:0 .45rem;color:var(--accent);text-underline-offset:.15em}
            .st-toc a:focus-visible{outline:.15rem solid var(--focus);outline-offset:0}
            .st-notfound ul{list-style:none;padding:0;margin:.4rem 0 0}
            .st-notfound a{display:inline-flex;align-items:center;min-height:2.75rem;color:var(--accent);text-underline-offset:.15em}
            .st-notfound a:focus-visible{outline:.15rem solid var(--focus);outline-offset:.15rem}
            .cap,.rk,.sg,.ev,.tm,.fn{min-width:0}
            .st-intro{color:var(--soft);font-size:.85rem;margin:.4rem 0 0}
            .st-range{color:var(--soft);font-size:.8rem;margin:.15rem 0 .65rem}
            .st-sub{font-size:.95rem;font-weight:650;margin:1.4rem 0 .1rem}
            .st-note{color:var(--soft);font-size:.8rem;margin:.55rem 0 0}.st-note b{color:var(--ink)}
            .st-empty{color:var(--soft);font-size:.88rem;border:.0625rem dashed var(--line);border-radius:var(--radius);padding:.7rem .9rem;margin:.3rem 0 0}
            .st-empty-dash{color:var(--ink);font-weight:700;margin-right:.35rem}
            .st-thin{color:var(--soft);font-size:.8rem;margin:.35rem 0 0}
            .st-warn{font-size:.85rem;color:var(--ink);border:.0625rem solid var(--line);
            border-left:.25rem solid var(--warn);border-radius:var(--radius-sm);padding:.5rem .75rem;margin:.6rem 0 .7rem}
            .st-ico{display:inline-flex;align-items:center;justify-content:center;flex:none;width:1.9rem;height:1.9rem;
            border:.0625rem solid var(--line);border-radius:var(--radius);background:var(--surface);color:var(--accent)}
            .st-ico svg{width:1.1rem;height:1.1rem}
            .st-scroll{position:relative;overflow-x:auto;margin:.4rem 0 0;border:.0625rem solid var(--line);border-radius:var(--radius)}
            .st-scroll:focus-visible{outline:.15rem solid var(--focus);outline-offset:.15rem}
            .st-table{width:100%;border-collapse:collapse;font-size:.83rem;font-variant-numeric:tabular-nums}
            .st-table caption{text-align:left}
            .st-table th,.st-table td{padding:.4rem .5rem;text-align:right;border-bottom:.0625rem solid var(--line);vertical-align:middle}
            .st-table thead th{color:var(--soft);font-weight:650;font-size:.76rem;vertical-align:bottom}
            .st-table th[scope=row]{text-align:left;font-weight:600}
            .st-table .st-l{text-align:left}
            .st-table:not(.st-heat) tbody tr:nth-child(even){background:var(--row-alt)}
            .st-table tfoot th,.st-table tfoot td{font-weight:700;border-bottom:0;border-top:.125rem solid var(--line)}
            .st-rank{color:var(--soft);width:2.5rem}
            .cap-table th[scope=row],.rk-table th[scope=row],.sg-matrix th[scope=row]{min-width:7rem}
            .st-scroll th[scope=row]{position:sticky;left:0;z-index:1;background:var(--surface)}
            .st-scroll .st-table:not(.st-heat) tbody tr:nth-child(even) th[scope=row]{background:var(--row-alt)}
            .st-nd,.st-sup,.st-ka{color:var(--soft);white-space:nowrap}
            .st-small{display:block;color:var(--soft);font-size:.74rem;font-weight:400;text-transform:none;letter-spacing:0}
            .st-nw{white-space:nowrap}
            .st-heat td,.st-heat th{border:.125rem solid var(--surface)}
            .st-heat td{color:var(--ink);min-width:3.2rem}
            .st-heat-1{background:var(--heat-1)}.st-heat-2{background:var(--heat-2)}.st-heat-3{background:var(--heat-3)}.st-heat-4{background:var(--heat-4)}
            .st-legend{display:flex;flex-wrap:wrap;gap:.25rem .9rem;list-style:none;padding:0;margin:.5rem 0 0;font-size:.78rem;color:var(--soft)}
            .st-legend li{display:inline-flex;flex-wrap:wrap;align-items:center;gap:.15rem .35rem;min-width:0}
            .st-sw{display:inline-block;width:1.1em;height:1.1em;border:.0625rem solid var(--line);border-radius:var(--radius-sm)}
            .st-sw-txt{display:inline-flex;align-items:center;justify-content:center;min-width:2.4em;height:1.3em;padding:0 .2em;
            border:.0625rem solid var(--line);border-radius:var(--radius-sm);background:var(--surface);color:var(--soft);font-size:.72rem}
            .st-fill{background:var(--accent)}
            .st-grid{display:grid;grid-template-columns:minmax(0,1fr);gap:0 1.4rem}.st-grid > div{min-width:0}
            .ib{border-left:.25rem solid var(--accent)}
            .ib-grid{display:grid;grid-template-columns:minmax(0,1fr);gap:.8rem;margin-top:.8rem}
            .ib-tile{min-width:0;border:.0625rem solid var(--line);border-left:.25rem solid var(--accent);border-radius:var(--radius);padding:.7rem .9rem}
            .ib-free{border-left-color:var(--ok)}.ib-tight{border-left-color:var(--warn)}
            .ib-hd{display:flex;align-items:center;flex-wrap:wrap;gap:.55rem}
            .ib-name{font-weight:650;margin:0;font-size:.95rem}
            .ib-src{color:var(--soft);font-size:.74rem;margin-left:auto}
            .ib-val{margin:.45rem 0 0;font-size:1.05rem;font-weight:700}
            .ib-num{font-size:1.5rem;line-height:1.15;font-variant-numeric:tabular-nums}
            .ib-sub{color:var(--soft);font-size:.8rem;margin:.25rem 0 0}
            .ib-list{list-style:none;padding:0;margin:.45rem 0 0}
            .ib-list li{display:flex;justify-content:space-between;flex-wrap:wrap;gap:0 .5rem;font-size:.86rem;padding:.14rem 0;
            border-top:.0625rem solid var(--line);font-variant-numeric:tabular-nums}
            .ib-list li:first-child{border-top:0}.ib-list span:last-child{color:var(--soft)}.ib-list span:first-child{color:var(--ink)}
            .cap-table{min-width:62rem}
            .cap-pill{display:inline-block;font-size:.76rem;font-weight:700;padding:.08rem .5rem;border:.0625rem solid var(--warn);
            border-radius:var(--radius-pill);background:var(--cap-tight);color:var(--ink);white-space:nowrap}
            .cap-pill-free{background:var(--cap-free);border-color:var(--ok)}
            .cap-pill-tight{background:var(--cap-booked);border-color:var(--bad)}
            .cap-pill-na{background:var(--surface);border-color:var(--soft);border-style:dashed;color:var(--soft);font-weight:650}
            .cap-when{display:block;color:var(--soft);font-size:.76rem;margin-top:.12rem}
            .cap-total{font-weight:700;font-size:.95rem}
            .cap-day{text-align:center;font-size:.72rem;color:var(--ink);min-width:2.9rem;padding:.4rem .3rem}
            .cap-day-free{background:var(--cap-free)}.cap-day-tight{background:var(--cap-tight)}.cap-day-booked{background:var(--cap-booked)}
            .cap-day-na{color:var(--soft)}
            .cap-sum{font-variant-numeric:tabular-nums;white-space:nowrap}
            .cap-class{font-weight:650;white-space:nowrap}
            .rk-table{min-width:52rem}
            .rk-cell{display:grid;grid-template-columns:minmax(3rem,1fr) 2.2rem;gap:.45rem;align-items:center;min-width:7.5rem}
            .rk-bar{display:block;height:.6rem;background:var(--track);border:.0625rem solid var(--line);border-radius:var(--radius-pill);overflow:hidden}
            .rk-fill{display:block;height:100%;background:var(--accent);border-radius:var(--radius-pill)}
            .rk-prev{display:block;color:var(--soft);font-size:.72rem}
            .rk-low th[scope=rowgroup]{text-align:left;color:var(--ink);font-size:.8rem;font-weight:650;background:var(--surface);padding-top:.8rem}
            .sg-matrix{min-width:46rem}
            .sg-matrix th,.sg-matrix td{padding:.35rem .35rem}
            .sg-matrix thead th{font-size:.72rem}
            .sg-matrix.st-heat td{min-width:3rem}
            .sg-other th[scope=row]{font-style:italic}
            .tm-kpis{display:grid;grid-template-columns:minmax(0,1fr);gap:.8rem;margin-top:.6rem}
            .tm-kpi{border:.0625rem solid var(--line);border-radius:var(--radius);padding:.6rem .8rem;min-width:0}
            .tm-kpi-val{font-size:1.5rem;font-weight:700;line-height:1.15;color:var(--accent);font-variant-numeric:tabular-nums}
            .tm-kpi-lbl{font-weight:650;font-size:.9rem}
            .tm-kpi-sub{color:var(--soft);font-size:.78rem;margin-top:.15rem;font-variant-numeric:tabular-nums}
            .tm-slots{min-width:25rem}
            .fn-steps{list-style:none;padding:0;margin:.6rem 0 0}
            .fn-step{display:grid;grid-template-columns:minmax(0,1fr);gap:.2rem .8rem;align-items:center;padding:.45rem 0;border-top:.0625rem solid var(--line)}
            .fn-step:first-child{border-top:0}
            .fn-lbl{font-weight:650;font-size:.88rem}.fn-lbl small{display:block;font-weight:400;color:var(--soft);font-size:.76rem}
            .fn-bar{display:flex;justify-content:center;height:1rem;background:var(--track);border:.0625rem solid var(--line);border-radius:var(--radius-sm);overflow:hidden}
            .fn-fill{display:block;height:100%;background:var(--accent)}
            .fn-val{font-size:.85rem;color:var(--soft);font-variant-numeric:tabular-nums}.fn-val b{color:var(--ink)}
            .fn-prev{display:block;font-size:.76rem}
            .fn-pct{display:block;color:var(--soft);font-size:.74rem;font-weight:400}
            .fn-table{min-width:34rem}
            .fn-table th[scope=row]{min-width:9rem;max-width:16rem}
            .fn-dropoff{background:transparent;border:0;border-top:.0625rem solid var(--line);border-radius:0;padding:1rem 0 0;margin:1.4rem 0 0}
            .fn-dropoff h3{margin-top:0}
            .fn-dropoff-table{min-width:56rem}
            .fn-dropoff-table td.st-l,.fn-dropoff-table th.st-l{max-width:18rem}
            .fn-dropoff-hint{font-size:.78rem;color:var(--soft);font-weight:400}
            .fn-dropoff-dev{min-width:34rem}
            .fn-dropoff-sum th,.fn-dropoff-sum td{font-weight:700;border-top:.125rem solid var(--line)}
            .st-limits{border-left:.25rem solid var(--soft)}
            .st-limits dl{display:grid;grid-template-columns:minmax(0,1fr);gap:.1rem .9rem;margin:.4rem 0 0;font-size:.85rem}
            .st-limits dt{font-weight:650}
            .st-limits dd{margin:0 0 .35rem;color:var(--soft);font-variant-numeric:tabular-nums}
            .st-limits ol,.st-limits ul{padding-left:1.25rem;margin:.4rem 0 0;font-size:.85rem}
            .st-limits li{margin:.2rem 0}
            @media(min-width:48em){.tabs li{flex:none}.tabs a{justify-content:flex-start}.ib-grid{grid-template-columns:repeat(2,minmax(0,1fr))}
            .tm-kpis{grid-template-columns:repeat(2,minmax(0,1fr))}.fn-step{grid-template-columns:12.5rem minmax(0,1fr) 13.5rem}.fn-val{text-align:right}
            .dev-wide{grid-template-columns:12rem 1fr 9rem}.cap-table th[scope=row],.rk-table th[scope=row],.sg-matrix th[scope=row]{min-width:9.5rem}
            .st-limits dl{grid-template-columns:auto minmax(0,1fr)}.st-limits dd{margin:0}}
            @media(min-width:64em){.st-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
            @media(max-width:47.99em){.dev-row{grid-template-columns:1fr auto}.dev-lbl{grid-column:1;grid-row:1}.dev-val{grid-column:2;grid-row:1}.dev-bar{grid-column:1/-1;grid-row:2}}
            @media(max-width:40em){.row{grid-template-columns:auto 1fr}.cats{grid-template-columns:1fr}.us-tiles{grid-template-columns:1fr}.us-rates{grid-template-columns:1fr}.us-months .opt{display:none}.st-table .opt{display:none}}
            </style>
            """;
    }
}
