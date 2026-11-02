package com.autohaus.backend.reporting;

import com.autohaus.backend.capacity.CapacitySectionRenderer;
import com.autohaus.backend.usage.UsageSectionRenderer;
import com.autohaus.backend.usage.insights.BranchRankingSectionRenderer;
import com.autohaus.backend.usage.insights.EntryVariantSectionRenderer;
import com.autohaus.backend.usage.insights.FunnelSectionRenderer;
import com.autohaus.backend.usage.insights.InsightsViewModel;
import com.autohaus.backend.usage.insights.ServiceGroupSectionRenderer;
import com.autohaus.backend.usage.insights.TimingSectionRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.autohaus.backend.reporting.ReportHtmlGenerator.esc;

/**
 * Composes the statistics tab body (REQ-038 §11.2) — the jump links {@code nav.st-toc} and the
 * sections in fixed order S0 → S7, omitting switched-off sources without replacement (BR-4) /
 * setzt den Statistik-Tab in fester Reihenfolge zusammen.
 *
 * <p><b>Decision rule (§10.1):</b> exclusively the fields of {@link StatisticsPage} decide —
 * S0 while {@code briefing != null} and at least one of its sources is active (capacity, or
 * insights with {@code bookingsEnabled}); S1 with {@code capacity != null}; S2, S3, S4 with
 * {@code insights != null && insights.bookingsEnabled()}; S5 with {@code usage != null}; S6 with
 * {@code insights != null && insights.funnelEnabled()}; S7 always. A section of a switched-off
 * source is never even asked for markup, so it produces neither a section nor a jump link nor an
 * {@code .st-empty} (§5.1 row 5). / Allein die Felder von {@code StatisticsPage} entscheiden.
 *
 * <p><b>Jump links:</b> „Inhalt:" lists exactly the sections that were rendered (AC-4) — a
 * renderer answering with an empty string gets no link either. S5 points to the existing
 * {@code #us-h} heading of the REQ-037 section. / Sprungmarken genau für die gerenderten Sektionen.
 *
 * <p>Stateless: string concatenation over precomputed view models only (BR-5). / Zustandslos.
 */
@Component
public class StatisticsPageRenderer {

    // ---- visible labels, German literals (§17 TOC_*) / sichtbare Beschriftungen ----

    /** Heading of the jump links (§17 {@code TOC_HEADING}) / Überschrift der Sprungmarken. */
    static final String TOC_HEADING = "Inhalt:";
    /** §17 {@code TOC_*} — S0 / Auf einen Blick. */
    static final String TOC_BRIEFING = "Auf einen Blick";
    /** §17 {@code TOC_*} — S1. */
    static final String TOC_CAPACITY = "Freie Termine";
    /** §17 {@code TOC_*} — S2. */
    static final String TOC_BRANCHES = "Standorte";
    /** §17 {@code TOC_*} — S3. */
    static final String TOC_SERVICE_GROUPS = "Servicegruppen";
    /** §17 {@code TOC_*} — REQ-042 Einstiegs-Variante (G-4). */
    static final String TOC_ENTRY_VARIANT = "Einstieg";
    /** §17 {@code TOC_*} — S4. */
    static final String TOC_TIMING = "Zeiten";
    /** §17 {@code TOC_*} — S5 (REQ-037 section). */
    static final String TOC_USAGE = "Nutzung";
    /** §17 {@code TOC_*} — S6. */
    static final String TOC_FUNNEL = "Buchungsstrecke";
    /** §17 {@code TOC_*} — S7. */
    static final String TOC_LIMITS = "Messgrenzen";

    // ---- anchors of the sections (§16 section IDs) / Sprungziele der Sektionen ----

    /** Anchor of S0 / Sprungziel S0. */
    static final String ANCHOR_BRIEFING = "briefing";

    /**
     * REQ-043 K-2: S0 „Auf einen Blick" (Engpass, Renner &amp; Abendbuchungen, …) is hidden — too
     * much information on the page (user decision 2026-10-29). Calculator and renderer stay intact;
     * {@code true} brings the section and its jump link back. / S0 ausgeblendet, Code bleibt.
     */
    static final boolean BRIEFING_VISIBLE = false;
    /** Anchor of S1. */
    static final String ANCHOR_CAPACITY = "capacity";
    /** Anchor of S2. */
    static final String ANCHOR_BRANCHES = "branches";
    /** Anchor of S3. */
    static final String ANCHOR_SERVICE_GROUPS = "service-groups";
    /** Anchor of the REQ-042 entry-variant section (G-4). */
    static final String ANCHOR_ENTRY_VARIANT = "entry-variant";
    /** Anchor of S4. */
    static final String ANCHOR_TIMING = "timing";
    /** Anchor of S5 — the existing {@code h2#us-h} of REQ-037 (the section itself has no id). */
    static final String ANCHOR_USAGE = "us-h";
    /** Anchor of S6. */
    static final String ANCHOR_FUNNEL = "funnel";
    /** Anchor of S7. */
    static final String ANCHOR_LIMITS = "limits";

    private final BriefingSectionRenderer briefingRenderer;
    private final CapacitySectionRenderer capacityRenderer;
    private final BranchRankingSectionRenderer rankingRenderer;
    private final ServiceGroupSectionRenderer serviceGroupRenderer;
    private final EntryVariantSectionRenderer entryVariantRenderer;
    private final TimingSectionRenderer timingRenderer;
    private final UsageSectionRenderer usageRenderer;
    private final FunnelSectionRenderer funnelRenderer;
    private final LimitsSectionRenderer limitsRenderer;

    /**
     * Spring wiring — the only {@code @Autowired} constructor / Spring-Verdrahtung.
     *
     * @param briefingRenderer S0 / Handlungsimpulse
     * @param capacityRenderer S1 / Kapazität
     * @param rankingRenderer S2 / Rangliste
     * @param serviceGroupRenderer S3 / Servicegruppen
     * @param timingRenderer S4 / Zeiten
     * @param usageRenderer S5 (REQ-037) / Nutzung
     * @param funnelRenderer S6 / Buchungsstrecke
     * @param limitsRenderer S7 / Messgrenzen
     */
    @Autowired
    public StatisticsPageRenderer(BriefingSectionRenderer briefingRenderer,
                                  CapacitySectionRenderer capacityRenderer,
                                  BranchRankingSectionRenderer rankingRenderer,
                                  ServiceGroupSectionRenderer serviceGroupRenderer,
                                  EntryVariantSectionRenderer entryVariantRenderer,
                                  TimingSectionRenderer timingRenderer,
                                  UsageSectionRenderer usageRenderer,
                                  FunnelSectionRenderer funnelRenderer,
                                  LimitsSectionRenderer limitsRenderer) {
        this.briefingRenderer = briefingRenderer;
        this.capacityRenderer = capacityRenderer;
        this.rankingRenderer = rankingRenderer;
        this.serviceGroupRenderer = serviceGroupRenderer;
        this.entryVariantRenderer = entryVariantRenderer;
        this.timingRenderer = timingRenderer;
        this.usageRenderer = usageRenderer;
        this.funnelRenderer = funnelRenderer;
        this.limitsRenderer = limitsRenderer;
    }

    /**
     * Convenience constructor for tests and the 1-argument {@code ReportHtmlGenerator}
     * constructor — all renderers are stateless. / Bequemlichkeitskonstruktor.
     */
    public StatisticsPageRenderer() {
        this(new BriefingSectionRenderer(), new CapacitySectionRenderer(),
            new BranchRankingSectionRenderer(), new ServiceGroupSectionRenderer(),
            new EntryVariantSectionRenderer(),
            new TimingSectionRenderer(), new UsageSectionRenderer(), new FunnelSectionRenderer(),
            new LimitsSectionRenderer());
    }

    /**
     * Body of {@code <main id="content">} for the statistics tab / Inhalt des Statistik-Tabs:
     * {@code nav.st-toc} followed by the rendered sections S0 → S7.
     *
     * @param page the render input; {@code null} yields an empty string
     * @return the escaped body markup, never {@code null} / das Markup, nie {@code null}
     */
    public String render(StatisticsPage page) {
        if (page == null) {
            return "";
        }
        InsightsViewModel insights = page.insights();
        boolean bookings = insights != null && insights.bookingsEnabled();
        boolean funnel = insights != null && insights.funnelEnabled();
        boolean briefingSource = page.capacity() != null || bookings;

        List<String[]> toc = new ArrayList<>(8);
        StringBuilder sections = new StringBuilder(65536);

        // S0 — only with at least one of its two sources (I-1/I-3 capacity, I-2/I-4 bookings);
        // hidden since REQ-043 (BRIEFING_VISIBLE).
        if (BRIEFING_VISIBLE && page.briefing() != null && briefingSource) {
            add(briefingRenderer.render(page.briefing()), ANCHOR_BRIEFING, TOC_BRIEFING, toc, sections);
        }
        // S1 — capacity scan (K1).
        if (page.capacity() != null) {
            add(capacityRenderer.render(page.capacity()), ANCHOR_CAPACITY, TOC_CAPACITY, toc, sections);
        }
        // S2–S4 — booking insights (K2); a funnel-only view never reaches these renderers.
        if (bookings) {
            add(rankingRenderer.render(insights), ANCHOR_BRANCHES, TOC_BRANCHES, toc, sections);
            add(serviceGroupRenderer.render(insights), ANCHOR_SERVICE_GROUPS, TOC_SERVICE_GROUPS,
                toc, sections);
            add(entryVariantRenderer.render(insights), ANCHOR_ENTRY_VARIANT, TOC_ENTRY_VARIANT,
                toc, sections);
            add(timingRenderer.render(insights), ANCHOR_TIMING, TOC_TIMING, toc, sections);
        }
        // S5 — REQ-037 usage statistics, unchanged markup.
        if (page.usage() != null) {
            add(usageRenderer.render(page.usage()), ANCHOR_USAGE, TOC_USAGE, toc, sections);
        }
        // S6 — global funnel (K3), only with usage.enabled && usage.funnel.enabled.
        if (funnel) {
            add(funnelRenderer.render(insights), ANCHOR_FUNNEL, TOC_FUNNEL, toc, sections);
        }
        // S7 — always at the end of the tab (AC-29).
        add(limitsRenderer.render(page), ANCHOR_LIMITS, TOC_LIMITS, toc, sections);

        StringBuilder h = new StringBuilder(sections.length() + 1024);
        h.append(tableOfContents(toc));
        h.append(sections);
        return h.toString();
    }

    /** Appends a section and its jump link — only when the renderer produced markup. */
    private static void add(String html, String anchor, String label, List<String[]> toc,
                            StringBuilder sections) {
        if (html == null || html.isBlank()) {
            return;
        }
        toc.add(new String[]{anchor, label});
        sections.append(html);
    }

    /**
     * {@code nav.st-toc} with plain anchors (no {@code :target} styling, WCAG 2.4.1) / die
     * Sprungmarken-Liste „Inhalt".
     */
    private static String tableOfContents(List<String[]> toc) {
        if (toc.isEmpty()) {
            return "";
        }
        StringBuilder n = new StringBuilder(512);
        n.append("<nav class=\"st-toc\" aria-labelledby=\"toc-h\">")
            .append("<span id=\"toc-h\" class=\"st-toc-hd\">").append(esc(TOC_HEADING)).append("</span><ul>");
        for (String[] entry : toc) {
            n.append("<li><a href=\"#").append(esc(entry[0])).append("\">").append(esc(entry[1]))
                .append("</a></li>");
        }
        n.append("</ul></nav>");
        return n.toString();
    }
}
