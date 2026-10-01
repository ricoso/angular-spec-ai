package com.autohaus.backend.reporting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S0 „Auf einen Blick: Handlungsimpulse" (REQ-038 §11.2, BR-21, AC-18) / Rendering von S0.
 *
 * <p>Covers the S0 part of TC-38: exactly the §11.2 classes, the empty state {@code .st-empty}
 * instead of „0", no {@code .us-*} class, every dynamic value escaped. / Klassenvertrag, Leerzustand
 * und Maskierung.
 */
class BriefingSectionRendererTest {

    private final BriefingSectionRenderer renderer = new BriefingSectionRenderer();

    private static final Impulse FREE = new Impulse(BriefingCalculator.KEY_FREE_TARGET_DAY,
        "Luft morgen (Fr 20.11.)", List.of("VW Nordtor", "Audi Waldkirchen"), null, "Scan 19.11.");
    private static final Impulse PROMO = new Impulse(BriefingCalculator.KEY_PROMO, "Werbe-Kandidat",
        List.of("VW Neuhaus"), null, "Oktober 2026");
    private static final Impulse BOTTLENECK = new Impulse(BriefingCalculator.KEY_BOTTLENECK, "Engpass",
        List.of("Audi Zentrum Rheinfeld"), null, "Scan 19.11.");
    private static final Impulse TOP = new Impulse(BriefingCalculator.KEY_TOP, "Renner & Abendbuchungen",
        List.of("Audi Zentrum Rheinfeld", "VW Bergheim-Süd", "ŠKODA Centrum Talstadt"), 38, "Oktober 2026");

    @Test
    @DisplayName("§17 IMPULSE_FREE_TEXT: with K-2 each I-1 name carries „{n} freie Online-Termine“")
    void rendersTheFreeSlotsPerBranchWithK2() {
        Impulse withSlots = new Impulse(BriefingCalculator.KEY_FREE_TARGET_DAY, "Luft morgen (Fr 20.11.)",
            List.of("VW Nordtor", "Audi Waldkirchen", "VW Neuhaus"), null, "Scan 19.11.",
            java.util.Arrays.asList(14, 1200, null));

        String html = renderer.render(new Briefing(List.of(withSlots)));

        assertThat(html).contains("<ul class=\"ib-list\"><li><span>VW Nordtor</span><span>14 freie Online-Termine"
            + "</span></li><li><span>Audi Waldkirchen</span><span>1.200 freie Online-Termine</span></li>"
            + "<li><span>VW Neuhaus</span></li></ul>");
    }

    @Test
    @DisplayName("BR-4: a switched-off source (null) renders nothing")
    void rendersNothingWithoutABriefing() {
        assertThat(renderer.render(null)).isEmpty();
    }

    @Test
    @DisplayName("AC-18 / TC-38: an empty briefing shows the .st-empty text, never a number")
    void rendersTheEmptyStateWithoutAnyImpulse() {
        String html = renderer.render(Briefing.empty());

        assertThat(html).startsWith("<section id=\"briefing\" class=\"ib\" aria-labelledby=\"briefing-h\">")
            .contains("<h2 id=\"briefing-h\">Auf einen Blick: Handlungsimpulse</h2>")
            .contains("<p class=\"st-intro\">")
            .contains("<p class=\"st-empty\"><span class=\"st-empty-dash\" aria-hidden=\"true\">&mdash;</span>"
                + "Noch keine Hinweise – zu wenig Daten.</p>")
            .doesNotContain("ib-grid")
            .doesNotContain("ib-tile")
            .endsWith("</section>");
    }

    @Test
    @DisplayName("TC-38: four tiles with exactly the §11.2 classes, in the order of the briefing")
    void rendersOneTilePerImpulse() {
        String html = renderer.render(new Briefing(List.of(FREE, PROMO, BOTTLENECK, TOP)));

        assertThat(html).contains("<div class=\"ib-grid\">");
        int free = html.indexOf("<div class=\"ib-tile ib-free\">");
        int promo = html.indexOf("<h3 class=\"ib-name\">Werbe-Kandidat</h3>");
        int tight = html.indexOf("<div class=\"ib-tile ib-tight\">");
        int top = html.indexOf("<h3 class=\"ib-name\">Renner &amp; Abendbuchungen</h3>");
        assertThat(free).isPositive();
        assertThat(promo).isGreaterThan(free);
        assertThat(tight).isGreaterThan(promo);
        assertThat(top).isGreaterThan(tight);

        assertThat(html)
            .contains("<div class=\"ib-hd\"><span class=\"st-ico\" aria-hidden=\"true\"><svg ")
            .contains("<h3 class=\"ib-name\">Luft morgen (Fr 20.11.)</h3><span class=\"ib-src\">Scan 19.11.</span>")
            .contains("<ul class=\"ib-list\"><li><span>VW Nordtor</span></li><li><span>Audi Waldkirchen</span></li></ul>")
            .contains("<p class=\"ib-sub\">Regel: am Zieltag frei im letzten Scan")
            .doesNotContain("st-empty");
        assertThat(countOf(html, "<div class=\"ib-tile")).isEqualTo(4);
    }

    @Test
    @DisplayName("§17: promo and bottleneck texts carry the month and the fixed rule")
    void rendersTheRuleTexts() {
        String html = renderer.render(new Briefing(List.of(PROMO, BOTTLENECK)));

        assertThat(html)
            .contains("Online viel frei, aber wenig Online-Buchungen im Oktober 2026. "
                + "Regel: 10-Tage-Einordnung „meist frei“ und Vormonat im unteren Drittel der Online-Buchungen.")
            .contains("Seit mehreren Scans erst in ≥ 5 Werktagen frei – Werbung derzeit wenig sinnvoll. "
                + "Regel: 10-Tage-Einordnung „meist belegt“.");
    }

    @Test
    @DisplayName("§11.2: I-4 shows the T-2 share as .ib-val/.ib-num and ranked names")
    void rendersTheTopTileWithTheShare() {
        String html = renderer.render(new Briefing(List.of(TOP)));

        assertThat(html)
            .contains("<p class=\"ib-val\"><span class=\"ib-num\">38&nbsp;%</span> außerhalb Mo–Fr 07–18 Uhr gebucht</p>")
            .contains("<ul class=\"ib-list\" aria-label=\"Top 3 im Oktober 2026\">"
                + "<li><span>1. Audi Zentrum Rheinfeld</span></li>")
            .contains("<li><span>3. ŠKODA Centrum Talstadt</span></li>")
            .contains("<p class=\"ib-sub\">Regel: die 3 Standorte mit den meisten Online-Buchungen im "
                + "Vormonat; Anteil außerhalb der Geschäftszeit erst ab 20 Buchungen.</p>");
        // The names appear once — in the list, not repeated in the rule line.
        assertThat(countOf(html, "Audi Zentrum Rheinfeld")).isEqualTo(1);
    }

    @Test
    @DisplayName("BR-21: without a visible T-2 share there is no value line")
    void omitsTheValueLineWithoutAShare() {
        Impulse top = new Impulse(BriefingCalculator.KEY_TOP, "Renner & Abendbuchungen",
            List.of("VW Nordtor"), null, "Oktober 2026");

        String html = renderer.render(new Briefing(List.of(top)));

        assertThat(html).doesNotContain("ib-val").doesNotContain("ib-num")
            .contains("<li><span>1. VW Nordtor</span></li>")
            .contains("<p class=\"ib-sub\">Regel:");
    }

    @Test
    @DisplayName("BR-21: a share without names still renders its sentence")
    void rendersTheShareWithoutNames() {
        Impulse top = new Impulse(BriefingCalculator.KEY_TOP, "Renner & Abendbuchungen", List.of(), 41, null);

        String html = renderer.render(new Briefing(List.of(top)));

        assertThat(html).contains("<span class=\"ib-num\">41&nbsp;%</span>")
            .doesNotContain("ib-list")
            .doesNotContain("ib-src")
            .contains("<p class=\"ib-sub\">41 % außerhalb Mo–Fr 07–18 Uhr gebucht. Regel:");
    }

    @Test
    @DisplayName("§9 Security: branch names, titles and sources are escaped")
    void escapesEveryDynamicValue() {
        Impulse hostile = new Impulse(BriefingCalculator.KEY_PROMO, "<i>t</i>", List.of("<b>X</b>"), null,
            "<script>");

        String html = renderer.render(new Briefing(List.of(hostile)));

        assertThat(html).contains("&lt;b&gt;X&lt;/b&gt;").contains("&lt;i&gt;t&lt;/i&gt;")
            .contains("&lt;script&gt;")
            .doesNotContain("<b>X</b>").doesNotContain("<script>");
    }

    @Test
    @DisplayName("TC-38: no .us-* class, no image, no script (BR-22, CSP)")
    void usesNeitherUsageClassesNorForbiddenElements() {
        String html = renderer.render(new Briefing(List.of(FREE, PROMO, BOTTLENECK, TOP)));

        assertThat(html).doesNotContain("class=\"us").doesNotContain(" us-")
            .doesNotContain("<img").doesNotContain("<script").doesNotContain("<form");
        assertThat(countOf(html, "aria-hidden=\"true\" focusable=\"false\"")).isEqualTo(4);
    }

    @Test
    @DisplayName("robustness: unknown keys and null entries are skipped -> empty state")
    void skipsUnknownImpulses() {
        Impulse unknown = new Impulse("foo", "Foo", List.of("A"), null, null);
        Impulse keyless = new Impulse(null, "Bar", null, null, null);
        java.util.List<Impulse> impulses = new java.util.ArrayList<>();
        impulses.add(unknown);
        impulses.add(null);
        impulses.add(keyless);

        String html = renderer.render(new Briefing(impulses));

        assertThat(html).contains("st-empty").doesNotContain("Foo").doesNotContain("ib-tile");
        assertThat(renderer.render(new Briefing(null))).contains("st-empty");
    }

    @Test
    @DisplayName("robustness: a promo impulse without source falls back to 'Vormonat'")
    void fallsBackToThePreviousMonthWord() {
        Impulse promo = new Impulse(BriefingCalculator.KEY_PROMO, "Werbe-Kandidat", List.of("VW Neuhaus"),
            null, " ");

        assertThat(renderer.render(new Briefing(List.of(promo))))
            .contains("Online-Buchungen im Vormonat.")
            .doesNotContain("ib-src");
    }

    private static int countOf(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i > -1; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
