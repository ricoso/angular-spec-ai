package com.autohaus.backend.usage.insights;

import com.autohaus.backend.common.html.GermanFormat;
import com.autohaus.backend.common.html.HtmlText;
import com.autohaus.backend.usage.StatFormat;
import com.autohaus.backend.usage.insights.CellHtml.Format;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.autohaus.backend.common.html.HtmlText.esc;
import static com.autohaus.backend.common.html.HtmlText.fill;

/**
 * Renders S6 „Buchungsstrecke" incl. the drop-off scenarios subsection (REQ-038 §11.2, AC-19,
 * N1) / rendert S6 mit den Absprungszenarien. {@code null} or {@code funnelEnabled == false} →
 * empty string (BR-4, BR-8).
 *
 * <p><b>Funnel / Trichter:</b> {@code ol.fn-steps > li.fn-step} with the five stages (stage 4 is
 * „Terminsuche ausgeführt", BR-8), absolute number, „% vom Start" and „% der Vorstufe"; then
 * {@code table.st-table.fn-table} „Ausstieg nach Stufe und Geräteklasse" with „Alle", the visible
 * device classes and „Sonstige ({Klassen})" (BR-9 Nr. 7). Without an own class only „Alle" plus
 * {@code .st-thin DEVICE_TOO_FEW} remains. / Trichter und Ausstiegstabelle.
 *
 * <p><b>Drop-off scenarios (N1, AC-35, AC-36):</b> {@code section.fn-dropoff} only when
 * {@code funnel.dropoff() != null}: bar list of the scenarios without the „quiet" group,
 * {@code table.st-table.fn-dropoff-table} (scenario · stages 1–4 · total · share · fixed action
 * hint {@code SC_*_HINT} of {@code InsightsViewModelFunnel}, the one source, BR-32) with the sum row
 * {@code tr.fn-dropoff-sum} „Alle Abbrüche {n} =
 * {visits} Besuche − {booking} Buchungen" (AC-33), and {@code table.st-table.fn-dropoff-dev} by
 * device class. Cells come suppressed from the view model („&lt; 5", „k. A.", BR-31). / Szenarien
 * nur bei aktivem Schalter.
 */
@Component
public class FunnelSectionRenderer {

    /** §17 {@code FUNNEL_HEADING}. */
    public static final String FUNNEL_HEADING = "Buchungsstrecke: Wo steigen Besucher aus?";
    /** Introduction of the section (mockup) / Einleitung. */
    public static final String FUNNEL_INTRO = "Je Sitzung und Kalendertag zählt die höchste erreichte Stufe. "
        + "Alle Standorte zusammen – es gibt keine Auswertung der Strecke je Standort. Stand gestern; die "
        + "Nutzungsstatistik oben enthält zusätzlich den laufenden Tag.";
    /** §17 {@code STAGE_*} in stage order 1–5. */
    public static final List<String> STAGE_LABELS = List.of(
        "Besuch (App-Aufruf)", "Leistungsauswahl", "Hinweise", "Terminsuche ausgeführt", "Gebucht");
    /** §17 {@code FUNNEL_OF_START}. */
    public static final String FUNNEL_OF_START = "{pct} % vom Start";
    /** §17 {@code FUNNEL_OF_PREVIOUS}. */
    public static final String FUNNEL_OF_PREVIOUS = "{pct} % der Vorstufe";
    /** §17 {@code DROPOUT_TITLE}. */
    public static final String DROPOUT_TITLE = "Ausstieg nach Stufe und Geräteklasse";
    /** §17 {@code DROPOUT_COL_PAGES} — main line. */
    public static final String DROPOUT_COL = "Ausstieg";
    /** §17 {@code DROPOUT_COL_PAGES} — sub line. */
    public static final String DROPOUT_COL_PAGES = "zuletzt gesehene Seiten";
    /** §17 {@code DROPOUT_PAGES_1…4}. */
    public static final List<String> DROPOUT_PAGES = List.of("Standort/Marke", "Leistungen", "Hinweise",
        "Terminwahl, Werkstattkalender, Fahrzeugdaten oder Übersicht");
    /** Row head of a drop-out stage / Zeilenkopf. */
    public static final String DROPOUT_ROW = "nach Stufe {k}";
    /** Range line of the drop-out table / Zeitraumzeile der Ausstiegstabelle. */
    public static final String DROPOUT_RANGE =
        "Sitzungen, deren höchste Stufe diese war · Geräteklassen unter 20 Sitzungen als „Sonstige“";
    /** §17 {@code DEVICE_*} — „Alle". */
    public static final String DEVICE_ALL = "Alle";
    /** §17 {@code DEVICE_*} keyed by device class / Geräteklassen. */
    public static final Map<String, String> DEVICE_LABELS = Map.of(
        "mobile", "Mobil", "tablet", "Tablet", "desktop", "Desktop", "unknown", "Unbekannt");
    /** §17 {@code DEVICE_OTHER}. */
    public static final String DEVICE_OTHER = "Sonstige ({classes})";
    /** §17 {@code DEVICE_TOO_FEW}. */
    public static final String DEVICE_TOO_FEW =
        "Aufschlüsselung nach Geräteklasse: zu wenig Daten (je Klasse n < 20)";
    /** §17 {@code FUNNEL_NOTE}. */
    public static final String FUNNEL_NOTE = "Grenzen: Marke, Fahrzeugdaten und Übersicht lösen keinen "
        + "Server-Aufruf aus und sind nicht einzeln sichtbar. Terminwahl und Werkstattkalender sind nicht "
        + "unterscheidbar. Ein Reload zählt als Abbruch plus neuer Besuch; eine Sitzung über Mitternacht "
        + "zählt an beiden Tagen.";
    /** Part of both range lines: the period, left out without one / Zeitraum-Teil. */
    public static final String RANGE_PERIOD = "{period} · ";
    /** Part of both range lines: the collection start, left out without one / Erfassungsbeginn-Teil. */
    public static final String RANGE_SINCE = "Erfassung seit {since} · ";
    /** Range line of the funnel, assembled from its parts / Zeitraumzeile des Trichters. */
    public static final String FUNNEL_RANGE = RANGE_PERIOD + RANGE_SINCE + "{visits} Sitzungen";
    /** {@code aria-label} of the funnel list / Name der Trichter-Liste. */
    public static final String FUNNEL_STEPS_ARIA = "Trichter der Buchungsstrecke";
    /** Caption of the drop-out table (screen readers) / Caption der Ausstiegstabelle. */
    public static final String DROPOUT_CAPTION = "{title}, {period}: Sitzungen je höchster erreichter Stufe{merged}";

    /** §17 {@code DROPOFF_HEADING}. */
    public static final String DROPOFF_HEADING = "Absprungszenarien: In welcher Situation steigen Besucher aus?";
    /** §17 {@code DROPOFF_SUM}. */
    public static final String DROPOFF_SUM = "Alle Abbrüche";
    /** §17 {@code DROPOFF_SUM_LINE} — the part after {@code DROPOFF_SUM} is the sub line. */
    public static final String DROPOFF_SUM_LINE = "Alle Abbrüche {n} = {visits} Besuche − {booking} Buchungen";
    /** §17 {@code DROPOFF_SHARE}. */
    public static final String DROPOFF_SHARE = "Anteil an allen Abbrüchen";
    /** §17 {@code DROPOFF_COL_HINT}. */
    public static final String DROPOFF_COL_HINT = "Handlungsimpuls";
    /** §17 {@code DROPOFF_DEVICE_TITLE}. */
    public static final String DROPOFF_DEVICE_TITLE = "Nach Geräteklasse";
    /** Range line of the drop-off subsection, assembled from its parts / Zeitraumzeile der Szenarien. */
    public static final String DROPOFF_RANGE = RANGE_PERIOD + RANGE_SINCE + "{total} Abbrüche";
    /** {@code aria-label} of the scenario bar list / Name der Szenario-Balkenliste. */
    public static final String DROPOFF_BARS_ARIA =
        "Absprungszenarien ohne die Sammelgruppe ohne erkennbaren Anlass, sortiert nach Häufigkeit";
    /** Title of the scenario × stage table (scroll region) / Tabellentitel Szenario × Stufe. */
    public static final String DROPOFF_STAGE_TABLE_TITLE = "Absprungszenarien je Stufe";
    /** Caption of the scenario × stage table / Caption Szenario × Stufe. */
    public static final String DROPOFF_STAGE_CAPTION = "{title}, {period}: abgebrochene Sitzungen je Szenario "
        + "und höchster erreichter Stufe, Anteil an allen Abbrüchen und fester Handlungsimpuls; jede "
        + "abgebrochene Sitzung zählt in genau einem Szenario.";
    /** Title of the scenario × device table (scroll region) / Tabellentitel Szenario × Gerät. */
    public static final String DROPOFF_DEVICE_TABLE_TITLE = "Absprungszenarien nach Geräteklasse";
    /** Caption of the scenario × device table / Caption Szenario × Gerät. */
    public static final String DROPOFF_DEVICE_CAPTION =
        "{title}, {period}: abgebrochene Sitzungen je Szenario und Geräteklasse{merged}";
    /** {@code {merged}} of both device captions with merged classes / zusammengefasste Klassen. */
    public static final String CAPTION_MERGED = "; {classes} haben je weniger als 20 {unit} und sind als "
        + "Sonstige zusammengefasst.";
    /** {@code {merged}} without merged classes / Satzende. */
    public static final String CAPTION_END = ".";
    /** {@code {unit}} of the drop-out caption / Einheit Sitzungen. */
    public static final String UNIT_SESSIONS = "Sitzungen";
    /** {@code {unit}} of the drop-off device caption / Einheit Abbrüche. */
    public static final String UNIT_DROPOFFS = "Abbrüche";
    /** Note below the bar list / Hinweis unter der Balkenliste. */
    public static final String DROPOFF_QUIET_NOTE =
        "Ohne die Sammelgruppe „ohne erkennbaren Anlass“ ({n} Abbrüche, {pct}) – siehe Tabelle.";
    /** Note below the device table / Hinweis unter der Gerätetabelle. */
    public static final String DROPOFF_NOTE = "Jede abgebrochene Sitzung zählt in genau einem Szenario "
        + "(erste zutreffende Regel gewinnt). „< 5“ = 1–4 Sitzungen; „k. A.“ = zusätzlich ausgeblendet, "
        + "damit sich ein „< 5“-Wert nicht aus Zeilen- oder Spaltensumme zurückrechnen lässt.";
    /** Note naming the merged device classes / Hinweis auf zusammengefasste Klassen. */
    public static final String DROPOFF_OTHER_NOTE =
        "{classes} haben je unter 20 Abbrüche und stehen zusammen als „Sonstige“.";
    /** Row head „Szenario" / Spalte „Szenario". */
    public static final String DROPOFF_COL_SCENARIO = "Szenario";
    /** Column „Stufe {k}" / Spalte „Stufe {k}". */
    public static final String DROPOFF_COL_STAGE = "Stufe {k}";
    /** Column „Gesamt" / Spalte „Gesamt". */
    public static final String DROPOFF_COL_TOTAL = "Gesamt";

    /** §17 {@code SC_SERVER_ERROR}. */
    public static final String SC_SERVER_ERROR = "Technischer Fehler (Fehlerseite)";
    /** §17 {@code SC_ACCESS_DENIED}. */
    public static final String SC_ACCESS_DENIED = "Zugriff abgelehnt oder Anfragelimit";
    /** §17 {@code SC_SLOT_EXPIRED}. */
    public static final String SC_SLOT_EXPIRED = "Termin nach längerer Pause abgelaufen";
    /** §17 {@code SC_SLOT_CONFLICT}. */
    public static final String SC_SLOT_CONFLICT = "Termin war inzwischen vergeben";
    /** §17 {@code SC_BOOKING_REJECTED}. */
    public static final String SC_BOOKING_REJECTED = "Buchung abgelehnt";
    /** §17 {@code SC_SEARCH_REJECTED}. */
    public static final String SC_SEARCH_REJECTED = "Terminsuche fehlgeschlagen";
    /** §17 {@code SC_ERROR_THEN_QUIT}. */
    public static final String SC_ERROR_THEN_QUIT = "Nach Fehler weitergemacht, dann aufgehört";
    /** §17 {@code SC_REQUESTS_LOST}. */
    public static final String SC_REQUESTS_LOST = "Verbindung unterbrochen";
    /** §17 {@code SC_NO_SLOTS}. */
    public static final String SC_NO_SLOTS = "Keine Termine gefunden";
    /** §17 {@code SC_FEW_SLOTS}. */
    public static final String SC_FEW_SLOTS = "Nur wenige Termine angeboten";
    /** §17 {@code SC_SLOW}. */
    public static final String SC_SLOW = "Lange Wartezeit auf Antwort";
    /** §17 {@code SC_MANY_SEARCHES}. */
    public static final String SC_MANY_SEARCHES = "Viele Terminsuchen ohne Buchung";
    /** §17 {@code SC_*} — the scenario texts above keyed by scenario key (catalogue §8). */
    public static final Map<String, String> SCENARIO_LABELS = scenarioLabels();
    /** §17 {@code SC_QUIET} — k = MaxStep 1–4. */
    public static final String SC_QUIET = "ohne erkennbaren Anlass – nach Stufe {k}";
    /** Label of the aggregated A-0 row (key {@code quiet}) / Sammelzeile A-0. */
    public static final String SC_QUIET_ALL = "ohne erkennbaren Anlass";
    /**
     * §17 {@code SC_*_HINT} fixed action hints keyed by scenario key (BR-32) — the texts of
     * {@code InsightsViewModelFunnel}, used for rows that carry no hint of their own. / Texte aus dem
     * View-Model-Builder, nur für Zeilen ohne eigenen Hinweis.
     */
    public static final Map<String, String> SCENARIO_HINTS = scenarioHints();

    /** Key of the aggregated A-0 row / Schlüssel der Sammelzeile. */
    public static final String QUIET_KEY = InsightsViewModelFunnel.QUIET_SCENARIO;
    /** Key of the merged device column / Schlüssel der Sammelspalte. */
    public static final String OTHER_KEY = InsightsViewModelFunnel.OTHER_DEVICES;

    /** The {@code ({classes})} part of {@code DEVICE_OTHER}, left out without classes. */
    private static final String DEVICE_OTHER_CLASSES_PART = " ({classes})";

    /** Renders the section, {@code ""} when there is nothing to render / rendert die Sektion. */
    public String render(InsightsViewModel viewModel) {
        if (viewModel == null || !viewModel.funnelEnabled()) {
            return "";
        }
        StringBuilder h = new StringBuilder(24576);
        h.append("<section id=\"funnel\" class=\"fn\" aria-labelledby=\"funnel-h\">");
        h.append("<h2 id=\"funnel-h\">").append(esc(FUNNEL_HEADING)).append("</h2>");
        h.append("<p class=\"st-intro\">").append(esc(FUNNEL_INTRO)).append("</p>");
        h.append(HtmlText.warnings(CellHtml.currentPeriodWarnings(viewModel.warnings())));
        if (!HtmlText.hasWarning(viewModel.warnings(), InsightsViewModel.WARN_HISTORY_UNAVAILABLE)) {
            FunnelView funnel = viewModel.funnel();
            if (funnel == null) {
                h.append(CellHtml.emptySince(viewModel.funnelCollectingSince()));
            } else {
                h.append(funnel(viewModel, funnel));
            }
            h.append("<p class=\"st-note\">").append(esc(FUNNEL_NOTE)).append("</p>");
        }
        h.append("</section>");
        return h.toString();
    }

    // -----------------------------------------------------------------------------------------
    // Funnel F-1..F-3 / Trichter
    // -----------------------------------------------------------------------------------------

    private static String funnel(InsightsViewModel vm, FunnelView funnel) {
        List<FunnelStage> stages = funnel.stages() == null ? List.of() : funnel.stages();
        String periodText = CellHtml.periodText(funnel.period(), CellHtml.PERIOD_MONTH);
        StringBuilder h = new StringBuilder(16384);
        String range = rangeTemplate(FUNNEL_RANGE, !periodText.isEmpty(), vm.funnelCollectingSince() != null);
        h.append("<p class=\"st-range\">").append(fill(range,
            "period", esc(periodText),
            "since", esc(GermanFormat.day(vm.funnelCollectingSince())),
            "visits", CellHtml.inline(stages.isEmpty() ? null : stages.getFirst().reached(), Format.COUNT)))
            .append("</p>");
        if (stages.isEmpty() || CellHtml.state(stages.getFirst().reached()) == CellState.NO_DATA) {
            h.append(CellHtml.emptySince(vm.funnelCollectingSince()));
            return h.toString();
        }
        h.append(steps(stages));
        h.append(dropoutTable(funnel, stages, periodText));
        if (funnel.dropoff() != null) {
            h.append(dropoff(funnel, funnel.dropoff(), stages, periodText));
        }
        return h.toString();
    }

    /** {@code ol.fn-steps} with bar, number, „% vom Start" and „% der Vorstufe" (F-1). */
    private static String steps(List<FunnelStage> stages) {
        StringBuilder h = new StringBuilder(4096);
        h.append("<ol class=\"fn-steps\" aria-label=\"").append(esc(FUNNEL_STEPS_ARIA)).append("\">");
        for (int i = 0; i < stages.size(); i++) {
            FunnelStage stage = stages.get(i);
            h.append("<li class=\"fn-step\"><span class=\"fn-lbl\">").append(i + 1).append(" &middot; ")
                .append(esc(stageLabel(stage, i))).append("</span>");
            h.append("<span class=\"fn-bar\" aria-hidden=\"true\"><span class=\"fn-fill\" style=\"width:")
                .append(stageWidth(stage)).append("%\"></span></span>");
            h.append("<span class=\"fn-val\">").append(CellHtml.strong(stage.reached(), Format.COUNT));
            if (stage.percentOfStart() != null) {
                h.append(" &middot; ").append(percentText(FUNNEL_OF_START, stage.percentOfStart()));
            }
            if (i > 0 && stage.percentOfPrevious() != null) {
                h.append("<span class=\"fn-prev\">").append(percentText(FUNNEL_OF_PREVIOUS, stage.percentOfPrevious()))
                    .append("</span>");
            }
            h.append("</span></li>");
        }
        h.append("</ol>");
        return h.toString();
    }

    /** Bar width of a stage: its visible „% vom Start", else 0 (BR-9 Nr. 5). */
    private static String stageWidth(FunnelStage stage) {
        if (!CellHtml.isValue(stage.reached())) {
            return "0.0";
        }
        return CellHtml.barWidth(stage.percentOfStart());
    }

    /** „71,0 % vom Start" or „k. A. vom Start" for a hidden percentage. */
    private static String percentText(String template, Cell cell) {
        if (CellHtml.isValue(cell) && cell.percent() != null) {
            return fill(template, "pct", StatFormat.decimal(cell.percent()));
        }
        return esc(template).replace("{pct} %", CellHtml.inline(cell, Format.PERCENT));
    }

    /** F-2/F-3 „Ausstieg nach Stufe und Geräteklasse" (BR-9 Nr. 7). */
    private static String dropoutTable(FunnelView funnel, List<FunnelStage> stages, String periodText) {
        List<String> columns = funnel.deviceColumns() == null ? List.of() : funnel.deviceColumns();
        StringBuilder h = new StringBuilder(6144);
        h.append("<h3 class=\"st-sub\">").append(esc(DROPOUT_TITLE)).append("</h3>");
        h.append("<p class=\"st-range\">").append(esc(DROPOUT_RANGE)).append("</p>");
        if (columns.isEmpty()) {
            h.append("<p class=\"st-thin\">").append(esc(DEVICE_TOO_FEW)).append("</p>");
        }
        h.append(HtmlText.scrollOpen(DROPOUT_TITLE));
        h.append("<table class=\"st-table fn-table\">");
        h.append("<caption class=\"sr\">").append(esc(HtmlText.fillPlain(DROPOUT_CAPTION,
            "title", DROPOUT_TITLE, "period", periodText,
            "merged", mergedText(funnel.otherDevices(), UNIT_SESSIONS)))).append("</caption>");
        h.append("<thead><tr><th scope=\"col\" class=\"st-l\">").append(esc(DROPOUT_COL))
            .append(" <span class=\"st-small\">").append(esc(DROPOUT_COL_PAGES)).append("</span></th>")
            .append("<th scope=\"col\">").append(esc(DEVICE_ALL)).append("</th>");
        for (String column : columns) {
            h.append("<th scope=\"col\">").append(esc(deviceLabel(column, funnel.otherDevices()))).append("</th>");
        }
        h.append("</tr></thead><tbody>");
        for (int k = 0; k < Math.min(4, stages.size()); k++) {
            h.append("<tr><th scope=\"row\">").append(fill(DROPOUT_ROW, "k", String.valueOf(k + 1)))
                .append(" <span class=\"st-small\">").append(esc(DROPOUT_PAGES.get(k))).append("</span></th>");
            h.append(countCell(stages.get(k).droppedHere()));
            for (String column : columns) {
                List<Cell> cells = CellHtml.get(funnel.byDevice(), column);
                h.append(countCell(cells == null || k >= cells.size() ? null : cells.get(k)));
            }
            h.append("</tr>");
        }
        h.append("</tbody></table></div>");
        return h.toString();
    }

    /** A count cell with the optional column share ({@code .fn-pct}) / Zählzelle mit Anteil. */
    private static String countCell(Cell cell) {
        if (!CellHtml.isValue(cell)) {
            return CellHtml.td(cell, Format.COUNT);
        }
        String pct = cell.percent() != null && cell.value() != null
            ? "<span class=\"fn-pct\">" + StatFormat.percent(cell.percent()) + "</span>"
            : "";
        return "<td>" + CellHtml.strong(cell, Format.COUNT) + pct + "</td>";
    }

    // -----------------------------------------------------------------------------------------
    // Drop-off scenarios F-4 / Absprungszenarien (N1)
    // -----------------------------------------------------------------------------------------

    private static String dropoff(FunnelView funnel, DropoffView dropoff, List<FunnelStage> stages,
                                  String periodText) {
        StringBuilder h = new StringBuilder(16384);
        h.append("<section class=\"fn-dropoff\" aria-labelledby=\"fn-dropoff-h\">");
        h.append("<h3 id=\"fn-dropoff-h\">").append(esc(DROPOFF_HEADING)).append("</h3>");
        List<DropoffRow> rows = dropoff.rows() == null ? List.of() : dropoff.rows();
        if (rows.isEmpty()) {
            // §17 DROPOFF_EMPTY is the sentence CellHtml.EMPTY_SINCE / derselbe Leerzustand.
            h.append(CellHtml.emptySince(dropoff.collectingSince()));
            h.append("</section>");
            return h.toString();
        }
        DropoffRow total = dropoff.total();
        String range = rangeTemplate(DROPOFF_RANGE, !periodText.isEmpty(), dropoff.collectingSince() != null);
        h.append("<p class=\"st-range\">").append(fill(range,
            "period", esc(periodText),
            "since", esc(GermanFormat.day(dropoff.collectingSince())),
            "total", CellHtml.inline(total == null ? null : total.total(), Format.COUNT))).append("</p>");
        h.append(barList(rows));
        h.append(stageTable(rows, total, stages, periodText));
        h.append(deviceTable(dropoff, rows, total, periodText));
        h.append("</section>");
        return h.toString();
    }

    /** Bar list of every scenario except the „quiet" group, in view-model order (§11.2). */
    private static String barList(List<DropoffRow> rows) {
        long max = 0;
        List<DropoffRow> listed = new ArrayList<>();
        DropoffRow quiet = null;
        for (DropoffRow row : rows) {
            if (isQuiet(row.scenarioKey())) {
                quiet = quiet == null ? row : quiet;
                continue;
            }
            listed.add(row);
            if (CellHtml.isValue(row.total()) && row.total().value() != null) {
                max = Math.max(max, row.total().value());
            }
        }
        StringBuilder h = new StringBuilder(4096);
        if (!listed.isEmpty()) {
            h.append("<ul class=\"dev\" aria-label=\"").append(esc(DROPOFF_BARS_ARIA)).append("\">");
            for (DropoffRow row : listed) {
                String width = "0.0";
                if (max > 0 && CellHtml.isValue(row.total()) && row.total().value() != null) {
                    width = StatFormat.cssWidth(CellHtml.clamp(100.0 * row.total().value() / max));
                }
                h.append("<li class=\"dev-row dev-wide\"><span class=\"dev-lbl\">")
                    .append(esc(scenarioLabel(row.scenarioKey()))).append("</span>")
                    .append("<span class=\"dev-bar\" aria-hidden=\"true\"><span class=\"dev-fill st-fill\" style=\"width:")
                    .append(width).append("%\"></span></span>")
                    .append("<span class=\"dev-val\">").append(CellHtml.strong(row.total(), Format.COUNT))
                    .append(" &middot; ").append(CellHtml.inline(row.share(), Format.PERCENT)).append("</span></li>");
            }
            h.append("</ul>");
        }
        if (quiet != null) {
            String template = DROPOFF_QUIET_NOTE;
            h.append("<p class=\"st-note\">").append(fill(template,
                "n", CellHtml.inline(quiet.total(), Format.COUNT),
                "pct", CellHtml.inline(quiet.share(), Format.PERCENT))).append("</p>");
        }
        return h.toString();
    }

    /** Table 1: scenario × stage 1–4 · total · share · action hint, sum row last (AC-33). */
    private static String stageTable(List<DropoffRow> rows, DropoffRow total, List<FunnelStage> stages,
                                     String periodText) {
        StringBuilder h = new StringBuilder(8192);
        h.append(HtmlText.scrollOpen(DROPOFF_STAGE_TABLE_TITLE));
        h.append("<table class=\"st-table fn-dropoff-table\">");
        h.append("<caption class=\"sr\">").append(esc(HtmlText.fillPlain(DROPOFF_STAGE_CAPTION,
            "title", DROPOFF_STAGE_TABLE_TITLE, "period", periodText))).append("</caption>");
        h.append("<thead><tr><th scope=\"col\" class=\"st-l\">").append(esc(DROPOFF_COL_SCENARIO)).append("</th>");
        for (int k = 1; k <= 4; k++) {
            h.append("<th scope=\"col\">").append(fill(DROPOFF_COL_STAGE, "k", String.valueOf(k)))
                .append(" <span class=\"st-small\">").append(esc(STAGE_LABELS.get(k - 1))).append("</span></th>");
        }
        h.append("<th scope=\"col\">").append(esc(DROPOFF_COL_TOTAL)).append("</th>")
            .append("<th scope=\"col\">").append(esc(DROPOFF_SHARE)).append("</th>")
            .append("<th scope=\"col\" class=\"st-l\">").append(esc(DROPOFF_COL_HINT)).append("</th></tr></thead>");
        h.append("<tbody>");
        for (DropoffRow row : rows) {
            h.append("<tr><th scope=\"row\">").append(esc(scenarioLabel(row.scenarioKey()))).append("</th>");
            h.append(stepCells(row));
            h.append("<td>").append(CellHtml.strong(row.total(), Format.COUNT)).append("</td>");
            h.append(CellHtml.td(row.share(), Format.PERCENT));
            h.append("<td class=\"st-l fn-dropoff-hint\">").append(hint(row)).append("</td></tr>");
        }
        h.append("</tbody>");
        if (total != null) {
            h.append("<tfoot><tr class=\"fn-dropoff-sum\"><th scope=\"row\">").append(sumHead(total, stages))
                .append("</th>");
            h.append(stepCells(total));
            h.append("<td>").append(CellHtml.strong(total.total(), Format.COUNT)).append("</td>");
            h.append(total.share() == null ? "<td></td>" : CellHtml.td(total.share(), Format.PERCENT));
            h.append("<td></td></tr></tfoot>");
        }
        h.append("</table></div>");
        return h.toString();
    }

    private static String stepCells(DropoffRow row) {
        StringBuilder h = new StringBuilder(512);
        for (int k = 0; k < 4; k++) {
            Cell cell = row.byStep() == null || k >= row.byStep().size() ? null : row.byStep().get(k);
            h.append(CellHtml.td(cell, Format.COUNT));
        }
        return h.toString();
    }

    /**
     * „Alle Abbrüche 1.854 = 2.140 Besuche − 286 Buchungen" (§17 {@code DROPOFF_SUM_LINE}); the
     * equation needs three visible counters, otherwise only „Alle Abbrüche" stays. / Summenzeile.
     *
     * <p>The equation is printed only when it actually holds (AC-33): while
     * {@code dropoffCollectingSince} lies inside the funnel month, the drop-off table covers fewer
     * days than the funnel, and a discarded row (E11) breaks the day invariant — the page must never
     * state a false equation. / Die Gleichung erscheint nur, wenn sie stimmt.
     */
    private static String sumHead(DropoffRow total, List<FunnelStage> stages) {
        Cell visits = stages.isEmpty() ? null : stages.getFirst().reached();
        Cell booking = stages.size() < 5 ? null : stages.get(4).reached();
        if (visits == null || booking == null || total.total() == null
            || !CellHtml.isValue(total.total()) || !CellHtml.isValue(visits) || !CellHtml.isValue(booking)
            || total.total().value() == null || visits.value() == null || booking.value() == null
            || total.total().value() != visits.value() - booking.value()) {
            return esc(DROPOFF_SUM);
        }
        String line = DROPOFF_SUM_LINE.substring(DROPOFF_SUM.length() + 1);
        return esc(DROPOFF_SUM) + " <span class=\"st-small\">" + fill(line,
            "n", StatFormat.num(total.total().value()),
            "visits", StatFormat.num(visits.value()),
            "booking", StatFormat.num(booking.value())) + "</span>";
    }

    /** Fixed action hint (BR-32), „– Messartefakt möglich" for A-0 and A-8. */
    private static String hint(DropoffRow row) {
        String text = row.hint() != null && !row.hint().isBlank()
            ? row.hint()
            : SCENARIO_HINTS.getOrDefault(isQuiet(row.scenarioKey()) ? QUIET_KEY : row.scenarioKey(), "");
        String html = esc(text);
        if (artefactPossible(row.scenarioKey()) && !text.contains(InsightsViewModelFunnel.HINT_ARTIFACT)) {
            html += (html.isEmpty() ? "" : " ") + "<span class=\"st-nw\">– "
                + esc(InsightsViewModelFunnel.HINT_ARTIFACT) + "</span>";
        }
        return html;
    }

    /** Table 2: scenario × device class, same row order (BR-31) / nach Geräteklasse. */
    private static String deviceTable(DropoffView dropoff, List<DropoffRow> rows, DropoffRow total,
                                      String periodText) {
        List<String> columns = dropoff.deviceColumns() == null ? List.of() : dropoff.deviceColumns();
        StringBuilder h = new StringBuilder(8192);
        h.append("<h4 class=\"st-sub\">").append(esc(DROPOFF_DEVICE_TITLE)).append("</h4>");
        if (columns.isEmpty()) {
            h.append("<p class=\"st-thin\">").append(esc(DEVICE_TOO_FEW)).append("</p>");
        } else {
            h.append(HtmlText.scrollOpen(DROPOFF_DEVICE_TABLE_TITLE));
            h.append("<table class=\"st-table fn-dropoff-dev\">");
            h.append("<caption class=\"sr\">").append(esc(HtmlText.fillPlain(DROPOFF_DEVICE_CAPTION,
                "title", DROPOFF_DEVICE_TABLE_TITLE, "period", periodText,
                "merged", mergedText(dropoff.otherDevices(), UNIT_DROPOFFS)))).append("</caption>");
            h.append("<thead><tr><th scope=\"col\" class=\"st-l\">").append(esc(DROPOFF_COL_SCENARIO)).append("</th>");
            for (String column : columns) {
                h.append("<th scope=\"col\">").append(esc(deviceLabel(column, dropoff.otherDevices()))).append("</th>");
            }
            h.append("<th scope=\"col\">").append(esc(DROPOFF_COL_TOTAL)).append("</th></tr></thead><tbody>");
            for (DropoffRow row : rows) {
                h.append("<tr><th scope=\"row\">").append(esc(scenarioLabel(row.scenarioKey()))).append("</th>");
                h.append(deviceCells(row, columns));
                h.append("<td>").append(CellHtml.strong(row.total(), Format.COUNT)).append("</td></tr>");
            }
            h.append("</tbody>");
            if (total != null) {
                h.append("<tfoot><tr><th scope=\"row\">").append(esc(DROPOFF_SUM)).append("</th>");
                h.append(deviceCells(total, columns));
                h.append("<td>").append(CellHtml.strong(total.total(), Format.COUNT)).append("</td></tr></tfoot>");
            }
            h.append("</table></div>");
        }
        h.append("<p class=\"st-note\">").append(esc(DROPOFF_NOTE));
        List<String> others = dropoff.otherDevices() == null ? List.of() : dropoff.otherDevices();
        if (!columns.isEmpty() && !others.isEmpty()) {
            h.append(' ').append(fill(DROPOFF_OTHER_NOTE, "classes", esc(GermanFormat.joinAnd(deviceNames(others)))));
        }
        h.append("</p>");
        return h.toString();
    }

    private static String deviceCells(DropoffRow row, List<String> columns) {
        StringBuilder h = new StringBuilder(512);
        for (String column : columns) {
            h.append(CellHtml.td(CellHtml.get(row.byDevice(), column), Format.COUNT));
        }
        return h.toString();
    }

    // -----------------------------------------------------------------------------------------
    // Labels / Beschriftungen
    // -----------------------------------------------------------------------------------------

    private static String stageLabel(FunnelStage stage, int index) {
        return index < STAGE_LABELS.size() ? STAGE_LABELS.get(index) : String.valueOf(stage.key());
    }

    /** „Mobil", …, or „Sonstige (Tablet, Unbekannt)" for the merged column (§17 {@code DEVICE_OTHER}). */
    static String deviceLabel(String key, List<String> otherDevices) {
        if (OTHER_KEY.equals(key)) {
            List<String> names = deviceNames(otherDevices == null ? List.of() : otherDevices);
            return names.isEmpty() ? DEVICE_OTHER.replace(DEVICE_OTHER_CLASSES_PART, "")
                : DEVICE_OTHER.replace("{classes}", String.join(GermanFormat.LIST_SEPARATOR, names));
        }
        return deviceName(key);
    }

    private static List<String> deviceNames(List<String> keys) {
        List<String> names = new ArrayList<>(keys.size());
        for (String key : keys) {
            names.add(deviceName(key));
        }
        return names;
    }

    /** Null-safe label of one device class; unknown classes are shown as is. */
    private static String deviceName(String key) {
        return key == null ? "" : DEVICE_LABELS.getOrDefault(key, key);
    }

    /** {@code {merged}} of a device caption: {@code CAPTION_MERGED} or the plain sentence end. */
    private static String mergedText(List<String> otherDevices, String unit) {
        if (otherDevices == null || otherDevices.isEmpty()) {
            return CAPTION_END;
        }
        return HtmlText.fillPlain(CAPTION_MERGED, "classes", GermanFormat.joinAnd(deviceNames(otherDevices)),
            "unit", unit);
    }

    /**
     * {@code FUNNEL_RANGE} / {@code DROPOFF_RANGE} without the parts the view does not carry —
     * {@code RANGE_PERIOD} without a period, {@code RANGE_SINCE} without a collection start. / Ohne
     * fehlende Teile.
     */
    private static String rangeTemplate(String template, boolean withPeriod, boolean withSince) {
        String range = withSince ? template : template.replace(RANGE_SINCE, "");
        return withPeriod ? range : range.replace(RANGE_PERIOD, "");
    }

    /** §17 {@code SC_*} text of a scenario key; the aggregated A-0 row reads „ohne erkennbaren Anlass". */
    static String scenarioLabel(String key) {
        if (key == null) {
            return "";
        }
        if (QUIET_KEY.equals(key)) {
            return SC_QUIET_ALL;
        }
        if (key.startsWith(QUIET_KEY + "-")) {
            return SC_QUIET.replace("{k}", key.substring(QUIET_KEY.length() + 1));
        }
        return SCENARIO_LABELS.getOrDefault(key, key);
    }

    private static boolean isQuiet(String key) {
        return key != null && (QUIET_KEY.equals(key) || key.startsWith(QUIET_KEY + "-"));
    }

    /** A-0 and A-8 carry „Messartefakt möglich" (BR-32) / Messartefakt-Hinweis. */
    private static boolean artefactPossible(String key) {
        if (isQuiet(key)) {
            return true;
        }
        DropoffScenario scenario = DropoffScenario.fromKey(key);
        return scenario != null && scenario.measurementArtefactPossible();
    }

    private static Map<String, String> scenarioLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(DropoffScenario.SERVER_ERROR.key(), SC_SERVER_ERROR);
        labels.put(DropoffScenario.ACCESS_DENIED.key(), SC_ACCESS_DENIED);
        labels.put(DropoffScenario.SLOT_EXPIRED.key(), SC_SLOT_EXPIRED);
        labels.put(DropoffScenario.SLOT_CONFLICT.key(), SC_SLOT_CONFLICT);
        labels.put(DropoffScenario.BOOKING_REJECTED.key(), SC_BOOKING_REJECTED);
        labels.put(DropoffScenario.SEARCH_REJECTED.key(), SC_SEARCH_REJECTED);
        labels.put(DropoffScenario.ERROR_THEN_QUIT.key(), SC_ERROR_THEN_QUIT);
        labels.put(DropoffScenario.REQUESTS_LOST.key(), SC_REQUESTS_LOST);
        labels.put(DropoffScenario.NO_SLOTS.key(), SC_NO_SLOTS);
        labels.put(DropoffScenario.FEW_SLOTS.key(), SC_FEW_SLOTS);
        labels.put(DropoffScenario.SLOW_RESPONSE.key(), SC_SLOW);
        labels.put(DropoffScenario.MANY_SEARCHES.key(), SC_MANY_SEARCHES);
        return Collections.unmodifiableMap(labels);
    }

    /** The §17 {@code SC_*_HINT} texts of {@code InsightsViewModelFunnel} by scenario key / nach Schlüssel. */
    private static Map<String, String> scenarioHints() {
        Map<String, String> hints = new LinkedHashMap<>();
        hints.put(DropoffScenario.SERVER_ERROR.key(), InsightsViewModelFunnel.SC_SERVER_ERROR_HINT);
        hints.put(DropoffScenario.ACCESS_DENIED.key(), InsightsViewModelFunnel.SC_ACCESS_DENIED_HINT);
        hints.put(DropoffScenario.SLOT_EXPIRED.key(), InsightsViewModelFunnel.SC_SLOT_EXPIRED_HINT);
        hints.put(DropoffScenario.SLOT_CONFLICT.key(), InsightsViewModelFunnel.SC_SLOT_CONFLICT_HINT);
        hints.put(DropoffScenario.BOOKING_REJECTED.key(), InsightsViewModelFunnel.SC_BOOKING_REJECTED_HINT);
        hints.put(DropoffScenario.SEARCH_REJECTED.key(), InsightsViewModelFunnel.SC_SEARCH_REJECTED_HINT);
        hints.put(DropoffScenario.ERROR_THEN_QUIT.key(), InsightsViewModelFunnel.SC_ERROR_THEN_QUIT_HINT);
        hints.put(DropoffScenario.REQUESTS_LOST.key(), InsightsViewModelFunnel.SC_REQUESTS_LOST_HINT);
        hints.put(DropoffScenario.NO_SLOTS.key(), InsightsViewModelFunnel.SC_NO_SLOTS_HINT);
        hints.put(DropoffScenario.FEW_SLOTS.key(), InsightsViewModelFunnel.SC_FEW_SLOTS_HINT);
        hints.put(DropoffScenario.SLOW_RESPONSE.key(), InsightsViewModelFunnel.SC_SLOW_HINT);
        hints.put(DropoffScenario.MANY_SEARCHES.key(), InsightsViewModelFunnel.SC_MANY_SEARCHES_HINT);
        hints.put(QUIET_KEY, InsightsViewModelFunnel.SC_QUIET_HINT);
        return Collections.unmodifiableMap(hints);
    }
}
