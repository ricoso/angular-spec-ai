package com.autohaus.backend.usage.insights;

import com.autohaus.backend.common.html.GermanFormat;
import com.autohaus.backend.common.html.HtmlText;
import com.autohaus.backend.usage.StatFormat;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.autohaus.backend.common.html.HtmlText.esc;
import static com.autohaus.backend.common.html.HtmlText.fill;

/**
 * Cell markup of the statistics sections S2–S4 and S6 (REQ-038 §11.2, §11.5, §17 {@code CELL_*}) /
 * Zell-Markup der Statistik-Sektionen.
 *
 * <p><b>One place for the cell contract / eine Stelle für den Zellvertrag:</b> every insights
 * section renderer turns a {@link Cell} into markup exclusively through this helper, so the four
 * display states look identical everywhere:
 * <ul>
 *   <li>{@code VALUE} — the number as text; a heat class {@code st-heat-1..4} only in this state</li>
 *   <li>{@code SUPPRESSED} — {@code .st-sup} „&lt; 5"</li>
 *   <li>{@code WITHHELD} — {@code .st-ka} „k. A."</li>
 *   <li>{@code TOO_FEW} — {@code .st-nd} „n &lt; 20"; {@code NO_DATA} — {@code .st-nd} „—"</li>
 * </ul>
 * Each hidden state carries its visible short text with {@code aria-hidden="true"} plus the
 * {@code .sr} long text from §17, so a screen reader never announces „kleiner 5" as a number. The
 * visible short texts come from {@link StatFormat#cell(Cell)} (their one source), the long texts
 * {@code CELL_*_SR} are defined here. / Kurztexte aus {@code StatFormat}, Langtexte hier.
 *
 * <p><b>No values leak / keine mittelbaren Werte (AC-20, BR-9 Nr. 5):</b> a non-{@code VALUE}
 * cell never renders a number, a heat class or a bar width. {@link Cell} already normalises hidden
 * cells on construction; this class refuses to undo a suppression all the same (second
 * safeguard). Suppression itself happens only in {@code InsightsViewModel.build(...)}. /
 * Zweite Sicherung neben der Normalisierung in {@code Cell}.
 *
 * <p>Generic helpers without a cell (escaping, templates, warning band, scroll region, empty
 * state, German dates) live in the neutral package {@code com.autohaus.backend.common.html}; this
 * class keeps only what needs cells or insights types: the cell states, heat legends, the empty
 * state „Erfassung seit …" and the period labels. / Generisches liegt im neutralen Paket.
 */
public final class CellHtml {

    /** Screen-reader text of {@code SUPPRESSED} (§17 {@code CELL_SUPPRESSED}, {@code .sr}). */
    public static final String CELL_SUPPRESSED_SR = "weniger als 5, unterdrückt";
    /** Screen-reader text of {@code WITHHELD} (§17 {@code CELL_WITHHELD}, {@code .sr}). */
    public static final String CELL_WITHHELD_SR = "keine Angabe, unterdrückt gegen Rückrechnung";
    /** Screen-reader text of {@code TOO_FEW} (§17 {@code CELL_TOO_FEW}, {@code .sr}). */
    public static final String CELL_TOO_FEW_SR = "zu wenig Daten, weniger als 20 Fälle";
    /** Screen-reader text of {@code NO_DATA} (§17 {@code CELL_NO_DATA}, {@code .sr}) / „keine Daten". */
    public static final String CELL_NO_DATA_SR = HtmlText.NO_DATA_SR;

    /** §17 {@code PERIOD_MONTH} (BR-10) / aktueller Monat bis gestern. */
    public static final String PERIOD_MONTH = "aktueller Monat bis gestern ({range})";
    /** §17 {@code PERIOD_YEAR} (BR-10) / laufendes Jahr bis gestern. */
    public static final String PERIOD_YEAR = "laufendes Jahr bis gestern ({range})";
    /** The {@code {range}} part of a period label, left out without a range / Bereichsteil. */
    private static final String PERIOD_RANGE_PART = " ({range})";

    /** Empty state with a known collection start (AC-23, BR-19) / Leerzustand mit Erfassungsbeginn. */
    public static final String EMPTY_SINCE = "Erfassung seit {date} – noch keine ausgewerteten Tage.";
    /** Empty state without any collection start / Leerzustand ohne Erfassungsbeginn. */
    public static final String EMPTY_NOT_STARTED = "Erfassung noch nicht begonnen.";

    /** Accessible name of a heat legend {@code ul.st-legend} / Name einer Farbstufen-Legende. */
    public static final String HEAT_LEGEND_ARIA = "Legende Farbstufen {title}";
    /** Separator of the items of a §17 {@code HEAT_LEGEND_*} text / Trenner der Legenden-Einträge. */
    public static final String LEGEND_ITEM_SEPARATOR = " · ";
    /** Separator between the value and the text of the zero item („0 %: keine Buchung"). */
    public static final String LEGEND_LABEL_SEPARATOR = ": ";

    /**
     * How a {@code VALUE} cell is printed / wie ein sichtbarer Wert formatiert wird. The precision
     * per metric is fixed in §10.5; the view model already rounded, the renderer only picks the
     * matching display. / Genauigkeit je Kennzahl nach §10.5.
     */
    public enum Format {
        /** Counter with German thousands separators, e.g. „1.122" / Zähler. */
        COUNT,
        /** Percentage with one decimal, e.g. „45,8 %" (G-1, T-5, F-1/F-2, Dropoff) / eine Stelle. */
        PERCENT,
        /** Integer percentage, e.g. „38 %" (R-2, T-2, T-4, G-2, G-3) / ganzzahlig. */
        PERCENT_INT,
        /** One-decimal ratio, e.g. „14,1" (R-4) / Verhältniswert. */
        RATIO
    }

    private CellHtml() {
    }

    // -----------------------------------------------------------------------------------------
    // Cell state / Zellzustand
    // -----------------------------------------------------------------------------------------

    /** The display state; a missing cell counts as {@code NO_DATA}, never as 0 (BR-19). */
    public static CellState state(Cell cell) {
        return cell == null || cell.state() == null ? CellState.NO_DATA : cell.state();
    }

    /** Whether the cell is visible / ob die Zelle einen sichtbaren Wert trägt. */
    public static boolean isValue(Cell cell) {
        return state(cell) == CellState.VALUE;
    }

    /** Whether the cell is a visible counter equal to 0 / sichtbare Null. */
    public static boolean isZero(Cell cell) {
        return isValue(cell) && cell.value() != null && cell.value() == 0L;
    }

    /** State class {@code st-sup|st-ka|st-nd}, empty for {@code VALUE} (§11.2). */
    public static String stateClass(Cell cell) {
        return switch (state(cell)) {
            case VALUE -> "";
            case SUPPRESSED -> "st-sup";
            case WITHHELD -> "st-ka";
            case TOO_FEW, NO_DATA -> "st-nd";
        };
    }

    /**
     * Heat class {@code st-heat-1..4} — only for a visible cell with a level (§10.5: hidden cells
     * and 0 stay without colour so the colour never betrays a hidden size). / Heat-Klasse nur bei
     * sichtbarem Wert.
     */
    public static String heatClass(Cell cell) {
        if (!isValue(cell) || cell.heat() < 1 || cell.heat() > 4) {
            return "";
        }
        return "st-heat-" + cell.heat();
    }

    /** Visible short text of a hidden state as HTML — from {@link StatFormat#cell(Cell)}. */
    public static String shortText(CellState state) {
        return StatFormat.cell(stateCell(state));
    }

    /** Screen-reader long text of a hidden state (plain) / Langtext eines Zustands. */
    public static String longText(CellState state) {
        return switch (state) {
            case VALUE -> "";
            case SUPPRESSED -> CELL_SUPPRESSED_SR;
            case WITHHELD -> CELL_WITHHELD_SR;
            case TOO_FEW -> CELL_TOO_FEW_SR;
            case NO_DATA -> CELL_NO_DATA_SR;
        };
    }

    // -----------------------------------------------------------------------------------------
    // Cell markup / Zell-Markup
    // -----------------------------------------------------------------------------------------

    /**
     * Inner markup without the state class: the formatted number for {@code VALUE}, otherwise the
     * short text ({@code aria-hidden}) plus the {@code .sr} long text. / Inhalt ohne Zustandsklasse.
     */
    public static String content(Cell cell, Format format) {
        CellState state = state(cell);
        if (state == CellState.VALUE) {
            return formatValue(cell, format);
        }
        return HtmlText.hidden(shortText(state), longText(state));
    }

    /** The cell as {@code <td>} / die Zelle als Tabellenzelle. */
    public static String td(Cell cell, Format format) {
        return td(cell, format, null, false);
    }

    /** The cell as {@code <td>} with extra classes such as {@code opt} / mit Zusatzklassen. */
    public static String td(Cell cell, Format format, String extraClasses) {
        return td(cell, format, extraClasses, false);
    }

    /** A heat cell ({@code st-heat-n} only for {@code VALUE}) / Heat-Zelle. */
    public static String heatTd(Cell cell, Format format) {
        return td(cell, format, null, true);
    }

    /**
     * The cell as {@code <td>} / die Zelle als Tabellenzelle.
     *
     * @param extraClasses additional classes (e.g. {@code opt}, {@code st-l}), may be {@code null}
     * @param heat whether a heat class may be added (heat tables only) / Heat-Klasse zulassen
     */
    public static String td(Cell cell, Format format, String extraClasses, boolean heat) {
        String classes = classes(extraClasses, stateClass(cell), heat ? heatClass(cell) : "");
        return "<td" + classAttr(classes) + ">" + content(cell, format) + "</td>";
    }

    /**
     * Inline form for text runs and composite cells: the bare number for {@code VALUE}, otherwise a
     * {@code <span>} with the state class. / Inline-Form für Fließtext und zusammengesetzte Zellen.
     */
    public static String inline(Cell cell, Format format) {
        if (isValue(cell)) {
            return formatValue(cell, format);
        }
        return "<span class=\"" + stateClass(cell) + "\">" + content(cell, format) + "</span>";
    }

    /** Like {@link #inline} but a visible number is bold ({@code <b>}) / fette Zahl. */
    public static String strong(Cell cell, Format format) {
        return isValue(cell) ? "<b>" + formatValue(cell, format) + "</b>" : inline(cell, format);
    }

    /**
     * The visible percentage of a cell as CSS width with a dot, {@code 0.0} for every hidden cell
     * (BR-9 Nr. 5) / Balkenbreite, 0.0 bei ausgeblendeten Zellen.
     */
    public static String barWidth(Cell cell) {
        if (!isValue(cell) || cell.percent() == null) {
            return "0.0";
        }
        return StatFormat.cssWidth(clamp(cell.percent()));
    }

    /** Clamps a width to 0–100 / begrenzt eine Breite auf 0–100. */
    public static double clamp(double width) {
        if (Double.isNaN(width) || width < 0) {
            return 0.0;
        }
        return Math.min(100.0, width);
    }

    // -----------------------------------------------------------------------------------------
    // Legends / Legenden (§10.5, §17 HEAT_LEGEND_*)
    // -----------------------------------------------------------------------------------------

    /** Opens a heat legend {@code ul.st-legend} named „Legende Farbstufen {title}" / öffnet eine Legende. */
    public static String heatLegendOpen(String title) {
        return "<ul class=\"st-legend\" aria-label=\""
            + esc(HtmlText.fillPlain(HEAT_LEGEND_ARIA, "title", title == null ? "" : title)) + "\">";
    }

    /**
     * The items of a §17 {@code HEAT_LEGEND_*} text, split at {@link #LEGEND_ITEM_SEPARATOR} — the
     * level items first, the zero item last / die Einträge einer Legende.
     */
    public static List<String> legendItems(String legend) {
        return legend == null ? List.of() : List.of(legend.split(Pattern.quote(LEGEND_ITEM_SEPARATOR)));
    }

    /** One colour item: swatch {@code st-sw st-heat-n} plus its escaped text / ein Farbstufen-Eintrag. */
    public static String legendSwatch(int level, String html) {
        return "<li><span class=\"st-sw st-heat-" + level + "\" aria-hidden=\"true\"></span>" + html + "</li>";
    }

    /**
     * The zero item of a heat legend from its §17 text „{value}: {text}" — the value as a text swatch
     * ({@code aria-hidden}), again with the separator for screen readers, then the text; e.g.
     * „0 %: keine Buchung". / Null-Eintrag der Legende.
     */
    public static String legendZero(String item) {
        String text = item == null ? "" : item;
        int split = text.indexOf(LEGEND_LABEL_SEPARATOR);
        String value = split < 0 ? "" : text.substring(0, split);
        String label = split < 0 ? text : text.substring(split + LEGEND_LABEL_SEPARATOR.length());
        return "<li><span class=\"st-sw-txt\" aria-hidden=\"true\">" + HtmlText.nonBreaking(esc(value))
            + "</span><span class=\"sr\">" + esc(value + LEGEND_LABEL_SEPARATOR) + "</span>" + esc(label) + "</li>";
    }

    /**
     * Legend items for the hidden states that actually occur in a table (e.g. „&lt; 5 weniger als
     * 5, unterdrückt") — the colour scale never explains them. / Legenden-Einträge für vorkommende
     * Zustände.
     */
    public static String legendStateItems(Iterable<Cell> cells) {
        Set<CellState> present = EnumSet.noneOf(CellState.class);
        if (cells != null) {
            for (Cell cell : cells) {
                present.add(state(cell));
            }
        }
        StringBuilder h = new StringBuilder(256);
        for (CellState state : present) {
            if (state != CellState.VALUE) {
                h.append("<li><span class=\"st-sw-txt\" aria-hidden=\"true\">").append(shortText(state))
                    .append("</span>").append(esc(longText(state))).append("</li>");
            }
        }
        return h.toString();
    }

    // -----------------------------------------------------------------------------------------
    // Insights section helpers / Sektions-Helfer der Auswertungen
    // -----------------------------------------------------------------------------------------

    /**
     * The warnings of a section that shows the <b>current</b> month (S4, S6) — without the two
     * closed-month warnings, which belong to S2 and the G-2 matrix (§11.5). / Hinweise ohne die
     * Vormonats-Hinweise für Sektionen des laufenden Monats.
     *
     * @param warnings the view model's warnings, may be {@code null}
     * @return a new list, never {@code null} / neue Liste, nie {@code null}
     */
    public static List<String> currentPeriodWarnings(List<String> warnings) {
        if (warnings == null) {
            return List.of();
        }
        return warnings.stream().filter(w -> !InsightsViewModelBuilder.isClosedMonthWarning(w)).toList();
    }

    /** „Erfassung seit {date} – noch keine ausgewerteten Tage." as {@code .st-empty} (AC-23). */
    public static String emptySince(LocalDate collectingSince) {
        return HtmlText.empty(collectingSince == null
            ? esc(EMPTY_NOT_STARTED)
            : fill(EMPTY_SINCE, "date", esc(GermanFormat.day(collectingSince))));
    }

    /**
     * The display text of a period: its label with the range (§17 {@code PERIOD_MONTH} /
     * {@code PERIOD_YEAR}), tolerant of labels that already contain the range or a {@code {range}}
     * placeholder. / Beschriftung eines Zeitraums inklusive Datumsbereich (Klartext).
     *
     * @param fallbackTemplate template used when the period carries no label
     */
    public static String periodText(Period period, String fallbackTemplate) {
        if (period == null) {
            return "";
        }
        String label = period.label();
        String range = period.rangeLabel();
        if (label == null || label.isBlank()) {
            label = fallbackTemplate == null ? "" : fallbackTemplate;
        }
        boolean hasRange = range != null && !range.isBlank();
        if (label.contains("{range}")) {
            return hasRange ? label.replace("{range}", range)
                : label.replace(PERIOD_RANGE_PART, "").replace("{range}", "");
        }
        if (hasRange && !label.contains(range)) {
            return label + PERIOD_RANGE_PART.replace("{range}", range);
        }
        return label;
    }

    /**
     * Null-safe lookup in the cell maps of the view model — immutable maps ({@code Map.of},
     * {@code Map.copyOf}) throw on a {@code null} key. / Null-sicherer Zugriff auf die Zell-Maps.
     */
    public static <V> V get(Map<String, V> map, String key) {
        return map == null || key == null ? null : map.get(key);
    }

    // -----------------------------------------------------------------------------------------
    // Internals / Interna
    // -----------------------------------------------------------------------------------------

    /**
     * The visible number of a {@code VALUE} cell in the requested precision; falls back to
     * whichever field is present, never to a hidden one. / Sichtbare Zahl in der gewünschten
     * Genauigkeit.
     */
    private static String formatValue(Cell cell, Format format) {
        switch (format) {
            case COUNT -> {
                if (cell.value() != null) {
                    return StatFormat.num(cell.value());
                }
            }
            case PERCENT -> {
                if (cell.percent() != null) {
                    return StatFormat.percent(cell.percent());
                }
            }
            case PERCENT_INT -> {
                if (cell.percent() != null) {
                    return StatFormat.percentInt(cell.percent());
                }
            }
            case RATIO -> {
                if (cell.ratio() != null) {
                    return StatFormat.decimal(cell.ratio());
                }
            }
        }
        return StatFormat.cell(cell);
    }

    private static Cell stateCell(CellState state) {
        return new Cell(null, null, null, 0, state);
    }

    private static String classes(String... parts) {
        StringBuilder h = new StringBuilder(32);
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (!h.isEmpty()) {
                    h.append(' ');
                }
                h.append(part.trim());
            }
        }
        return h.toString();
    }

    private static String classAttr(String classes) {
        return classes.isEmpty() ? "" : " class=\"" + classes + "\"";
    }
}
