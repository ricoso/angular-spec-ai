package com.autohaus.backend.common.html;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The neutral HTML text helpers of the status page (REQ-038 §9, §11.2, §11.5; REQ-037 TC-14) /
 * die neutralen HTML-Text-Helfer der Statusseite.
 *
 * <p>Covers the one escaping of the whole page, the §17 template substitution (single pass), the
 * warning band {@code p.st-warn}, the empty state {@code p.st-empty}, the scroll region of AC-27 and
 * the „—" with its {@code .sr} text. {@link #references(Class, String)} is the shared dependency
 * check of the section tests (§9: no cross dependency between the feature packages). / Deckt
 * Maskierung, Vorlagen, Warnband, Leerzustand und Scroll-Region ab.
 */
public class HtmlTextTest {

    /**
     * Whether the compiled class names a type below {@code internalPrefix} (e.g.
     * {@code "com/autohaus/backend/usage/"}) in its constant pool — i.e. depends on that package
     * at run time. Inlined compile-time constants do not count. / Ob die Klasse ein Paket
     * referenziert.
     */
    public static boolean references(Class<?> type, String internalPrefix) throws IOException {
        try (InputStream in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            assertNotNull(in, "class file of " + type.getName());
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1).contains(internalPrefix);
        }
    }

    @Test
    @DisplayName("TC-14: esc masks & < > \" ' and turns null into an empty string")
    void escapesTheFiveCharacters() {
        assertEquals("&lt;b&gt;X&lt;/b&gt; &amp; &quot;Y&quot; &#39;Z&#39;", HtmlText.esc("<b>X</b> & \"Y\" 'Z'"));
        assertEquals("", HtmlText.esc(null));
        assertEquals("Räderwechsel · 1–4 — ≥ 5", HtmlText.esc("Räderwechsel · 1–4 — ≥ 5"),
            "no other character changes");
    }

    @Test
    @DisplayName("§17 templates: the template is escaped, values are inserted as HTML")
    void fillEscapesTheTemplateAndInsertsValues() {
        assertEquals("a &lt; b: <b>1</b> ", HtmlText.fill("a < b: {x} {y}", "x", "<b>1</b>", "y", null));
        assertEquals("{x}", HtmlText.fill("{x}", "x"), "a key without value leaves the placeholder");
        assertEquals("{unknown} 2", HtmlText.fill("{unknown} {n}", "n", "2"), "unknown placeholders stay");
        assertEquals("", HtmlText.fill(null, "x", "1"));
        assertEquals("bis 11 · 12–22", HtmlText.fill("bis {upper1} · {upper1+1}–{upper2}",
            "upper1", "11", "upper1+1", "12", "upper2", "22"), "keys with operators");
    }

    @Test
    @DisplayName("§17 templates: a single pass — an inserted value is never scanned for placeholders")
    void fillNeverRescansInsertedValues() {
        assertEquals("{days} 19.11.", HtmlText.fillPlain("{subject} {days}", "subject", "{days}", "days", "19.11."));
        assertEquals("{a{b}", HtmlText.fillPlain("{a{b}", "x", "1"));
        assertEquals("{a1", HtmlText.fillPlain("{a{b}", "b", "1"), "an unknown opening brace is kept");
        assertEquals("text", HtmlText.fillPlain("text"));
        assertEquals("", HtmlText.fillPlain(null, "a", "b"));
    }

    @Test
    @DisplayName("fillPlain escapes nothing — the caller escapes the whole result")
    void fillPlainEscapesNothing() {
        assertEquals("a < b & <c>", HtmlText.fillPlain("a < b & {v}", "v", "<c>"));
    }

    @Test
    void nonBreakingProtectsEveryBlank() {
        assertEquals("n&nbsp;&lt;&nbsp;20", HtmlText.nonBreaking(HtmlText.esc("n < 20")));
        assertEquals("", HtmlText.nonBreaking(null));
    }

    @Test
    @DisplayName("§11.2: visible short form aria-hidden plus escaped .sr long form; „—“ is „keine Daten“")
    void hiddenPairsTheShortAndTheLongForm() {
        assertEquals("<span aria-hidden=\"true\">&lt; 5</span><span class=\"sr\">a &amp; b</span>",
            HtmlText.hidden("&lt; 5", "a & b"));
        assertEquals("<span aria-hidden=\"true\"></span><span class=\"sr\"></span>", HtmlText.hidden(null, null));
        assertEquals("<span aria-hidden=\"true\">&mdash;</span><span class=\"sr\">keine Daten</span>",
            HtmlText.noData());
    }

    @Test
    @DisplayName("§11.5: the warning band is one escaped p.st-warn per non-blank warning")
    void warningsAreEscapedParagraphs() {
        String html = HtmlText.warnings(Arrays.asList("<b>X</b>", null, " ", "Historie derzeit nicht verfügbar"));
        assertEquals("<p class=\"st-warn\">&lt;b&gt;X&lt;/b&gt;</p>"
            + "<p class=\"st-warn\">Historie derzeit nicht verfügbar</p>", html);
        assertEquals("", HtmlText.warnings(null));
        assertEquals("", HtmlText.warnings(List.of()));
        assertTrue(HtmlText.hasWarning(List.of("a", "b"), "b"));
        assertFalse(HtmlText.hasWarning(List.of("a"), "b"));
        assertFalse(HtmlText.hasWarning(null, "b"));
        assertFalse(HtmlText.hasWarning(List.of("a"), null));
    }

    @Test
    @DisplayName("BR-19: the empty state shows the dash, never 0")
    void emptyStateShowsTheDash() {
        assertEquals("<p class=\"st-empty\"><span class=\"st-empty-dash\" aria-hidden=\"true\">&mdash;</span>"
            + "Noch keine Hinweise.</p>", HtmlText.empty("Noch keine Hinweise."));
        assertTrue(HtmlText.empty(null).endsWith("</span></p>"));
    }

    @Test
    @DisplayName("AC-27: scroll region with role, tabindex and escaped aria-label")
    void scrollRegionCarriesItsAttributes() {
        assertEquals("<div class=\"st-scroll\" role=\"region\" tabindex=\"0\" "
            + "aria-label=\"Tabelle A &amp; B, horizontal scrollbar\">", HtmlText.scrollOpen("A & B"));
        assertTrue(HtmlText.scrollOpen(null).contains("aria-label=\"Tabelle , horizontal scrollbar\""));
        assertTrue(HtmlText.scrollOpen("{caption}").contains("aria-label=\"Tabelle {caption}, horizontal"),
            "a caption is inserted once, never re-filled");
    }

    @Test
    @DisplayName("§9: the neutral package depends on no feature package")
    void dependsOnNoFeaturePackage() throws IOException {
        for (Class<?> type : List.of(HtmlText.class, GermanFormat.class)) {
            for (String pkg : List.of("com/autohaus/backend/reporting/", "com/autohaus/backend/usage/",
                "com/autohaus/backend/capacity/")) {
                assertFalse(references(type, pkg), type.getSimpleName() + " must not use " + pkg);
            }
        }
    }
}
