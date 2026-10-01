package com.autohaus.backend.reporting;

import com.autohaus.backend.common.html.HtmlText;
import com.autohaus.backend.usage.StatFormat;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.autohaus.backend.common.html.HtmlText.esc;

/**
 * Renders S0 „Auf einen Blick: Handlungsimpulse" (REQ-038 §11.2, BR-21) / rendert S0.
 * {@code null} → empty string (source off, BR-4); an empty briefing → {@code .st-empty}
 * „Noch keine Hinweise – zu wenig Daten." (AC-18).
 *
 * <p>Markup (§11.2): {@code section#briefing.ib} &gt; {@code div.ib-grid} &gt; {@code div.ib-tile}
 * (+ {@code .ib-free} for I-1, {@code .ib-tight} for I-3) with {@code div.ib-hd}
 * ({@code span.st-ico} with an inline SVG, {@code h3.ib-name}, {@code span.ib-src}), the names as
 * {@code ul.ib-list}, I-4 additionally {@code p.ib-val} &gt; {@code span.ib-num}, and the rule
 * text in {@code p.ib-sub}. Icons are {@code aria-hidden}; no image element (CSP
 * {@code img-src 'none'}). / Markup nach §11.2.
 *
 * <p>Stateless renderer, view model in, HTML string out; every dynamic value passes through
 * {@link HtmlText#esc(String)}, the empty state is the shared {@link HtmlText#empty(String)}. The
 * German labels are the §17 literals. / Zustandsloser Renderer.
 */
@Component
public class BriefingSectionRenderer {

    // ---- visible labels, German literals (§17) / sichtbare Beschriftungen ----

    /** §17 {@code BRIEFING_HEADING}. */
    static final String BRIEFING_HEADING = "Auf einen Blick: Handlungsimpulse";
    /** §17 {@code BRIEFING_INTRO}. */
    static final String BRIEFING_INTRO = "Regelbasierte Hinweise aus Kapazitäts-Scan und "
        + "Online-Buchungen – keine KI. Fehlt die Datenbasis, entfällt der Hinweis.";
    /** §17 {@code BRIEFING_EMPTY} (AC-18). */
    static final String BRIEFING_EMPTY = "Noch keine Hinweise – zu wenig Daten.";
    /** §17 {@code IMPULSE_FREE_TEXT} — I-1 per branch, only while K-2 is shown. */
    static final String IMPULSE_FREE_TEXT = "{n} freie Online-Termine";
    /** §17 {@code IMPULSE_PROMO_TEXT}. */
    static final String IMPULSE_PROMO_TEXT = "online viel frei, aber wenig Online-Buchungen im {month}";
    /** §17 {@code IMPULSE_BOTTLENECK_TEXT}. */
    static final String IMPULSE_BOTTLENECK_TEXT =
        "seit mehreren Scans erst in ≥ 5 Werktagen frei – Werbung derzeit wenig sinnvoll";
    /**
     * §17 {@code IMPULSE_TOP_TEXT}, first part — {@code {names}} is the ranked list itself, the
     * rest labels it / erster Teil; die Namen bilden die Liste.
     */
    static final String IMPULSE_TOP_TEXT = "Top 3 im {month}: {names}";
    /** §17 {@code IMPULSE_TOP_TEXT}, share part (only with a visible T-2) / Anteilsteil. */
    static final String IMPULSE_TOP_SHARE_TEXT = "{pct} % außerhalb Mo–Fr 07–18 Uhr gebucht";
    /** §17 {@code IMPULSE_RULE_*} — I-1 (BR-21). */
    static final String IMPULSE_RULE_FREE = "Regel: am Zieltag frei im letzten Scan (Scan von heute "
        + "oder gestern), höchstens 5, sortiert nach freien Online-Terminen, sonst alphabetisch.";
    /** §17 {@code IMPULSE_RULE_*} — I-2 (BR-21). */
    static final String IMPULSE_RULE_PROMO = "Regel: 10-Tage-Einordnung „meist frei“ und Vormonat "
        + "im unteren Drittel der Online-Buchungen.";
    /** §17 {@code IMPULSE_RULE_*} — I-3 (BR-21). */
    static final String IMPULSE_RULE_BOTTLENECK = "Regel: 10-Tage-Einordnung „meist belegt“.";
    /** §17 {@code IMPULSE_RULE_*} — I-4 (BR-21). */
    static final String IMPULSE_RULE_TOP = "Regel: die 3 Standorte mit den meisten Online-Buchungen "
        + "im Vormonat; Anteil außerhalb der Geschäftszeit erst ab 20 Buchungen.";
    /** Sub line of a tile: impulse text, then its rule / Unterzeile: Hinweistext, dann Regel. */
    static final String TILE_SUB = "{text}. {rule}";
    /** {@code {month}} of I-2/I-4 when the impulse carries no month label / Ersatz für den Monat. */
    static final String MONTH_FALLBACK = "Vormonat";

    /** The {@code {names}} part of {@code IMPULSE_TOP_TEXT} — the list itself carries the names. */
    private static final String TOP_NAMES_PART = ": {names}";
    /** The value part of {@code IMPULSE_TOP_SHARE_TEXT} — shown in {@code .ib-num} instead. */
    private static final String TOP_SHARE_VALUE_PART = "{pct} % ";

    /** Inline icons (stroke = currentColor, {@code aria-hidden}) / Inline-Icons. */
    private static final String ICON_CALENDAR = svg(
        "<rect x=\"3\" y=\"5\" width=\"18\" height=\"16\" rx=\"2\"/><path d=\"M3 10h18M8 3v4M16 3v4M8 15l2.5 2.5L16 13\"/>");
    private static final String ICON_MEGAPHONE = svg(
        "<path d=\"M3 10v4h3l8 5V5L6 10H3z\"/><path d=\"M18 9a4 4 0 0 1 0 6\"/>");
    private static final String ICON_HOURGLASS = svg(
        "<path d=\"M6 3h12M6 21h12M7 3v3l5 6-5 6v3M17 3v3l-5 6 5 6v3\"/>");
    private static final String ICON_MOON = svg(
        "<path d=\"M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z\"/>");

    /** Renders the section, {@code ""} when there is nothing to render / rendert die Sektion. */
    public String render(Briefing briefing) {
        if (briefing == null) {
            return "";
        }
        StringBuilder h = new StringBuilder(4096);
        h.append("<section id=\"briefing\" class=\"ib\" aria-labelledby=\"briefing-h\">")
            .append("<h2 id=\"briefing-h\">").append(esc(BRIEFING_HEADING)).append("</h2>")
            .append("<p class=\"st-intro\">").append(esc(BRIEFING_INTRO)).append("</p>");

        List<Impulse> impulses = briefing.impulses() == null ? List.of() : briefing.impulses();
        StringBuilder tiles = new StringBuilder(2048);
        for (Impulse impulse : impulses) {
            if (impulse != null) {
                tiles.append(tile(impulse));
            }
        }
        if (tiles.isEmpty()) {
            h.append(HtmlText.empty(esc(BRIEFING_EMPTY)));
        } else {
            h.append("<div class=\"ib-grid\">").append(tiles).append("</div>");
        }
        h.append("</section>");
        return h.toString();
    }

    /** One tile per impulse; an unknown key renders nothing / eine Kachel je Impuls. */
    private static String tile(Impulse impulse) {
        String key = impulse.key() == null ? "" : impulse.key();
        return switch (key) {
            case BriefingCalculator.KEY_FREE_TARGET_DAY ->
                tile(impulse, " ib-free", ICON_CALENDAR, "", IMPULSE_RULE_FREE, false);
            case BriefingCalculator.KEY_PROMO ->
                tile(impulse, "", ICON_MEGAPHONE, "", sub(text(IMPULSE_PROMO_TEXT, impulse),
                    IMPULSE_RULE_PROMO), false);
            case BriefingCalculator.KEY_BOTTLENECK ->
                tile(impulse, " ib-tight", ICON_HOURGLASS, "", sub(ucFirst(IMPULSE_BOTTLENECK_TEXT),
                    IMPULSE_RULE_BOTTLENECK), false);
            case BriefingCalculator.KEY_TOP ->
                tile(impulse, "", ICON_MOON, topValue(impulse), topSub(impulse), true);
            default -> "";
        };
    }

    /**
     * Tile markup / Kachel-Markup.
     *
     * @param modifier additional tile class incl. leading blank, or {@code ""}
     * @param icon the inline SVG / das Inline-Icon
     * @param valueHtml escaped {@code p.ib-val} markup or {@code ""}
     * @param sub the plain rule text (escaped here) / Regeltext
     * @param numbered whether the names are ranked (I-4) / Namen mit Rang
     */
    private static String tile(Impulse impulse, String modifier, String icon, String valueHtml,
                               String sub, boolean numbered) {
        StringBuilder t = new StringBuilder(1024);
        t.append("<div class=\"ib-tile").append(modifier).append("\">");
        t.append("<div class=\"ib-hd\"><span class=\"st-ico\" aria-hidden=\"true\">").append(icon)
            .append("</span><h3 class=\"ib-name\">").append(esc(impulse.title())).append("</h3>");
        if (impulse.source() != null && !impulse.source().isBlank()) {
            t.append("<span class=\"ib-src\">").append(esc(impulse.source())).append("</span>");
        }
        t.append("</div>");
        t.append(valueHtml);
        List<String> names = impulse.branchNames() == null ? List.of() : impulse.branchNames();
        if (!names.isEmpty()) {
            t.append("<ul class=\"ib-list\"");
            if (numbered) {
                // I-4: the ranked names are the „{names}" of IMPULSE_TOP_TEXT — named for AT.
                t.append(" aria-label=\"").append(esc(IMPULSE_TOP_TEXT.replace(TOP_NAMES_PART, "")
                    .replace("{month}", month(impulse)))).append('"');
            }
            t.append('>');
            List<Integer> values = impulse.values() == null ? List.of() : impulse.values();
            int position = 0;
            for (String name : names) {
                position++;
                t.append("<li><span>");
                if (numbered) {
                    t.append(position).append(". ");
                }
                t.append(esc(name)).append("</span>");
                Integer value = position <= values.size() ? values.get(position - 1) : null;
                if (value != null) {
                    // I-1 with K-2: „14 freie Online-Termine" as the second span (§11.2 mockup).
                    t.append("<span>").append(esc(IMPULSE_FREE_TEXT.replace("{n}",
                        StatFormat.num(value.longValue())))).append("</span>");
                }
                t.append("</li>");
            }
            t.append("</ul>");
        }
        t.append("<p class=\"ib-sub\">").append(esc(sub)).append("</p>");
        t.append("</div>");
        return t.toString();
    }

    /** I-4 value line with the T-2 share, only when visible / Anteilszeile nur bei sichtbarem Wert. */
    private static String topValue(Impulse impulse) {
        if (impulse.percent() == null) {
            return "";
        }
        String share = IMPULSE_TOP_SHARE_TEXT.replace(TOP_SHARE_VALUE_PART, "");
        return "<p class=\"ib-val\"><span class=\"ib-num\">" + impulse.percent() + "&nbsp;%</span> "
            + esc(share) + "</p>";
    }

    /**
     * Rule line of I-4 / Regelzeile von I-4. §17 {@code IMPULSE_TOP_TEXT} is split over the tile:
     * the share in {@code .ib-val}, the names as the ranked {@code .ib-list} (labelled „Top 3 im
     * {month}"); only without names the sentence „{pct} % außerhalb …" leads the rule. / Der Text
     * verteilt sich auf Wert, Liste und Regel.
     */
    private static String topSub(Impulse impulse) {
        List<String> names = impulse.branchNames() == null ? List.of() : impulse.branchNames();
        if (names.isEmpty() && impulse.percent() != null) {
            return sub(IMPULSE_TOP_SHARE_TEXT.replace("{pct}", String.valueOf(impulse.percent())), IMPULSE_RULE_TOP);
        }
        return IMPULSE_RULE_TOP;
    }

    /** {@code TILE_SUB}: „{text}. {rule}" (plain) / Hinweistext und Regel. */
    private static String sub(String text, String rule) {
        return HtmlText.fillPlain(TILE_SUB, "text", text, "rule", rule);
    }

    /** Fills {@code {month}} from the source line of a booking-based impulse / Monat einsetzen. */
    private static String text(String template, Impulse impulse) {
        return ucFirst(template.replace("{month}", month(impulse)));
    }

    /** The month label is the source line of I-2/I-4 ({@link BriefingCalculator}). */
    private static String month(Impulse impulse) {
        return impulse.source() == null || impulse.source().isBlank() ? MONTH_FALLBACK : impulse.source();
    }

    private static String ucFirst(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String svg(String paths) {
        return "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" "
            + "stroke-linecap=\"round\" stroke-linejoin=\"round\" aria-hidden=\"true\" focusable=\"false\">"
            + paths + "</svg>";
    }
}
