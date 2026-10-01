package com.autohaus.backend.usage.insights;

import com.autohaus.backend.common.html.GermanFormat;
import com.autohaus.backend.common.html.HtmlText;
import com.autohaus.backend.usage.StatFormat;
import com.autohaus.backend.usage.insights.CellHtml.Format;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.autohaus.backend.common.html.HtmlText.esc;
import static com.autohaus.backend.common.html.HtmlText.fill;

/**
 * Renders S4 „Buchungs- und Terminzeiten" — T-1..T-5 (REQ-038 §11.2, AC-17) / rendert S4.
 * {@code null} or {@code bookingsEnabled == false} → empty string (BR-4).
 *
 * <p>Layout per §11.2: the T-2 tiles {@code div.tm-kpis > div.tm-kpi} (month and year; „n &lt; 20"
 * as {@code .st-nd}, never „—"), then for „aktueller Monat bis gestern" and „laufendes Jahr bis
 * gestern" one below the other: T-1 (Mo–So × Tag/Abend/Nacht) next to T-3 (Mo–Sa × five slot
 * windows, {@code .tm-slots}) in {@code div.st-grid}, each a {@code table.st-table.st-heat} with
 * caption and {@code th scope}, and the T-5 bar list. / Kennzahl-Kacheln, zwei Heatmaps je
 * Zeitraum nebeneinander und die Vorlauf-Balken.
 *
 * <p><b>Relative legend at runtime (§10.5, TC-53):</b> below every heat table stands an
 * {@code ul.st-legend} built from {@link HeatLegend} and the items of §17
 * {@link #HEAT_LEGEND_RELATIVE} — „bis 11 · 12–22 · 23–33 · ab 34 (größter Wert 44)" — plus the
 * hidden states that occur. Heat classes only on visible cells. / Legende zur Laufzeit aus den
 * Grenzen und den Einträgen von {@code HEAT_LEGEND_RELATIVE}.
 */
@Component
public class TimingSectionRenderer {

    /** §17 {@code TIMING_HEADING}. */
    public static final String TIMING_HEADING = "Buchungs- und Terminzeiten";
    /** §17 {@code OFF_HOURS_KPI} — value and label are shown in separate lines of the tile. */
    public static final String OFF_HOURS_KPI = "{pct} % außerhalb Mo–Fr 07–18 Uhr gebucht";
    /** §17 {@code BOOKED_WHEN_TITLE}. */
    public static final String BOOKED_WHEN_TITLE = "Wann wird gebucht?";
    /** §17 {@code BOOKED_WHEN_COL_*} — main lines Tag · Abend · Nacht. */
    public static final List<String> BOOKED_WHEN_COL = List.of("Tag", "Abend", "Nacht");
    /** §17 {@code BOOKED_WHEN_COL_*} — sub lines 07–18 Uhr · 18–22 Uhr · 22–07 Uhr. */
    public static final List<String> BOOKED_WHEN_COL_SUB = List.of("07–18 Uhr", "18–22 Uhr", "22–07 Uhr");
    /** §17 {@code SLOT_TITLE}. */
    public static final String SLOT_TITLE = "Welche Terminzeiten sind gefragt?";
    /** §17 {@code SLOT_COL_*} — main lines. */
    public static final List<String> SLOT_COL = List.of("früh", "vorm.", "mittags", "nachm.", "spät");
    /** §17 {@code SLOT_COL_*} — sub lines. */
    public static final List<String> SLOT_COL_SUB = List.of("vor 9", "9–12", "12–14", "14–16", "ab 16");
    /** §17 {@code SLOT_NOTE}. */
    public static final String SLOT_NOTE =
        "Kunden wählen aus dem, was SoftNet anbietet. Die Verteilung zeigt also Angebot und Nachfrage.";
    /** §17 {@code SLOT_UNKNOWN} (only for n &gt; 0, E12). */
    public static final String SLOT_UNKNOWN = "ohne Terminzeit: {n}";
    /** §17 {@code LEAD_TITLE}. */
    public static final String LEAD_TITLE = "Vorlauf: Tage zwischen Buchung und Termin";
    /** §17 {@code LEAD_*} in bucket order 0-1 … 31+. */
    public static final List<String> LEAD_LABELS =
        List.of("0–1 Tag", "2–3 Tage", "4–7 Tage", "8–14 Tage", "15–30 Tage", "über 30 Tage");
    /**
     * §17 {@code HEAT_LEGEND_RELATIVE} — its items (levels 1–4, then the zero item) are rendered one
     * by one and filled from {@link HeatLegend}. / Die Einträge werden einzeln aus den Grenzen
     * befüllt.
     */
    public static final String HEAT_LEGEND_RELATIVE = "bis {upper1} · {upper1+1}–{upper2} · "
        + "{upper2+1}–{upper3} · ab {upper3+1} (größter Wert {max}) · 0: keine Buchung";

    /** Range line of a period / Zeitraumzeile. */
    public static final String TIMING_RANGE = "{period} · alle Standorte · {bookings} Buchungen · Europe/Berlin";
    /** Sub line of T-1 / Unterzeile T-1. */
    public static final String BOOKED_WHEN_RANGE = "Wochentag und Tageszeit der Buchung";
    /** Sub line of T-3 / Unterzeile T-3. */
    public static final String SLOT_RANGE = "Wochentag und Uhrzeit des gebuchten Termins";
    /** First column of both heat tables / erste Spalte. */
    public static final String COL_WEEKDAY = "Wochentag";
    /** Tile sub line when n &lt; 20 / Kachel-Unterzeile bei n &lt; 20. */
    public static final String KPI_TOO_FEW = "zu wenig Daten (n < 20)";
    /** Tile sub line with numerator and denominator / Zähler und Nenner. */
    public static final String KPI_OF = "{n} von {total}";
    /** Caption of T-1 (screen readers) / Caption T-1. */
    public static final String BOOKED_WHEN_CAPTION =
        "{title} Buchungen nach Wochentag und Tageszeit der Buchung, {period}{relative}";
    /** Caption of T-3 (screen readers) / Caption T-3. */
    public static final String SLOT_CAPTION =
        "{title} Gebuchte Termine nach Wochentag und Uhrzeit des Termins, {period}{relative}";
    /** {@code {relative}} of both captions when the table has a maximum / Bezug der Farbstufe. */
    public static final String CAPTION_RELATIVE = "; Farbstufe relativ zum Höchstwert {max}.";
    /** {@code {relative}} of both captions without a maximum / Satzende. */
    public static final String CAPTION_END = ".";
    /** {@code aria-label} part of a T-5 bar: bookings / Buchungen. */
    public static final String LEAD_ARIA_BOOKINGS = "{n} Buchungen";
    /** {@code aria-label} part of a T-5 bar: share / Anteil. */
    public static final String LEAD_ARIA_SHARE = "{pct} Prozent";

    /** Weekday keys of T-1 / Wochentage T-1. */
    private static final List<String> WEEKDAYS = List.of("MO", "TU", "WE", "TH", "FR", "SA", "SU");
    /** Key of the Sunday row, shown in T-3 only when needed / Schlüssel der Sonntagszeile. */
    private static final String SUNDAY = WEEKDAYS.getLast();
    /** The items of {@link #HEAT_LEGEND_RELATIVE}: levels 1–4, then the zero item / die Einträge. */
    private static final List<String> RELATIVE_LEGEND = CellHtml.legendItems(HEAT_LEGEND_RELATIVE);
    /** Index of the zero item in {@link #RELATIVE_LEGEND} / Index des Null-Eintrags. */
    private static final int LEGEND_ZERO_ITEM = 4;
    /** Booking dayparts of T-1 / Tagesabschnitte der Buchung. */
    private static final List<String> BOOKING_DAYPARTS = List.of(
        BookingDaypart.DAY.key(), BookingDaypart.EVENING.key(), BookingDaypart.NIGHT.key());
    /** Slot dayparts of T-3 / Terminfenster. */
    private static final List<String> SLOT_DAYPARTS = List.of(
        SlotDaypart.EARLY.key(), SlotDaypart.MORNING.key(), SlotDaypart.MIDDAY.key(),
        SlotDaypart.AFTERNOON.key(), SlotDaypart.LATE.key());
    /** Lead-time buckets of T-5 / Vorlauf-Buckets. */
    private static final List<String> LEAD_KEYS = List.of(
        LeadTimeBucket.D0_1.key(), LeadTimeBucket.D2_3.key(), LeadTimeBucket.D4_7.key(),
        LeadTimeBucket.D8_14.key(), LeadTimeBucket.D15_30.key(), LeadTimeBucket.D31_PLUS.key());

    /** Renders the section, {@code ""} when there is nothing to render / rendert die Sektion. */
    public String render(InsightsViewModel viewModel) {
        if (viewModel == null || !viewModel.bookingsEnabled()) {
            return "";
        }
        StringBuilder h = new StringBuilder(24576);
        h.append("<section id=\"timing\" class=\"tm\" aria-labelledby=\"timing-h\">");
        h.append("<h2 id=\"timing-h\">").append(esc(TIMING_HEADING)).append("</h2>");
        h.append(HtmlText.warnings(CellHtml.currentPeriodWarnings(viewModel.warnings())));
        if (!HtmlText.hasWarning(viewModel.warnings(), InsightsViewModel.WARN_HISTORY_UNAVAILABLE)) {
            h.append(body(viewModel));
        }
        h.append("</section>");
        return h.toString();
    }

    private static String body(InsightsViewModel vm) {
        if (!hasData(vm.month()) && !hasData(vm.year())) {
            return CellHtml.emptySince(vm.collectingSince());
        }
        StringBuilder h = new StringBuilder(24576);
        h.append("<div class=\"tm-kpis\">");
        h.append(kpi(vm.month(), CellHtml.PERIOD_MONTH));
        h.append(kpi(vm.year(), CellHtml.PERIOD_YEAR));
        h.append("</div>");
        h.append(period(vm, vm.month(), CellHtml.PERIOD_MONTH));
        h.append(period(vm, vm.year(), CellHtml.PERIOD_YEAR));
        return h.toString();
    }

    /** Whether a period carries collected data — „—" periods show the empty state (AC-23). */
    private static boolean hasData(Period period) {
        return period != null && CellHtml.state(period.bookings()) != CellState.NO_DATA;
    }

    // -----------------------------------------------------------------------------------------
    // T-2 tile / Kennzahl-Kachel
    // -----------------------------------------------------------------------------------------

    /** One T-2 tile; {@code TOO_FEW} shows „n &lt; 20" as {@code .st-nd} (§11.2). */
    private static String kpi(Period period, String fallback) {
        if (period == null) {
            return "";
        }
        Cell share = period.offHoursShare();
        String stateClass = CellHtml.stateClass(share);
        StringBuilder sub = new StringBuilder(esc(CellHtml.periodText(period, fallback)));
        if (CellHtml.isValue(share) && share.value() != null && CellHtml.isValue(period.bookings())) {
            sub.append(" &middot; ").append(fill(KPI_OF,
                "n", StatFormat.num(share.value()),
                "total", CellHtml.content(period.bookings(), Format.COUNT)));
        } else if (CellHtml.state(share) == CellState.TOO_FEW) {
            sub.append(" &middot; ").append(esc(KPI_TOO_FEW));
        }
        return "<div class=\"tm-kpi\"><div class=\"tm-kpi-val" + (stateClass.isEmpty() ? "" : " " + stateClass)
            + "\">" + CellHtml.content(share, Format.PERCENT_INT) + "</div>"
            + "<div class=\"tm-kpi-lbl\">" + esc(offHoursLabel()) + "</div>"
            + "<div class=\"tm-kpi-sub\">" + sub + "</div></div>";
    }

    /** The label part of {@code OFF_HOURS_KPI} („außerhalb Mo–Fr 07–18 Uhr gebucht"). */
    static String offHoursLabel() {
        return OFF_HOURS_KPI.substring(OFF_HOURS_KPI.indexOf("% ") + 2);
    }

    // -----------------------------------------------------------------------------------------
    // One period / ein Zeitraum
    // -----------------------------------------------------------------------------------------

    private static String period(InsightsViewModel vm, Period period, String fallback) {
        if (period == null) {
            return "";
        }
        String periodText = CellHtml.periodText(period, fallback);
        StringBuilder h = new StringBuilder(12288);
        h.append("<p class=\"st-range\">").append(fill(TIMING_RANGE,
            "period", esc(periodText),
            "bookings", CellHtml.inline(period.bookings(), Format.COUNT))).append("</p>");
        if (!hasData(period)) {
            h.append(CellHtml.emptySince(vm.collectingSince()));
            return h.toString();
        }
        h.append("<div class=\"st-grid\"><div>");
        h.append(bookedWhen(period, periodText));
        h.append("</div><div>");
        h.append(slots(period, periodText));
        h.append("</div></div>");
        h.append(leadTime(period, periodText));
        return h.toString();
    }

    /** T-1: Mo–So × Tag/Abend/Nacht / Wann wird gebucht. */
    private static String bookedWhen(Period period, String periodText) {
        Map<String, Map<String, Cell>> grid = period.bookedWhen();
        List<Cell> cells = collect(grid, WEEKDAYS, BOOKING_DAYPARTS);
        StringBuilder h = new StringBuilder(4096);
        h.append(title(BOOKED_WHEN_TITLE, periodText));
        h.append("<p class=\"st-range\">").append(esc(BOOKED_WHEN_RANGE)).append("</p>");
        HeatLegend legend = legendOf(period.bookedWhenLegend(), cells);
        h.append(HtmlText.scrollOpen(BOOKED_WHEN_TITLE + " " + periodText));
        h.append("<table class=\"st-table st-heat\">");
        h.append("<caption class=\"sr\">").append(esc(HtmlText.fillPlain(BOOKED_WHEN_CAPTION,
            "title", BOOKED_WHEN_TITLE, "period", periodText, "relative", relativeTo(legend)))).append("</caption>");
        h.append(head(BOOKED_WHEN_COL, BOOKED_WHEN_COL_SUB));
        h.append(heatBody(grid, WEEKDAYS, BOOKING_DAYPARTS));
        h.append("</table></div>");
        h.append(relativeLegend(legend, cells, BOOKED_WHEN_TITLE));
        return h.toString();
    }

    /** T-3: Mo–Sa (So only when needed) × five slot windows / Welche Terminzeiten sind gefragt. */
    private static String slots(Period period, String periodText) {
        Map<String, Map<String, Cell>> grid = period.slotGrid();
        List<String> days = new ArrayList<>(WEEKDAYS.subList(0, 6));
        if (sundayNeeded(grid)) {
            days.add(SUNDAY);
        }
        List<Cell> cells = collect(grid, days, SLOT_DAYPARTS);
        StringBuilder h = new StringBuilder(4096);
        h.append(title(SLOT_TITLE, periodText));
        h.append("<p class=\"st-range\">").append(esc(SLOT_RANGE)).append("</p>");
        HeatLegend legend = legendOf(period.slotLegend(), cells);
        h.append(HtmlText.scrollOpen(SLOT_TITLE + " " + periodText));
        h.append("<table class=\"st-table st-heat tm-slots\">");
        h.append("<caption class=\"sr\">").append(esc(HtmlText.fillPlain(SLOT_CAPTION,
            "title", SLOT_TITLE, "period", periodText, "relative", relativeTo(legend)))).append("</caption>");
        h.append(head(SLOT_COL, SLOT_COL_SUB));
        h.append(heatBody(grid, days, SLOT_DAYPARTS));
        h.append("</table></div>");
        h.append(relativeLegend(legend, cells, SLOT_TITLE));
        h.append("<p class=\"st-note\">").append(esc(SLOT_NOTE)).append("</p>");
        Cell unknown = period.slotUnknown();
        if (unknown != null && CellHtml.state(unknown) != CellState.NO_DATA && !CellHtml.isZero(unknown)) {
            h.append("<p class=\"st-note\">").append(fill(SLOT_UNKNOWN, "n", CellHtml.inline(unknown, Format.COUNT)))
                .append("</p>");
        }
        return h.toString();
    }

    /** T-5 bar list, six buckets in fixed order (§11.2) / Vorlauf-Balken. */
    private static String leadTime(Period period, String periodText) {
        Map<String, Cell> lead = period.leadTime();
        if (lead == null || lead.isEmpty()) {
            return "";
        }
        StringBuilder h = new StringBuilder(2048);
        h.append(title(LEAD_TITLE, periodText));
        h.append("<ul class=\"dev\" aria-label=\"").append(esc(LEAD_TITLE + ", " + periodText)).append("\">");
        for (int i = 0; i < LEAD_KEYS.size(); i++) {
            h.append(leadRow(LEAD_LABELS.get(i), lead.get(LEAD_KEYS.get(i))));
        }
        h.append("</ul>");
        return h.toString();
    }

    private static String leadRow(String label, Cell cell) {
        String aria;
        String value;
        if (CellHtml.isValue(cell)) {
            aria = label + ": "
                + (cell.value() != null ? LEAD_ARIA_BOOKINGS.replace("{n}", StatFormat.num(cell.value())) : "")
                + (cell.value() != null && cell.percent() != null ? GermanFormat.LIST_SEPARATOR : "")
                + (cell.percent() != null ? LEAD_ARIA_SHARE.replace("{pct}", StatFormat.decimal(cell.percent())) : "");
            value = (cell.value() != null ? "<b>" + StatFormat.num(cell.value()) + "</b>" : "")
                + (cell.value() != null && cell.percent() != null ? " &middot; " : "")
                + (cell.percent() != null ? StatFormat.percent(cell.percent()) : "");
        } else {
            aria = label + ": " + CellHtml.longText(CellHtml.state(cell));
            value = CellHtml.inline(cell, Format.COUNT);
        }
        return "<li class=\"dev-row\"><span class=\"dev-lbl\">" + esc(label) + "</span>"
            + "<span class=\"dev-bar\" role=\"img\" aria-label=\"" + esc(aria) + "\">"
            + "<span class=\"dev-fill st-fill\" style=\"width:" + CellHtml.barWidth(cell) + "%\"></span></span>"
            + "<span class=\"dev-val\">" + (value.isEmpty() ? HtmlText.DASH : value) + "</span></li>";
    }

    // -----------------------------------------------------------------------------------------
    // Heat tables / Heat-Tabellen
    // -----------------------------------------------------------------------------------------

    /** {@code h3.st-sub} with the period for screen readers (headings stay unique). */
    private static String title(String title, String periodText) {
        return "<h3 class=\"st-sub\">" + esc(title) + "<span class=\"sr\"> – " + esc(periodText) + "</span></h3>";
    }

    private static String head(List<String> columns, List<String> subs) {
        StringBuilder h = new StringBuilder(512);
        h.append("<thead><tr><th scope=\"col\" class=\"st-l\">").append(esc(COL_WEEKDAY)).append("</th>");
        for (int i = 0; i < columns.size(); i++) {
            h.append("<th scope=\"col\">").append(esc(columns.get(i))).append(" <span class=\"st-small\">")
                .append(esc(subs.get(i))).append("</span></th>");
        }
        h.append("</tr></thead>");
        return h.toString();
    }

    private static String heatBody(Map<String, Map<String, Cell>> grid, List<String> days, List<String> parts) {
        StringBuilder h = new StringBuilder(2048);
        h.append("<tbody>");
        for (String day : days) {
            int index = WEEKDAYS.indexOf(day);
            h.append("<tr><th scope=\"row\">").append(HtmlText.hidden(esc(GermanFormat.WEEKDAY_SHORT.get(index)),
                GermanFormat.WEEKDAY_LONG.get(index))).append("</th>");
            Map<String, Cell> row = CellHtml.get(grid, day);
            for (String part : parts) {
                h.append(CellHtml.heatTd(CellHtml.get(row, part), Format.COUNT));
            }
            h.append("</tr>");
        }
        h.append("</tbody>");
        return h.toString();
    }

    /** Sunday row of T-3 only when it carries something other than visible zeros (§11.2). */
    private static boolean sundayNeeded(Map<String, Map<String, Cell>> grid) {
        Map<String, Cell> sunday = CellHtml.get(grid, SUNDAY);
        if (sunday == null) {
            return false;
        }
        for (Cell cell : sunday.values()) {
            if (cell != null && !CellHtml.isZero(cell) && CellHtml.state(cell) != CellState.NO_DATA) {
                return true;
            }
        }
        return false;
    }

    private static List<Cell> collect(Map<String, Map<String, Cell>> grid, List<String> days, List<String> parts) {
        List<Cell> cells = new ArrayList<>();
        for (String day : days) {
            Map<String, Cell> row = CellHtml.get(grid, day);
            for (String part : parts) {
                cells.add(CellHtml.get(row, part));
            }
        }
        return cells;
    }

    /**
     * The legend of the view model, else rebuilt from the visible cells only (§10.5: hidden cells
     * never enter {@code max}). / Legende aus dem View-Model, sonst aus sichtbaren Zellen.
     */
    static HeatLegend legendOf(HeatLegend legend, List<Cell> cells) {
        if (legend != null) {
            return legend;
        }
        long max = 0;
        for (Cell cell : cells) {
            if (CellHtml.isValue(cell) && cell.value() != null) {
                max = Math.max(max, cell.value());
            }
        }
        return HeatLegend.ofMax(max);
    }

    private static String relativeTo(HeatLegend legend) {
        return legend.max() > 0 ? CAPTION_RELATIVE.replace("{max}", StatFormat.num(legend.max())) : CAPTION_END;
    }

    /**
     * {@code ul.st-legend} built at runtime from the bounds (§10.5, §17 {@code HEAT_LEGEND_RELATIVE});
     * empty levels (small maxima) are left out. / Laufzeit-Legende; leere Stufen entfallen.
     */
    static String relativeLegend(HeatLegend legend, List<Cell> cells, String title) {
        StringBuilder h = new StringBuilder(1024);
        h.append(CellHtml.heatLegendOpen(title));
        if (legend.max() > 0) {
            if (legend.upper1() >= 1) {
                h.append(CellHtml.legendSwatch(1, fill(RELATIVE_LEGEND.get(0),
                    "upper1", StatFormat.num(legend.upper1()))));
            }
            h.append(rangeItem(2, RELATIVE_LEGEND.get(1), "upper1+1", "upper2", legend.upper1() + 1, legend.upper2()));
            h.append(rangeItem(3, RELATIVE_LEGEND.get(2), "upper2+1", "upper3", legend.upper2() + 1, legend.upper3()));
            h.append(CellHtml.legendSwatch(4, fill(RELATIVE_LEGEND.get(3),
                "upper3+1", StatFormat.num(legend.upper3() + 1),
                "max", StatFormat.num(legend.max()))));
        }
        h.append(CellHtml.legendZero(RELATIVE_LEGEND.get(LEGEND_ZERO_ITEM)));
        h.append(CellHtml.legendStateItems(cells));
        h.append("</ul>");
        return h.toString();
    }

    /**
     * Level 2 or 3 of the relative legend: „{from}–{to}" from its §17 item, a single number when
     * both bounds meet, nothing for an empty level. / Stufe 2 bzw. 3; leere Stufen entfallen.
     */
    private static String rangeItem(int level, String template, String fromKey, String toKey, long from, long to) {
        if (to < from || to < 1) {
            return "";
        }
        String text = from == to
            ? StatFormat.num(from)
            : fill(template, fromKey, StatFormat.num(from), toKey, StatFormat.num(to));
        return CellHtml.legendSwatch(level, text);
    }
}
