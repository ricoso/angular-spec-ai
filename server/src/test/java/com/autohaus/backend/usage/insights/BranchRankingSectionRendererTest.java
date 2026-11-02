package com.autohaus.backend.usage.insights;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rendering of S2 „Online-Buchungen je Standort: Renner und Penner" (REQ-038 §11.2, AC-15) /
 * Rendering der Standort-Rangliste.
 *
 * <p>Covers the S2 part of TC-38 (classes, one table with {@code .opt} columns, caption/scope,
 * scroll region, empty state, no {@code .us-*}), the render side of TC-28 (no closed month yet),
 * TC-29 (ranks, rank change „↑ 2"/„↓ 1"/„="/„neu", {@code tbody.rk-low} after all ranked rows,
 * „&lt; 5" and „0") and TC-57 (bar width 0 for a hidden count), plus the approximation labels of
 * AC-29/BR-20. / Deckt TC-28, TC-29, TC-38 und TC-57 (Render-Teil) ab.
 */
class BranchRankingSectionRendererTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-19T08:00:00Z"), ZoneId.of("Europe/Berlin"));
    private static final LocalDate TODAY = LocalDate.now(CLOCK);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    private final BranchRankingSectionRenderer renderer = new BranchRankingSectionRenderer();

    // -----------------------------------------------------------------------------------------
    // Fixtures / Testdaten
    // -----------------------------------------------------------------------------------------

    private static BranchRankingRow ranked(String name, int rank, String move, Integer previous, long bookings,
                                           double bar) {
        return new BranchRankingRow(name, rank, move, previous, Cell.of(bookings), Cell.percentOf(19.0),
            Cell.of(104), Cell.of(410), Cell.ratioOf(14.1), Cell.percentOf(34.0), Cell.percentOf(21.0), bar);
    }

    private static List<BranchRankingRow> ranking() {
        return List.of(
            ranked("VW Zentrum Rheinfeld", 1, "up", 2, 58, 100.0),
            ranked("Audi Zentrum Rheinfeld", 2, "down", 1, 47, 81.0),
            new BranchRankingRow("Audi Waldkirchen", 3, "same", 3, Cell.of(29), Cell.percentOf(9.0), Cell.of(53),
                Cell.of(164), Cell.ratioOf(17.7), Cell.percentOf(45.0), Cell.withheld(), 50.0),
            new BranchRankingRow("VW & ŠKODA Service Lindenau", 4, "new", null, Cell.of(19), Cell.percentOf(6.0),
                Cell.of(36), Cell.of(188), Cell.ratioOf(10.1), Cell.tooFew(), Cell.tooFew(), 32.8),
            ranked("VW Altstadt", 5, "↑ 2", null, 14, 24.1),
            ranked("<b>X</b>", 6, null, 8, 9, 15.5));
    }

    private static List<BranchRankingRow> belowThreshold() {
        return List.of(
            new BranchRankingRow("VW Neuhaus", null, null, 7, Cell.suppressed(), Cell.withheld(), Cell.suppressed(),
                Cell.of(95), Cell.withheld(), Cell.tooFew(), Cell.tooFew(), 37.0),
            new BranchRankingRow("VW Oststadt", null, null, null, Cell.of(0), Cell.percentOf(0.0), Cell.of(0),
                Cell.of(40), Cell.ratioOf(0.0), Cell.tooFew(), Cell.tooFew(), 0.0));
    }

    static InsightsViewModel model(YearMonth closedMonth, LocalDate collectingSince, List<BranchRankingRow> ranking,
                                   List<BranchRankingRow> below, List<String> warnings) {
        return new InsightsViewModel(TODAY.minusDays(1), true, false, collectingSince, null, closedMonth, null,
            false, 0, ranking, below, null, null, null, Cell.tooFew(), null, warnings);
    }

    private String renderFull() {
        return renderer.render(model(OCTOBER, LocalDate.of(2026, 9, 1), ranking(), belowThreshold(), List.of()));
    }

    // -----------------------------------------------------------------------------------------
    // TC-29 / TC-38
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("TC-38: section, range line, one table in a scroll region, markup contract")
    void rendersSectionRangeAndTable() {
        String html = renderFull();

        assertTrue(html.startsWith("<section id=\"branches\" class=\"rk\" aria-labelledby=\"branches-h\">"
            + "<h2 id=\"branches-h\">Online-Buchungen je Standort: Renner und Penner</h2>"));
        assertTrue(html.contains("<p class=\"st-range\">Oktober 2026 (abgeschlossener Monat) · Jahr 2026 bis Ende "
            + "Oktober</p>"));
        assertTrue(html.contains("aria-label=\"Tabelle Online-Buchungen je Standort, horizontal scrollbar\">"
            + "<table class=\"st-table rk-table\"><caption class=\"sr\">Rangliste der Standorte nach "
            + "Online-Buchungen im Oktober 2026"));
        assertTrue(html.contains("Rangänderung gegenüber September"));
        assertEquals(1, CellHtmlTest.count(html, "<table"), "all columns in one table (§11.2)");
        CellHtmlTest.assertMarkupContract(html);
    }

    @Test
    @DisplayName("AC-29/BR-20: optional columns carry .opt and the approximation labels stand in the head")
    void headNamesApproximationsAndOptionalColumns() {
        String html = renderFull();

        assertTrue(html.contains("<th scope=\"col\">Rang</th><th scope=\"col\" class=\"st-l\">Standort</th>"
            + "<th scope=\"col\" class=\"st-l\">Online-Buchungen</th>"
            + "<th scope=\"col\">Anteil <span class=\"st-small\">am Online-Volumen</span></th>"
            + "<th scope=\"col\" class=\"opt\">Jahr bis Vormonat</th>"
            + "<th scope=\"col\" class=\"opt\">Leistungsaufrufe <span class=\"st-small\">Seitenaufrufe</span></th>"
            + "<th scope=\"col\" class=\"opt\">Buchungen je 100 Aufrufe <span class=\"st-small\">(Näherung)</span></th>"
            + "<th scope=\"col\" class=\"opt\">Außerhalb <span class=\"st-small st-nw\">Mo–Fr 07–18 Uhr</span></th>"
            + "<th scope=\"col\" class=\"opt\">Späte Termine <span class=\"st-small\">ab 16 Uhr</span></th>"
            + "<th scope=\"col\">Rangänderung</th>"));
    }

    @Test
    @DisplayName("TC-29: ranked row with bar, number, shares, ratio and rank change")
    void rendersARankedRow() {
        String html = renderFull();

        assertTrue(html.contains("<tr><td class=\"st-rank\">1</td><th scope=\"row\">VW Zentrum Rheinfeld</th>"
            + "<td class=\"st-l\"><span class=\"rk-cell\"><span class=\"rk-bar\" aria-hidden=\"true\">"
            + "<span class=\"rk-fill\" style=\"width:100.0%\"></span></span><b>58</b></span></td>"
            + "<td>19&nbsp;%</td><td class=\"opt\">104</td><td class=\"opt\">410</td><td class=\"opt\">14,1</td>"
            + "<td class=\"opt\">34&nbsp;%</td><td class=\"opt\">21&nbsp;%</td>"
            + "<td><span aria-hidden=\"true\">↑ 1</span><span class=\"sr\">aufgestiegen um 1</span>"
            + "<span class=\"rk-prev\">Sep.: 2</span></td></tr>"));
    }

    @Test
    @DisplayName("TC-29: rank change „↑ n“ / „↓ n“ / „=“ / „neu“ with .sr text and the previous rank")
    void rendersEveryRankChange() {
        String html = renderFull();

        assertTrue(html.contains("<td><span aria-hidden=\"true\">↓ 1</span><span class=\"sr\">abgestiegen um 1</span>"
            + "<span class=\"rk-prev\">Sep.: 1</span></td>"));
        assertTrue(html.contains("<td><span aria-hidden=\"true\">=</span><span class=\"sr\">unverändert</span>"
            + "<span class=\"rk-prev\">Sep.: 3</span></td>"));
        assertTrue(html.contains("<td>neu</td>"));
        assertTrue(html.contains("<td><span aria-hidden=\"true\">↑ 2</span><span class=\"sr\">aufgestiegen um 2</span>"
            + "</td>"), "symbol form without previous rank");
        assertTrue(html.contains("<td><span aria-hidden=\"true\">↑ 2</span><span class=\"sr\">aufgestiegen um 2</span>"
            + "<span class=\"rk-prev\">Sep.: 8</span></td>"), "direction from the ranks when rankMove is missing");
    }

    @Test
    @DisplayName("TC-29/BR-9: hidden shares render as „k. A.“ / „n < 20“, never as a number")
    void rendersHiddenCellsWithTheirStateClasses() {
        String html = renderFull();

        assertTrue(html.contains("<td class=\"opt st-ka\"><span aria-hidden=\"true\">k.&nbsp;A.</span>"));
        assertTrue(html.contains("<td class=\"opt st-nd\"><span aria-hidden=\"true\">n&nbsp;&lt;&nbsp;20</span>"
            + "<span class=\"sr\">zu wenig Daten, weniger als 20 Fälle</span></td>"));
    }

    @Test
    @DisplayName("TC-29/AC-15: tbody.rk-low after all ranked rows, alphabetical names, no rank")
    void rendersTheBelowThresholdGroupLast() {
        String html = renderFull();

        String group = "</tbody><tbody class=\"rk-low\"><tr><th scope=\"rowgroup\" colspan=\"10\">Unter 5 Buchungen: "
            + "VW Neuhaus, VW Oststadt</th></tr>";
        assertTrue(html.contains(group));
        assertTrue(html.indexOf(group) > html.indexOf("&lt;b&gt;X&lt;/b&gt;"), "after every ranked row");
        assertEquals(1, CellHtmlTest.count(html, "rk-low"));
        assertTrue(html.contains("<td class=\"st-rank\"><span aria-hidden=\"true\">&mdash;</span>"
            + "<span class=\"sr\">kein Rang</span></td><th scope=\"row\">VW Neuhaus</th>"));
    }

    @Test
    @DisplayName("TC-57: a „< 5“ branch has bar width 0 and no number anywhere in its row")
    void suppressedBranchHasNoWidthAndNoNumber() {
        String html = renderFull();

        int start = html.indexOf("<th scope=\"row\">VW Neuhaus</th>");
        String row = html.substring(start, html.indexOf("</tr>", start));
        assertTrue(row.contains("<span class=\"rk-fill\" style=\"width:0.0%\"></span></span><span class=\"st-sup\">"
            + "<span aria-hidden=\"true\">&lt; 5</span><span class=\"sr\">weniger als 5, unterdrückt</span></span>"));
        assertFalse(row.contains("37"), "the broken bar width of the fixture must not leak");
        assertTrue(row.contains("<td class=\"st-ka\">"), "R-2 of a hidden count is „k. A.“");
        assertTrue(row.contains("<td class=\"opt\">95</td>"), "service views stay visible");
        assertTrue(row.contains("<td class=\"st-nd\"><span aria-hidden=\"true\">&mdash;</span><span class=\"sr\">"
            + "kein Rang</span><span class=\"rk-prev\">Sep.: 7</span></td>"));
    }

    @Test
    @DisplayName("TC-29: a branch with 0 bookings shows „0“, „0 %“ and „0,0“")
    void zeroBranchShowsZeros() {
        String html = renderFull();

        int start = html.indexOf("<th scope=\"row\">VW Oststadt</th>");
        String row = html.substring(start, html.indexOf("</tr>", start));
        assertTrue(row.contains("style=\"width:0.0%\"></span></span><b>0</b></span></td><td>0&nbsp;%</td>"
            + "<td class=\"opt\">0</td><td class=\"opt\">40</td><td class=\"opt\">0,0</td>"));
    }

    @Test
    @DisplayName("TC-38: branch names and warnings are escaped; note stands below the table")
    void escapesAndAddsTheNote() {
        String html = renderer.render(model(OCTOBER, LocalDate.of(2026, 9, 1), ranking(), belowThreshold(),
            List.of("Monat unvollständig – Erfassung seit 18.10.2026", "<i>x</i>")));

        assertTrue(html.contains("<th scope=\"row\">&lt;b&gt;X&lt;/b&gt;</th>"));
        assertFalse(html.contains("<b>X</b>"));
        assertTrue(html.contains("<p class=\"st-warn\">Monat unvollständig – Erfassung seit 18.10.2026</p>"));
        assertTrue(html.contains("<p class=\"st-warn\">&lt;i&gt;x&lt;/i&gt;</p>"));
        assertTrue(html.indexOf("st-warn") < html.indexOf("st-range"));
        assertTrue(html.endsWith("<p class=\"st-note\">" + BranchRankingSectionRenderer.RANKING_NOTE
            + "</p></section>"));
    }

    // -----------------------------------------------------------------------------------------
    // Empty states / Leerzustände (TC-28, TC-48)
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("TC-28: no closed month → .st-empty with collection start and first ranking day")
    void noClosedMonthShowsTheEmptyState() {
        String html = renderer.render(model(null, LocalDate.of(2026, 10, 1), null, null, List.of()));

        assertTrue(html.contains("<p class=\"st-empty\"><span class=\"st-empty-dash\" aria-hidden=\"true\">&mdash;"
            + "</span>Noch kein abgeschlossener Monat seit Erfassungsbeginn (01.10.2026). Die erste Rangliste "
            + "erscheint am 01.11.2026.</p>"));
        assertFalse(html.contains("<table"));
        assertFalse(html.contains(">0<"), "never 0 instead of „—“");
        CellHtmlTest.assertMarkupContract(html);

        String later = renderer.render(model(null, LocalDate.of(2026, 11, 18), null, null, List.of()));
        assertTrue(later.contains("(18.11.2026). Die erste Rangliste erscheint am 01.12.2026."));

        String unknown = renderer.render(model(null, null, null, null, List.of()));
        assertTrue(unknown.contains("Noch kein abgeschlossener Monat seit Erfassungsbeginn.</p>"));
    }

    @Test
    @DisplayName("TC-48: history unavailable → only the warning")
    void historyUnavailableShowsOnlyTheWarning() {
        String html = renderer.render(model(OCTOBER, null, ranking(), belowThreshold(),
            List.of(InsightsViewModel.WARN_HISTORY_UNAVAILABLE)));

        assertTrue(html.contains("<p class=\"st-warn\">Historie derzeit nicht verfügbar</p>"));
        assertFalse(html.contains("<table"));
        assertFalse(html.contains("st-empty"));
        assertFalse(html.contains("st-range"));
    }

    @Test
    void closedMonthWithoutRowsRendersNoTable() {
        String html = renderer.render(model(OCTOBER, null, List.of(), null, null));

        assertTrue(html.contains("st-range"));
        assertFalse(html.contains("<table"));
        assertTrue(html.contains("st-note"));
    }

    @Test
    void onlyBelowThresholdRowsStillRenderTheGroup() {
        String html = renderer.render(model(OCTOBER, null, null, belowThreshold(), List.of()));

        assertTrue(html.contains("<tbody></tbody><tbody class=\"rk-low\">"));
        CellHtmlTest.assertMarkupContract(html);
    }

    @Test
    @DisplayName("BR-4: switched-off bookings or null render nothing")
    void switchedOffRendersNothing() {
        assertEquals("", renderer.render(null));
        InsightsViewModel funnelOnly = new InsightsViewModel(TODAY, false, true, null, null, OCTOBER, null, false, 0,
            ranking(), belowThreshold(), null, null, null, null, null, List.of());
        assertEquals("", renderer.render(funnelOnly));
    }

    @Test
    void rankMoveParsingIsTolerant() {
        assertNull(BranchRankingSectionRenderer.Move.of(null, null, null));
        assertNull(BranchRankingSectionRenderer.Move.of("foo", 3, null));
        assertEquals(BranchRankingSectionRenderer.Move.DOWN, BranchRankingSectionRenderer.Move.of(null, 4, 2));
        assertEquals(BranchRankingSectionRenderer.Move.SAME, BranchRankingSectionRenderer.Move.of(null, 4, 4));
        assertEquals(BranchRankingSectionRenderer.Move.NEW, BranchRankingSectionRenderer.Move.of("neu", 4, null));
        assertEquals(BranchRankingSectionRenderer.Move.DOWN, BranchRankingSectionRenderer.Move.of("↓ 3", null, null));
        assertEquals(3, BranchRankingSectionRenderer.Move.DOWN.steps("↓ 3", null, null));
        assertEquals(1, BranchRankingSectionRenderer.Move.UP.steps("up", null, null));
        assertEquals(1, BranchRankingSectionRenderer.Move.UP.steps("↑ 123456", null, null));
        assertEquals(1, BranchRankingSectionRenderer.Move.UP.steps(null, 2, null));
    }
}
