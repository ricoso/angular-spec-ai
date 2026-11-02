package com.autohaus.backend.usage.insights;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rendering of S4 „Buchungs- und Terminzeiten" — T-1..T-5 (REQ-038 §11.2, AC-17) / Rendering der
 * Buchungs- und Terminzeiten.
 *
 * <p>Covers the render part of TC-53 (heat levels and the runtime legend „bis 11 · 12–22 · 23–33 ·
 * ab 34" as {@code ul.st-legend} below every T-1 and T-3 table), TC-51 (b)/(d) („ohne Terminzeit:
 * 2" only for n &gt; 0), the S4 part of TC-38 (T-2 tile with „n &lt; 20" as {@code .st-nd},
 * caption/scope, scroll regions, no {@code .us-*}) and TC-27 (a hidden heat cell has no colour and
 * no number). / Deckt TC-27, TC-38, TC-51 und TC-53 (Render-Teil) ab.
 */
class TimingSectionRendererTest {

    private final TimingSectionRenderer renderer = new TimingSectionRenderer();

    // -----------------------------------------------------------------------------------------
    // Fixtures / Testdaten
    // -----------------------------------------------------------------------------------------

    private static Map<String, Cell> row(Cell... cells) {
        List<String> keys = cells.length == 3 ? List.of("day", "evening", "night")
            : List.of("early", "morning", "midday", "afternoon", "late");
        Map<String, Cell> row = new LinkedHashMap<>();
        for (int i = 0; i < cells.length; i++) {
            row.put(keys.get(i), cells[i]);
        }
        return row;
    }

    private static Period month() {
        Map<String, Map<String, Cell>> bookedWhen = new LinkedHashMap<>();
        bookedWhen.put("MO", row(Cell.of(44, 4), Cell.of(9, 1), Cell.suppressed()));
        bookedWhen.put("TU", row(Cell.of(14, 2), Cell.of(33, 3), Cell.of(34, 4)));
        for (String day : List.of("WE", "TH", "FR", "SA", "SU")) {
            bookedWhen.put(day, row(Cell.of(0), Cell.of(0), Cell.of(0)));
        }
        Map<String, Map<String, Cell>> slots = new LinkedHashMap<>();
        for (String day : List.of("MO", "TU", "WE", "TH", "FR", "SA")) {
            slots.put(day, row(Cell.of(9, 2), Cell.of(13, 3), Cell.of(0), Cell.of(24, 4), Cell.suppressed()));
        }
        slots.put("SU", row(Cell.of(0), Cell.of(0), Cell.of(0), Cell.of(0), Cell.of(0)));
        Map<String, Cell> lead = new LinkedHashMap<>();
        lead.put("0-1", new Cell(38L, null, 13.3, 0, CellState.VALUE));
        lead.put("2-3", new Cell(71L, null, 24.8, 0, CellState.VALUE));
        lead.put("4-7", Cell.withheld());
        lead.put("8-14", new Cell(54L, null, 18.9, 0, CellState.VALUE));
        lead.put("15-30", Cell.suppressed());
        lead.put("31+", Cell.percentOf(3.5));
        return new Period("aktueller Monat bis gestern", "01.11.–18.11.2026", Cell.of(286), Map.of(), bookedWhen,
            slots, lead, new Cell(105L, null, 37.0, 0, CellState.VALUE), Cell.of(2), HeatLegend.ofMax(44),
            HeatLegend.ofMax(24));
    }

    private static Period year() {
        Map<String, Map<String, Cell>> bookedWhen = new LinkedHashMap<>();
        bookedWhen.put("WE", row(Cell.of(8, 4), Cell.suppressed(), Cell.suppressed()));
        Map<String, Map<String, Cell>> slots = new LinkedHashMap<>();
        slots.put("MO", row(Cell.of(0), Cell.suppressed(), Cell.of(0), Cell.suppressed(), Cell.of(0)));
        slots.put("SU", row(Cell.of(0), Cell.of(0), Cell.of(0), Cell.of(0), Cell.of(5, 4)));
        return new Period("laufendes Jahr bis gestern ({range})", "01.01.–18.11.2026", Cell.of(849), Map.of(),
            bookedWhen, slots, null, Cell.tooFew(), Cell.of(0), null, null);
    }

    private static InsightsViewModel model(Period month, Period year, List<String> warnings) {
        return new InsightsViewModel(LocalDate.of(2026, 11, 18), true, false, LocalDate.of(2026, 9, 1), null, null,
            null, false, 0, List.of(), List.of(), month, year, null, Cell.tooFew(), null, warnings);
    }

    private String renderFull() {
        return renderer.render(model(month(), year(), List.of()));
    }

    /** The markup of the first table after {@code marker} / Tabelle nach einer Marke. */
    private static String block(String html, String marker, int occurrence) {
        int at = -1;
        for (int i = 0; i <= occurrence; i++) {
            at = html.indexOf(marker, at + 1);
        }
        return html.substring(at, html.indexOf("</ul>", at));
    }

    // -----------------------------------------------------------------------------------------
    // T-2 tiles / Kacheln
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("TC-38: section frame and markup contract")
    void rendersSectionFrame() {
        String html = renderFull();

        assertTrue(html.startsWith("<section id=\"timing\" class=\"tm\" aria-labelledby=\"timing-h\">"
            + "<h2 id=\"timing-h\">Buchungs- und Terminzeiten</h2><div class=\"tm-kpis\">"));
        assertEquals(2, CellHtmlTest.count(html, "<div class=\"st-grid\">"), "T-1 next to T-3 per period");
        CellHtmlTest.assertMarkupContract(html);
    }

    @Test
    @DisplayName("AC-17: T-2 tile with value, label and numerator of denominator")
    void rendersTheOffHoursTile() {
        String html = renderFull();

        assertTrue(html.contains("<div class=\"tm-kpi\"><div class=\"tm-kpi-val\">37&nbsp;%</div>"
            + "<div class=\"tm-kpi-lbl\">außerhalb Mo–Fr 07–18 Uhr gebucht</div><div class=\"tm-kpi-sub\">aktueller "
            + "Monat bis gestern (01.11.–18.11.2026) &middot; 105 von 286</div></div>"));
        assertEquals("außerhalb Mo–Fr 07–18 Uhr gebucht", TimingSectionRenderer.offHoursLabel());
    }

    @Test
    @DisplayName("§11.2: T-2 with n < 20 shows .st-nd „n < 20“ (not „—“)")
    void tooFewTileShowsNLessThanTwenty() {
        String html = renderFull();

        assertTrue(html.contains("<div class=\"tm-kpi-val st-nd\"><span aria-hidden=\"true\">n&nbsp;&lt;&nbsp;20</span>"
            + "<span class=\"sr\">zu wenig Daten, weniger als 20 Fälle</span></div>"));
        assertTrue(html.contains("laufendes Jahr bis gestern (01.01.–18.11.2026) &middot; zu wenig Daten (n &lt; 20)"));
    }

    @Test
    void tileWithoutVisibleDenominatorOmitsTheFraction() {
        Period hidden = new Period("aktueller Monat bis gestern", null, Cell.suppressed(), Map.of(), null, null, null,
            new Cell(3L, null, 60.0, 0, CellState.VALUE), null, null, null);
        String html = renderer.render(model(hidden, null, List.of()));

        assertTrue(html.contains("<div class=\"tm-kpi-sub\">aktueller Monat bis gestern</div>"));
        assertFalse(html.contains(" von "));
    }

    // -----------------------------------------------------------------------------------------
    // T-1 / T-3 heat tables and legends (TC-53)
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("TC-53: T-1 heat cells 44/9/14/33/34 → st-heat-4/1/2/3/4, hidden cell without colour")
    void rendersBookedWhenHeatCells() {
        String html = renderFull();

        assertTrue(html.contains("<table class=\"st-table st-heat\"><caption class=\"sr\">Wann wird gebucht? "
            + "Buchungen nach Wochentag und Tageszeit der Buchung, aktueller Monat bis gestern (01.11.–18.11.2026); "
            + "Farbstufe relativ zum Höchstwert 44.</caption>"));
        assertTrue(html.contains("<thead><tr><th scope=\"col\" class=\"st-l\">Wochentag</th><th scope=\"col\">Tag "
            + "<span class=\"st-small\">07–18 Uhr</span></th><th scope=\"col\">Abend <span class=\"st-small\">18–22 "
            + "Uhr</span></th><th scope=\"col\">Nacht <span class=\"st-small\">22–07 Uhr</span></th></tr></thead>"));
        assertTrue(html.contains("<tr><th scope=\"row\"><span aria-hidden=\"true\">Mo</span><span class=\"sr\">Montag"
            + "</span></th><td class=\"st-heat-4\">44</td><td class=\"st-heat-1\">9</td><td class=\"st-sup\">"));
        assertTrue(html.contains("<td class=\"st-heat-2\">14</td><td class=\"st-heat-3\">33</td>"
            + "<td class=\"st-heat-4\">34</td>"));
        assertTrue(html.contains("<span class=\"sr\">Sonntag</span></th><td>0</td><td>0</td><td>0</td>"),
            "T-1 always shows Mo–So");
    }

    @Test
    @DisplayName("TC-53: T-1 legend „bis 11 · 12–22 · 23–33 · ab 34“ as ul.st-legend right below the table")
    void rendersTheRelativeLegendBelowT1() {
        String html = renderFull();

        String legend = block(html, "<ul class=\"st-legend\" aria-label=\"Legende Farbstufen Wann wird gebucht?\">", 0);
        assertTrue(legend.contains("<li><span class=\"st-sw st-heat-1\" aria-hidden=\"true\"></span>bis 11</li>"
            + "<li><span class=\"st-sw st-heat-2\" aria-hidden=\"true\"></span>12–22</li>"
            + "<li><span class=\"st-sw st-heat-3\" aria-hidden=\"true\"></span>23–33</li>"
            + "<li><span class=\"st-sw st-heat-4\" aria-hidden=\"true\"></span>ab 34 (größter Wert 44)</li>"
            + "<li><span class=\"st-sw-txt\" aria-hidden=\"true\">0</span><span class=\"sr\">0: </span>keine Buchung</li>"));
        assertTrue(legend.contains("weniger als 5, unterdrückt"));
        assertFalse(legend.contains("keine Angabe"), "only states that occur");
        assertEquals(4, CellHtmlTest.count(html, "</table></div><ul class=\"st-legend\""),
            "a legend below each T-1 and T-3 table of both periods");
    }

    @Test
    @DisplayName("TC-53: T-3 legend „bis 6 · 7–12 · 13–18 · ab 19“, Mo–Sa without an empty Sunday")
    void rendersSlotTableWithLegendAndWithoutEmptySunday() {
        String html = renderFull();

        int start = html.indexOf("<table class=\"st-table st-heat tm-slots\">");
        String table = html.substring(start, html.indexOf("</table>", start));
        assertTrue(table.contains("<th scope=\"col\">früh <span class=\"st-small\">vor 9</span></th>"));
        assertTrue(table.contains("<th scope=\"col\">spät <span class=\"st-small\">ab 16</span></th>"));
        assertTrue(table.contains("<td class=\"st-heat-2\">9</td><td class=\"st-heat-3\">13</td><td>0</td>"
            + "<td class=\"st-heat-4\">24</td><td class=\"st-sup\">"));
        assertFalse(table.contains("Sonntag"), "Sunday only when needed");
        assertTrue(table.contains("Samstag"));
        String legend = block(html, "<ul class=\"st-legend\" aria-label=\"Legende Farbstufen Welche Terminzeiten sind "
            + "gefragt?\">", 0);
        assertTrue(legend.contains(">bis 6</li>") && legend.contains(">7–12</li>") && legend.contains(">13–18</li>")
            && legend.contains(">ab 19 (größter Wert 24)</li>"));
        assertTrue(html.contains("<p class=\"st-note\">Kunden wählen aus dem, was SoftNet anbietet. Die Verteilung "
            + "zeigt also Angebot und Nachfrage.</p>"));
    }

    @Test
    @DisplayName("§10.5: without a legend in the view model it is rebuilt from visible cells only")
    void rebuildsTheLegendFromVisibleCells() {
        String html = renderFull();

        String bookedWhenYear = block(html, "<ul class=\"st-legend\" aria-label=\"Legende Farbstufen Wann wird "
            + "gebucht?\">", 1);
        assertTrue(bookedWhenYear.contains(">bis 2</li>") && bookedWhenYear.contains(">3–4</li>")
            && bookedWhenYear.contains(">5–6</li>") && bookedWhenYear.contains(">ab 7 (größter Wert 8)</li>"));
        String slotYear = block(html, "<ul class=\"st-legend\" aria-label=\"Legende Farbstufen Welche Terminzeiten "
            + "sind gefragt?\">", 1);
        assertTrue(slotYear.contains(">bis 1</li>"));
        assertTrue(slotYear.contains("<span class=\"st-sw st-heat-2\" aria-hidden=\"true\"></span>2</li>"));
        assertTrue(slotYear.contains("<span class=\"st-sw st-heat-3\" aria-hidden=\"true\"></span>3</li>"));
        assertTrue(slotYear.contains(">ab 4 (größter Wert 5)</li>"));

        HeatLegend rebuilt = TimingSectionRenderer.legendOf(null,
            List.of(Cell.of(8), new Cell(50L, null, null, 4, CellState.SUPPRESSED)));
        assertEquals(8, rebuilt.max(), "hidden cells never enter max");
        HeatLegend given = HeatLegend.ofMax(40);
        assertSame(given, TimingSectionRenderer.legendOf(given, List.of()));
    }

    @Test
    @DisplayName("§11.2: Sunday row of T-3 when it carries values; year period rows without data are „—“")
    void rendersSundayWhenNeededAndNoDataCells() {
        String html = renderFull();

        int start = html.lastIndexOf("<table class=\"st-table st-heat tm-slots\">");
        String table = html.substring(start, html.indexOf("</table>", start));
        assertTrue(table.contains("Sonntag"));
        assertTrue(table.contains("<td class=\"st-heat-4\">5</td>"));
        assertTrue(table.contains("<span class=\"sr\">Dienstag</span></th><td class=\"st-nd\">"),
            "a weekday without data shows „—“, never 0");
    }

    @Test
    @DisplayName("TC-53/§17: the relative legend is HEAT_LEGEND_RELATIVE item by item — max 44 → 11/22/33")
    void fillsTheItemsOfHeatLegendRelative() {
        assertEquals("<ul class=\"st-legend\" aria-label=\"Legende Farbstufen x\">"
            + "<li><span class=\"st-sw st-heat-1\" aria-hidden=\"true\"></span>bis 11</li>"
            + "<li><span class=\"st-sw st-heat-2\" aria-hidden=\"true\"></span>12–22</li>"
            + "<li><span class=\"st-sw st-heat-3\" aria-hidden=\"true\"></span>23–33</li>"
            + "<li><span class=\"st-sw st-heat-4\" aria-hidden=\"true\"></span>ab 34 (größter Wert 44)</li>"
            + "<li><span class=\"st-sw-txt\" aria-hidden=\"true\">0</span><span class=\"sr\">0: </span>keine Buchung</li>"
            + "</ul>", TimingSectionRenderer.relativeLegend(HeatLegend.ofMax(44), List.of(), "x"));
        assertEquals(5, CellHtml.legendItems(TimingSectionRenderer.HEAT_LEGEND_RELATIVE).size());
    }

    @Test
    void smallMaximaLeaveOutEmptyLevels() {
        String one = TimingSectionRenderer.relativeLegend(HeatLegend.ofMax(1), List.of(), "x");
        assertFalse(one.contains("bis "));
        assertFalse(one.contains("st-heat-2"));
        assertTrue(one.contains(">ab 1 (größter Wert 1)</li>"));
        String three = TimingSectionRenderer.relativeLegend(HeatLegend.ofMax(3), List.of(), "x");
        assertFalse(three.contains("bis 0"));
        assertTrue(three.contains("st-heat-2\" aria-hidden=\"true\"></span>1</li>"));
        String zero = TimingSectionRenderer.relativeLegend(new HeatLegend(0, 0, 0, 0), List.of(), "x");
        assertFalse(zero.contains("st-sw st-heat"));
        assertTrue(zero.contains("keine Buchung"));
        assertTrue(TimingSectionRenderer.HEAT_LEGEND_RELATIVE.startsWith("bis {upper1}"));
    }

    // -----------------------------------------------------------------------------------------
    // SLOT_UNKNOWN (TC-51) and T-5
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("TC-51 (b)/(d): „ohne Terminzeit: 2“ only for n > 0")
    void rendersSlotUnknownOnlyAboveZero() {
        String html = renderFull();

        assertEquals(1, CellHtmlTest.count(html, "ohne Terminzeit:"));
        assertTrue(html.contains("<p class=\"st-note\">ohne Terminzeit: 2</p>"));

        Period suppressed = new Period("aktueller Monat bis gestern", null, Cell.of(40), Map.of(), null, null, null,
            Cell.percentOf(20.0), Cell.suppressed(), null, null);
        assertTrue(renderer.render(model(suppressed, null, List.of()))
            .contains("<p class=\"st-note\">ohne Terminzeit: <span class=\"st-sup\">"));
        Period missing = new Period("aktueller Monat bis gestern", null, Cell.of(40), Map.of(), null, null, null,
            Cell.percentOf(20.0), null, null, null);
        assertFalse(renderer.render(model(missing, null, List.of())).contains("ohne Terminzeit"));
    }

    @Test
    @DisplayName("AC-17: T-5 bar list with six buckets, hidden buckets without width and number")
    void rendersLeadTimeBars() {
        String html = renderFull();

        assertTrue(html.contains("<h3 class=\"st-sub\">Vorlauf: Tage zwischen Buchung und Termin<span class=\"sr\"> – "
            + "aktueller Monat bis gestern (01.11.–18.11.2026)</span></h3>"));
        assertTrue(html.contains("<li class=\"dev-row\"><span class=\"dev-lbl\">0–1 Tag</span><span class=\"dev-bar\" "
            + "role=\"img\" aria-label=\"0–1 Tag: 38 Buchungen, 13,3 Prozent\"><span class=\"dev-fill st-fill\" "
            + "style=\"width:13.3%\"></span></span><span class=\"dev-val\"><b>38</b> &middot; 13,3&nbsp;%</span></li>"));
        assertTrue(html.contains("aria-label=\"4–7 Tage: keine Angabe, unterdrückt gegen Rückrechnung\">"
            + "<span class=\"dev-fill st-fill\" style=\"width:0.0%\"></span></span><span class=\"dev-val\">"
            + "<span class=\"st-ka\">"));
        assertTrue(html.contains("aria-label=\"über 30 Tage: 3,5 Prozent\""));
        assertEquals(6, CellHtmlTest.count(html, "<li class=\"dev-row\">"), "only the month has T-5 in the fixture");
    }

    @Test
    void leadRowWithoutAnyValueShowsTheDash() {
        Map<String, Cell> lead = new LinkedHashMap<>();
        lead.put("0-1", new Cell(null, null, null, 0, CellState.VALUE));
        Period period = new Period("aktueller Monat bis gestern", null, Cell.of(40), Map.of(), null, null, lead,
            Cell.percentOf(20.0), null, null, null);
        String html = renderer.render(model(period, null, List.of()));

        assertTrue(html.contains("aria-label=\"0–1 Tag: \""));
        assertTrue(html.contains("<span class=\"dev-val\">&mdash;</span>"));
        assertTrue(html.contains("aria-label=\"2–3 Tage: keine Daten\""), "missing bucket is „—“");
    }

    // -----------------------------------------------------------------------------------------
    // Empty states / Leerzustände
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("§11.5: S4 (current month) shows pending/stale hints but not the closed-month warnings")
    void omitsTheClosedMonthWarnings() {
        String partial = InsightsViewModelBuilder.WARN_MONTH_PARTIAL.formatted("18.10.2026");
        String incomplete = InsightsViewModelBuilder.WARN_MONTH_INCOMPLETE.formatted(2);
        String pending = InsightsViewModelBuilder.WARN_DAY_PENDING.formatted("22.10.2026");

        String html = renderer.render(model(month(), year(), List.of(partial, incomplete, pending)));

        assertTrue(html.contains("<p class=\"st-warn\">" + pending + "</p>"));
        assertFalse(html.contains(partial), "WARN_MONTH_PARTIAL belongs to S2 and G-2");
        assertFalse(html.contains(incomplete), "WARN_MONTH_INCOMPLETE belongs to S2 and G-2");
        assertTrue(InsightsViewModelBuilder.isClosedMonthWarning(partial));
        assertTrue(InsightsViewModelBuilder.isClosedMonthWarning(incomplete));
        assertFalse(InsightsViewModelBuilder.isClosedMonthWarning(pending));
        assertFalse(InsightsViewModelBuilder.isClosedMonthWarning(null));
        assertEquals(List.of(), CellHtml.currentPeriodWarnings(null));
    }

    @Test
    @DisplayName("TC-48/AC-23/BR-4: history unavailable, no period, switched off")
    void emptyStates() {
        String unavailable = renderer.render(model(month(), year(), List.of(InsightsViewModel.WARN_HISTORY_UNAVAILABLE)));
        assertTrue(unavailable.contains("<p class=\"st-warn\">Historie derzeit nicht verfügbar</p>"));
        assertFalse(unavailable.contains("tm-kpi"));
        assertFalse(unavailable.contains("<table"));

        String none = renderer.render(model(null, null, List.of("<b>stale</b>")));
        assertTrue(none.contains("<p class=\"st-warn\">&lt;b&gt;stale&lt;/b&gt;</p>"));
        assertTrue(none.contains("Erfassung seit 01.09.2026 – noch keine ausgewerteten Tage."));
        assertFalse(none.contains("tm-kpis"));

        assertEquals("", renderer.render(null));
        assertEquals("", renderer.render(new InsightsViewModel(null, false, true, null, null, null, null, false, 0,
            null, null, month(), null, null, null, null, List.of())));
    }

    @Test
    @DisplayName("AC-23: a period without collected data shows „Erfassung seit …“ instead of tables of „—“")
    void periodWithoutDataShowsTheEmptyState() {
        Period empty = new Period("aktueller Monat bis gestern", "01.11.–18.11.2026", Cell.noData(), Map.of(), Map.of(),
            Map.of(), Map.of(), Cell.noData(), null, null, null);
        String partly = renderer.render(model(empty, year(), List.of()));
        assertTrue(partly.contains("aktueller Monat bis gestern (01.11.–18.11.2026) · alle Standorte · "
            + "<span class=\"st-nd\">"));
        assertTrue(partly.contains("Erfassung seit 01.09.2026 – noch keine ausgewerteten Tage."));
        assertEquals(1, CellHtmlTest.count(partly, "<div class=\"st-grid\">"), "only the year has tables");
        assertTrue(partly.contains("tm-kpis"));

        String none = renderer.render(model(empty, empty, List.of()));
        assertFalse(none.contains("tm-kpis"));
        assertFalse(none.contains("<table"));
        assertEquals(1, CellHtmlTest.count(none, "st-empty\""));
    }
}
