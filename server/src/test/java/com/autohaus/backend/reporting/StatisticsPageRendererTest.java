package com.autohaus.backend.reporting;

import com.autohaus.backend.capacity.CapacitySectionRenderer;
import com.autohaus.backend.capacity.CapacityViewModel;
import com.autohaus.backend.capacity.ScanState;
import com.autohaus.backend.usage.UsageSectionRenderer;
import com.autohaus.backend.usage.UsageStats;
import com.autohaus.backend.usage.insights.BranchRankingSectionRenderer;
import com.autohaus.backend.usage.insights.FunnelSectionRenderer;
import com.autohaus.backend.usage.insights.InsightsViewModel;
import com.autohaus.backend.usage.insights.ServiceGroupSectionRenderer;
import com.autohaus.backend.usage.insights.TimingSectionRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Composition of the statistics tab (REQ-038 §11.2, §10.1, BR-4, AC-4) / Komposition des
 * Statistik-Tabs.
 *
 * <p>The section renderers S1–S4 and S6 are mocked: this test is about the fixed order S0 → S7,
 * the decision rule „only the {@link StatisticsPage} fields decide" (§10.1) and the jump links
 * listing exactly the rendered sections (AC-4). S0 and S7 use the real renderers. / Die
 * Sektions-Renderer S1–S4/S6 sind gemockt; geprüft werden Reihenfolge, Entscheidungsregel und
 * Sprungmarken.
 */
class StatisticsPageRendererTest {

    private static final Pattern TOC_LINK = Pattern.compile("<a href=\"#([^\"]+)\">");
    private static final Pattern SECTION = Pattern.compile("<section[^>]*?class=\"([^\" ]+)");

    private CapacitySectionRenderer capacity;
    private BranchRankingSectionRenderer ranking;
    private ServiceGroupSectionRenderer groups;
    private TimingSectionRenderer timing;
    private UsageSectionRenderer usage;
    private FunnelSectionRenderer funnel;
    private StatisticsPageRenderer renderer;

    @BeforeEach
    void setUp() {
        capacity = mock(CapacitySectionRenderer.class);
        ranking = mock(BranchRankingSectionRenderer.class);
        groups = mock(ServiceGroupSectionRenderer.class);
        timing = mock(TimingSectionRenderer.class);
        usage = mock(UsageSectionRenderer.class);
        funnel = mock(FunnelSectionRenderer.class);
        when(capacity.render(any())).thenReturn(section("capacity", "cap"));
        when(ranking.render(any())).thenReturn(section("branches", "rk"));
        when(groups.render(any())).thenReturn(section("service-groups", "sg"));
        when(timing.render(any())).thenReturn(section("timing", "tm"));
        when(usage.render(any())).thenReturn("<section class=\"us\" aria-labelledby=\"us-h\"><h2 id=\"us-h\">N</h2></section>");
        when(funnel.render(any())).thenReturn(section("funnel", "fn"));
        renderer = new StatisticsPageRenderer(new BriefingSectionRenderer(), capacity, ranking, groups,
            timing, usage, funnel, new LimitsSectionRenderer());
    }

    @Test
    @DisplayName("robustness: no page -> empty body")
    void rendersNothingWithoutAPage() {
        assertThat(renderer.render(null)).isEmpty();
    }

    @Test
    @DisplayName("§12.2 / TC-4: all sources -> toc, then S0 .. S7 in fixed order")
    void composesAllSectionsInTheFixedOrder() {
        String html = renderer.render(new StatisticsPage(UsageStats.unavailable(), insights(true, true),
            capacity(), Briefing.empty()));

        assertThat(html).startsWith("<nav class=\"st-toc\" aria-labelledby=\"toc-h\">"
            + "<span id=\"toc-h\" class=\"st-toc-hd\">Inhalt:</span><ul>");
        assertThat(sections(html)).containsExactly("ib", "cap", "rk", "sg", "tm", "us", "fn", "st-limits");
        assertThat(tocLinks(html)).containsExactly("briefing", "capacity", "branches", "service-groups",
            "timing", "us-h", "funnel", "limits");
        assertThat(html).contains("<li><a href=\"#briefing\">Auf einen Blick</a></li>")
            .contains("<li><a href=\"#capacity\">Freie Termine</a></li>")
            .contains("<li><a href=\"#branches\">Standorte</a></li>")
            .contains("<li><a href=\"#service-groups\">Servicegruppen</a></li>")
            .contains("<li><a href=\"#timing\">Zeiten</a></li>")
            .contains("<li><a href=\"#us-h\">Nutzung</a></li>")
            .contains("<li><a href=\"#funnel\">Buchungsstrecke</a></li>")
            .contains("<li><a href=\"#limits\">Messgrenzen</a></li>");
        // The toc precedes every section / Sprungmarken vor allen Sektionen.
        assertThat(html.indexOf("</nav>")).isLessThan(html.indexOf("<section"));
    }

    @Test
    @DisplayName("§5.1 row 5 / TC-46: funnel only -> S5, S6, S7; S0 and S2–S4 never asked, no .st-empty")
    void rendersOnlyTheFunnelWithoutBookings() {
        String html = renderer.render(new StatisticsPage(UsageStats.unavailable(), insights(false, true),
            null, Briefing.empty()));

        assertThat(sections(html)).containsExactly("us", "fn", "st-limits");
        assertThat(tocLinks(html)).containsExactly("us-h", "funnel", "limits");
        assertThat(html).doesNotContain("st-empty").doesNotContain("class=\"ib\"");
        verify(ranking, never()).render(any());
        verify(groups, never()).render(any());
        verify(timing, never()).render(any());
        verify(capacity, never()).render(any());
    }

    @Test
    @DisplayName("§5.1 row 2: bookings without funnel -> no S6 at all")
    void omitsTheFunnelWhileSwitchedOff() {
        String html = renderer.render(new StatisticsPage(UsageStats.unavailable(), insights(true, false),
            null, Briefing.empty()));

        assertThat(sections(html)).containsExactly("ib", "rk", "sg", "tm", "us", "st-limits");
        verify(funnel, never()).render(any());
    }

    @Test
    @DisplayName("§5.1 row 7: capacity only -> S0, S1, S7")
    void rendersCapacityOnly() {
        String html = renderer.render(new StatisticsPage(null, null, capacity(), Briefing.empty()));

        assertThat(sections(html)).containsExactly("ib", "cap", "st-limits");
        assertThat(tocLinks(html)).containsExactly("briefing", "capacity", "limits");
        verify(usage, never()).render(any());
    }

    @Test
    @DisplayName("§10.1: a briefing without any of its sources is not rendered")
    void dropsAnOrphanedBriefing() {
        String html = renderer.render(new StatisticsPage(UsageStats.unavailable(), insights(false, true),
            null, new Briefing(List.of(new Impulse("top", "Renner", List.of("A"), null, "Oktober 2026")))));

        assertThat(html).doesNotContain("id=\"briefing\"").doesNotContain("#briefing");
    }

    @Test
    @DisplayName("§10.1: no briefing (both sources off) -> no S0 even with capacity")
    void rendersNoBriefingWhenItIsNull() {
        String html = renderer.render(new StatisticsPage(null, null, capacity(), null));

        assertThat(sections(html)).containsExactly("cap", "st-limits");
    }

    @Test
    @DisplayName("AC-4: a renderer answering with an empty string gets no jump link")
    void listsOnlyRenderedSectionsInTheToc() {
        when(ranking.render(any())).thenReturn("");
        when(timing.render(any())).thenReturn("   ");

        String html = renderer.render(new StatisticsPage(null, insights(true, false), null, Briefing.empty()));

        assertThat(tocLinks(html)).containsExactly("briefing", "service-groups", "limits");
        assertThat(sections(html)).containsExactly("ib", "sg", "st-limits");
    }

    @Test
    @DisplayName("§10.1: an empty page renders S7 alone with its jump link")
    void rendersTheLimitsAlone() {
        String html = renderer.render(new StatisticsPage(null, null, null, null));

        assertThat(sections(html)).containsExactly("st-limits");
        assertThat(tocLinks(html)).containsExactly("limits");
    }

    @Test
    @DisplayName("AC-4: nothing rendered at all -> no empty 'Inhalt' list either")
    void rendersNoTocWithoutAnySection() {
        LimitsSectionRenderer silent = mock(LimitsSectionRenderer.class);
        when(silent.render(any())).thenReturn(null);
        StatisticsPageRenderer composer = new StatisticsPageRenderer(new BriefingSectionRenderer(), capacity,
            ranking, groups, timing, usage, funnel, silent);

        assertThat(composer.render(new StatisticsPage(null, null, null, null))).isEmpty();
    }

    @Test
    @DisplayName("§14.2: the convenience constructor wires stateless real renderers")
    void worksWithTheConvenienceConstructor() {
        String html = new StatisticsPageRenderer().render(
            new StatisticsPage(UsageStats.unavailable(), null, null, null));

        assertThat(html).contains("<section class=\"us\"").contains("id=\"limits\"")
            .contains("<a href=\"#us-h\">Nutzung</a>");
    }

    // ---- helpers / Hilfen ----

    private static String section(String id, String cssClass) {
        return "<section id=\"" + id + "\" class=\"" + cssClass + "\" aria-labelledby=\"" + id + "-h\">"
            + "<h2 id=\"" + id + "-h\">" + id + "</h2></section>";
    }

    private static InsightsViewModel insights(boolean bookings, boolean funnelEnabled) {
        return new InsightsViewModel(LocalDate.of(2026, 10, 22), bookings, funnelEnabled,
            LocalDate.of(2026, 9, 1), null, null, null, false, 0, null, null, null, null, null, null,
            null, List.of());
    }

    private static CapacityViewModel capacity() {
        return new CapacityViewModel(LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 23),
            ScanState.COMPLETE, false, null, null, LocalDate.of(2026, 10, 5), List.of(), List.of(), List.of());
    }

    private static List<String> sections(String html) {
        List<String> classes = new ArrayList<>();
        Matcher m = SECTION.matcher(html);
        while (m.find()) {
            classes.add(m.group(1));
        }
        return classes;
    }

    private static List<String> tocLinks(String html) {
        int end = html.indexOf("</nav>");
        String toc = end > -1 ? html.substring(0, end) : "";
        List<String> links = new ArrayList<>();
        Matcher m = TOC_LINK.matcher(toc);
        while (m.find()) {
            links.add(m.group(1));
        }
        return links;
    }
}
