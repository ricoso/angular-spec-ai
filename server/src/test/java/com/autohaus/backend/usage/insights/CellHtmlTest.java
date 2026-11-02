package com.autohaus.backend.usage.insights;

import com.autohaus.backend.common.html.HtmlTextTest;
import com.autohaus.backend.usage.StatFormat;
import com.autohaus.backend.usage.insights.CellHtml.Format;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cell and table markup shared by the statistics sections (REQ-038 §11.2, §11.5, §17
 * {@code CELL_*}) / gemeinsames Zell- und Tabellen-Markup.
 *
 * <p>Covers the render part of TC-38 (state classes {@code .st-sup}/{@code .st-ka}/{@code .st-nd},
 * visible short text plus {@code .sr} long text, heat classes only for {@code VALUE}), TC-57
 * (no indirect values: a hidden cell is normalised by {@link Cell} and never prints a number, a
 * heat class or a bar width) and the heat legend items of §17 {@code HEAT_LEGEND_*}. The generic
 * helpers (escaping, warning band, scroll region of AC-27, German dates) are covered by
 * {@code HtmlTextTest} and {@code GermanFormatTest}. / Deckt TC-38, TC-57 (Render-Teil) und die
 * Legenden-Einträge ab.
 *
 * <p>{@link #assertMarkupContract(String)} is the shared structural check of every section test:
 * each table has a caption, each {@code th} a scope, each scroll region its three attributes, only
 * classes from §11.2/§16 and never a {@code .us-*} class (BR-22). / Gemeinsamer Strukturtest.
 */
public class CellHtmlTest {

    // -----------------------------------------------------------------------------------------
    // Shared contract check / gemeinsame Vertragsprüfung
    // -----------------------------------------------------------------------------------------

    /** Every CSS class the new sections may use (§11.2, §16) / erlaubte Klassen. */
    public static final Set<String> ALLOWED_CLASSES = Set.of(
        "tabs", "st-toc", "st-toc-hd", "st-sub", "st-grid", "st-thin", "st-intro", "st-range", "st-note",
        "st-warn", "st-empty", "st-empty-dash", "st-table", "st-rank", "st-l", "st-small", "st-nw", "st-sup",
        "st-ka", "st-nd", "st-scroll", "st-heat", "st-heat-1", "st-heat-2", "st-heat-3", "st-heat-4",
        "st-legend", "st-sw", "st-sw-txt", "st-fill", "st-ico", "st-limits", "st-notfound",
        "cap", "cap-table", "cap-pill", "cap-pill-free", "cap-pill-tight", "cap-pill-na", "cap-when",
        "cap-total", "cap-day", "cap-day-free", "cap-day-tight", "cap-day-booked", "cap-day-na", "cap-sum",
        "cap-class", "rk", "rk-table", "rk-cell", "rk-bar", "rk-fill", "rk-prev", "rk-low", "sg", "sg-matrix",
        "sg-other", "tm", "tm-kpis", "tm-kpi", "tm-kpi-val", "tm-kpi-lbl", "tm-kpi-sub", "tm-slots", "fn",
        "fn-steps", "fn-step", "fn-lbl", "fn-bar", "fn-fill", "fn-val", "fn-pct", "fn-prev", "fn-table",
        "fn-dropoff", "fn-dropoff-table", "fn-dropoff-hint", "fn-dropoff-dev", "fn-dropoff-sum", "dev-wide",
        "sr", "opt", "dev", "dev-row", "dev-lbl", "dev-bar", "dev-fill", "dev-val");

    private static final Pattern CLASS_ATTR = Pattern.compile("class=\"([^\"]*)\"");
    private static final Pattern TABLE_OPEN = Pattern.compile("<table[^>]*>");
    private static final Pattern TH = Pattern.compile("<th(\\s[^>]*)?>");
    private static final Pattern SCROLL = Pattern.compile("<div class=\"st-scroll\"[^>]*>");

    /**
     * Structural contract of a section (TC-38, AC-26, AC-27, BR-22) / Strukturvertrag einer
     * Sektion: caption per table, scope per th, scroll regions with role/tabindex/aria-label, only
     * §16 classes, no {@code .us-*}, no script/form/img, balanced containers.
     */
    public static void assertMarkupContract(String html) {
        Matcher tables = TABLE_OPEN.matcher(html);
        while (tables.find()) {
            assertTrue(html.startsWith("<caption", tables.end()),
                "every table starts with a caption: " + snippet(html, tables.start()));
        }
        Matcher ths = TH.matcher(html);
        while (ths.find()) {
            String attrs = ths.group(1) == null ? "" : ths.group(1);
            assertTrue(attrs.matches(".*scope=\"(col|row|colgroup|rowgroup)\".*"),
                "every th carries a scope: " + snippet(html, ths.start()));
        }
        Matcher scrolls = SCROLL.matcher(html);
        while (scrolls.find()) {
            String tag = scrolls.group();
            assertTrue(tag.contains("role=\"region\""), "scroll region role: " + tag);
            assertTrue(tag.contains("tabindex=\"0\""), "scroll region tabindex: " + tag);
            assertTrue(tag.matches(".*aria-label=\"Tabelle [^\"]+, horizontal scrollbar\".*"),
                "scroll region aria-label: " + tag);
        }
        Matcher classes = CLASS_ATTR.matcher(html);
        while (classes.find()) {
            for (String token : classes.group(1).trim().split("\\s+")) {
                assertFalse(token.startsWith("us-"), "no .us-* class outside S5 (BR-22): " + token);
                assertTrue(ALLOWED_CLASSES.contains(token), "class from §11.2/§16 only: " + token);
            }
        }
        for (String forbidden : List.of("<script", "<form", "<img", "role=\"tab")) {
            assertFalse(html.contains(forbidden), "no " + forbidden);
        }
        for (String tag : List.of("section", "table", "div", "ul", "ol", "tr", "li", "p", "tbody", "thead")) {
            assertEquals(count(html, "<" + tag + ">") + count(html, "<" + tag + " "), count(html, "</" + tag + ">"),
                "balanced <" + tag + ">");
        }
    }

    /** Occurrences of {@code needle} / Anzahl der Vorkommen. */
    public static int count(String html, String needle) {
        int n = 0;
        int i = html.indexOf(needle);
        while (i >= 0) {
            n++;
            i = html.indexOf(needle, i + needle.length());
        }
        return n;
    }

    private static String snippet(String html, int at) {
        return html.substring(at, Math.min(html.length(), at + 120));
    }

    // -----------------------------------------------------------------------------------------
    // States / Zustände
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("TC-38: visible counter prints the number without a state class")
    void visibleCounterPrintsTheNumberWithoutStateClass() {
        assertEquals("<td>1.122</td>", CellHtml.td(Cell.of(1122), Format.COUNT));
        assertEquals("1.122", CellHtml.inline(Cell.of(1122), Format.COUNT));
        assertEquals("<b>0</b>", CellHtml.strong(Cell.of(0), Format.COUNT));
    }

    @Test
    @DisplayName("TC-38: each hidden state has its class, short text aria-hidden and .sr long text")
    void hiddenStatesCarryClassShortTextAndLongText() {
        assertEquals("<td class=\"st-sup\"><span aria-hidden=\"true\">&lt; 5</span>"
            + "<span class=\"sr\">weniger als 5, unterdrückt</span></td>", CellHtml.td(Cell.suppressed(), Format.COUNT));
        assertEquals("<td class=\"st-ka\"><span aria-hidden=\"true\">k.&nbsp;A.</span>"
            + "<span class=\"sr\">keine Angabe, unterdrückt gegen Rückrechnung</span></td>",
            CellHtml.td(Cell.withheld(), Format.COUNT));
        assertEquals("<td class=\"st-nd\"><span aria-hidden=\"true\">n&nbsp;&lt;&nbsp;20</span>"
            + "<span class=\"sr\">zu wenig Daten, weniger als 20 Fälle</span></td>",
            CellHtml.td(Cell.tooFew(), Format.PERCENT_INT));
        assertEquals("<td class=\"st-nd\"><span aria-hidden=\"true\">&mdash;</span>"
            + "<span class=\"sr\">keine Daten</span></td>", CellHtml.td(Cell.noData(), Format.COUNT));
    }

    @Test
    @DisplayName("BR-19: a missing cell is „—“ (NO_DATA), never 0")
    void missingCellIsNoDataNeverZero() {
        assertEquals(CellState.NO_DATA, CellHtml.state(null));
        assertEquals(CellState.NO_DATA, CellHtml.state(new Cell(5L, null, null, 0, null)));
        String td = CellHtml.td(null, Format.COUNT);
        assertTrue(td.contains("st-nd"));
        assertFalse(td.contains(">0<"));
        assertFalse(CellHtml.isValue(null));
        assertFalse(CellHtml.isZero(Cell.noData()));
        assertTrue(CellHtml.isZero(Cell.of(0)));
        assertFalse(CellHtml.isZero(Cell.percentOf(0.0)));
    }

    @Test
    @DisplayName("TC-57/AC-20: a hidden cell built with numbers is normalised and never prints a number, heat or width")
    void hiddenCellIsNormalisedAndNeverLeaks() {
        Cell broken = new Cell(3L, 1.5, 12.0, 3, CellState.SUPPRESSED);
        assertNull(broken.value(), "Cell drops the hidden counter");
        assertNull(broken.ratio(), "Cell drops the hidden ratio");
        assertNull(broken.percent(), "Cell drops the hidden percentage");
        assertEquals(0, broken.heat(), "Cell drops the heat level");
        assertEquals(CellState.SUPPRESSED, broken.state());
        assertEquals(Cell.suppressed(), broken);

        String td = CellHtml.td(broken, Format.COUNT, "opt", true);
        assertEquals("<td class=\"opt st-sup\"><span aria-hidden=\"true\">&lt; 5</span>"
            + "<span class=\"sr\">weniger als 5, unterdrückt</span></td>", td);
        assertEquals("", CellHtml.heatClass(broken));
        assertEquals("0.0", CellHtml.barWidth(broken));
        String inline = CellHtml.inline(new Cell(7L, null, null, 0, CellState.WITHHELD), Format.COUNT);
        assertFalse(inline.contains("7"), "withheld value must not appear: " + inline);
        assertTrue(inline.startsWith("<span class=\"st-ka\">"));
    }

    @Test
    @DisplayName("TC-53: heat class st-heat-n only for visible cells with level 1–4")
    void heatClassOnlyForVisibleLevels() {
        assertEquals("<td class=\"st-heat-2\">14</td>", CellHtml.heatTd(Cell.of(14, 2), Format.COUNT));
        assertEquals("<td>0</td>", CellHtml.heatTd(Cell.of(0, 0), Format.COUNT));
        assertEquals("", CellHtml.heatClass(Cell.of(9, -1)), "Cell clamps a negative level to 0");
        assertEquals("st-heat-4", CellHtml.heatClass(Cell.of(9, 5)), "Cell clamps the level to 4");
        assertEquals("st-heat-4", CellHtml.heatClass(Cell.percentOf(45.0, 4)));
        assertEquals("<td>14</td>", CellHtml.td(Cell.of(14, 2), Format.COUNT), "no heat outside heat tables");
    }

    @Test
    @DisplayName("§10.5: precision per metric — integer, one decimal, ratio, and fallbacks")
    void formatsFollowThePrecisionPerMetric() {
        assertEquals("38&nbsp;%", CellHtml.inline(Cell.percentOf(38.0), Format.PERCENT_INT));
        assertEquals("45,8&nbsp;%", CellHtml.inline(Cell.percentOf(45.8), Format.PERCENT));
        assertEquals("14,1", CellHtml.inline(Cell.ratioOf(14.1), Format.RATIO));
        assertEquals("0,0", CellHtml.inline(Cell.ratioOf(0.0), Format.RATIO));
        assertEquals("19,0&nbsp;%", CellHtml.inline(Cell.percentOf(19.0), Format.COUNT), "fallback to the percent");
        assertEquals("19&nbsp;%", CellHtml.inline(new Cell(null, 3.0, 19.0, 0, CellState.VALUE), Format.PERCENT_INT));
        assertEquals("19,0&nbsp;%", CellHtml.inline(Cell.percentOf(19.0), Format.RATIO), "fallback to one decimal");
        assertEquals("12", CellHtml.inline(Cell.of(12), Format.PERCENT), "fallback to the counter");
        assertEquals("2,5", CellHtml.inline(Cell.ratioOf(2.5), Format.COUNT), "fallback to the ratio");
        assertEquals("&mdash;", CellHtml.inline(new Cell(null, null, null, 0, CellState.VALUE), Format.COUNT));
    }

    @Test
    @DisplayName("BR-9 Nr. 5: bar width is the visible percent, clamped to 0–100")
    void barWidthIsTheVisiblePercentClamped() {
        assertEquals("45.8", CellHtml.barWidth(Cell.percentOf(45.8)));
        assertEquals("100.0", CellHtml.barWidth(Cell.percentOf(130.0)));
        assertEquals("0.0", CellHtml.barWidth(Cell.of(12)));
        assertEquals("0.0", CellHtml.barWidth(null));
        assertEquals(0.0, CellHtml.clamp(-3.0));
        assertEquals(0.0, CellHtml.clamp(Double.NaN));
        assertEquals(55.5, CellHtml.clamp(55.5));
    }

    @Test
    void stateClassesAndTextsPerState() {
        assertEquals("", CellHtml.stateClass(Cell.of(1)));
        assertEquals("st-sup", CellHtml.stateClass(Cell.suppressed()));
        assertEquals("st-ka", CellHtml.stateClass(Cell.withheld()));
        assertEquals("st-nd", CellHtml.stateClass(Cell.tooFew()));
        assertEquals("st-nd", CellHtml.stateClass(null));
        assertEquals("", CellHtml.longText(CellState.VALUE));
        assertEquals("&mdash;", CellHtml.shortText(CellState.NO_DATA));
        assertEquals("&lt; 5", CellHtml.shortText(CellState.SUPPRESSED));
        for (CellState state : CellState.values()) {
            if (state != CellState.VALUE) {
                assertEquals(StatFormat.cell(new Cell(null, null, null, 0, state)), CellHtml.shortText(state),
                    "one source for the short texts (§17 CELL_*): " + state);
            }
        }
        assertEquals("keine Daten", CellHtml.CELL_NO_DATA_SR);
    }

    // -----------------------------------------------------------------------------------------
    // Section helpers / Sektions-Helfer
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("§11.5: current-period sections drop the two closed-month warnings")
    void currentPeriodWarningsDropTheClosedMonthWarnings() {
        assertEquals(List.of(), CellHtml.currentPeriodWarnings(null));
        assertEquals(List.of("a"), CellHtml.currentPeriodWarnings(List.of("a")));
    }

    @Test
    @DisplayName("AC-23: empty state shows „—“ with „Erfassung seit …“, never 0")
    void emptyStateNamesTheCollectionStart() {
        assertEquals("<p class=\"st-empty\"><span class=\"st-empty-dash\" aria-hidden=\"true\">&mdash;</span>"
            + "Erfassung seit 01.10.2026 – noch keine ausgewerteten Tage.</p>",
            CellHtml.emptySince(LocalDate.of(2026, 10, 1)));
        assertTrue(CellHtml.emptySince(null).contains("Erfassung noch nicht begonnen."));
    }

    @Test
    @DisplayName("TC-53: legend lists only the hidden states that occur")
    void legendListsOnlyOccurringHiddenStates() {
        String items = CellHtml.legendStateItems(Arrays.asList(Cell.of(3), Cell.suppressed(), null, Cell.suppressed()));
        assertEquals(1, count(items, "weniger als 5, unterdrückt"));
        assertTrue(items.contains("keine Daten"), "null counts as NO_DATA");
        assertFalse(items.contains("k.&nbsp;A."));
        assertEquals("", CellHtml.legendStateItems(null));
        assertEquals("", CellHtml.legendStateItems(List.of(Cell.of(1))));
    }

    @Test
    @DisplayName("§17 HEAT_LEGEND_*: items, colour swatches and the zero item with its .sr separator")
    void rendersTheHeatLegendItems() {
        assertEquals(List.of("1–14 %", "15–29 %", "30–44 %", "ab 45 %", "0 %: keine Buchung"),
            CellHtml.legendItems(ServiceGroupSectionRenderer.HEAT_LEGEND_SHARE));
        assertEquals(List.of(), CellHtml.legendItems(null));
        assertEquals("<ul class=\"st-legend\" aria-label=\"Legende Farbstufen A &amp; B\">",
            CellHtml.heatLegendOpen("A & B"));
        assertEquals("<ul class=\"st-legend\" aria-label=\"Legende Farbstufen \">", CellHtml.heatLegendOpen(null));
        assertEquals("<li><span class=\"st-sw st-heat-2\" aria-hidden=\"true\"></span>15–29 %</li>",
            CellHtml.legendSwatch(2, "15–29 %"));
        assertEquals("<li><span class=\"st-sw-txt\" aria-hidden=\"true\">0&nbsp;%</span><span class=\"sr\">0 %: </span>"
            + "keine Buchung</li>", CellHtml.legendZero("0 %: keine Buchung"));
        assertEquals("<li><span class=\"st-sw-txt\" aria-hidden=\"true\">0</span><span class=\"sr\">0: </span>"
            + "keine Buchung</li>", CellHtml.legendZero("0: keine Buchung"));
        assertTrue(CellHtml.legendZero(null).endsWith("</span></li>"));
    }

    @Test
    @DisplayName("§9: cell markup and the insights renderers never depend on the reporting package")
    void insightsRenderingDependsOnTheNeutralPackageOnly() throws IOException {
        for (Class<?> type : List.of(CellHtml.class, BranchRankingSectionRenderer.class,
            ServiceGroupSectionRenderer.class, TimingSectionRenderer.class, FunnelSectionRenderer.class)) {
            assertFalse(HtmlTextTest.references(type, "com/autohaus/backend/reporting/"),
                type.getSimpleName() + " escapes through common.html, not ReportHtmlGenerator");
            assertTrue(HtmlTextTest.references(type, "com/autohaus/backend/common/html/"), type.getSimpleName());
        }
    }

    @Test
    @DisplayName("BR-10: period text tolerates label forms with and without range")
    void periodTextToleratesLabelForms() {
        Period withPlaceholder = period("aktueller Monat bis gestern ({range})", "01.11.–18.11.2026");
        assertEquals("aktueller Monat bis gestern (01.11.–18.11.2026)", CellHtml.periodText(withPlaceholder, null));
        Period plain = period("laufendes Jahr bis gestern", "01.01.–18.11.2026");
        assertEquals("laufendes Jahr bis gestern (01.01.–18.11.2026)", CellHtml.periodText(plain, null));
        Period complete = period("aktueller Monat bis gestern (01.11.–18.11.2026)", "01.11.–18.11.2026");
        assertEquals("aktueller Monat bis gestern (01.11.–18.11.2026)", CellHtml.periodText(complete, null));
        Period noLabel = period(null, "01.11.–18.11.2026");
        assertEquals("aktueller Monat bis gestern (01.11.–18.11.2026)",
            CellHtml.periodText(noLabel, CellHtml.PERIOD_MONTH));
        Period noRange = period("aktueller Monat bis gestern ({range})", null);
        assertEquals("aktueller Monat bis gestern", CellHtml.periodText(noRange, null));
        assertEquals("", CellHtml.periodText(null, CellHtml.PERIOD_YEAR));
        assertEquals("", CellHtml.periodText(period(" ", null), null));
    }

    @Test
    void nullSafeLookupInCellMaps() {
        assertNull(CellHtml.get(null, "a"));
        assertNull(CellHtml.get(Map.of("a", Cell.of(1)), null));
        assertEquals(Cell.of(1), CellHtml.get(Map.of("a", Cell.of(1)), "a"));
    }

    @Test
    void contractCheckRejectsForbiddenMarkup() {
        assertThrows(AssertionError.class, () -> assertMarkupContract("<table class=\"st-table\"><tr></tr></table>"));
        assertThrows(AssertionError.class, () -> assertMarkupContract("<table><caption>x</caption><th>x</th></table>"));
        assertThrows(AssertionError.class, () -> assertMarkupContract("<p class=\"us-empty\">x</p>"));
        assertThrows(AssertionError.class, () -> assertMarkupContract("<p class=\"mk-note\">x</p>"));
        assertThrows(AssertionError.class, () -> assertMarkupContract("<div class=\"st-scroll\" role=\"region\"></div>"));
        assertDoesNotThrow(() -> assertMarkupContract(
            "<div class=\"st-scroll\" role=\"region\" tabindex=\"0\" aria-label=\"Tabelle x, horizontal scrollbar\">"
                + "<table class=\"st-table\"><caption class=\"sr\">x</caption><thead><tr><th scope=\"col\">a</th>"
                + "</tr></thead></table></div>"));
    }

    private static Period period(String label, String range) {
        return new Period(label, range, Cell.of(10), Map.of(), Map.of(), Map.of(), Map.of(),
            Cell.tooFew(), null, null, null);
    }
}
