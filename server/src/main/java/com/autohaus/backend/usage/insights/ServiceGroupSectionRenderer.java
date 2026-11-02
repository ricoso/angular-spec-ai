package com.autohaus.backend.usage.insights;

import com.autohaus.backend.common.html.GermanFormat;
import com.autohaus.backend.common.html.HtmlText;
import com.autohaus.backend.usage.StatFormat;
import com.autohaus.backend.usage.insights.CellHtml.Format;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.autohaus.backend.common.html.HtmlText.esc;
import static com.autohaus.backend.common.html.HtmlText.fill;

/**
 * Renders S3 „Servicegruppen" — G-1 bars and the G-2/G-3 matrix (REQ-038 §11.2, AC-16) /
 * rendert S3. {@code null} or {@code bookingsEnabled == false} → empty string (BR-4).
 *
 * <p><b>G-1</b> „Servicegruppen gesamt" is a bar list in the {@code .dev} pattern of REQ-037
 * ({@code ul.dev > li.dev-row.dev-wide}) — current month and running year up to yesterday, one
 * list below the other. Each bar is {@code role="img"} with mentions and share in its
 * {@code aria-label}; the value stands next to it as text. / Balkenliste mit Wert als Text.
 *
 * <p><b>G-2/G-3</b> is the heat table location × group of the closed month
 * ({@code table.st-table.st-heat.sg-matrix}) with an integer percentage and a heat level per cell;
 * the collective row {@code tr.sg-other} gathers the branches below 20 bookings; its label is
 * §17 {@code MATRIX_OTHER_BRANCHES} of {@code InsightsViewModelBranches} (one source). Heat classes
 * only on visible cells, the fixed legend {@link #HEAT_LEGEND_SHARE} below as {@code ul.st-legend} —
 * its items are read from that §17 text. / Heatmap mit Legende aus {@code HEAT_LEGEND_SHARE};
 * Heat-Klassen nur auf sichtbaren Zellen.
 *
 * <p><b>Order never betrays a hidden value (AC-20):</b> visible groups are sorted by their count,
 * hidden ones follow in catalogue order. / Ausgeblendete Gruppen stehen in fester Reihenfolge am
 * Ende.
 */
@Component
public class ServiceGroupSectionRenderer {

    /** §17 {@code GROUPS_HEADING}. */
    public static final String GROUPS_HEADING = "Servicegruppen: Was wird wo gebucht?";
    /** §17 {@code GROUPS_TOTAL_TITLE}. */
    public static final String GROUPS_TOTAL_TITLE = "Servicegruppen gesamt";
    /** §17 {@code MATRIX_TITLE}. */
    public static final String MATRIX_TITLE = "Standort × Servicegruppe ({month})";
    /** §17 {@code GROUPS_NOTE}. */
    public static final String GROUPS_NOTE = "Mehrfachnennung: eine Buchung zählt in jeder ihrer Gruppen "
        + "einmal. „Zusatzleistungen“ = Leistungen mit mehreren SoftNet-Tags; „nicht zuordenbar“ = "
        + "Standorte ohne Tags.";
    /** §17 {@code MOBILITY_NOTE}. */
    public static final String MOBILITY_NOTE =
        "Zusätzlich mit Mietwagen gebucht: {n} von {total} Buchungen ({pct} %).";
    /**
     * §17 {@code HEAT_LEGEND_SHARE} (G-2, fixed) — the four level items and the zero item joined by
     * {@link CellHtml#LEGEND_ITEM_SEPARATOR}; rendered item by item. / Vier Stufen und der
     * Null-Eintrag.
     */
    public static final String HEAT_LEGEND_SHARE = "1–14 % · 15–29 % · 30–44 % · ab 45 % · 0 %: keine Buchung";
    /** §17 {@code NO_CLOSED_MONTH}. */
    public static final String NO_CLOSED_MONTH = BranchRankingSectionRenderer.NO_CLOSED_MONTH;

    /** §17 {@code GROUP_*} — Inspektion. */
    public static final String GROUP_INSPECTION = "Inspektion";
    /** §17 {@code GROUP_*} — TÜV. */
    public static final String GROUP_TUV = "TÜV";
    /** §17 {@code GROUP_*} — Bremsflüssigkeit. */
    public static final String GROUP_BRAKE_FLUID = "Bremsflüssigkeit";
    /** §17 {@code GROUP_*} — Räderwechsel. */
    public static final String GROUP_TIRE_CHANGE = "Räderwechsel";
    /** §17 {@code GROUP_*} — Aktionen / Checks. */
    public static final String GROUP_ACTIONS_CHECKS = "Aktionen / Checks";
    /** §17 {@code GROUP_*} — Reparatur / Beanstandung. */
    public static final String GROUP_REPAIR = "Reparatur / Beanstandung";
    /** §17 {@code GROUP_*} — Karosserie / Frontscheibe. */
    public static final String GROUP_BODYWORK = "Karosserie / Frontscheibe";
    /** §17 {@code GROUP_*} — Zusatzleistungen. */
    public static final String GROUP_EXTRAS = "Zusatzleistungen";
    /** §17 {@code GROUP_*} — Hol & Bring. */
    public static final String GROUP_PICKUP = "Hol & Bring";
    /** §17 {@code GROUP_*} — nicht zuordenbar. */
    public static final String GROUP_UNKNOWN = "nicht zuordenbar";
    /** §17 {@code GROUP_*} — Mietwagen (G-3). */
    public static final String GROUP_MOBILITY = "Mietwagen";
    /** §17 {@code GROUP_*} — the labels above keyed by {@link ServiceGroup} key plus {@code mobility}. */
    public static final Map<String, String> GROUP_LABELS = groupLabels();

    /** Range line of G-1 / Zeitraumzeile G-1. */
    public static final String GROUPS_RANGE = "{period} · alle Standorte · {bookings} Buchungen";
    /** Range line of the matrix / Zeitraumzeile der Matrix. */
    public static final String MATRIX_RANGE = "{month} (abgeschlossener Monat) · Anteil der Buchungen des "
        + "Standorts, die diese Gruppe enthalten · eigene Zeile ab 20 Buchungen";
    /** Hint when the period has fewer than 20 bookings / Hinweis bei n &lt; 20. */
    public static final String SHARES_TOO_FEW = "Anteile: zu wenig Daten (n < 20). Gezeigt werden nur Nennungen.";
    /** Note on G-3 below the matrix / Hinweis zu G-3. */
    public static final String MATRIX_MOBILITY_NOTE =
        "„Mietwagen“: Anteil der Buchungen des Standorts, die zusätzlich mit Mietwagen gebucht wurden.";
    /** Column „Buchungen" of the matrix / Spalte „Buchungen". */
    public static final String MATRIX_COL_BOOKINGS = "Buchungen";
    /** Column „Standort" of the matrix / Spalte „Standort". */
    public static final String MATRIX_COL_BRANCH = "Standort";
    /** Title of the matrix table (scroll region) / Tabellentitel der Matrix. */
    public static final String MATRIX_TABLE_TITLE = "Standort mal Servicegruppe, {month}";
    /** Caption of the matrix (screen readers) / Caption der Matrix. */
    public static final String MATRIX_CAPTION = MATRIX_TABLE_TITLE + ": Anteil der Buchungen des Standorts, "
        + "die diese Gruppe enthalten, in Prozent; eigene Zeile nur für Standorte mit mindestens 20 Buchungen, "
        + "die übrigen zusammen als weitere Standorte.";
    /** Title part of the matrix legend „Legende Farbstufen Matrix" / Legendenname. */
    public static final String MATRIX_LEGEND_TITLE = "Matrix";
    /** {@code aria-label} part of a G-1 bar: mentions / Nennungen. */
    public static final String GROUP_ARIA_MENTIONS = "{n} Nennungen";
    /** {@code aria-label} part of a G-1 bar: share / Anteil. */
    public static final String GROUP_ARIA_SHARE = "{pct} Prozent der Buchungen";

    /** The key of the rental-car column / Schlüssel der Mietwagen-Spalte. */
    public static final String MOBILITY_KEY = "mobility";

    /** The items of {@link #HEAT_LEGEND_SHARE}: levels 1–4, then the zero item / die Einträge. */
    private static final List<String> SHARE_LEGEND = CellHtml.legendItems(HEAT_LEGEND_SHARE);
    /** Number of colour levels of G-2 / Farbstufen. */
    private static final int SHARE_LEVELS = 4;

    /** The {@code ({month})} part of {@code MATRIX_TITLE}, left out without a closed month. */
    private static final String MATRIX_TITLE_MONTH_PART = " ({month})";
    /** The {@code ({date})} part of {@code NO_CLOSED_MONTH}, left out without a collection start. */
    private static final String NO_CLOSED_MONTH_DATE_PART = " ({date})";
    /** The {@code ({pct} %)} part of {@code MOBILITY_NOTE}, left out without a visible share. */
    private static final String MOBILITY_PCT_PART = " ({pct} %)";
    /** Where the sub line of {@code MATRIX_OTHER_BRANCHES} starts / Beginn der Unterzeile. */
    private static final String OTHER_BRANCHES_SUB_START = " (";

    /** Renders the section, {@code ""} when there is nothing to render / rendert die Sektion. */
    public String render(InsightsViewModel viewModel) {
        if (viewModel == null || !viewModel.bookingsEnabled()) {
            return "";
        }
        StringBuilder h = new StringBuilder(16384);
        h.append("<section id=\"service-groups\" class=\"sg\" aria-labelledby=\"service-groups-h\">");
        h.append("<h2 id=\"service-groups-h\">").append(esc(GROUPS_HEADING)).append("</h2>");
        h.append(HtmlText.warnings(viewModel.warnings()));
        if (!HtmlText.hasWarning(viewModel.warnings(), InsightsViewModel.WARN_HISTORY_UNAVAILABLE)) {
            h.append(totals(viewModel));
            h.append(matrix(viewModel));
        }
        h.append("</section>");
        return h.toString();
    }

    // -----------------------------------------------------------------------------------------
    // G-1 / Servicegruppen gesamt
    // -----------------------------------------------------------------------------------------

    private static String totals(InsightsViewModel vm) {
        StringBuilder h = new StringBuilder(6144);
        h.append("<h3 class=\"st-sub\">").append(esc(GROUPS_TOTAL_TITLE)).append("</h3>");
        if (vm.month() == null && vm.year() == null) {
            h.append(CellHtml.emptySince(vm.collectingSince()));
            return h.toString();
        }
        h.append(periodTotals(vm, vm.month(), CellHtml.PERIOD_MONTH));
        h.append(periodTotals(vm, vm.year(), CellHtml.PERIOD_YEAR));
        h.append("<p class=\"st-note\">").append(esc(GROUPS_NOTE)).append("</p>");
        return h.toString();
    }

    /** One period: range line, bar list and rental-car note / ein Zeitraum. */
    private static String periodTotals(InsightsViewModel vm, Period period, String fallback) {
        if (period == null) {
            return "";
        }
        String periodText = CellHtml.periodText(period, fallback);
        StringBuilder h = new StringBuilder(4096);
        h.append("<p class=\"st-range\">").append(fill(GROUPS_RANGE,
            "period", esc(periodText),
            "bookings", CellHtml.inline(period.bookings(), Format.COUNT))).append("</p>");
        if (CellHtml.state(period.bookings()) == CellState.NO_DATA) {
            h.append(CellHtml.emptySince(vm.collectingSince()));
            return h.toString();
        }
        List<Map.Entry<String, Cell>> groups = orderedGroups(period.groups());
        boolean anyValue = groups.stream().anyMatch(e -> CellHtml.isValue(e.getValue()));
        boolean anyShare = groups.stream()
            .anyMatch(e -> CellHtml.isValue(e.getValue()) && e.getValue().percent() != null);
        if (anyValue && !anyShare) {
            h.append("<p class=\"st-thin\">").append(esc(SHARES_TOO_FEW)).append("</p>");
        }
        h.append("<ul class=\"dev\" aria-label=\"").append(esc(GROUPS_TOTAL_TITLE + ", " + periodText)).append("\">");
        for (Map.Entry<String, Cell> group : groups) {
            h.append(groupRow(label(group.getKey()), group.getValue()));
        }
        h.append("</ul>");
        h.append(mobilityNote(period));
        return h.toString();
    }

    /**
     * Visible groups by count descending (ties in catalogue order), then hidden groups in catalogue
     * order — the order never depends on a hidden value (AC-20). The rental-car key is excluded;
     * it is shown as {@code MOBILITY_NOTE}. / Sichtbare nach Nennungen, ausgeblendete fest am Ende.
     */
    static List<Map.Entry<String, Cell>> orderedGroups(Map<String, Cell> groups) {
        List<Map.Entry<String, Cell>> visible = new ArrayList<>();
        List<Map.Entry<String, Cell>> hidden = new ArrayList<>();
        if (groups == null) {
            return visible;
        }
        List<String> keys = new ArrayList<>(groups.keySet());
        keys.remove(MOBILITY_KEY);
        keys.removeIf(Objects::isNull);
        keys.sort(Comparator.comparingInt(ServiceGroupSectionRenderer::catalogueIndex));
        for (String key : keys) {
            Map.Entry<String, Cell> entry = Map.entry(key, groups.get(key) == null ? Cell.noData() : groups.get(key));
            (CellHtml.isValue(entry.getValue()) ? visible : hidden).add(entry);
        }
        visible.sort(Comparator.comparingDouble((Map.Entry<String, Cell> e) -> -sortValue(e.getValue())));
        List<Map.Entry<String, Cell>> ordered = new ArrayList<>(visible);
        ordered.addAll(hidden);
        return ordered;
    }

    private static double sortValue(Cell cell) {
        if (cell.value() != null) {
            return cell.value();
        }
        return cell.percent() != null ? cell.percent() : 0.0;
    }

    private static int catalogueIndex(String key) {
        ServiceGroup group = ServiceGroup.fromKey(key);
        if (group == ServiceGroup.UNKNOWN && !ServiceGroup.UNKNOWN.key().equals(key)) {
            return ServiceGroup.values().length;
        }
        return group.ordinal();
    }

    /** One bar row: label, bar ({@code role="img"}), mentions and share as text. */
    private static String groupRow(String label, Cell cell) {
        String aria;
        if (CellHtml.isValue(cell)) {
            aria = label + ": "
                + (cell.value() != null ? GROUP_ARIA_MENTIONS.replace("{n}", StatFormat.num(cell.value())) : "")
                + (cell.value() != null && cell.percent() != null ? GermanFormat.LIST_SEPARATOR : "")
                + (cell.percent() != null ? GROUP_ARIA_SHARE.replace("{pct}", StatFormat.decimal(cell.percent())) : "");
        } else {
            aria = label + ": " + CellHtml.longText(CellHtml.state(cell));
        }
        return "<li class=\"dev-row dev-wide\"><span class=\"dev-lbl\">" + esc(label) + "</span>"
            + "<span class=\"dev-bar\" role=\"img\" aria-label=\"" + esc(aria) + "\">"
            + "<span class=\"dev-fill st-fill\" style=\"width:" + CellHtml.barWidth(cell) + "%\"></span></span>"
            + "<span class=\"dev-val\">" + valueText(cell) + "</span></li>";
    }

    /** „<b>131</b> · 45,8 %" or the hidden state / Wert als Text. */
    private static String valueText(Cell cell) {
        if (!CellHtml.isValue(cell)) {
            return CellHtml.inline(cell, Format.COUNT);
        }
        StringBuilder h = new StringBuilder(64);
        if (cell.value() != null) {
            h.append("<b>").append(StatFormat.num(cell.value())).append("</b>");
        }
        if (cell.percent() != null) {
            if (!h.isEmpty()) {
                h.append(" &middot; ");
            }
            h.append(StatFormat.percent(cell.percent()));
        }
        return h.isEmpty() ? StatFormat.DASH : h.toString();
    }

    /** {@code MOBILITY_NOTE} when the period carries the rental-car counter (G-3 global). */
    private static String mobilityNote(Period period) {
        Cell mobility = CellHtml.get(period.groups(), MOBILITY_KEY);
        if (mobility == null || CellHtml.state(mobility) == CellState.NO_DATA) {
            return "";
        }
        String template = MOBILITY_NOTE;
        if (!CellHtml.isValue(mobility) || mobility.percent() == null) {
            template = template.replace(MOBILITY_PCT_PART, "");
        }
        return "<p class=\"st-note\">" + fill(template,
            "n", CellHtml.inline(mobility, Format.COUNT),
            "total", CellHtml.inline(period.bookings(), Format.COUNT),
            "pct", mobility.percent() == null ? "" : StatFormat.decimal(mobility.percent())) + "</p>";
    }

    // -----------------------------------------------------------------------------------------
    // G-2/G-3 / Matrix
    // -----------------------------------------------------------------------------------------

    private static String matrix(InsightsViewModel vm) {
        YearMonth month = vm.closedMonth();
        StringBuilder h = new StringBuilder(8192);
        h.append("<h3 class=\"st-sub\">").append(month == null
            ? esc(MATRIX_TITLE.replace(MATRIX_TITLE_MONTH_PART, ""))
            : fill(MATRIX_TITLE, "month", esc(GermanFormat.monthYear(month)))).append("</h3>");
        if (month == null) {
            h.append(HtmlText.empty(vm.collectingSince() == null
                ? esc(NO_CLOSED_MONTH.replace(NO_CLOSED_MONTH_DATE_PART, ""))
                : fill(NO_CLOSED_MONTH, "date", esc(GermanFormat.day(vm.collectingSince())))));
            return h.toString();
        }
        GroupMatrix matrix = vm.matrix();
        if (matrix == null || matrix.columns() == null || matrix.rows() == null) {
            return h.toString();
        }
        String monthText = GermanFormat.monthYear(month);
        h.append("<p class=\"st-range\">").append(fill(MATRIX_RANGE, "month", esc(monthText))).append("</p>");
        h.append(HtmlText.scrollOpen(HtmlText.fillPlain(MATRIX_TABLE_TITLE, "month", monthText)));
        h.append("<table class=\"st-table st-heat sg-matrix\">");
        h.append("<caption class=\"sr\">").append(fill(MATRIX_CAPTION, "month", esc(monthText)))
            .append("</caption>");
        h.append("<thead><tr><th scope=\"col\" class=\"st-l\">").append(esc(MATRIX_COL_BRANCH)).append("</th>")
            .append("<th scope=\"col\">").append(esc(MATRIX_COL_BOOKINGS)).append("</th>");
        for (String column : matrix.columns()) {
            h.append("<th scope=\"col\">").append(esc(label(column))).append("</th>");
        }
        h.append("</tr></thead><tbody>");
        List<Cell> all = new ArrayList<>();
        for (MatrixRow row : matrix.rows()) {
            h.append(row.otherBranches() ? "<tr class=\"sg-other\">" : "<tr>");
            h.append("<th scope=\"row\">").append(rowLabel(row)).append("</th>");
            h.append(CellHtml.td(row.bookings(), Format.COUNT));
            all.add(row.bookings());
            for (String column : matrix.columns()) {
                Cell cell = CellHtml.get(row.cells(), column);
                all.add(cell);
                h.append(CellHtml.heatTd(cell, Format.PERCENT_INT));
            }
            h.append("</tr>");
        }
        h.append("</tbody></table></div>");
        h.append(shareLegend(all));
        if (matrix.columns().contains(MOBILITY_KEY)) {
            h.append("<p class=\"st-note\">").append(esc(MATRIX_MOBILITY_NOTE)).append("</p>");
        }
        return h.toString();
    }

    /** Branch name, or „weitere Standorte" with its sub line for the collective row (BR-9 Nr. 3). */
    private static String rowLabel(MatrixRow row) {
        if (!row.otherBranches()) {
            return esc(row.branchName());
        }
        String label = InsightsViewModelBranches.MATRIX_OTHER_BRANCHES;
        int split = label.indexOf(OTHER_BRANCHES_SUB_START);
        return esc(label.substring(0, split)) + " <span class=\"st-small\">"
            + esc(label.substring(split + 1)) + "</span>";
    }

    /** Fixed legend of G-2 plus the hidden states that occur (§17 {@code HEAT_LEGEND_SHARE}). */
    private static String shareLegend(List<Cell> cells) {
        StringBuilder h = new StringBuilder(1024);
        h.append(CellHtml.heatLegendOpen(MATRIX_LEGEND_TITLE));
        for (int level = 1; level <= SHARE_LEVELS; level++) {
            h.append(CellHtml.legendSwatch(level, esc(SHARE_LEGEND.get(level - 1))));
        }
        h.append(CellHtml.legendZero(SHARE_LEGEND.get(SHARE_LEVELS)));
        h.append(CellHtml.legendStateItems(cells));
        h.append("</ul>");
        return h.toString();
    }

    /** Visible label of a group key; unknown keys are shown as is (escaped by the caller). */
    static String label(String key) {
        String label = GROUP_LABELS.get(key);
        return label != null ? label : key == null ? "" : key;
    }

    private static Map<String, String> groupLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(ServiceGroup.INSPECTION.key(), GROUP_INSPECTION);
        labels.put(ServiceGroup.TUV.key(), GROUP_TUV);
        labels.put(ServiceGroup.BRAKE_FLUID.key(), GROUP_BRAKE_FLUID);
        labels.put(ServiceGroup.TIRE_CHANGE.key(), GROUP_TIRE_CHANGE);
        labels.put(ServiceGroup.ACTIONS_CHECKS.key(), GROUP_ACTIONS_CHECKS);
        labels.put(ServiceGroup.REPAIR.key(), GROUP_REPAIR);
        labels.put(ServiceGroup.BODYWORK.key(), GROUP_BODYWORK);
        labels.put(ServiceGroup.EXTRAS.key(), GROUP_EXTRAS);
        labels.put(ServiceGroup.PICKUP.key(), GROUP_PICKUP);
        labels.put(ServiceGroup.UNKNOWN.key(), GROUP_UNKNOWN);
        labels.put(MOBILITY_KEY, GROUP_MOBILITY);
        return Collections.unmodifiableMap(labels);
    }
}
