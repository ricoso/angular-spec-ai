package com.autohaus.backend.usage.insights;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rendering of S3 „Servicegruppen" — G-1 bars and the G-2/G-3 matrix (REQ-038 §11.2, AC-16) /
 * Rendering der Servicegruppen.
 *
 * <p>Covers the S3 part of TC-38 (classes, caption/scope, scroll region, empty states, no
 * {@code .us-*}), the render side of TC-26 (collective row {@code tr.sg-other} with „k. A.",
 * column „nicht zuordenbar", heat 0 for hidden cells) and TC-53 (G-2 heat levels 1/2/4 with the
 * fixed legend {@code HEAT_LEGEND_SHARE} as {@code ul.st-legend} below the table), plus AC-20 (the
 * order of the bar list never depends on a hidden value). / Deckt TC-26, TC-38 und TC-53
 * (Render-Teil) ab.
 */
class ServiceGroupSectionRendererTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final List<String> COLUMNS = List.of("inspection", "tuv", "brake-fluid", "tire-change",
        "actions-checks", "repair", "bodywork", "extras", "pickup", "unknown", "mobility");

    private final ServiceGroupSectionRenderer renderer = new ServiceGroupSectionRenderer();

    // -----------------------------------------------------------------------------------------
    // Fixtures / Testdaten
    // -----------------------------------------------------------------------------------------

    private static Cell count(long value, double percent) {
        return new Cell(value, null, percent, 0, CellState.VALUE);
    }

    private static Period month() {
        Map<String, Cell> groups = new LinkedHashMap<>();
        groups.put("inspection", count(74, 25.9));
        groups.put("tuv", Cell.suppressed());
        groups.put("tire-change", count(131, 45.8));
        groups.put("extras", count(52, 18.2));
        groups.put("pickup", Cell.withheld());
        groups.put("unknown", count(9, 3.1));
        groups.put("mobility", count(34, 11.9));
        return new Period("aktueller Monat bis gestern", "01.11.–18.11.2026", Cell.of(286), groups, Map.of(),
            Map.of(), Map.of(), Cell.percentOf(37.0), null, null, null);
    }

    private static Period year() {
        Map<String, Cell> groups = new LinkedHashMap<>();
        groups.put("inspection", count(210, 24.7));
        groups.put("tire-change", count(390, 45.9));
        groups.put("mobility", Cell.suppressed());
        return new Period("laufendes Jahr bis gestern ({range})", "01.01.–18.11.2026", Cell.of(849), groups, Map.of(),
            Map.of(), Map.of(), Cell.percentOf(37.0), null, null, null);
    }

    private static GroupMatrix matrix() {
        Map<String, Cell> a = new HashMap<>();
        a.put("inspection", Cell.percentOf(14.0, 1));
        a.put("tuv", Cell.percentOf(15.0, 2));
        a.put("tire-change", Cell.percentOf(45.0, 4));
        a.put("repair", Cell.suppressed());
        a.put("bodywork", new Cell(null, null, 30.0, 3, CellState.WITHHELD));
        a.put("unknown", Cell.percentOf(0.0, 0));
        MatrixRow branch = new MatrixRow("<b>X</b>", Cell.of(58), a, false);
        Map<String, Cell> other = new HashMap<>();
        for (String column : COLUMNS) {
            other.put(column, Cell.withheld());
        }
        MatrixRow others = new MatrixRow("weitere Standorte (unter 20 Buchungen)", Cell.withheld(), other, true);
        return new GroupMatrix(COLUMNS, List.of(branch, others));
    }

    private static InsightsViewModel model(YearMonth closedMonth, Period month, Period year, GroupMatrix matrix,
                                           List<String> warnings) {
        return new InsightsViewModel(LocalDate.of(2026, 11, 18), true, false, LocalDate.of(2026, 9, 1), null,
            closedMonth, null, false, 0, List.of(), List.of(), month, year, matrix, Cell.tooFew(), null, warnings);
    }

    private String renderFull() {
        return renderer.render(model(OCTOBER, month(), year(), matrix(), List.of()));
    }

    // -----------------------------------------------------------------------------------------
    // G-1
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("TC-38: section frame, G-1 heading and markup contract")
    void rendersSectionFrame() {
        String html = renderFull();

        assertTrue(html.startsWith("<section id=\"service-groups\" class=\"sg\" aria-labelledby=\"service-groups-h\">"
            + "<h2 id=\"service-groups-h\">Servicegruppen: Was wird wo gebucht?</h2>"
            + "<h3 class=\"st-sub\">Servicegruppen gesamt</h3>"));
        assertTrue(html.contains("<p class=\"st-range\">aktueller Monat bis gestern (01.11.–18.11.2026) · alle "
            + "Standorte · 286 Buchungen</p>"));
        assertTrue(html.contains("<p class=\"st-range\">laufendes Jahr bis gestern (01.01.–18.11.2026) · alle "
            + "Standorte · 849 Buchungen</p>"));
        CellHtmlTest.assertMarkupContract(html);
    }

    @Test
    @DisplayName("AC-16: G-1 bar rows with role=img, aria-label, width and the value as text")
    void rendersGroupBars() {
        String html = renderFull();

        assertTrue(html.contains("<li class=\"dev-row dev-wide\"><span class=\"dev-lbl\">Räderwechsel</span>"
            + "<span class=\"dev-bar\" role=\"img\" aria-label=\"Räderwechsel: 131 Nennungen, 45,8 Prozent der "
            + "Buchungen\"><span class=\"dev-fill st-fill\" style=\"width:45.8%\"></span></span>"
            + "<span class=\"dev-val\"><b>131</b> &middot; 45,8&nbsp;%</span></li>"));
        assertTrue(html.contains("<ul class=\"dev\" aria-label=\"Servicegruppen gesamt, aktueller Monat bis gestern "
            + "(01.11.–18.11.2026)\">"));
        assertEquals(2, CellHtmlTest.count(html, "<ul class=\"dev\""), "month and year one below the other");
    }

    @Test
    @DisplayName("AC-20: hidden groups have width 0, no number, and stand last in catalogue order")
    void hiddenGroupsAreLastWithoutWidth() {
        String html = renderFull();

        assertTrue(html.contains("<span class=\"dev-bar\" role=\"img\" aria-label=\"TÜV: weniger als 5, unterdrückt\">"
            + "<span class=\"dev-fill st-fill\" style=\"width:0.0%\"></span></span><span class=\"dev-val\">"
            + "<span class=\"st-sup\">"));
        int tire = html.indexOf(">Räderwechsel<");
        int inspection = html.indexOf(">Inspektion<");
        int extras = html.indexOf(">Zusatzleistungen<");
        int unknown = html.indexOf(">nicht zuordenbar<");
        int tuv = html.indexOf(">TÜV<");
        int pickup = html.indexOf(">Hol &amp; Bring<");
        assertTrue(tire < inspection && inspection < extras && extras < unknown, "visible by count descending");
        assertTrue(unknown < tuv && tuv < pickup, "hidden after all visible ones, catalogue order");
        assertFalse(html.contains(">Mietwagen</span>"), "rental car is a note, not a bar");
    }

    @Test
    @DisplayName("G-3 global: MOBILITY_NOTE per period; hidden count without percentage")
    void rendersTheMobilityNote() {
        String html = renderFull();

        assertTrue(html.contains("<p class=\"st-note\">Zusätzlich mit Mietwagen gebucht: 34 von 286 Buchungen "
            + "(11,9 %).</p>"));
        assertTrue(html.contains("<p class=\"st-note\">Zusätzlich mit Mietwagen gebucht: <span class=\"st-sup\">"
            + "<span aria-hidden=\"true\">&lt; 5</span><span class=\"sr\">weniger als 5, unterdrückt</span></span> "
            + "von 849 Buchungen.</p>"));
        assertTrue(html.contains("<p class=\"st-note\">" + ServiceGroupSectionRenderer.GROUPS_NOTE + "</p>"));
    }

    @Test
    @DisplayName("BR-9 Nr. 2: fewer than 20 bookings → only mentions, .st-thin hint")
    void fewBookingsShowOnlyMentions() {
        Period thin = new Period("aktueller Monat bis gestern", "18.11.2026", Cell.of(12),
            Map.of("tire-change", Cell.of(6), "inspection", Cell.suppressed()), Map.of(), Map.of(), Map.of(),
            Cell.tooFew(), null, null, null);
        String html = renderer.render(model(null, thin, null, null, List.of()));

        assertTrue(html.contains("<p class=\"st-thin\">Anteile: zu wenig Daten (n &lt; 20). Gezeigt werden nur "
            + "Nennungen.</p>"));
        assertTrue(html.contains("aria-label=\"Räderwechsel: 6 Nennungen\"><span class=\"dev-fill st-fill\" "
            + "style=\"width:0.0%\"></span></span><span class=\"dev-val\"><b>6</b></span>"));
        CellHtmlTest.assertMarkupContract(html);
    }

    @Test
    @DisplayName("AC-23: a period without data shows „—“ with „Erfassung seit …“")
    void periodWithoutDataShowsTheEmptyState() {
        Period empty = new Period(null, "01.11.–18.11.2026", Cell.noData(), Map.of(), Map.of(), Map.of(), Map.of(),
            Cell.noData(), null, null, null);
        String html = renderer.render(model(null, empty, null, null, List.of()));

        assertTrue(html.contains("aktueller Monat bis gestern (01.11.–18.11.2026) · alle Standorte · "
            + "<span class=\"st-nd\">"));
        assertTrue(html.contains("Erfassung seit 01.09.2026 – noch keine ausgewerteten Tage."));
        assertFalse(html.contains("<ul class=\"dev\""));

        String none = renderer.render(model(null, null, null, null, List.of()));
        assertTrue(none.contains("<h3 class=\"st-sub\">Servicegruppen gesamt</h3><p class=\"st-empty\">"));
    }

    // -----------------------------------------------------------------------------------------
    // G-2/G-3 matrix
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("TC-26/AC-16: matrix title, range, table in scroll region with all 11 columns")
    void rendersTheMatrixTable() {
        String html = renderFull();

        assertTrue(html.contains("<h3 class=\"st-sub\">Standort × Servicegruppe (Oktober 2026)</h3>"));
        assertTrue(html.contains("aria-label=\"Tabelle Standort mal Servicegruppe, Oktober 2026, horizontal "
            + "scrollbar\"><table class=\"st-table st-heat sg-matrix\"><caption class=\"sr\">Standort mal "
            + "Servicegruppe, Oktober 2026: Anteil der Buchungen"));
        assertTrue(html.contains("<th scope=\"col\" class=\"st-l\">Standort</th><th scope=\"col\">Buchungen</th>"
            + "<th scope=\"col\">Inspektion</th><th scope=\"col\">TÜV</th><th scope=\"col\">Bremsflüssigkeit</th>"
            + "<th scope=\"col\">Räderwechsel</th><th scope=\"col\">Aktionen / Checks</th>"
            + "<th scope=\"col\">Reparatur / Beanstandung</th><th scope=\"col\">Karosserie / Frontscheibe</th>"
            + "<th scope=\"col\">Zusatzleistungen</th><th scope=\"col\">Hol &amp; Bring</th>"
            + "<th scope=\"col\">nicht zuordenbar</th><th scope=\"col\">Mietwagen</th>"));
    }

    @Test
    @DisplayName("TC-53: G-2 cells 14 %, 15 %, 45 % carry st-heat-1/2/4; hidden cells no heat, no number")
    void rendersHeatCells() {
        String html = renderFull();

        assertTrue(html.contains("<tr><th scope=\"row\">&lt;b&gt;X&lt;/b&gt;</th><td>58</td>"
            + "<td class=\"st-heat-1\">14&nbsp;%</td><td class=\"st-heat-2\">15&nbsp;%</td>"));
        assertTrue(html.contains("<td class=\"st-heat-4\">45&nbsp;%</td>"));
        assertTrue(html.contains("<td>0&nbsp;%</td>"), "0 % without colour");
        assertFalse(html.contains("<td class=\"st-heat-3\""), "a withheld cell never shows its heat level");
        assertTrue(html.contains("<td class=\"st-ka\"><span aria-hidden=\"true\">k.&nbsp;A.</span>"));
        assertFalse(html.contains("30&nbsp;%"), "a withheld cell never shows its percentage");
        assertTrue(html.contains("<td class=\"st-nd\"><span aria-hidden=\"true\">&mdash;</span>"),
            "a missing column cell is „—“");
    }

    @Test
    @DisplayName("TC-26: collective row tr.sg-other with „k. A.“ everywhere incl. the booking count")
    void rendersTheCollectiveRow() {
        String html = renderFull();

        int start = html.indexOf("<tr class=\"sg-other\">");
        assertTrue(start > 0);
        String row = html.substring(start, html.indexOf("</tr>", start));
        assertTrue(row.startsWith("<tr class=\"sg-other\"><th scope=\"row\">weitere Standorte <span class=\"st-small\">"
            + "(unter 20 Buchungen)</span></th><td class=\"st-ka\">"));
        assertEquals(12, CellHtmlTest.count(row, "<td class=\"st-ka\">"));
    }

    @Test
    @DisplayName("TC-53: fixed legend HEAT_LEGEND_SHARE as ul.st-legend below the matrix plus occurring states")
    void rendersTheShareLegendBelowTheMatrix() {
        String html = renderFull();

        String legend = "<ul class=\"st-legend\" aria-label=\"Legende Farbstufen Matrix\">"
            + "<li><span class=\"st-sw st-heat-1\" aria-hidden=\"true\"></span>1–14 %</li>"
            + "<li><span class=\"st-sw st-heat-2\" aria-hidden=\"true\"></span>15–29 %</li>"
            + "<li><span class=\"st-sw st-heat-3\" aria-hidden=\"true\"></span>30–44 %</li>"
            + "<li><span class=\"st-sw st-heat-4\" aria-hidden=\"true\"></span>ab 45 %</li>"
            + "<li><span class=\"st-sw-txt\" aria-hidden=\"true\">0&nbsp;%</span><span class=\"sr\">0 %: </span>"
            + "keine Buchung</li>";
        assertTrue(html.contains(legend));
        assertTrue(html.indexOf(legend) > html.indexOf("</table>"), "legend below its table");
        int legendStart = html.indexOf(legend);
        String items = html.substring(legendStart, html.indexOf("</ul>", legendStart));
        assertTrue(items.contains("weniger als 5, unterdrückt"));
        assertTrue(items.contains("keine Angabe, unterdrückt gegen Rückrechnung"));
        assertTrue(html.contains("<p class=\"st-note\">„Mietwagen“: Anteil der Buchungen des Standorts, die "
            + "zusätzlich mit Mietwagen gebucht wurden.</p>"));
    }

    @Test
    @DisplayName("§17: legend items come from HEAT_LEGEND_SHARE, the collective row from MATRIX_OTHER_BRANCHES")
    void readsTheLegendAndTheCollectiveLabelFromTheirSources() {
        String html = renderFull();

        List<String> items = CellHtml.legendItems(ServiceGroupSectionRenderer.HEAT_LEGEND_SHARE);
        assertEquals(5, items.size());
        for (String item : items.subList(0, 4)) {
            assertTrue(html.contains("aria-hidden=\"true\"></span>" + item + "</li>"), item);
        }
        assertTrue(html.contains(CellHtml.legendZero(items.get(4))));
        String label = InsightsViewModelBranches.MATRIX_OTHER_BRANCHES;
        assertTrue(html.contains("<th scope=\"row\">" + label.substring(0, label.indexOf(" (")) + " <span"));
    }

    @Test
    @DisplayName("TC-28: no closed month → matrix title without month and .st-empty")
    void noClosedMonthShowsTheEmptyMatrixState() {
        String html = renderer.render(model(null, month(), null, null, List.of()));

        assertTrue(html.contains("<h3 class=\"st-sub\">Standort × Servicegruppe</h3><p class=\"st-empty\">"
            + "<span class=\"st-empty-dash\" aria-hidden=\"true\">&mdash;</span>Noch kein abgeschlossener Monat "
            + "seit Erfassungsbeginn (01.09.2026).</p>"));
        assertFalse(html.contains("sg-matrix"));

        InsightsViewModel noStart = new InsightsViewModel(null, true, false, null, null, null, null, false, 0,
            null, null, null, null, null, null, null, null);
        assertTrue(renderer.render(noStart).contains("Noch kein abgeschlossener Monat seit Erfassungsbeginn.</p>"));
    }

    @Test
    void matrixWithoutColumnsOrWithoutMobilityIsTolerated() {
        String none = renderer.render(model(OCTOBER, month(), null, null, List.of()));
        assertFalse(none.contains("sg-matrix"));

        GroupMatrix small = new GroupMatrix(List.of("inspection", "foo<"),
            List.of(new MatrixRow("A", Cell.of(20), null, false)));
        String html = renderer.render(model(OCTOBER, null, null, small, List.of()));
        assertTrue(html.contains("<th scope=\"col\">foo&lt;</th>"));
        assertFalse(html.contains("„Mietwagen“"));
        CellHtmlTest.assertMarkupContract(html);
    }

    @Test
    @DisplayName("TC-48/BR-4: history unavailable → only the warning; switched off → nothing")
    void historyUnavailableAndSwitchedOff() {
        String html = renderer.render(model(OCTOBER, month(), year(), matrix(),
            List.of(InsightsViewModel.WARN_HISTORY_UNAVAILABLE, "<b>w</b>")));
        assertTrue(html.contains("<p class=\"st-warn\">Historie derzeit nicht verfügbar</p>"));
        assertTrue(html.contains("<p class=\"st-warn\">&lt;b&gt;w&lt;/b&gt;</p>"));
        assertFalse(html.contains("<h3"));

        assertEquals("", renderer.render(null));
        assertEquals("", renderer.render(new InsightsViewModel(null, false, true, null, null, null, null, false, 0,
            null, null, null, null, null, null, null, List.of())));
    }

    @Test
    void orderedGroupsHandlesNullsAndForeignKeys() {
        assertTrue(ServiceGroupSectionRenderer.orderedGroups(null).isEmpty());
        Map<String, Cell> groups = new HashMap<>();
        groups.put("zzz", null);
        groups.put(null, Cell.of(99));
        groups.put("inspection", Cell.percentOf(10.0));
        groups.put("repair", Cell.of(10));
        List<Map.Entry<String, Cell>> ordered = ServiceGroupSectionRenderer.orderedGroups(groups);
        assertEquals(List.of("inspection", "repair", "zzz"), ordered.stream().map(e -> e.getKey()).toList());
        assertEquals(CellState.NO_DATA, ordered.get(2).getValue().state());
        assertEquals("", ServiceGroupSectionRenderer.label(null));
        assertEquals("Mietwagen", ServiceGroupSectionRenderer.label("mobility"));
    }
}
