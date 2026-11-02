package com.autohaus.backend.usage.insights;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rendering of S6 „Buchungsstrecke" incl. the drop-off scenarios (REQ-038 §11.2, AC-19, N1) /
 * Rendering der Buchungsstrecke mit Absprungszenarien.
 *
 * <p>Covers the render part of TC-64 (rows in view-model order with A-0 last, sum row „Alle
 * Abbrüche … = … Besuche − … Buchungen", „&lt; 5" as {@code .st-sup}, secondary suppression as
 * {@code .st-ka} „k. A." — never „•" —, merged column „Sonstige (Tablet, Unbekannt)", action hint
 * per row with „Messartefakt möglich" for A-0/A-8, empty state {@code .st-empty}), the render side
 * of TC-52 (device columns incl. „Sonstige", only „Alle" plus {@code DEVICE_TOO_FEW}), AC-35 (no
 * subsection while the switch is off) and the S6 part of TC-38. / Deckt TC-38, TC-52 und TC-64
 * (Render-Teil) ab.
 */
class FunnelSectionRendererTest {

    private static final LocalDate FUNNEL_SINCE = LocalDate.of(2026, 11, 1);

    private final FunnelSectionRenderer renderer = new FunnelSectionRenderer();

    // -----------------------------------------------------------------------------------------
    // Fixtures / Testdaten
    // -----------------------------------------------------------------------------------------

    private static Cell count(long value, double percent) {
        return new Cell(value, null, percent, 0, CellState.VALUE);
    }

    private static List<FunnelStage> stages() {
        return List.of(
            new FunnelStage("visits", Cell.of(2140), Cell.percentOf(100.0), null, count(620, 29.0)),
            new FunnelStage("services", Cell.of(1520), Cell.percentOf(71.0), Cell.percentOf(71.0), count(340, 15.9)),
            new FunnelStage("notes", Cell.of(1180), Cell.percentOf(55.1), Cell.percentOf(77.6), Cell.of(170)),
            new FunnelStage("search", Cell.of(1010), Cell.withheld(), Cell.percentOf(85.6), Cell.suppressed()),
            new FunnelStage("booking", Cell.of(286), Cell.percentOf(13.4), Cell.withheld(), null));
    }

    private static Map<String, List<Cell>> byDevice() {
        Map<String, List<Cell>> byDevice = new LinkedHashMap<>();
        byDevice.put("mobile", List.of(count(419, 30.3), Cell.of(226), Cell.of(113), Cell.of(465)));
        byDevice.put("desktop", List.of(Cell.of(189), Cell.of(107), Cell.withheld(), Cell.of(252)));
        byDevice.put("other", List.of(Cell.of(12), Cell.of(7)));
        return byDevice;
    }

    private static Map<String, Cell> devices(Cell mobile, Cell desktop, Cell other) {
        Map<String, Cell> map = new LinkedHashMap<>();
        map.put("mobile", mobile);
        map.put("desktop", desktop);
        map.put("other", other);
        return map;
    }

    private static DropoffRow scenario(String key, String hint, List<Cell> steps, Cell total, Cell share) {
        return new DropoffRow(key, hint, steps, total, share, devices(Cell.of(10), Cell.of(5), Cell.of(0)));
    }

    private static DropoffView dropoff() {
        List<DropoffRow> rows = List.of(
            new DropoffRow("no-slots", null, List.of(Cell.of(0), Cell.of(0), Cell.of(0), Cell.of(146)), Cell.of(146),
                Cell.percentOf(7.9), devices(Cell.of(94), Cell.of(47), Cell.of(5))),
            scenario("server-error", "Bugs-Tab prüfen", List.of(Cell.of(38), Cell.of(21), Cell.of(12), Cell.of(18)),
                Cell.of(89), Cell.percentOf(4.8)),
            scenario("error-then-quit", null, List.of(Cell.of(11), Cell.withheld(), Cell.suppressed(), Cell.of(8)),
                Cell.of(30), Cell.percentOf(1.6)),
            scenario("requests-lost", "Netz bzw. Circuit Breaker",
                List.of(Cell.withheld(), Cell.suppressed(), Cell.suppressed(), Cell.of(5)), Cell.of(18),
                Cell.percentOf(1.0)),
            new DropoffRow("booking-rejected", null, List.of(Cell.of(0), Cell.of(0), Cell.of(0), Cell.suppressed()),
                Cell.suppressed(), Cell.withheld(), devices(Cell.withheld(), Cell.suppressed(), Cell.of(0))),
            scenario("quiet", InsightsViewModelFunnel.SC_QUIET_HINT + " – " + InsightsViewModelFunnel.HINT_ARTIFACT,
                List.of(Cell.of(542), Cell.of(283), Cell.of(129), Cell.of(342)),
                Cell.of(1296), Cell.percentOf(69.9)));
        DropoffRow total = new DropoffRow("total", null, List.of(Cell.of(620), Cell.of(340), Cell.of(170),
            Cell.of(724)), Cell.of(1854), Cell.percentOf(100.0), devices(Cell.of(1223), Cell.of(600), Cell.of(31)));
        return new DropoffView(FUNNEL_SINCE, rows, total, List.of("mobile", "desktop", "other"),
            List.of("tablet", "unknown"));
    }

    private static FunnelView funnel(List<String> columns, List<String> others, DropoffView dropoff) {
        Period period = new Period("aktueller Monat bis gestern", "01.11.–18.11.2026", Cell.of(286), Map.of(),
            Map.of(), Map.of(), Map.of(), Cell.percentOf(37.0), null, null, null);
        return new FunnelView(period, stages(), columns, others, byDevice(), dropoff);
    }

    private static InsightsViewModel model(FunnelView funnel, LocalDate since, List<String> warnings) {
        return new InsightsViewModel(LocalDate.of(2026, 11, 18), false, true, null, since, null, null, false, 0,
            null, null, null, null, null, null, funnel, warnings);
    }

    private String renderFull() {
        return renderer.render(model(funnel(List.of("mobile", "desktop", "other"), List.of("tablet", "unknown"),
            dropoff()), FUNNEL_SINCE, List.of()));
    }

    private static String rowOf(String html, String head) {
        int start = html.indexOf("<tr><th scope=\"row\">" + head);
        assertTrue(start >= 0, "row " + head);
        return html.substring(start, html.indexOf("</tr>", start));
    }

    // -----------------------------------------------------------------------------------------
    // Funnel / Trichter
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("TC-38: section frame, intro, range line and markup contract")
    void rendersSectionFrame() {
        String html = renderFull();

        assertTrue(html.startsWith("<section id=\"funnel\" class=\"fn\" aria-labelledby=\"funnel-h\">"
            + "<h2 id=\"funnel-h\">Buchungsstrecke: Wo steigen Besucher aus?</h2><p class=\"st-intro\">"));
        assertTrue(html.contains("<p class=\"st-range\">aktueller Monat bis gestern (01.11.–18.11.2026) · Erfassung "
            + "seit 01.11.2026 · 2.140 Sitzungen</p>"));
        assertTrue(html.endsWith("<p class=\"st-note\">" + FunnelSectionRenderer.FUNNEL_NOTE + "</p></section>"));
        CellHtmlTest.assertMarkupContract(html);
    }

    @Test
    @DisplayName("AC-19: five stages with number, „% vom Start“ and „% der Vorstufe“; stage 4 „Terminsuche ausgeführt“")
    void rendersTheFiveStages() {
        String html = renderFull();

        assertEquals(5, CellHtmlTest.count(html, "<li class=\"fn-step\">"));
        assertTrue(html.contains("<ol class=\"fn-steps\" aria-label=\"Trichter der Buchungsstrecke\">"
            + "<li class=\"fn-step\"><span class=\"fn-lbl\">1 &middot; Besuch (App-Aufruf)</span><span class=\"fn-bar\" "
            + "aria-hidden=\"true\"><span class=\"fn-fill\" style=\"width:100.0%\"></span></span><span class=\"fn-val\">"
            + "<b>2.140</b> &middot; 100,0 % vom Start</span></li>"));
        assertTrue(html.contains("<span class=\"fn-val\"><b>1.520</b> &middot; 71,0 % vom Start<span class=\"fn-prev\">"
            + "71,0 % der Vorstufe</span></span>"));
        assertTrue(html.contains("<span class=\"fn-lbl\">4 &middot; Terminsuche ausgeführt</span>"));
        assertFalse(html.contains("Termine angezeigt"), "BR-8: stage 4 is not „Termine angezeigt“");
    }

    @Test
    @DisplayName("TC-57: a withheld percentage renders „k. A.“ and bar width 0")
    void withheldPercentagesHaveNoWidthAndNoNumber() {
        String html = renderFull();

        assertTrue(html.contains("<span class=\"fn-fill\" style=\"width:0.0%\"></span></span><span class=\"fn-val\">"
            + "<b>1.010</b> &middot; <span class=\"st-ka\"><span aria-hidden=\"true\">k.&nbsp;A.</span>"
            + "<span class=\"sr\">keine Angabe, unterdrückt gegen Rückrechnung</span></span> vom Start"));
        assertTrue(html.contains("<b>286</b> &middot; 13,4 % vom Start<span class=\"fn-prev\"><span class=\"st-ka\">"));
    }

    @Test
    @DisplayName("TC-52: drop-out table with Alle, visible classes and „Sonstige (Tablet, Unbekannt)“")
    void rendersTheDropoutTableWithMergedColumn() {
        String html = renderFull();

        assertTrue(html.contains("aria-label=\"Tabelle Ausstieg nach Stufe und Geräteklasse, horizontal scrollbar\">"
            + "<table class=\"st-table fn-table\"><caption class=\"sr\">Ausstieg nach Stufe und Geräteklasse, aktueller "
            + "Monat bis gestern (01.11.–18.11.2026): Sitzungen je höchster erreichter Stufe; Tablet und Unbekannt haben "
            + "je weniger als 20 Sitzungen und sind als Sonstige zusammengefasst.</caption>"));
        assertTrue(html.contains("<th scope=\"col\" class=\"st-l\">Ausstieg <span class=\"st-small\">zuletzt gesehene "
            + "Seiten</span></th><th scope=\"col\">Alle</th><th scope=\"col\">Mobil</th><th scope=\"col\">Desktop</th>"
            + "<th scope=\"col\">Sonstige (Tablet, Unbekannt)</th>"));
        assertEquals("<tr><th scope=\"row\">nach Stufe 1 <span class=\"st-small\">Standort/Marke</span></th>"
            + "<td><b>620</b><span class=\"fn-pct\">29,0&nbsp;%</span></td><td><b>419</b><span class=\"fn-pct\">"
            + "30,3&nbsp;%</span></td><td><b>189</b></td><td><b>12</b></td>", rowOf(html, "nach Stufe 1"));
        assertTrue(rowOf(html, "nach Stufe 3").contains("<td class=\"st-ka\">"));
        String stage4 = rowOf(html, "nach Stufe 4");
        assertTrue(stage4.contains("Terminwahl, Werkstattkalender, Fahrzeugdaten oder Übersicht"));
        assertTrue(stage4.startsWith("<tr><th scope=\"row\">nach Stufe 4 <span class=\"st-small\">Terminwahl, "
            + "Werkstattkalender, Fahrzeugdaten oder Übersicht</span></th><td class=\"st-sup\">"));
        assertTrue(stage4.endsWith("<td class=\"st-nd\"><span aria-hidden=\"true\">&mdash;</span><span class=\"sr\">"
            + "keine Daten</span></td>"), "a missing device cell is „—“");
        assertFalse(html.contains("nach Stufe 5"));
    }

    @Test
    void singleMergedClassIsNamedInTheCaption() {
        String html = renderer.render(model(funnel(List.of("mobile", "other"), List.of("tablet"), null), FUNNEL_SINCE,
            List.of()));

        assertTrue(html.contains("Sitzungen je höchster erreichter Stufe; Tablet haben je weniger als 20 Sitzungen"));
        assertTrue(html.contains("<th scope=\"col\">Sonstige (Tablet)</th>"));
    }

    @Test
    @DisplayName("TC-52 (4): no own class left → only „Alle“ plus .st-thin DEVICE_TOO_FEW")
    void onlyAllColumnWithoutOwnClasses() {
        String html = renderer.render(model(funnel(List.of(), List.of(), null), FUNNEL_SINCE, List.of()));

        assertTrue(html.contains("<p class=\"st-thin\">Aufschlüsselung nach Geräteklasse: zu wenig Daten (je Klasse "
            + "n &lt; 20)</p>"));
        assertTrue(html.contains("<th scope=\"col\">Alle</th></tr></thead>"));
        assertFalse(html.contains(">Mobil<"));
        assertTrue(html.contains("Sitzungen je höchster erreichter Stufe.</caption>"));
    }

    // -----------------------------------------------------------------------------------------
    // Drop-off scenarios / Absprungszenarien (TC-64)
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("AC-35: without usage.dropoff.enabled the subsection is missing entirely")
    void noDropoffSubsectionWhileSwitchedOff() {
        String html = renderer.render(model(funnel(List.of("mobile"), List.of(), null), FUNNEL_SINCE, List.of()));

        assertFalse(html.contains("fn-dropoff"));
        assertFalse(html.contains("Absprungszenarien"));
    }

    @Test
    @DisplayName("TC-64: subsection with heading, range and bar list without the quiet group")
    void rendersTheDropoffSubsection() {
        String html = renderFull();

        assertTrue(html.contains("<section class=\"fn-dropoff\" aria-labelledby=\"fn-dropoff-h\"><h3 id=\"fn-dropoff-h\">"
            + "Absprungszenarien: In welcher Situation steigen Besucher aus?</h3><p class=\"st-range\">aktueller Monat "
            + "bis gestern (01.11.–18.11.2026) · Erfassung seit 01.11.2026 · 1.854 Abbrüche</p>"));
        assertTrue(html.contains("<li class=\"dev-row dev-wide\"><span class=\"dev-lbl\">Keine Termine gefunden</span>"
            + "<span class=\"dev-bar\" aria-hidden=\"true\"><span class=\"dev-fill st-fill\" style=\"width:100.0%\">"
            + "</span></span><span class=\"dev-val\"><b>146</b> &middot; 7,9&nbsp;%</span></li>"));
        assertTrue(html.contains(">Technischer Fehler (Fehlerseite)</span><span class=\"dev-bar\" aria-hidden=\"true\">"
            + "<span class=\"dev-fill st-fill\" style=\"width:61.0%\">"));
        assertTrue(html.contains(">Buchung abgelehnt</span><span class=\"dev-bar\" aria-hidden=\"true\">"
            + "<span class=\"dev-fill st-fill\" style=\"width:0.0%\"></span></span><span class=\"dev-val\">"
            + "<span class=\"st-sup\">"), "a hidden total has no bar width");
        assertFalse(html.contains("<span class=\"dev-lbl\">ohne erkennbaren Anlass</span>"), "bar list without A-0");
        assertTrue(html.contains("<p class=\"st-note\">Ohne die Sammelgruppe „ohne erkennbaren Anlass“ (1.296 Abbrüche, "
            + "69,9&nbsp;%) – siehe Tabelle.</p>"));
    }

    @Test
    @DisplayName("TC-64/BR-31: stage table with „< 5“ (.st-sup) and „k. A.“ (.st-ka), never „•“")
    void rendersTheStageTableWithSuppression() {
        String html = renderFull();

        assertTrue(html.contains("<table class=\"st-table fn-dropoff-table\"><caption class=\"sr\">Absprungszenarien je "
            + "Stufe, aktueller Monat bis gestern (01.11.–18.11.2026)"));
        assertTrue(html.contains("<th scope=\"col\">Stufe 4 <span class=\"st-small\">Terminsuche ausgeführt</span></th>"
            + "<th scope=\"col\">Gesamt</th><th scope=\"col\">Anteil an allen Abbrüchen</th>"
            + "<th scope=\"col\" class=\"st-l\">Handlungsimpuls</th>"));
        assertEquals("<tr><th scope=\"row\">Keine Termine gefunden</th><td>0</td><td>0</td><td>0</td><td>146</td>"
            + "<td><b>146</b></td><td>7,9&nbsp;%</td><td class=\"st-l fn-dropoff-hint\">Kapazität bzw. Terminangebot, "
            + "zusammen mit S1 lesen</td>", rowOf(html, "Keine Termine gefunden"));
        String quit = rowOf(html, "Nach Fehler weitergemacht, dann aufgehört");
        assertTrue(quit.contains("<td>11</td><td class=\"st-ka\"><span aria-hidden=\"true\">k.&nbsp;A.</span>"));
        assertTrue(quit.contains("<td class=\"st-sup\"><span aria-hidden=\"true\">&lt; 5</span>"));
        assertTrue(quit.contains("wie „Technischer Fehler“; der Fehler hat die Sitzung nachhaltig gestört"));
        assertFalse(html.contains("•"), "no second suppression symbol (§19 Abweichung 12)");
    }

    @Test
    @DisplayName("BR-32: fixed action hint per row; A-8 and A-0 carry „Messartefakt möglich“ exactly once")
    void rendersActionHintsWithArtefactNote() {
        String html = renderFull();

        assertTrue(rowOf(html, "Verbindung unterbrochen").endsWith("<td class=\"st-l fn-dropoff-hint\">Netz bzw. "
            + "Circuit Breaker <span class=\"st-nw\">– Messartefakt möglich</span></td>"));
        String quiet = rowOf(html, "ohne erkennbaren Anlass");
        assertEquals(1, CellHtmlTest.count(quiet, "Messartefakt möglich"));
        assertTrue(rowOf(html, "Technischer Fehler (Fehlerseite)").endsWith(">Bugs-Tab prüfen</td>"));
        assertFalse(rowOf(html, "Technischer Fehler (Fehlerseite)").contains("Messartefakt"));
    }

    @Test
    @DisplayName("TC-64: rows keep the view-model order, A-0 aggregated last, sum row as AC-33 equation")
    void rendersRowOrderAndTheSumRow() {
        String html = renderFull();

        int table = html.indexOf("fn-dropoff-table");
        int noSlots = html.indexOf(">Keine Termine gefunden</th>", table);
        int server = html.indexOf(">Technischer Fehler (Fehlerseite)</th>", table);
        int quiet = html.indexOf(">ohne erkennbaren Anlass</th>", table);
        assertTrue(noSlots < server && server < quiet);
        assertTrue(html.contains("<tfoot><tr class=\"fn-dropoff-sum\"><th scope=\"row\">Alle Abbrüche <span "
            + "class=\"st-small\">1.854 = 2.140 Besuche − 286 Buchungen</span></th><td>620</td><td>340</td><td>170</td>"
            + "<td>724</td><td><b>1.854</b></td><td>100,0&nbsp;%</td><td></td></tr></tfoot>"));
    }

    @Test
    @DisplayName("AC-33: a drop-off window shorter than the funnel month shows „Alle Abbrüche“ without a false equation")
    void omitsTheEquationWhenItDoesNotHold() {
        DropoffView full = dropoff();
        DropoffRow shorter = new DropoffRow("total", null, full.total().byStep(), Cell.of(1200),
            Cell.percentOf(100.0), full.total().byDevice());
        DropoffView view = new DropoffView(FUNNEL_SINCE, full.rows(), shorter, full.deviceColumns(),
            full.otherDevices());

        String html = renderer.render(model(funnel(List.of("mobile", "desktop", "other"),
            List.of("tablet", "unknown"), view), FUNNEL_SINCE, List.of()));

        assertTrue(html.contains("<tfoot><tr class=\"fn-dropoff-sum\"><th scope=\"row\">Alle Abbrüche</th>"),
            "1.200 ≠ 2.140 − 286: no equation");
        assertFalse(html.contains("Besuche − 286 Buchungen"));
    }

    @Test
    @DisplayName("TC-64/BR-31: device table with „Sonstige (Tablet, Unbekannt)“, totals and the merge note")
    void rendersTheDeviceTable() {
        String html = renderFull();

        assertTrue(html.contains("<h4 class=\"st-sub\">Nach Geräteklasse</h4><div class=\"st-scroll\" role=\"region\" "
            + "tabindex=\"0\" aria-label=\"Tabelle Absprungszenarien nach Geräteklasse, horizontal scrollbar\">"
            + "<table class=\"st-table fn-dropoff-dev\"><caption class=\"sr\">"));
        assertTrue(html.contains("<th scope=\"col\" class=\"st-l\">Szenario</th><th scope=\"col\">Mobil</th>"
            + "<th scope=\"col\">Desktop</th><th scope=\"col\">Sonstige (Tablet, Unbekannt)</th>"
            + "<th scope=\"col\">Gesamt</th></tr></thead>"));
        int dev = html.indexOf("fn-dropoff-dev");
        assertTrue(html.indexOf("<tr><th scope=\"row\">Keine Termine gefunden</th><td>94</td><td>47</td><td>5</td>"
            + "<td><b>146</b></td></tr>", dev) > dev);
        assertTrue(html.contains("<tfoot><tr><th scope=\"row\">Alle Abbrüche</th><td>1.223</td><td>600</td><td>31</td>"
            + "<td><b>1.854</b></td></tr></tfoot>"));
        assertTrue(html.contains(" Tablet und Unbekannt haben je unter 20 Abbrüche und stehen zusammen als "
            + "„Sonstige“.</p>"));
    }

    @Test
    @DisplayName("TC-64/AC-35: empty drop-off view → .st-empty „Erfassung seit … – noch keine ausgewerteten Tage.“")
    void emptyDropoffShowsTheEmptyState() {
        DropoffView empty = new DropoffView(LocalDate.of(2026, 11, 19), List.of(), null, List.of(), List.of());
        String html = renderer.render(model(funnel(List.of("mobile"), List.of(), empty), FUNNEL_SINCE, List.of()));

        assertTrue(html.contains("<section class=\"fn-dropoff\" aria-labelledby=\"fn-dropoff-h\"><h3 id=\"fn-dropoff-h\">"
            + "Absprungszenarien: In welcher Situation steigen Besucher aus?</h3><p class=\"st-empty\"><span "
            + "class=\"st-empty-dash\" aria-hidden=\"true\">&mdash;</span>Erfassung seit 19.11.2026 – noch keine "
            + "ausgewerteten Tage.</p></section>"));
        assertFalse(html.contains("fn-dropoff-table"));

        DropoffView noStart = new DropoffView(null, null, null, null, null);
        assertTrue(renderer.render(model(funnel(List.of(), List.of(), noStart), null, List.of()))
            .contains("</h3><p class=\"st-empty\"><span class=\"st-empty-dash\" aria-hidden=\"true\">&mdash;</span>"
                + "Erfassung noch nicht begonnen.</p></section>"));
    }

    @Test
    void dropoffWithoutDeviceColumnsOrVisibleTotals() {
        List<DropoffRow> rows = List.of(
            new DropoffRow("quiet-3", null, null, Cell.of(40), Cell.percentOf(100.0), null),
            new DropoffRow("foo<", "", List.of(Cell.of(1)), Cell.withheld(), null, null));
        DropoffRow total = new DropoffRow("total", null, null, Cell.withheld(), null, null);
        DropoffView view = new DropoffView(null, rows, total, null, List.of("tablet"));
        List<FunnelStage> stages = List.of(new FunnelStage("visits", Cell.suppressed(), null, null, null));
        FunnelView funnel = new FunnelView(null, stages, null, null, null, view);
        String html = renderer.render(model(funnel, null, List.of()));

        assertTrue(html.contains("<p class=\"st-range\"><span class=\"st-sup\">"), "range without period and start");
        assertTrue(html.contains("<p class=\"st-range\"><span class=\"st-ka\">"), "drop-off range without start");
        assertTrue(html.contains("<th scope=\"row\">ohne erkennbaren Anlass – nach Stufe 3</th>"));
        assertTrue(html.contains(">" + InsightsViewModelFunnel.SC_QUIET_HINT
            + " <span class=\"st-nw\">– Messartefakt möglich</span></td>"), "fallback hint for A-0");
        assertTrue(html.contains("<th scope=\"row\">foo&lt;</th>"));
        assertTrue(html.contains("<tfoot><tr class=\"fn-dropoff-sum\"><th scope=\"row\">Alle Abbrüche</th>"),
            "no equation without visible counters");
        assertFalse(html.contains("fn-dropoff-dev"));
        assertTrue(html.contains("<h4 class=\"st-sub\">Nach Geräteklasse</h4><p class=\"st-thin\">"));
        assertFalse(html.contains("haben je unter 20 Abbrüche"), "no merge note without device columns");
        assertTrue(html.contains("<span class=\"dev-lbl\">foo&lt;</span>"));
        CellHtmlTest.assertMarkupContract(html);
    }

    // -----------------------------------------------------------------------------------------
    // Empty states and labels / Leerzustände und Beschriftungen
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("BR-4/BR-8: funnel switched off or null renders nothing")
    void switchedOffRendersNothing() {
        assertEquals("", renderer.render(null));
        InsightsViewModel off = new InsightsViewModel(null, true, false, null, null, null, null, false, 0, null, null,
            null, null, null, null, funnel(List.of(), List.of(), null), List.of());
        assertEquals("", renderer.render(off));
    }

    @Test
    @DisplayName("§11.5: S6 (current month) shows the pending hint but not the closed-month warning")
    void omitsTheClosedMonthWarning() {
        String partial = InsightsViewModelBuilder.WARN_MONTH_PARTIAL.formatted("18.10.2026");
        String pending = InsightsViewModelBuilder.WARN_DAY_PENDING.formatted("22.10.2026");

        String html = renderer.render(model(funnel(List.of("mobile", "desktop", "other"),
            List.of("tablet", "unknown"), dropoff()), FUNNEL_SINCE, List.of(partial, pending)));

        assertTrue(html.contains("<p class=\"st-warn\">" + pending + "</p>"));
        assertFalse(html.contains(partial));
    }

    @Test
    @DisplayName("AC-23/TC-48: no funnel yet → „Erfassung seit …“; history unavailable → only the warning")
    void emptyAndUnavailableStates() {
        String none = renderer.render(model(null, FUNNEL_SINCE, List.of("<b>w</b>")));
        assertTrue(none.contains("<p class=\"st-warn\">&lt;b&gt;w&lt;/b&gt;</p>"));
        assertTrue(none.contains("Erfassung seit 01.11.2026 – noch keine ausgewerteten Tage."));
        assertTrue(none.contains(FunnelSectionRenderer.FUNNEL_NOTE));

        String unavailable = renderer.render(model(funnel(List.of(), List.of(), dropoff()), FUNNEL_SINCE,
            List.of(InsightsViewModel.WARN_HISTORY_UNAVAILABLE)));
        assertTrue(unavailable.contains("<p class=\"st-warn\">Historie derzeit nicht verfügbar</p>"));
        assertFalse(unavailable.contains("fn-steps"));
        assertFalse(unavailable.contains("st-note"));

        FunnelView noStages = new FunnelView(null, List.of(), List.of(), List.of(), Map.of(), null);
        String empty = renderer.render(model(noStages, FUNNEL_SINCE, null));
        assertTrue(empty.contains("<p class=\"st-range\">Erfassung seit 01.11.2026 · <span class=\"st-nd\">"));
        assertTrue(empty.contains("<p class=\"st-empty\">"));
        assertFalse(empty.contains("fn-steps"));
    }

    @Test
    @DisplayName("AC-23: stages without collected data show „Erfassung seit …“, never a funnel of „—“")
    void stagesWithoutDataShowTheEmptyState() {
        List<FunnelStage> none = List.of(new FunnelStage("visits", Cell.noData(), Cell.noData(), Cell.noData(),
            Cell.noData()));
        FunnelView view = new FunnelView(null, none, List.of(), List.of(), Map.of(), null);
        String html = renderer.render(model(view, FUNNEL_SINCE, List.of()));

        assertTrue(html.contains("Erfassung seit 01.11.2026 – noch keine ausgewerteten Tage."));
        assertFalse(html.contains("fn-steps"));
        assertFalse(html.contains("<table"));
    }

    @Test
    void labelsForScenariosAndDevices() {
        assertEquals("ohne erkennbaren Anlass", FunnelSectionRenderer.scenarioLabel("quiet"));
        assertEquals("ohne erkennbaren Anlass – nach Stufe 2", FunnelSectionRenderer.scenarioLabel("quiet-2"));
        assertEquals("Termin war inzwischen vergeben", FunnelSectionRenderer.scenarioLabel("slot-conflict"));
        assertEquals("foo", FunnelSectionRenderer.scenarioLabel("foo"));
        assertEquals("", FunnelSectionRenderer.scenarioLabel(null));
        assertEquals("Sonstige", FunnelSectionRenderer.deviceLabel("other", null));
        assertEquals("Sonstige (Desktop, Tablet, Unbekannt)",
            FunnelSectionRenderer.deviceLabel("other", List.of("desktop", "tablet", "unknown")));
        assertEquals("Tablet", FunnelSectionRenderer.deviceLabel("tablet", List.of()));
        assertEquals("tv", FunnelSectionRenderer.deviceLabel("tv", List.of()));
        assertEquals("", FunnelSectionRenderer.deviceLabel(null, List.of()));
        assertEquals(12, FunnelSectionRenderer.SCENARIO_LABELS.size());
        assertEquals(13, FunnelSectionRenderer.SCENARIO_HINTS.size());
    }

    @Test
    @DisplayName("BR-32: the fallback hints are the SC_*_HINT texts of the view model — one source")
    void fallbackHintsComeFromTheViewModel() {
        assertEquals(InsightsViewModelFunnel.SC_SERVER_ERROR_HINT, FunnelSectionRenderer.SCENARIO_HINTS.get("server-error"));
        assertEquals(InsightsViewModelFunnel.SC_SLOW_HINT,
            FunnelSectionRenderer.SCENARIO_HINTS.get(DropoffScenario.SLOW_RESPONSE.key()));
        assertEquals(InsightsViewModelFunnel.SC_QUIET_HINT, FunnelSectionRenderer.SCENARIO_HINTS.get("quiet"));
        for (DropoffScenario scenario : DropoffScenario.values()) {
            if (scenario.ordinal() < DropoffScenario.QUIET_1.ordinal()) {
                String hint = FunnelSectionRenderer.SCENARIO_HINTS.get(scenario.key());
                assertNotNull(hint, scenario.key());
                assertTrue(InsightsViewModelFunnel.hint(scenario.ordinal()).startsWith(hint),
                    "the view model builds the same hint for " + scenario.key());
            }
        }
        assertEquals(InsightsViewModelFunnel.QUIET_SCENARIO, FunnelSectionRenderer.QUIET_KEY);
        assertEquals(InsightsViewModelFunnel.OTHER_DEVICES, FunnelSectionRenderer.OTHER_KEY);
    }
}
