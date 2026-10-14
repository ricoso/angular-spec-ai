package com.autohaus.backend.usage.insights;

import com.autohaus.backend.common.html.GermanFormat;
import com.autohaus.backend.common.html.HtmlText;
import com.autohaus.backend.usage.StatFormat;
import com.autohaus.backend.usage.insights.CellHtml.Format;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.autohaus.backend.common.html.HtmlText.esc;
import static com.autohaus.backend.common.html.HtmlText.fill;

/**
 * REQ-042 (G-4) — renders the „Einstiegs-Variante" section: how many bookings came through the
 * normal flow ({@code default}), the Radwechsel/Boxenstopp shortcut ({@code tireChange}) and the
 * recall campaign ({@code recall}). Anonymous aggregate only (counts + share, never „who").
 * {@code null} or {@code bookingsEnabled == false} → empty string (BR-4), same contract as
 * {@link ServiceGroupSectionRenderer}. One bar list per display period (current month + running
 * year up to yesterday); the three variants are shown in a fixed order and always add up to the
 * period's bookings.
 */
@Component
public class EntryVariantSectionRenderer {

    /** §17 {@code ENTRY_VARIANT_HEADING}. */
    public static final String HEADING = "Einstieg: Woher kommen die Buchungen?";
    /** Range line / Zeitraumzeile. */
    public static final String RANGE = "{period} · alle Standorte · {bookings} Buchungen";
    /** Explanatory note / Erläuterung. */
    public static final String NOTE = "Jede Buchung zählt in genau einer Einstiegs-Variante. "
        + "„Normal“ = reguläre Terminbuchung; „Radwechsel“ = Boxenstopp-Einstieg; "
        + "„Rückrufaktion“ = Einstieg über den Rückruf-Link.";
    /** {@code aria-label} part of a bar: count / Anzahl. */
    public static final String ARIA_COUNT = "{n} Buchungen";
    /** {@code aria-label} part of a bar: share / Anteil. */
    public static final String ARIA_SHARE = "{pct} Prozent der Buchungen";

    /** §17 label — normal flow. */
    public static final String VARIANT_DEFAULT = "Normal";
    /** §17 label — tire-change shortcut. */
    public static final String VARIANT_TIRE_CHANGE = "Radwechsel";
    /** §17 label — recall campaign. */
    public static final String VARIANT_RECALL = "Rückrufaktion";

    /** Variant key → visible label, in display order / Variantenschlüssel auf Beschriftung. */
    public static final Map<String, String> VARIANT_LABELS = variantLabels();

    /** Renders the section, {@code ""} when there is nothing to render / rendert die Sektion. */
    public String render(InsightsViewModel viewModel) {
        if (viewModel == null || !viewModel.bookingsEnabled()) {
            return "";
        }
        StringBuilder h = new StringBuilder(4096);
        h.append("<section id=\"entry-variant\" class=\"ev\" aria-labelledby=\"entry-variant-h\">");
        h.append("<h2 id=\"entry-variant-h\">").append(esc(HEADING)).append("</h2>");
        h.append(HtmlText.warnings(viewModel.warnings()));
        if (!HtmlText.hasWarning(viewModel.warnings(), InsightsViewModel.WARN_HISTORY_UNAVAILABLE)) {
            if (viewModel.month() == null && viewModel.year() == null) {
                h.append(CellHtml.emptySince(viewModel.collectingSince()));
            } else {
                h.append(periodTotals(viewModel, viewModel.month(), CellHtml.PERIOD_MONTH));
                h.append(periodTotals(viewModel, viewModel.year(), CellHtml.PERIOD_YEAR));
                h.append("<p class=\"st-note\">").append(esc(NOTE)).append("</p>");
            }
        }
        h.append("</section>");
        return h.toString();
    }

    /** One period: range line and the bar list of the three variants / ein Zeitraum. */
    private static String periodTotals(InsightsViewModel vm, Period period, String fallback) {
        if (period == null) {
            return "";
        }
        String periodText = CellHtml.periodText(period, fallback);
        StringBuilder h = new StringBuilder(2048);
        h.append("<p class=\"st-range\">").append(fill(RANGE,
            "period", esc(periodText),
            "bookings", CellHtml.inline(period.bookings(), Format.COUNT))).append("</p>");
        if (CellHtml.state(period.bookings()) == CellState.NO_DATA) {
            h.append(CellHtml.emptySince(vm.collectingSince()));
            return h.toString();
        }
        h.append("<ul class=\"dev\" aria-label=\"").append(esc(HEADING + ", " + periodText)).append("\">");
        for (String key : VARIANT_LABELS.keySet()) {
            Cell cell = CellHtml.get(period.variants(), key);
            h.append(variantRow(VARIANT_LABELS.get(key), cell == null ? Cell.noData() : cell));
        }
        h.append("</ul>");
        return h.toString();
    }

    /** One bar row: label, bar ({@code role="img"}), count and share as text. */
    private static String variantRow(String label, Cell cell) {
        String aria;
        if (CellHtml.isValue(cell)) {
            aria = label + ": "
                + (cell.value() != null ? ARIA_COUNT.replace("{n}", StatFormat.num(cell.value())) : "")
                + (cell.value() != null && cell.percent() != null ? GermanFormat.LIST_SEPARATOR : "")
                + (cell.percent() != null ? ARIA_SHARE.replace("{pct}", StatFormat.decimal(cell.percent())) : "");
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

    private static Map<String, String> variantLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(UsageEventLogger.VARIANT_DEFAULT, VARIANT_DEFAULT);
        labels.put(UsageEventLogger.VARIANT_TIRE_CHANGE, VARIANT_TIRE_CHANGE);
        labels.put(UsageEventLogger.VARIANT_RECALL, VARIANT_RECALL);
        return java.util.Collections.unmodifiableMap(labels);
    }
}
