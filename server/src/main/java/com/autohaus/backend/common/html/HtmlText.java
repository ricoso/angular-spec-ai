package com.autohaus.backend.common.html;

import java.util.List;

/**
 * Generic HTML text helpers of the internal status page (REQ-030, REQ-037, REQ-038 §11.2, §11.5) /
 * generische HTML-Text-Helfer der internen Statusseite.
 *
 * <p><b>Neutral package / neutrales Paket:</b> {@code reporting}, {@code usage},
 * {@code usage.insights} and {@code capacity} all render parts of the same page. The helpers they
 * share live here, so none of those packages has to import another one just for escaping or a
 * warning band (REQ-038 §9: no cross dependency {@code capacity} ↔ {@code usage/insights}). Nothing
 * in this class knows a view model or a cell state. / Gemeinsame Helfer ohne Abhängigkeit zu einem
 * Fachpaket.
 *
 * <p><b>One escaping for the whole page (REQ-037 TC-14):</b> {@link #esc(String)} is the only
 * implementation; {@code ReportHtmlGenerator.esc} delegates here. Every dynamic value of every
 * section passes through it. / Eine einzige Maskierung für die ganze Seite.
 *
 * <p>Templates ({@link #fill}, {@link #fillPlain}) use the §17 placeholder form {@code {key}}. The
 * substitution is a single pass over the template, so a value is never scanned for further
 * placeholders. / Platzhalter werden in einem Durchgang ersetzt, eingesetzte Werte nie erneut.
 */
public final class HtmlText {

    /** Visible dash as HTML entity („keine Daten", REQ-037 AC-13) / sichtbarer Strich als Entity. */
    public static final String DASH = "&mdash;";

    /**
     * The same dash as plain text, for text that is escaped later ({@code title}, {@code aria-label},
     * date fallbacks) / derselbe Strich als Klartext.
     */
    public static final String DASH_TEXT = "—";

    /** §17 {@code CELL_NO_DATA} ({@code .sr}) — screen-reader text of every „—" / Langtext „keine Daten". */
    public static final String NO_DATA_SR = "keine Daten";

    /** §17 {@code SCROLL_ARIA_*} — accessible name of every scroll region / Name der Scroll-Region. */
    public static final String SCROLL_ARIA = "Tabelle {caption}, horizontal scrollbar";

    private HtmlText() {
    }

    /**
     * HTML-escapes a dynamic value / maskiert einen dynamischen Wert: {@code & < > " '}.
     *
     * @param s the raw value; {@code null} yields an empty string
     * @return the escaped value / der maskierte Wert
     */
    public static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Fills a §17 template as HTML / befüllt eine §17-Vorlage als HTML. The template is escaped
     * first, then each {@code {key}} is replaced by an <b>already escaped</b> HTML value —
     * placeholders never contain escapable characters. / Die Vorlage wird maskiert, die Werte sind
     * bereits maskiertes HTML.
     *
     * @param template the plain-text template / die Klartext-Vorlage
     * @param keyValues alternating placeholder names and HTML values; {@code null} values insert
     *     nothing, a trailing key without value leaves its placeholder / abwechselnd Name und Wert
     * @return the HTML / das HTML
     */
    public static String fill(String template, String... keyValues) {
        return substitute(esc(template), keyValues);
    }

    /**
     * Fills a §17 template as <b>plain text</b> — nothing is escaped, the caller escapes the result
     * (captions, {@code aria-label}s, scroll titles). / befüllt eine Vorlage als Klartext; maskiert
     * wird beim Aufrufer.
     *
     * @param template the plain-text template, {@code null} yields {@code ""}
     * @param keyValues alternating placeholder names and plain values / abwechselnd Name und Wert
     * @return the plain text / der Klartext
     */
    public static String fillPlain(String template, String... keyValues) {
        return substitute(template == null ? "" : template, keyValues);
    }

    /**
     * Turns every blank of already escaped HTML into {@code &nbsp;}, so a short text such as
     * „k. A." never breaks inside a table cell / macht Leerzeichen nicht umbrechend.
     *
     * @param html escaped HTML, {@code null} yields {@code ""}
     * @return the HTML with non-breaking blanks / HTML mit geschützten Leerzeichen
     */
    public static String nonBreaking(String html) {
        return html == null ? "" : html.replace(" ", "&nbsp;");
    }

    /**
     * A visible short form for sighted users plus its long form for screen readers:
     * {@code <span aria-hidden="true">…</span><span class="sr">…</span>} (§11.2) — so „—" or
     * „&lt; 5" is never announced as a symbol or a number. / Kurzform sichtbar, Langform für
     * Screenreader.
     *
     * @param visibleHtml the escaped visible short form / sichtbare Kurzform (maskiert)
     * @param srText the plain screen-reader text (escaped here) / Langtext (Klartext)
     * @return the two spans / die beiden Spans
     */
    public static String hidden(String visibleHtml, String srText) {
        return "<span aria-hidden=\"true\">" + (visibleHtml == null ? "" : visibleHtml)
            + "</span><span class=\"sr\">" + esc(srText) + "</span>";
    }

    /** „—" with the {@code .sr} text „keine Daten" (§17 {@code CELL_NO_DATA}, BR-19) / „keine Daten". */
    public static String noData() {
        return hidden(DASH, NO_DATA_SR);
    }

    /**
     * The warning band: every warning of a view model as {@code p.st-warn} — text plus border, never
     * colour alone (§11.5); blank entries are skipped. / Warnband: jeder Hinweis als eigener Absatz.
     *
     * @param warnings the plain warnings, may be {@code null}
     * @return the escaped paragraphs or {@code ""} / die maskierten Absätze
     */
    public static String warnings(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return "";
        }
        StringBuilder h = new StringBuilder(128 * warnings.size());
        for (String warning : warnings) {
            if (warning != null && !warning.isBlank()) {
                h.append("<p class=\"st-warn\">").append(esc(warning)).append("</p>");
            }
        }
        return h.toString();
    }

    /**
     * Whether the warnings contain {@code warning} — e.g. „Historie derzeit nicht verfügbar" (E4,
     * AC-32), which replaces the numbers of a section. / Ob ein bestimmter Hinweis vorliegt.
     *
     * @param warnings the warnings, may be {@code null}
     * @param warning the warning looked for, may be {@code null}
     * @return {@code true} only when both are given and the list contains it
     */
    public static boolean hasWarning(List<String> warnings, String warning) {
        return warnings != null && warning != null && warnings.contains(warning);
    }

    /**
     * Empty state {@code p.st-empty} with the visible dash (§11.2, BR-19: never „0") / Leerzustand
     * mit Strich.
     *
     * @param html the already escaped text / bereits maskierter Text
     * @return the paragraph / der Absatz
     */
    public static String empty(String html) {
        return "<p class=\"st-empty\"><span class=\"st-empty-dash\" aria-hidden=\"true\">" + DASH + "</span>"
            + (html == null ? "" : html) + "</p>";
    }

    /**
     * Opens the horizontal scroll region of a wide table (AC-27): {@code role="region"},
     * {@code tabindex="0"} and the accessible name „Tabelle {caption}, horizontal scrollbar"; the
     * caller closes it with {@code </div>}. / Öffnet den Scroll-Container einer breiten Tabelle.
     *
     * @param caption the plain table title (escaped here), may be {@code null}
     * @return the opening tag / das öffnende Tag
     */
    public static String scrollOpen(String caption) {
        return "<div class=\"st-scroll\" role=\"region\" tabindex=\"0\" aria-label=\""
            + esc(fillPlain(SCROLL_ARIA, "caption", caption == null ? "" : caption)) + "\">";
    }

    /**
     * Single-pass substitution of {@code {key}} placeholders / Ersetzung in einem Durchgang. An
     * unknown {@code {…}} stays as written.
     */
    private static String substitute(String text, String... keyValues) {
        if (keyValues == null || keyValues.length < 2 || text.indexOf('{') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + 64);
        int i = 0;
        while (i < text.length()) {
            int open = text.indexOf('{', i);
            int close = open < 0 ? -1 : text.indexOf('}', open + 1);
            if (close < 0) {
                break;
            }
            out.append(text, i, open);
            int value = valueIndex(keyValues, text, open + 1, close);
            if (value < 0) {
                out.append('{');
                i = open + 1;
            } else {
                String v = keyValues[value];
                out.append(v == null ? "" : v);
                i = close + 1;
            }
        }
        out.append(text, i, text.length());
        return out.toString();
    }

    /** Index of the value whose key equals {@code text[from, to)}, else −1 / Index des Werts. */
    private static int valueIndex(String[] keyValues, String text, int from, int to) {
        int length = to - from;
        for (int k = 0; k + 1 < keyValues.length; k += 2) {
            String key = keyValues[k];
            if (key != null && key.length() == length && text.regionMatches(from, key, 0, length)) {
                return k + 1;
            }
        }
        return -1;
    }
}
