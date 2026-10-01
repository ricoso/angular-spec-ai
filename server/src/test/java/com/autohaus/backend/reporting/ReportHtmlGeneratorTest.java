package com.autohaus.backend.reporting;

import com.autohaus.backend.capacity.CapacitySectionRenderer;
import com.autohaus.backend.capacity.CapacityViewModel;
import com.autohaus.backend.capacity.ScanState;
import com.autohaus.backend.common.html.HtmlText;
import com.autohaus.backend.usage.UsageSectionRenderer;
import com.autohaus.backend.usage.UsageStats;
import com.autohaus.backend.usage.insights.BranchRankingSectionRenderer;
import com.autohaus.backend.usage.insights.FunnelSectionRenderer;
import com.autohaus.backend.usage.insights.InsightsViewModel;
import com.autohaus.backend.usage.insights.ServiceGroupSectionRenderer;
import com.autohaus.backend.usage.insights.TimingSectionRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ReportHtmlGeneratorTest {

    private final ReportHtmlGenerator generator = new ReportHtmlGenerator();

    private ReportModel sampleModel(String requestBody) {
        BookingStats appointments = new BookingStats(100, 80, 20);
        BookingStats snaps = new BookingStats(200, 130, 70);
        ErrorEntry error = new ErrorEntry(
            Instant.parse("2026-07-09T02:10:00Z"),
            "trace-123",
            "volkswagen",
            "Volkswagen Essen-Kray",
            List.of("Inspektion", "Ölwechsel"),
            "No match (Operation/Marke)",
            "/api/planning/snaps",
            requestBody,
            "{\"code\":\"InternalServerError\"}");
        AiErrorReportSummary summary = new AiErrorReportSummary(
            "Alles im grünen Bereich",
            "Die Buchungsstrecke ist stabil.",
            List.of(new AiErrorReportSummary.PrioritizedIssue(
                "VW No-match", "high", "volkswagen", "Essen-Kray", "SoftNet-Katalog")),
            List.of("SoftNet-Ticket eröffnen"));
        return new ReportModel(Instant.parse("2026-07-09T03:00:00Z"), snaps, appointments,
            Map.of("volkswagen", 3L), Map.of("Volkswagen Essen-Kray", 3L), List.of(error), summary);
    }

    @Test
    void rendersSummaryStatsAndErrorFields() {
        String html = generator.generate(sampleModel("{\"brand\":\"volkswagen\"}"));
        assertTrue(html.contains("Alles im grünen Bereich"));
        assertTrue(html.contains("VW No-match"));
        assertTrue(html.contains("SoftNet-Ticket eröffnen"));
        assertTrue(html.contains("volkswagen"));
        assertTrue(html.contains("Volkswagen Essen-Kray"));
        assertTrue(html.contains("Inspektion, Ölwechsel"));
        assertTrue(html.contains("100"));
        assertTrue(html.contains("80"));
        assertTrue(html.contains("20"));
        assertTrue(html.contains("trace-123"));
        // Iteration 2: category status overview + PII note
        assertTrue(html.contains("Status-Übersicht"));
        assertTrue(html.contains("Terminsuchen"));
        assertTrue(html.contains("Buchungen"));
        assertTrue(html.contains("Marken"));
        assertTrue(html.contains("Standorte"));
        assertTrue(html.contains("Payloads PII-maskiert"));
    }

    @Test
    void rendersErrorFilterBarWithCountsAndDataAttributes() {
        String html = generator.generate(sampleModel("{\"brand\":\"volkswagen\"}"));
        // filter bar + radios present
        assertTrue(html.contains("class=\"filters\""), "filter bar must be rendered");
        assertTrue(html.contains("id=\"flt-all\""));
        assertTrue(html.contains("Alle (1)"));
        assertTrue(html.contains("id=\"flt-ep-snaps\""));
        assertTrue(html.contains("Terminsuchen (1)"));
        // cards carry the filterable endpoint attribute
        assertTrue(html.contains("data-ep=\"snaps\""));
        // no appointment filter button (sample has no appointment error)
        assertFalse(html.contains("id=\"flt-ep-appointments\""));
        // no error-type filter buttons (filter by endpoint only)
        assertFalse(html.contains("id=\"flt-ty-"));
    }

    @Test
    void escapesHtmlInPayloads() {
        String html = generator.generate(sampleModel("{\"note\":\"<script>alert(1)</script>\"}"));
        assertTrue(html.contains("&lt;script&gt;"), "payload angle brackets must be escaped");
        assertFalse(html.contains("<script>alert(1)</script>"), "raw script tag must not appear");
    }

    @Test
    @DisplayName("TC-14 / REQ-038 §9: esc stays public and delegates to the one implementation in common.html")
    void escDelegatesToTheNeutralImplementation() {
        for (String value : Arrays.asList(null, "", "plain", "<b>X</b> & \"Y\" 'Z'", "Räderwechsel · 1–4")) {
            assertEquals(HtmlText.esc(value), ReportHtmlGenerator.esc(value), String.valueOf(value));
        }
        assertEquals("&lt;b&gt;&amp;&quot;&#39;", ReportHtmlGenerator.esc("<b>&\"'"));
    }

    @Test
    void rendersFallbackSummaryAndEmptyErrorList() {
        ReportModel model = new ReportModel(
            Instant.parse("2026-07-09T03:00:00Z"),
            new BookingStats(0, 0, 0),
            new BookingStats(0, 0, 0),
            Map.of(),
            Map.of(),
            List.of(),
            AiErrorReportSummary.unavailable());
        String html = generator.generate(model);
        assertTrue(html.contains("KI-Zusammenfassung nicht verfügbar"));
        assertTrue(html.contains("Keine Fehler im Auswertungszeitraum."));
    }

    @Test
    void toleratesNullSummary() {
        ReportModel model = new ReportModel(
            Instant.parse("2026-07-09T03:00:00Z"), new BookingStats(1, 1, 0), new BookingStats(1, 1, 0),
            Map.of(), Map.of(), List.of(), null);
        String html = generator.generate(model);
        assertNotNull(html);
        assertTrue(html.contains("<!doctype html>"));
    }

    // -------------------------------------------------------------------------------------
    // REQ-037 §14.2: page frame and report fragment are separate / Seitenrahmen und Fragment
    // sind getrennt. The store holds a fragment, renderPage(...) adds the frame — otherwise a
    // full document would end up nested inside the page (TC-17).
    // -------------------------------------------------------------------------------------

    @Test
    void reportFragmentCarriesNoDocumentFrame() {
        ReportModel model = new ReportModel(
            Instant.parse("2026-07-09T03:00:00Z"),
            new BookingStats(0, 0, 0), new BookingStats(0, 0, 0),
            Map.of(), Map.of(), List.of(), AiErrorReportSummary.unavailable());

        String fragment = generator.reportFragment(model);

        assertFalse(fragment.contains("<!doctype"), "a fragment must not open a document");
        assertFalse(fragment.contains("<html"), "a fragment must not open <html>");
        assertFalse(fragment.contains("<style"), "the page stylesheet belongs to renderPage(...)");
        assertTrue(fragment.startsWith("<section class=\"ai\""), "the fragment starts with the AI section");
    }

    @Test
    void reportFragmentWithErrorsStillCarriesNoDocumentFrame() {
        String fragment = generator.reportFragment(sampleModel("{\"brand\":\"volkswagen\"}"));

        assertFalse(fragment.contains("<!doctype"));
        assertFalse(fragment.contains("<html"));
        // The only <style> a fragment may carry is the per-report CSS-only error filter kept from
        // REQ-030 (one selector per filter button); the page stylesheet is never part of it. /
        // Nur der Filter-Style des Fehler-Reports, nie das Seiten-CSS.
        assertFalse(fragment.contains(":root{--ink:"), "the page stylesheet must stay in renderPage(...)");
    }

    @Test
    void renderPagePlacesUsageStatisticsBeforeTheAiSection() {
        String fragment = generator.reportFragment(sampleModel("{\"brand\":\"volkswagen\"}"));

        String html = generator.renderPage(com.autohaus.backend.usage.UsageStats.unavailable(), fragment);

        int header = html.indexOf("<header class=\"hd\">");
        int usage = html.indexOf("class=\"us\"");
        int ai = html.indexOf("<section class=\"ai\"");
        assertTrue(header > -1, "page header must be rendered");
        assertTrue(usage > -1, "AC-2: the usage section must be rendered");
        assertTrue(ai > -1, "the report fragment must be embedded");
        assertTrue(header < usage, "AC-2: the statistics follow the page header");
        assertTrue(usage < ai, "AC-2: the statistics precede the KI-Zusammenfassung");
        assertTrue(html.startsWith("<!doctype html>"), "renderPage(...) still produces a full document");
    }

    @Test
    void renderPageDropsTheUsageSectionWhenTheFeatureIsOff() {
        String html = generator.renderPage(null, "<section class=\"ai\">x</section>");

        assertFalse(html.contains("class=\"us\""), "AC-22: the section is dropped");
        assertFalse(html.contains("nicht verf\u00fcgbar"), "AC-22: without any replacement hint");
        assertTrue(html.contains("<section class=\"ai\">x</section>"), "the fragment is still embedded");
    }

    // =========================================================================================
    // REQ-038 TC-12 — shared frame, tab navigation, constructors and CSS / Rahmen, Navigation,
    // Konstruktoren und CSS
    // =========================================================================================

    /** TC-12: frame and navigation of both tabs (BR-2, AC-2, AC-4). */
    @Nested
    class Frame {

        @Test
        @DisplayName("TC-12: statistics frame — title, one H1, nav with aria-current on 'Statistik', main")
        void rendersTheStatisticsFrame() {
            String html = generator.renderFrame(ReportTab.STATISTICS, true, "Stand: x", "<p>body</p>");

            assertTrue(html.startsWith("<!doctype html><html lang=\"de\">"));
            assertTrue(html.contains("<title>Statusseite – Statistik</title>"));
            assertEquals(1, count(html, "<h1>"), "exactly one h1");
            assertTrue(html.contains("<header class=\"hd\"><h1>Statusseite</h1><p class=\"meta\">Stand: x</p></header>"));
            assertTrue(html.contains("<nav class=\"tabs\" aria-label=\"Bereiche der Statusseite\"><ul>"
                + "<li><a href=\"/internal/report/statistics\" aria-current=\"page\">Statistik</a></li>"
                + "<li><a href=\"/internal/report/bugs\">Bugs</a></li></ul></nav>"));
            assertTrue(html.contains("</nav><main id=\"content\"><p>body</p></main>"
                + "<footer class=\"ft\">Automatisch generiert &middot; REQ-030 &middot; gs-terminbuchungs-app</footer>"));
            assertEquals(1, count(html, "aria-current=\"page\""), "only the active tab carries aria-current");
            assertFalse(html.contains("role=\"tab"), "BR-2: no role=tab*");
            assertFalse(html.contains("<script"), "BR-2: no script");
            assertFalse(html.contains("<form"), "BR-2: no form");
            assertFalse(html.contains("<img"), "CSP img-src 'none': no image");
        }

        @Test
        @DisplayName("TC-12: bugs frame — aria-current moves to 'Bugs'")
        void rendersTheBugsFrame() {
            String html = generator.renderFrame(ReportTab.BUGS, true, null, "");

            assertTrue(html.contains("<title>Statusseite – Bugs</title>"));
            assertTrue(html.contains("<li><a href=\"/internal/report/statistics\">Statistik</a></li>"
                + "<li><a href=\"/internal/report/bugs\" aria-current=\"page\">Bugs</a></li>"));
            assertFalse(html.contains("<p class=\"meta\">"), "a missing meta line is omitted");
        }

        @Test
        @DisplayName("BR-4: without navigation there is no tab bar at all; null tab = bugs")
        void dropsTheNavigationOnRequest() {
            String html = generator.renderFrame(null, false, "m", null);

            assertFalse(html.contains("<nav class=\"tabs\""));
            assertFalse(html.contains("aria-current=\"page\""));
            assertTrue(html.contains("<title>Statusseite – Bugs</title>"));
            assertTrue(html.contains("</header><main id=\"content\"></main>"));
        }

        @Test
        @DisplayName("AC-3 / BR-3: the bugs tab embeds the fragment byte-identical, incl. the filter style")
        void embedsTheFragmentByteIdentical() {
            String fragment = generator.reportFragment(sampleModel("{\"brand\":\"volkswagen\"}"));

            String html = generator.renderBugsPage(fragment, true);

            assertTrue(html.contains("<main id=\"content\">" + fragment + "</main>"), "fragment unchanged");
            assertTrue(html.contains("Fehler-Auswertung: letzte 24 Stunden"));
            assertTrue(html.contains("<p class=\"meta\">Stand: "));
            assertTrue(html.contains("aria-current=\"page\">Bugs</a>"));
            assertFalse(html.contains("class=\"st-toc\""), "no statistics jump links on the bugs tab");
            assertTrue(generator.renderBugsPage(null, false).contains("<main id=\"content\"></main>"));
        }

        @Test
        @DisplayName("TC-6 / AC-5: 404 page — title, heading, links to the existing tabs, no nav")
        void rendersTheNotFoundPage() {
            String withStatistics = generator.renderNotFoundPage(true);
            String bugsOnly = generator.renderNotFoundPage(false);

            assertTrue(withStatistics.contains("<title>Statusseite – Seite nicht gefunden</title>"));
            assertTrue(withStatistics.contains("<main id=\"content\"><section class=\"st-notfound\" "
                + "aria-labelledby=\"notfound-h\"><h2 id=\"notfound-h\">Seite nicht gefunden</h2>"
                + "<p>Diese Adresse gibt es auf der Statusseite nicht.</p><ul>"
                + "<li><a href=\"/internal/report/statistics\">Zur Statistik</a></li>"
                + "<li><a href=\"/internal/report/bugs\">Zu den Bugs</a></li></ul></section></main>"));
            assertFalse(withStatistics.contains("<nav class=\"tabs\""), "the 404 page has no tab bar");
            assertFalse(bugsOnly.contains("Zur Statistik"), "AC-6: no link to a tab that does not exist");
            assertTrue(bugsOnly.contains("Zu den Bugs"));
        }

        @Test
        @DisplayName("§12.2: the statistics page carries toc, S7 and no error-report section")
        void rendersTheStatisticsPage() {
            String html = generator.renderStatisticsPage(
                new StatisticsPage(UsageStats.unavailable(), null, null, null));

            assertTrue(html.contains("<title>Statusseite – Statistik</title>"));
            assertTrue(html.contains("<main id=\"content\"><nav class=\"st-toc\""));
            assertTrue(html.contains("<section class=\"us\""));
            assertTrue(html.contains("id=\"limits\""));
            assertFalse(html.contains("class=\"ai\""), "BR-3: no error report on the statistics tab");
            assertTrue(generator.renderStatisticsPage(null).contains("id=\"limits\""), "null page: S7 only");
        }

        @Test
        @DisplayName("§14.2: renderPage(...) keeps its composition but loses the tab bar")
        void renderPageHasNoNavigation() {
            String html = generator.renderPage(UsageStats.unavailable(), "<section class=\"ai\">x</section>");

            assertFalse(html.contains("<nav class=\"tabs\""), "the legacy path has no navigation");
            assertTrue(html.contains("<h1>Statusseite</h1>"));
            assertTrue(html.indexOf("class=\"us\"") < html.indexOf("<section class=\"ai\">x</section>"));
        }
    }

    /** §17 META_STATISTICS, META_SCAN_RUNNING, META_SCAN_FAILED — meta line of the statistics tab. */
    @Nested
    class MetaLine {

        private final Instant now = Instant.parse("2026-10-23T07:12:00Z");

        @Test
        @DisplayName("§17 META_STATISTICS: Stand · Auswertung bis gestern (…) · Kapazitäts-Scan vom …")
        void namesEvaluationDayAndScanDay() {
            String meta = ReportHtmlGenerator.statisticsMetaLine(new StatisticsPage(null,
                insights(LocalDate.of(2026, 10, 22)), capacity(false, LocalDate.of(2026, 10, 22), null,
                    List.of()), null), now);

            assertEquals("Stand: 23.10.2026 09:12 Uhr &middot; Auswertung bis gestern (22.10.2026)"
                + " &middot; Kapazitäts-Scan vom 22.10.2026", meta);
        }

        @Test
        @DisplayName("§5.1 row 1: usage only -> just 'Stand'")
        void namesOnlyTheTimestampWithoutNewSources() {
            assertEquals("Stand: 23.10.2026 09:12 Uhr", ReportHtmlGenerator.statisticsMetaLine(
                new StatisticsPage(UsageStats.unavailable(), null, null, null), now));
        }

        @Test
        @DisplayName("TC-55 / §17 META_SCAN_RUNNING: first scan in progress")
        void namesTheRunningFirstScan() {
            String meta = ReportHtmlGenerator.statisticsMetaLine(new StatisticsPage(null, null,
                capacity(false, null, Instant.parse("2026-10-22T20:31:00Z"), List.of()), null), now);

            assertTrue(meta.endsWith("Kapazitäts-Scan: erster Lauf aktiv (seit 22:31 Uhr)"), meta);
        }

        @Test
        @DisplayName("TC-55: a running scan with a previous scan keeps showing that scan's day")
        void keepsThePreviousScanWhileRunning() {
            String meta = ReportHtmlGenerator.statisticsMetaLine(new StatisticsPage(null, null,
                capacity(false, LocalDate.of(2026, 10, 21), Instant.parse("2026-10-22T20:31:00Z"),
                    List.of()), null), now);

            assertTrue(meta.endsWith("Kapazitäts-Scan vom 21.10.2026"), meta);
            assertFalse(meta.contains("erster Lauf"));
        }

        @Test
        @DisplayName("AC-10 / §17 META_SCAN_FAILED: failed scan day and the shown fallback")
        void namesTheFailedScanAndTheFallback() {
            String meta = ReportHtmlGenerator.statisticsMetaLine(new StatisticsPage(null, null,
                capacity(true, LocalDate.of(2026, 10, 21), null,
                    List.of(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 22))), null), now);

            assertTrue(meta.endsWith("Kapazitäts-Scan vom 22.10.2026 fehlgeschlagen – angezeigt: "
                + "Scan vom 21.10.2026"), meta);
        }

        @Test
        @DisplayName("§17 META_STATISTICS / §11.1: the scan part names the start time of the shown scan")
        void namesTheScanStartTime() {
            CapacityViewModel timed = new CapacityViewModel(LocalDate.of(2026, 10, 22),
                LocalDate.of(2026, 10, 23), ScanState.COMPLETE, false, null, null, null, List.of(), List.of(),
                List.of(), Instant.parse("2026-10-22T20:31:12Z"), Instant.parse("2026-10-22T20:35:40Z"),
                LocalDate.of(2026, 10, 22));

            String meta = ReportHtmlGenerator.statisticsMetaLine(new StatisticsPage(null,
                insights(LocalDate.of(2026, 10, 22)), timed, null), now);

            assertEquals("Stand: 23.10.2026 09:12 Uhr &middot; Auswertung bis gestern (22.10.2026)"
                + " &middot; Kapazitäts-Scan vom 22.10.2026, 22:31 Uhr", meta);
        }

        @Test
        @DisplayName("AC-10 / §17 META_SCAN_FAILED: the failed day comes from latestScanDay (a Sunday is no grid day)")
        void namesAFailedSundayScanFromTheViewModel() {
            CapacityViewModel sunday = new CapacityViewModel(LocalDate.of(2026, 10, 24),
                LocalDate.of(2026, 10, 26), ScanState.FAILED, true, ScanState.COMPLETE, null, null,
                List.of(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 24)), List.of(), List.of(),
                Instant.parse("2026-10-24T20:31:00Z"), Instant.parse("2026-10-24T20:34:00Z"),
                LocalDate.of(2026, 10, 25));

            String meta = ReportHtmlGenerator.statisticsMetaLine(new StatisticsPage(null, null, sunday, null), now);

            assertTrue(meta.endsWith("Kapazitäts-Scan vom 25.10.2026 fehlgeschlagen – angezeigt: "
                + "Scan vom 24.10.2026"), meta);
        }

        @Test
        @DisplayName("AC-10: fallback without a newer grid day -> undated variant")
        void namesTheFallbackWithoutTheFailedDay() {
            String meta = ReportHtmlGenerator.statisticsMetaLine(new StatisticsPage(null, null,
                capacity(true, LocalDate.of(2026, 10, 21), null, null), null), now);

            assertTrue(meta.endsWith("Kapazitäts-Scan fehlgeschlagen – angezeigt: Scan vom 21.10.2026"), meta);
        }

        @Test
        @DisplayName("§5.2: no scan yet and none running -> no scan part")
        void omitsTheScanPartBeforeTheFirstScan() {
            assertEquals("Stand: 23.10.2026 09:12 Uhr", ReportHtmlGenerator.statisticsMetaLine(
                new StatisticsPage(null, null, capacity(false, null, null, List.of()), null), now));
        }

        @Test
        @DisplayName("§17 META_BUGS: byte-identical to the REQ-030 header text")
        void keepsTheBugsMetaLine() {
            assertEquals("Stand: 23.10.2026 09:12 Uhr &middot; Fehler-Auswertung: letzte 24 Stunden",
                ReportHtmlGenerator.bugsMetaLine(now));
        }

        private InsightsViewModel insights(LocalDate asOf) {
            return new InsightsViewModel(asOf, true, false, null, null, null, null, false, 0, null, null,
                null, null, null, null, null, List.of());
        }

        private CapacityViewModel capacity(boolean fallback, LocalDate scanDay, Instant runningSince,
                                           List<LocalDate> days) {
            return new CapacityViewModel(scanDay, scanDay == null ? null : scanDay.plusDays(1),
                fallback ? ScanState.FAILED : ScanState.COMPLETE, fallback,
                fallback ? ScanState.COMPLETE : null, runningSince, null, days, List.of(), List.of());
        }
    }

    /** TC-12: the three constructors and the Spring wiring (§14.2). */
    @Nested
    class Constructors {

        @Test
        @DisplayName("TC-12: new ReportHtmlGenerator() and the 1-argument constructor still render")
        void keepsTheLegacyConstructors() {
            String a = new ReportHtmlGenerator().renderPage(null, "<section class=\"ai\">x</section>");
            String b = new ReportHtmlGenerator(new UsageSectionRenderer())
                .renderStatisticsPage(new StatisticsPage(UsageStats.unavailable(), null, null, null));

            assertTrue(a.contains("<section class=\"ai\">x</section>"));
            assertTrue(b.contains("<section class=\"us\""));
        }

        @Test
        @DisplayName("TC-12: exactly one @Autowired constructor — the 2-argument one")
        void hasExactlyOneAutowiredConstructor() {
            List<Constructor<?>> autowired = Arrays.stream(ReportHtmlGenerator.class.getConstructors())
                .filter(c -> c.isAnnotationPresent(Autowired.class)).toList();

            assertEquals(3, ReportHtmlGenerator.class.getConstructors().length, "three public constructors");
            assertEquals(1, autowired.size(), "exactly one @Autowired constructor");
            assertArrayEquals(new Class<?>[]{UsageSectionRenderer.class, StatisticsPageRenderer.class},
                autowired.get(0).getParameterTypes());
        }

        @Test
        @DisplayName("TC-12: the Spring context uses the @Autowired constructor with the renderer bean")
        void springUsesTheTwoArgumentConstructor() throws Exception {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ReportHtmlGenerator.class, StatisticsPageRenderer.class, UsageSectionRenderer.class,
                BriefingSectionRenderer.class, CapacitySectionRenderer.class,
                BranchRankingSectionRenderer.class, ServiceGroupSectionRenderer.class,
                TimingSectionRenderer.class, FunnelSectionRenderer.class, LimitsSectionRenderer.class)) {

                ReportHtmlGenerator bean = context.getBean(ReportHtmlGenerator.class);
                Field field = ReportHtmlGenerator.class.getDeclaredField("statisticsRenderer");
                field.setAccessible(true);
                assertSame(context.getBean(StatisticsPageRenderer.class), field.get(bean),
                    "the context injects its own StatisticsPageRenderer bean");
            }
        }
    }

    /** TC-12, BR-22, BR-23, §11.3, §11.4 — the stylesheet contract / der CSS-Vertrag. */
    @Nested
    class Stylesheet {

        private final String css = stylesheet(generator.renderFrame(ReportTab.STATISTICS, true, null, ""));

        /** Position of the first REQ-038 rule / Beginn der neuen Regeln. */
        private final int newRules = css.indexOf("/* REQ-038 status page tabs and statistics");

        /** Position of the first existing media query / erste Bestands-Media-Query. */
        private final int existingQueries = css.indexOf("@media(max-width:47.99em)");

        @Test
        @DisplayName("§11.3: all seven tokens with their documented values in :root")
        void definesTheNewTokensInRoot() {
            String root = css.substring(css.indexOf(":root{"), css.indexOf('}', css.indexOf(":root{")));
            for (String token : List.of("--heat-1:#d8ecef", "--heat-2:#aed8df", "--heat-3:#7fbfca",
                "--heat-4:#4f9dab", "--cap-free:#dcefe4", "--cap-tight:#f6e7c8", "--cap-booked:#f4dcd6")) {
                assertTrue(root.contains(token), "missing token " + token);
            }
            assertTrue(root.contains("--radius:6px;--radius-sm:3px;--radius-pill:999px;"),
                "the existing tokens stay");
        }

        @Test
        @DisplayName("§11.4: every new rule incl. the new min-width queries precedes the existing queries")
        void placesTheNewRulesBeforeTheExistingQueries() {
            assertTrue(newRules > css.indexOf(".sr{"), "new rules after the existing rules");
            assertTrue(newRules < existingQueries, "new rules before @media(max-width:47.99em)");
            int tablet = css.indexOf("@media(min-width:48em)");
            int desktop = css.indexOf("@media(min-width:64em)");
            assertTrue(tablet > newRules && tablet < existingQueries, "tablet query placement");
            assertTrue(desktop > tablet && desktop < existingQueries, "desktop query placement");
            assertEquals(1, count(css, "@media(min-width:48em)"));
            assertEquals(1, count(css, "@media(min-width:64em)"));
        }

        @Test
        @DisplayName("§11.4: the existing queries are only extended (.st-table .opt at <= 40em)")
        void extendsTheExistingQueriesOnly() {
            assertTrue(css.contains("@media(max-width:47.99em){.dev-row{grid-template-columns:1fr auto}"
                + ".dev-lbl{grid-column:1;grid-row:1}.dev-val{grid-column:2;grid-row:1}"
                + ".dev-bar{grid-column:1/-1;grid-row:2}}"));
            assertTrue(css.contains("@media(max-width:40em){.row{grid-template-columns:auto 1fr}"
                + ".cats{grid-template-columns:1fr}.us-tiles{grid-template-columns:1fr}"
                + ".us-rates{grid-template-columns:1fr}.us-months .opt{display:none}"
                + ".st-table .opt{display:none}}"));
        }

        @Test
        @DisplayName("BR-23: no hex colour and no px value in the new rules (tokens, em/rem only)")
        void usesOnlyTokensAndRelativeUnitsInTheNewRules() {
            String block = css.substring(newRules, existingQueries);
            assertFalse(Pattern.compile("#[0-9a-fA-F]{3,6}\\b").matcher(block).find(), "no hex outside :root");
            assertFalse(Pattern.compile("\\d+px").matcher(block).find(), "no px in the new rules");
        }

        @Test
        @DisplayName("§16 / BR-22: every new class of the glossary has a rule")
        void coversEveryClassOfTheGlossary() {
            String block = css.substring(newRules, existingQueries);
            List<String> classes = List.of("tabs", "st-toc", "st-toc-hd", "st-sub", "st-grid", "st-thin",
                "st-intro", "st-range", "st-note", "st-warn", "st-empty", "st-empty-dash", "st-table",
                "st-rank", "st-l", "st-small", "st-nw", "st-sup", "st-ka", "st-nd", "st-scroll", "st-heat",
                "st-heat-1", "st-heat-2", "st-heat-3", "st-heat-4", "st-legend", "st-sw", "st-sw-txt",
                "st-fill", "st-ico", "st-limits", "st-notfound", "ib", "ib-grid", "ib-tile", "ib-free",
                "ib-tight", "ib-hd", "ib-name", "ib-src", "ib-list", "ib-val", "ib-num", "ib-sub", "cap",
                "cap-table", "cap-pill", "cap-pill-free", "cap-pill-tight", "cap-pill-na", "cap-when",
                "cap-total", "cap-day", "cap-day-free", "cap-day-tight", "cap-day-booked", "cap-day-na",
                "cap-sum", "cap-class", "rk", "rk-table", "rk-cell", "rk-bar", "rk-fill", "rk-prev",
                "rk-low", "sg", "sg-matrix", "sg-other", "tm", "tm-kpis", "tm-kpi", "tm-kpi-val",
                "tm-kpi-lbl", "tm-kpi-sub", "tm-slots", "fn", "fn-steps", "fn-step", "fn-lbl", "fn-bar",
                "fn-fill", "fn-val", "fn-pct", "fn-prev", "fn-table", "fn-dropoff", "fn-dropoff-table",
                "fn-dropoff-hint", "fn-dropoff-dev", "fn-dropoff-sum", "dev-wide");
            for (String cls : classes) {
                Matcher m = Pattern.compile("\\." + Pattern.quote(cls) + "(?![\\w-])").matcher(block);
                assertTrue(m.find(), "no rule for ." + cls);
            }
        }

        @Test
        @DisplayName("§11.1 / AC-26: tab underline 0.2em plus bold, touch target, focus ring")
        void stylesTheActiveTabNotByColourAlone() {
            assertTrue(css.contains(".tabs a[aria-current=page]{color:var(--ink);font-weight:700;"
                + "border-bottom-color:var(--accent)}"));
            assertTrue(css.contains("border-bottom:.2em solid transparent"));
            assertTrue(css.contains("min-height:2.75em"));
            assertTrue(css.contains(".tabs a:focus-visible{outline:.15rem solid var(--focus)"));
            assertTrue(css.contains(".st-scroll{position:relative;"), "AC-27: .sr texts stay clipped");
            assertTrue(css.contains(".st-scroll th[scope=row]{position:sticky;left:0;"));
            assertFalse(css.contains("prefers-color-scheme"), "§11.3: no dark mode");
        }

        @Test
        @DisplayName("§11.5: the warning band is a bordered paragraph without icon — no dead icon/flex rules")
        void carriesNoDeadWarningBandRules() {
            assertTrue(css.contains(".st-warn{font-size:.85rem;color:var(--ink);border:.0625rem solid var(--line);"),
                "p.st-warn keeps text plus border");
            assertTrue(css.contains("border-left:.25rem solid var(--warn)"));
            assertFalse(css.contains(".st-warn p{"), "the band is the paragraph itself");
            assertFalse(css.contains(".st-warn .st-ico"), "the band renders no icon");
            assertFalse(css.contains("display:flex;align-items:flex-start;gap:.6rem"), "no flex layout for one text");
            assertTrue(css.contains(".st-ico svg{width:1.1rem;height:1.1rem}"), "S0 icons keep their rule");
            assertTrue(css.contains(".cap-day-na{color:var(--soft)}"));
            assertFalse(css.contains("td.cap-day-na"), "no redundant selector");
        }

        @Test
        @DisplayName("BR-22 / TC-11: the stylesheet never names 'nicht verfügbar' nor class=\"us\"")
        void carriesNoContentThatBreaksExistingAssertions() {
            assertFalse(css.contains("nicht verf"));
            assertFalse(css.contains("class=\"us\""));
            assertFalse(css.contains("Nutzungsstatistik"));
        }
    }

    // ---- REQ-038 helpers / Hilfen ----

    private static String stylesheet(String html) {
        int start = html.indexOf("<style>");
        return html.substring(start, html.indexOf("</style>", start));
    }

    private static int count(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i > -1; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
