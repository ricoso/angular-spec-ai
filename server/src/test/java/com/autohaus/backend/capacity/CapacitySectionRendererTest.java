package com.autohaus.backend.capacity;

import com.autohaus.backend.common.html.HtmlTextTest;
import com.autohaus.backend.usage.insights.CellHtmlTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.autohaus.backend.capacity.CapacityDayState.BOOKED;
import static com.autohaus.backend.capacity.CapacityDayState.FREE;
import static com.autohaus.backend.capacity.CapacityDayState.NOT_MEASURABLE;
import static com.autohaus.backend.capacity.CapacityDayState.TIGHT_DAY;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Rendering of S1 „Freie Online-Termine je Standort" (REQ-038 §11.2, N2) / Rendering von S1.
 *
 * <p>Covers TC-37 (all five levels and all four grid states, fallback on a failed scan — complete
 * and partial —, escaping of {@code <b>X</b>}), the S1 part of TC-38 (classes, caption/scope,
 * scroll region, no {@code .us-*}), TC-51 (a) (partial warning), the render part of TC-55 (first
 * scan running) and the display side of AC-9, AC-10, AC-26, AC-29 and BR-20/BR-23/BR-26. / Deckt
 * TC-37, TC-38, TC-51 (a) und TC-55 (Render-Teil) ab.
 *
 * <p>The renderer is a pure function over {@link CapacityViewModel}, so the fixtures are built by
 * hand as records; the fixed clock only pins „today" for the scan fixture. / Reine Funktion,
 * handgebaute Records.
 */
class CapacitySectionRendererTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    /** Fixed clock of the fixture: 19.11.2026 22:40 Berlin / fester Clock der Fixture. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-19T21:40:00Z"), BERLIN);
    private static final LocalDate TODAY = LocalDate.now(CLOCK);
    private static final LocalDate SCAN_DAY = TODAY;
    private static final LocalDate TARGET_DAY = LocalDate.of(2026, 11, 20); // Fr
    private static final List<LocalDate> DAYS = List.of(
        LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11),
        LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 14),
        LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 18),
        LocalDate.of(2026, 11, 19));
    private static final String PARTIAL_WARNING = "Scan vom 19.11.2026 unvollständig: 2 Standorte nicht messbar.";

    private final CapacitySectionRenderer renderer = new CapacitySectionRenderer();

    // -----------------------------------------------------------------------------------------
    // Fixtures / Testdaten
    // -----------------------------------------------------------------------------------------

    private static CapacityRow nordtor() {
        return new CapacityRow("VW Nordtor", CapacityLevel.FREE_NEXT_DAY, "morgen frei", 0, null, 14,
            List.of(FREE, FREE, TIGHT_DAY, FREE, FREE, FREE, FREE, TIGHT_DAY, FREE, FREE), nulls(10),
            8, 10, CapacityTrend.MOSTLY_FREE);
    }

    private static CapacityRow hostile() {
        return new CapacityRow("<b>X</b>", CapacityLevel.WITHIN_4_WORKDAYS, "in 2 Werktagen", 2, null, 3,
            List.of(TIGHT_DAY, FREE, TIGHT_DAY, FREE, TIGHT_DAY, FREE, TIGHT_DAY, FREE, TIGHT_DAY, FREE), nulls(10),
            5, 10, CapacityTrend.MIXED);
    }

    private static CapacityRow booked() {
        return new CapacityRow("Audi Zentrum Rheinfeld", CapacityLevel.TIGHT, "belegt: ≥ 5 Werktage", 6, null, 0,
            Collections.nCopies(10, BOOKED), nulls(10), 0, 10, CapacityTrend.MOSTLY_BOOKED);
    }

    private static CapacityRow noneFound() {
        List<CapacityDayState> states = List.of(TIGHT_DAY, FREE, NOT_MEASURABLE, TIGHT_DAY, BOOKED, NOT_MEASURABLE,
            TIGHT_DAY, FREE, TIGHT_DAY, NOT_MEASURABLE);
        List<String> reasons = Arrays.asList(null, null, "timeout", null, null, "timeout", null, null, null, "timeout");
        return new CapacityRow("Audi Hafenstadt", CapacityLevel.NONE_FOUND, "keine Online-Termine gefunden", null,
            null, null, states, reasons, 2, 7, CapacityTrend.MIXED);
    }

    private static CapacityRow notMeasurable() {
        List<CapacityDayState> states = new ArrayList<>(Collections.nCopies(7, NOT_MEASURABLE));
        states.addAll(List.of(TIGHT_DAY, TIGHT_DAY, NOT_MEASURABLE));
        List<String> reasons = new ArrayList<>(Collections.nCopies(7, "softnet-forbidden"));
        reasons.addAll(Arrays.asList(null, null, null));
        return new CapacityRow("Lindenau", CapacityLevel.NOT_MEASURABLE, "nicht messbar", null, "softnet-forbidden",
            null, states, reasons, 0, 2, CapacityTrend.TOO_FEW_DAYS);
    }

    private static List<CapacityRow> allRows() {
        return List.of(nordtor(), hostile(), booked(), noneFound(), notMeasurable());
    }

    private static CapacityViewModel model(List<CapacityRow> rows, List<String> warnings) {
        return new CapacityViewModel(SCAN_DAY, TARGET_DAY, ScanState.PARTIAL, false, null, null,
            LocalDate.of(2026, 10, 5), DAYS, rows, warnings);
    }

    private static List<String> nulls(int n) {
        return Collections.nCopies(n, null);
    }

    // -----------------------------------------------------------------------------------------
    // TC-37: levels, grid, summary / Stufen, Raster, Zusammenfassung
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("TC-37: section frame, range line and markup contract (TC-38)")
    void rendersTheSectionFrameAndRangeLine() {
        String html = renderer.render(model(allRows(), List.of(PARTIAL_WARNING)));

        assertTrue(html.startsWith("<section id=\"capacity\" class=\"cap\" aria-labelledby=\"capacity-h\">"
            + "<h2 id=\"capacity-h\">Freie Online-Termine je Standort</h2>"));
        assertTrue(html.contains("<p class=\"st-range\">Kapazitäts-Scan vom 19.11.2026 · Zieltag: Fr 20.11.2026 "
            + "(nächster Werktag) · Werktage Mo–Sa · Verlauf: letzte 10 Scans (09.11.–19.11.)</p>"));
        assertTrue(html.contains("<table class=\"st-table cap-table\"><caption class=\"sr\">Freie Online-Termine je "
            + "Standort, Scan vom 19.11.2026, sortiert nach der Einordnung des 10-Tage-Verlaufs (meist frei zuerst"));
        assertTrue(html.contains("aria-label=\"Tabelle freie Online-Termine je Standort, horizontal scrollbar\""));
        CellHtmlTest.assertMarkupContract(html);
    }

    @Test
    @DisplayName("§17 CAPACITY_RANGE: the range line names the scan times of the shown scan (Berlin)")
    void namesTheScanTimesInTheRangeLine() {
        CapacityViewModel timed = new CapacityViewModel(SCAN_DAY, TARGET_DAY, ScanState.PARTIAL, false, null,
            null, LocalDate.of(2026, 10, 5), DAYS, allRows(), List.of(),
            Instant.parse("2026-11-19T21:31:12Z"), Instant.parse("2026-11-19T21:35:40Z"), SCAN_DAY);

        String html = renderer.render(timed);

        assertTrue(html.contains("<p class=\"st-range\">Kapazitäts-Scan vom 19.11.2026, 22:31–22:35 Uhr · "
            + "Zieltag: Fr 20.11.2026 (nächster Werktag) · Werktage Mo–Sa · Verlauf: letzte 10 Scans "
            + "(09.11.–19.11.)</p>"), html);
    }

    @Test
    @DisplayName("§17 CAPACITY_RANGE_FALLBACK: the fallback range names the times of the shown scan")
    void namesTheScanTimesOfTheFallbackScan() {
        CapacityViewModel fallback = new CapacityViewModel(SCAN_DAY, TARGET_DAY, ScanState.FAILED, true,
            ScanState.COMPLETE, null, LocalDate.of(2026, 10, 5), DAYS, allRows(), List.of(),
            Instant.parse("2026-11-19T21:31:00Z"), Instant.parse("2026-11-19T21:34:00Z"), SCAN_DAY.plusDays(1));

        String html = renderer.render(fallback);

        assertTrue(html.contains("<p class=\"st-range\">Letzter vollständiger Scan: 19.11.2026, 22:31–22:34 Uhr · "
            + "Zieltag damals: Fr 20.11.2026"), html);
    }

    @Test
    @DisplayName("§11.2 .cap-when: the exact first free date of the row wins over the Mo–Sa derivation")
    void prefersTheFirstFreeDateOfTheRow() {
        CapacityRow sundayFree = new CapacityRow("Sonntagsbetrieb", CapacityLevel.WITHIN_4_WORKDAYS,
            "in 2 Werktagen", 2, null, null, Collections.nCopies(10, TIGHT_DAY), nulls(10), 0, 10,
            CapacityTrend.MOSTLY_BOOKED, LocalDate.of(2026, 11, 22));

        String html = renderer.render(model(List.of(sundayFree), List.of()));

        assertTrue(html.contains("<span class=\"cap-pill\">in 2 Werktagen</span><span class=\"cap-when\">So 22.11."
            + "</span>"), "the Sunday date of the scan, not the derived Monday: " + html);
    }

    @Test
    @DisplayName("TC-51 (a): the partial warning of the view model is a p.st-warn before the range")
    void rendersViewModelWarningsBeforeTheRange() {
        String html = renderer.render(model(allRows(), List.of(PARTIAL_WARNING)));

        String warn = "<p class=\"st-warn\">" + PARTIAL_WARNING + "</p>";
        assertTrue(html.contains(warn));
        assertTrue(html.indexOf(warn) < html.indexOf("class=\"st-range\""));
    }

    @Test
    @DisplayName("TC-37: all five levels as text pill with the §11.2 modifier and .cap-when")
    void rendersAllFiveLevelsAsPills() {
        String html = renderer.render(model(allRows(), List.of()));

        assertTrue(html.contains("<span class=\"cap-pill cap-pill-free\">morgen frei</span>"
            + "<span class=\"cap-when\">Fr 20.11.</span>"));
        assertTrue(html.contains("<span class=\"cap-pill\">in 2 Werktagen</span><span class=\"cap-when\">Mo 23.11.</span>"),
            "Sa 21.11. = 1, Mo 23.11. = 2 working days (Sunday skipped)");
        assertTrue(html.contains("<span class=\"cap-pill cap-pill-tight\">belegt: ≥ 5 Werktage</span>"
            + "<span class=\"cap-when\">Fr 27.11.</span>"));
        assertTrue(html.contains("<span class=\"cap-pill cap-pill-tight\">keine Online-Termine gefunden</span></td>"));
        assertTrue(html.contains("<span class=\"cap-pill cap-pill-na\">nicht messbar</span>"
            + "<span class=\"cap-when\">Grund: kein Zugriff auf die SoftNet-Gruppe</span>"));
    }

    @Test
    @DisplayName("TC-37/AC-10: a failed branch is „nicht messbar“ with reason — never 0")
    void failedBranchIsNeverShownAsZero() {
        String html = renderer.render(model(List.of(notMeasurable()), List.of()));

        int start = html.indexOf("<th scope=\"row\">Lindenau</th>");
        String row = html.substring(start, html.indexOf("</tr>", start));
        assertTrue(row.startsWith("<th scope=\"row\">Lindenau</th><td class=\"st-l\"><span class=\"cap-pill "
            + "cap-pill-na\">nicht messbar</span><span class=\"cap-when\">Grund: kein Zugriff auf die "
            + "SoftNet-Gruppe</span></td><td class=\"cap-day cap-day-na\""), "level is text with reason, never 0");
        assertFalse(row.contains("cap-total"), "no slot count without a K-2 column");
        assertFalse(row.contains("keine Online-Termine gefunden"), "an error is never „none found“ (BR-14)");
        assertTrue(row.contains("an <b>0</b> von 2 Tagen frei"), "the summary counts measurable days only");
    }

    @Test
    @DisplayName("TC-37: 10-day grid with TT.MM. heads, oldest first, short text in every cell")
    void rendersTheTenDayGridOldestFirst() {
        String html = renderer.render(model(allRows(), List.of()));

        assertTrue(html.contains("<th scope=\"colgroup\" colspan=\"10\" class=\"st-l\">Verlauf letzte 10 Tage"
            + "<span class=\"st-small\">Zustand des jeweiligen Zieltags</span></th>"));
        assertTrue(html.contains("<th scope=\"col\">09.11.<span class=\"st-small\">Mo</span></th>"));
        assertTrue(html.contains("<th scope=\"col\">16.11.<span class=\"st-small\">Mo</span></th>"));
        assertTrue(html.indexOf(">09.11.<") < html.indexOf(">19.11.<"), "oldest left, newest right");
        assertTrue(html.contains("<td class=\"cap-day cap-day-free\">frei</td>"));
        assertTrue(html.contains("<td class=\"cap-day cap-day-tight\">knapp</td>"));
        assertTrue(html.contains("<td class=\"cap-day cap-day-booked\">belegt</td>"));
        assertTrue(html.contains("<td class=\"cap-day cap-day-na\" title=\"nicht messbar: Zeitüberschreitung\">"
            + "<span aria-hidden=\"true\">—</span><span class=\"sr\">nicht messbar: Zeitüberschreitung</span></td>"));
        assertTrue(html.contains("<td class=\"cap-day cap-day-na\" title=\"kein Scan\"><span aria-hidden=\"true\">—"
            + "</span><span class=\"sr\">kein Scan</span></td>"));
        assertEquals(50, CellHtmlTest.count(html, "<td class=\"cap-day "), "5 rows × 10 cells, each with text");
    }

    @Test
    @DisplayName("TC-37: summary „an {x} von {n} Tagen frei“ plus classification, „zu wenig Daten“ below 5")
    void rendersSummaryAndClassification() {
        String html = renderer.render(model(allRows(), List.of()));

        assertTrue(html.contains("<td class=\"cap-sum\">an <b>8</b> von 10 Tagen frei</td>"
            + "<td class=\"st-l\"><span class=\"cap-class\">meist frei</span></td>"));
        assertTrue(html.contains("<span class=\"cap-class\">wechselnd</span>"));
        assertTrue(html.contains("<span class=\"cap-class\">meist belegt</span>"));
        assertTrue(html.contains("<span class=\"cap-class\">zu wenig Daten</span>"
            + "<span class=\"sr\"> (unter 5 messbare Tage)</span>"));
        assertTrue(html.contains("an <b>2</b> von 7 Tagen frei"));
    }

    @Test
    @DisplayName("TC-37/BR-26: rows keep the view-model order (meist frei oben)")
    void keepsTheViewModelOrder() {
        String html = renderer.render(model(allRows(), List.of()));

        int nordtor = html.indexOf(">VW Nordtor<");
        int hostile = html.indexOf(">&lt;b&gt;X&lt;/b&gt;<");
        int booked = html.indexOf(">Audi Zentrum Rheinfeld<");
        int none = html.indexOf(">Audi Hafenstadt<");
        int na = html.indexOf(">Lindenau<");
        assertTrue(nordtor > 0 && nordtor < hostile && hostile < booked && booked < none && none < na);
    }

    @Test
    @DisplayName("TC-37: branch name <b>X</b> is escaped")
    void escapesTheBranchName() {
        String html = renderer.render(model(allRows(), List.of("<b>warn</b>")));

        assertTrue(html.contains("&lt;b&gt;X&lt;/b&gt;"));
        assertFalse(html.contains("<b>X</b>"));
        assertTrue(html.contains("<p class=\"st-warn\">&lt;b&gt;warn&lt;/b&gt;</p>"));
    }

    @Test
    @DisplayName("TC-37: K-2 only as one total per branch with .sr text; 0–4 are not suppressed (BR-26)")
    void rendersTheSlotTotalWithoutSuppression() {
        String html = renderer.render(model(allRows(), List.of()));

        assertTrue(html.contains("<th scope=\"col\" rowspan=\"2\">Frei am Zieltag<span class=\"st-small\">"
            + "Referenz-Leistung, Näherung</span></th>"), "approximation text next to the value (BR-20, AC-29)");
        assertTrue(html.contains("<td><span class=\"cap-total\">14</span><span class=\"sr\"> freie Online-Termine "
            + "am Zieltag</span></td>"));
        assertTrue(html.contains("<span class=\"cap-total\">3</span>"), "capacity values are not „< 5“");
        assertTrue(html.contains("<span class=\"cap-total\">0</span>"));
        assertFalse(html.contains("&lt; 5</span><span class=\"sr\">weniger als 5"), "no suppression in S1");
        for (String absent : List.of("cap-slots", "cap-mini", "cap-spark")) {
            assertFalse(html.contains(absent), "no daypart split (N2): " + absent);
        }
    }

    @Test
    @DisplayName("TC-37: without CAPACITY_SHOW_SLOT_COUNT there is no K-2 column")
    void omitsTheSlotColumnWithoutTheSwitch() {
        List<CapacityRow> rows = List.of(new CapacityRow("VW Nordtor", CapacityLevel.FREE_NEXT_DAY, "heute frei", 0,
            null, null, Collections.nCopies(10, FREE), nulls(10), 10, 10, CapacityTrend.MOSTLY_FREE));
        String html = renderer.render(model(rows, List.of()));

        assertFalse(html.contains("Frei am Zieltag"));
        assertFalse(html.contains("cap-total"));
        assertTrue(html.contains("<colgroup span=\"2\"></colgroup><colgroup span=\"10\"></colgroup>"));
        assertTrue(html.contains(">heute frei<"));
    }

    @Test
    @DisplayName("TC-37: footnote names every „—“ cell with branch, day and reason")
    void footnoteNamesEveryNotMeasurableCell() {
        String html = renderer.render(model(allRows(), List.of()));

        assertTrue(html.contains("<p class=\"st-note\">„—“ nicht messbar: Audi Hafenstadt 11.11., 14.11. und 19.11.: "
            + "Zeitüberschreitung · Lindenau 09.11.–16.11.: kein Zugriff auf die SoftNet-Gruppe · Lindenau 19.11.: "
            + "kein Scan.</p>"));
    }

    @Test
    @DisplayName("BR-20/AC-29: reference-service note and the legend of pills, cells and thresholds")
    void rendersNoteAndLegend() {
        String html = renderer.render(model(allRows(), List.of()));

        assertTrue(html.contains("<p class=\"st-note\">" + CapacitySectionRenderer.CAPACITY_NOTE
            .replace("<", "&lt;") + "</p>"));
        assertTrue(html.contains("<ul class=\"st-legend\" aria-label=\"Legende Stufen und Verlauf\">"));
        assertTrue(html.contains("<li><span class=\"st-sw cap-day-free\" aria-hidden=\"true\"></span>"
            + "frei = am Zieltag online frei</li>"));
        assertTrue(html.contains("— = nicht messbar oder kein Scan (Grund in der Fußnote)"));
        assertTrue(html.contains("Einordnung ab 5 messbaren Tagen: meist frei ≥ 7 von 10 Tagen frei"));
        assertTrue(html.indexOf("st-legend") > html.indexOf("</table>"), "legend below the table");
    }

    // -----------------------------------------------------------------------------------------
    // TC-37: fallback on a failed scan / Fallback bei fehlgeschlagenem Scan
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("TC-37/AC-10: failed latest scan shows the fallback scan with date and warning")
    void rendersTheFallbackScanWithDateAndWarning() {
        String failed = "Letzter Scan (19.11.2026, 22:30 Uhr) fehlgeschlagen – angezeigt: Stand 18.11.2026.";
        CapacityViewModel vm = new CapacityViewModel(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 19),
            ScanState.COMPLETE, true, ScanState.COMPLETE, null, LocalDate.of(2026, 10, 5), DAYS,
            List.of(nordtor(), booked()), List.of(failed));
        String html = renderer.render(vm);

        assertTrue(html.contains("<p class=\"st-warn\">" + failed + "</p>"));
        assertTrue(html.contains("<p class=\"st-range\">Letzter vollständiger Scan: 18.11.2026 · Zieltag damals: "
            + "Do 19.11.2026 · Werktage Mo–Sa · Verlauf: letzte 10 Scans (09.11.–19.11.)</p>"));
        assertTrue(html.contains("Nächster freier Online-Termin<span class=\"st-small\">Stand 18.11.</span>"));
        assertTrue(html.contains("aria-label=\"Tabelle freie Online-Termine je Standort, Stand 18.11.2026, "
            + "horizontal scrollbar\""));
        assertTrue(html.contains("Stand des letzten vollständigen Scans vom 18.11.2026"));
        CellHtmlTest.assertMarkupContract(html);
    }

    @Test
    @DisplayName("TC-37: partial fallback additionally shows CAPACITY_SCAN_PARTIAL; a day failed everywhere is one note")
    void rendersPartialFallbackAndAllBranchesFootnote() {
        String failed = "Letzter Scan (19.11.2026, 22:30 Uhr) fehlgeschlagen – angezeigt: Stand 18.11.2026.";
        String partial = "Scan vom 18.11.2026 unvollständig: 1 Standorte nicht messbar.";
        List<CapacityDayState> states = new ArrayList<>(Collections.nCopies(9, FREE));
        states.add(NOT_MEASURABLE);
        List<String> reasons = new ArrayList<>(nulls(9));
        reasons.add("softnet-unavailable");
        CapacityRow a = new CapacityRow("A", CapacityLevel.FREE_NEXT_DAY, "morgen frei", 0, null, null, states,
            reasons, 9, 9, CapacityTrend.MOSTLY_FREE);
        CapacityRow b = new CapacityRow("B", CapacityLevel.FREE_NEXT_DAY, "morgen frei", 0, null, null, states,
            reasons, 9, 9, CapacityTrend.MOSTLY_FREE);
        CapacityViewModel vm = new CapacityViewModel(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 19),
            ScanState.PARTIAL, true, ScanState.PARTIAL, null, null, DAYS, List.of(a, b), List.of(failed, partial));
        String html = renderer.render(vm);

        assertTrue(html.indexOf(failed) < html.indexOf(partial), "failure first, then the partial hint");
        assertTrue(html.contains("<p class=\"st-warn\">" + partial + "</p>"));
        assertTrue(html.contains("„—“ nicht messbar: alle Standorte 19.11.: SoftNet nicht erreichbar.</p>"));
    }

    @Test
    @DisplayName("BR-26: a day failed everywhere with different reasons is named per branch")
    void differentReasonsOnTheSameDayAreNamedPerBranch() {
        CapacityRow a = new CapacityRow("A", CapacityLevel.NOT_MEASURABLE, "nicht messbar", null, "timeout", null,
            List.of(NOT_MEASURABLE), List.of("timeout"), 0, 0, CapacityTrend.TOO_FEW_DAYS);
        CapacityRow b = new CapacityRow("B", CapacityLevel.NOT_MEASURABLE, "nicht messbar", null, "no-brand", null,
            List.of(NOT_MEASURABLE), List.of("no-brand"), 0, 0, CapacityTrend.TOO_FEW_DAYS);
        CapacityViewModel vm = new CapacityViewModel(SCAN_DAY, TARGET_DAY, ScanState.FAILED, false, null, null, null,
            List.of(SCAN_DAY), List.of(a, b), List.of());
        String html = renderer.render(vm);

        assertTrue(html.contains("„—“ nicht messbar: A 19.11.: Zeitüberschreitung · B 19.11.: keine Marke hinterlegt."));
        assertFalse(html.contains("alle Standorte"));
    }

    // -----------------------------------------------------------------------------------------
    // Empty states / Leerzustände (§11.5, TC-55, TC-48)
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("TC-55: first scan running → .st-empty CAPACITY_EMPTY_FIRST_SCAN, no table")
    void rendersTheFirstScanRunningState() {
        Instant running = Instant.parse("2026-11-19T21:34:00Z"); // 22:34 Berlin (CET)
        CapacityViewModel vm = new CapacityViewModel(null, null, null, false, null, running,
            LocalDate.of(2026, 11, 19), List.of(), List.of(), List.of());
        String html = renderer.render(vm);

        assertTrue(html.contains("<p class=\"st-empty\"><span class=\"st-empty-dash\" aria-hidden=\"true\">&mdash;"
            + "</span>Noch keine Messung. Der erste Kapazitäts-Scan läuft seit 22:34 Uhr (gestartet 19.11.2026; "
            + "üblich 3–5 Minuten). Erfassung seit 19.11.2026.</p>"));
        assertTrue(html.contains("Der erste Kapazitäts-Scan läuft seit 22:34 Uhr"));
        assertFalse(html.contains("<table"));
        assertFalse(html.contains("st-legend"));
        assertTrue(html.contains("Referenz-Leistung „Wischerblätter erneuern“"));
        CellHtmlTest.assertMarkupContract(html);
    }

    @Test
    @DisplayName("TC-55: without collection start the day of the running scan is named")
    void firstScanWithoutCollectionStartNamesTheScanDay() {
        Instant running = Instant.parse("2026-07-01T20:31:00Z"); // 22:31 Berlin (CEST)
        String html = renderer.render(new CapacityViewModel(null, null, null, false, null, running, null,
            null, null, null));

        assertTrue(html.contains("läuft seit 22:31 Uhr (gestartet 01.07.2026; üblich 3–5 Minuten). "
            + "Erfassung seit 01.07.2026."));
    }

    @Test
    @DisplayName("§11.5: no scan yet → CAPACITY_NO_SCAN with the default scan time")
    void rendersNoScanYet() {
        String html = renderer.render(new CapacityViewModel(null, null, null, false, null, null, null,
            List.of(), List.of(), List.of()));

        assertTrue(html.contains("Noch kein Kapazitäts-Scan – der erste läuft heute um 22:30 Uhr."));
        assertTrue(html.contains("class=\"st-empty\""));
        assertFalse(html.contains(">0<"));
    }

    @Test
    @DisplayName("TC-48/AC-32: history unavailable → only the warning, no numbers, no empty state")
    void historyUnavailableShowsOnlyTheWarning() {
        String html = renderer.render(new CapacityViewModel(null, null, null, false, null, null, null,
            List.of(), List.of(), List.of(CapacityViewModel.WARN_HISTORY_UNAVAILABLE)));

        assertTrue(html.contains("<p class=\"st-warn\">Historie derzeit nicht verfügbar</p>"));
        assertFalse(html.contains("st-empty"));
        assertFalse(html.contains("st-note"));
        assertFalse(html.contains("<table"));
    }

    @Test
    @DisplayName("BR-4: switched-off source renders nothing")
    void nullRendersNothing() {
        assertEquals("", renderer.render(null));
    }

    // -----------------------------------------------------------------------------------------
    // Robustness / Robustheit
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("BR-25 fallback: labels without levelLabel follow §17")
    void defaultLabelsWithoutLevelLabel() {
        List<CapacityRow> rows = List.of(
            new CapacityRow("A", CapacityLevel.FREE_NEXT_DAY, null, 0, null, null, List.of(), List.of(), 0, 0,
                CapacityTrend.TOO_FEW_DAYS),
            new CapacityRow("B", CapacityLevel.WITHIN_4_WORKDAYS, " ", 1, null, null, List.of(), List.of(), 0, 0,
                CapacityTrend.TOO_FEW_DAYS),
            new CapacityRow("C", CapacityLevel.WITHIN_4_WORKDAYS, null, null, null, null, List.of(), List.of(), 0, 0,
                CapacityTrend.TOO_FEW_DAYS),
            new CapacityRow("D", CapacityLevel.TIGHT, null, 5, null, null, List.of(), List.of(), 0, 0,
                CapacityTrend.TOO_FEW_DAYS),
            new CapacityRow("E", CapacityLevel.NONE_FOUND, null, null, null, null, List.of(), List.of(), 0, 0,
                CapacityTrend.TOO_FEW_DAYS),
            new CapacityRow("F", null, null, null, null, null, List.of(), List.of(), 0, 0, null));
        String html = renderer.render(model(rows, List.of()));

        assertTrue(html.contains("<span class=\"cap-pill cap-pill-free\">am Fr 20.11. frei</span>"));
        assertTrue(html.contains("<span class=\"cap-pill\">in 1 Werktag</span><span class=\"cap-when\">Sa 21.11.</span>"));
        assertTrue(html.contains("<span class=\"cap-pill\">in 1–4 Werktagen</span></td>"));
        assertTrue(html.contains(">belegt: ≥ 5 Werktage</span><span class=\"cap-when\">Do 26.11.</span>"));
        assertTrue(html.contains(">keine Online-Termine gefunden</span></td>"));
        assertTrue(html.contains("<span class=\"cap-pill cap-pill-na\">nicht messbar</span></td>"));
        assertTrue(html.contains("<td class=\"st-l\"><span class=\"st-nd\"><span aria-hidden=\"true\">&mdash;</span>"
            + "<span class=\"sr\">keine Daten</span></span></td>"), "missing classification is „—“");
        CellHtmlTest.assertMarkupContract(html);
    }

    @Test
    @DisplayName("BR-26: short grid lists are padded with „kein Scan“, missing counters computed")
    void padsShortGridListsAndComputesCounters() {
        CapacityRow row = new CapacityRow("A", CapacityLevel.FREE_NEXT_DAY, "morgen frei", 0, null, null,
            List.of(FREE, BOOKED, FREE), null, null, null, CapacityTrend.TOO_FEW_DAYS);
        String html = renderer.render(model(List.of(row), List.of()));

        assertEquals(7, CellHtmlTest.count(html, "title=\"kein Scan\""));
        assertTrue(html.contains("an <b>2</b> von 3 Tagen frei"));
        assertTrue(html.contains("„—“ nicht messbar: A 12.11.–19.11.: kein Scan.</p>"));
    }

    @Test
    void unknownReasonKeyIsShownEscaped() {
        CapacityRow row = new CapacityRow("A", CapacityLevel.NOT_MEASURABLE, "nicht messbar", null, "foo<x>", null,
            List.of(NOT_MEASURABLE), List.of("foo<x>"), 0, 0, CapacityTrend.TOO_FEW_DAYS);
        String html = renderer.render(model(List.of(row), List.of()));

        assertTrue(html.contains("Grund: foo&lt;x&gt;"));
        assertFalse(html.contains("foo<x>"));
        assertEquals("foo", CapacitySectionRenderer.reason("foo"));
        assertEquals("Zeitüberschreitung", CapacitySectionRenderer.reason("timeout"));
    }

    @Test
    @DisplayName("§11.2: without grid days, target day or scan day the parts are left out")
    void leavesOutMissingParts() {
        CapacityRow row = new CapacityRow("A", CapacityLevel.FREE_NEXT_DAY, "morgen frei", 0, null, null,
            List.of(), List.of(), 0, 0, CapacityTrend.TOO_FEW_DAYS);
        String noDays = renderer.render(new CapacityViewModel(SCAN_DAY, null, ScanState.COMPLETE, false, null, null,
            null, List.of(), List.of(row), List.of()));
        assertTrue(noDays.contains("<p class=\"st-range\">Kapazitäts-Scan vom 19.11.2026 · Werktage Mo–Sa</p>"));
        assertFalse(noDays.contains("colgroup span=\"0\""));
        assertFalse(noDays.contains("rowspan"));
        assertFalse(noDays.contains("nicht messbar: A"));
        CellHtmlTest.assertMarkupContract(noDays);

        String noScanDay = renderer.render(new CapacityViewModel(null, null, null, false, null, null, null,
            DAYS, List.of(row), List.of()));
        assertFalse(noScanDay.contains("st-range"));
    }

    // -----------------------------------------------------------------------------------------
    // Text sources and dependencies / Textquellen und Abhängigkeiten (§9, §17)
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("§9: S1 depends on the neutral package only — no usage or usage/insights type")
    void dependsOnTheNeutralPackageOnly() throws IOException {
        assertFalse(HtmlTextTest.references(CapacitySectionRenderer.class, "com/autohaus/backend/usage/"),
            "no cross dependency capacity ↔ usage/insights");
        assertFalse(HtmlTextTest.references(CapacitySectionRenderer.class, "com/autohaus/backend/reporting/"));
        assertTrue(HtmlTextTest.references(CapacitySectionRenderer.class, "com/autohaus/backend/common/html/"));
    }

    @Test
    @DisplayName("§17: LEVEL_* and REASON_* have one source (the builder); no dead CAPACITY_SCAN_RUNNING")
    void keepsNoCopyOfTheLevelAndReasonTexts() {
        for (Field field : CapacitySectionRenderer.class.getDeclaredFields()) {
            String name = field.getName();
            assertFalse(name.startsWith("LEVEL_"), "level texts live in CapacityViewModelBuilder: " + name);
            assertFalse(name.startsWith("REASON_") && !name.equals("REASON_PREFIX"),
                "reason texts live in CapacityViewModelBuilder: " + name);
            assertNotEquals("CAPACITY_SCAN_RUNNING", name, "the sentence is part of CAPACITY_EMPTY_FIRST_SCAN");
        }
        assertEquals(CapacityViewModelBuilder.REASON_TIMEOUT, CapacitySectionRenderer.reason("timeout"));
        for (ProbeReason reason : ProbeReason.values()) {
            assertEquals(CapacityViewModelBuilder.reasonLabel(reason), CapacitySectionRenderer.reason(reason.key()));
        }
        assertEquals("", CapacitySectionRenderer.reason(null));
    }

    @Test
    @DisplayName("§17 CAPACITY_RANGE / CAPACITY_RANGE_FALLBACK are assembled from their parts")
    void assemblesTheRangeLinesFromTheirParts() {
        assertEquals("Kapazitäts-Scan vom {scanDate}, {from}–{to} Uhr · Zieltag: {weekday} {targetDate} "
            + "(nächster Werktag) · Werktage Mo–Sa · Verlauf: letzte 10 Scans ({trendFrom}–{trendTo})",
            CapacitySectionRenderer.CAPACITY_RANGE);
        assertEquals("Letzter vollständiger Scan: {scanDate}, {from}–{to} Uhr · Zieltag damals: {weekday} "
            + "{targetDate} · Werktage Mo–Sa · Verlauf: letzte 10 Scans ({trendFrom}–{trendTo})",
            CapacitySectionRenderer.CAPACITY_RANGE_FALLBACK);
        assertTrue(CapacitySectionRenderer.CAPACITY_EMPTY_FIRST_SCAN.contains("Der erste Kapazitäts-Scan läuft seit "
            + "{time} Uhr"), "§5.2 sentence of CAPACITY_SCAN_RUNNING");
    }

    @Test
    @DisplayName("§10.4: first free day = target day plus working days Mo–Sa")
    void firstFreeDateSkipsSundays() {
        assertEquals(LocalDate.of(2026, 11, 23), CapacitySectionRenderer.firstFreeDate(LocalDate.of(2026, 11, 21), 1));
        assertEquals(LocalDate.of(2026, 11, 20), CapacitySectionRenderer.firstFreeDate(TARGET_DAY, 0));
        assertNull(CapacitySectionRenderer.firstFreeDate(null, 2));
        assertNull(CapacitySectionRenderer.firstFreeDate(TARGET_DAY, null));
        assertNull(CapacitySectionRenderer.firstFreeDate(TARGET_DAY, -1));
    }
}
