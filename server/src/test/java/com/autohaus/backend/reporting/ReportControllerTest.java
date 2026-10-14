package com.autohaus.backend.reporting;

import com.autohaus.backend.capacity.CapacityBlobClient;
import com.autohaus.backend.capacity.CapacityDayState;
import com.autohaus.backend.capacity.CapacityLevel;
import com.autohaus.backend.capacity.CapacityRow;
import com.autohaus.backend.capacity.CapacitySectionRenderer;
import com.autohaus.backend.capacity.CapacityStore;
import com.autohaus.backend.capacity.CapacityTrend;
import com.autohaus.backend.capacity.CapacityViewModel;
import com.autohaus.backend.capacity.ScanState;
import com.autohaus.backend.config.CapacityProperties;
import com.autohaus.backend.config.ErrorReportingProperties;
import com.autohaus.backend.config.SecurityConfig;
import com.autohaus.backend.config.UsageDropoffProperties;
import com.autohaus.backend.config.UsageFunnelProperties;
import com.autohaus.backend.config.UsageInsightsProperties;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.config.setup.BranchSetup;
import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.autohaus.backend.usage.DeviceBreakdown;
import com.autohaus.backend.usage.PeriodUsage;
import com.autohaus.backend.usage.UsageBlobClient;
import com.autohaus.backend.usage.UsageSectionRenderer;
import com.autohaus.backend.usage.UsageStats;
import com.autohaus.backend.usage.UsageStatsAggregator;
import com.autohaus.backend.usage.UsageStatsScheduler;
import com.autohaus.backend.usage.UsageStatsService;
import com.autohaus.backend.usage.UsageStatsStore;
import com.autohaus.backend.usage.insights.BranchRankingRow;
import com.autohaus.backend.usage.insights.BranchRankingSectionRenderer;
import com.autohaus.backend.usage.insights.EntryVariantSectionRenderer;
import com.autohaus.backend.usage.insights.Cell;
import com.autohaus.backend.usage.insights.FunnelSectionRenderer;
import com.autohaus.backend.usage.insights.FunnelStage;
import com.autohaus.backend.usage.insights.FunnelView;
import com.autohaus.backend.usage.insights.InsightsBlobClient;
import com.autohaus.backend.usage.insights.InsightsStore;
import com.autohaus.backend.usage.insights.InsightsViewModel;
import com.autohaus.backend.usage.insights.ServiceGroupSectionRenderer;
import com.autohaus.backend.usage.insights.TimingSectionRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The internal status page, its two tabs and its sibling endpoints behind Basic Auth (REQ-037 §12,
 * REQ-038 §12) / die interne Statusseite, ihre zwei Tabs und die Nachbar-Endpoints.
 *
 * <p>REQ-037: TC-13 (page renders without the LLM toggle), TC-13c ({@code usage.json}), TC-16
 * (kill switch), TC-21 (refresh throttle), TC-22 (CSP on {@code /internal/**}) and AC-7 (no
 * aggregation in the request path). REQ-038: TC-1–TC-11 and the controller parts of TC-46 and
 * TC-48. The former REQ-037 cases {@code rendersStatisticsAboveTheMissingReportHint} and
 * {@code placesStatisticsBeforeTheAiSectionWhenAReportExists} are replaced by TC-1–TC-3: the
 * hint and {@code .ai} now live on {@code /bugs} (BR-3).
 *
 * <p><b>Why a hand-built context:</b> the contexts below contain nothing but the controller, the
 * real {@link SecurityConfig} and the collaborators each test case needs. That keeps the
 * assertions about the security chain honest — the {@code WWW-Authenticate} realm, the CSP and the
 * {@code 401} come from production configuration, not from a test double — without booting the
 * whole application (SoftNet clients, schedulers, Angular resource handler). / Produktions-Chain,
 * minimaler Kontext.
 *
 * <p><b>Section renderers S1–S4 and S6 are markers here</b> ({@link #statisticsRenderer}): each
 * emits {@code <section id=… class=…>} plus its view model's warnings as {@code .st-warn}, exactly
 * under the same {@code null}/flag conditions as the contract demands. Their markup has its own
 * tests; this class verifies the controller — kill-switch matrix, composition and the absence of
 * any backend call. S0, S5 and S7 are the real renderers. / Sektions-Renderer S1–S4/S6 sind
 * Marker; geprüft wird der Controller.
 *
 * <p><b>Kill-switch matrix (TC-7) and the store-dependent cases (TC-46, TC-48)</b> run on a
 * standalone {@link MockMvc} over a directly constructed controller: eight switch combinations and
 * empty stores need eight contexts otherwise. Security is irrelevant there and covered by the
 * context tests. / Matrix und leere Stores ohne Spring-Kontext.
 */
class ReportControllerTest {

    private static final String USER = "report-admin";
    private static final String PASSWORD = "report-secret";

    /** AC-20: the exact policy the {@code /internal/**} chain has to send. */
    private static final String EXPECTED_CSP =
        "default-src 'none'; style-src 'self' 'unsafe-inline'; img-src 'none'; "
            + "form-action 'none'; frame-ancestors 'none'; base-uri 'none'";

    /** The hint shown while no error report exists (REQ-030 wording, kept by REQ-037). */
    private static final String NO_REPORT_HINT = "Es liegt noch kein Report vor.";

    /** A stored report fragment — no document frame, exactly what the store holds (§14.2). */
    private static final String REPORT_FRAGMENT =
        "<section class=\"ai\"><h2>KI-Zusammenfassung</h2></section>"
            + "<section class=\"ov\"><h2>Status-Uebersicht</h2></section>"
            + "<section class=\"err\"><h2>Fehler (0)</h2></section>";

    /** The request time of every test: Friday 23.10.2026, 09:12 Berlin / Abrufzeitpunkt. */
    private static final Instant NOW = Instant.parse("2026-10-23T07:12:00Z");

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    /** Every GET path of the status page (TC-8) / alle GET-Pfade. */
    private static final List<String> GET_PATHS = List.of("/internal/report", "/internal/report/",
        "/internal/report/statistics", "/internal/report/bugs", "/internal/report/usage.json",
        "/internal/report/insights.json", "/internal/report/capacity.json", "/internal/report/xyz");

    private static final Pattern SECTION = Pattern.compile("<section[^>]*?class=\"([^\" ]+)");
    private static final Pattern TOC_LINK = Pattern.compile("<a href=\"#([^\"]+)\">");

    // ===========================================================================================
    // Shared beans / gemeinsame Beans
    // ===========================================================================================

    @Configuration
    @EnableWebMvc
    static class BaseConfig {

        @Bean
        ErrorReportingProperties errorReportingProperties() {
            return new ErrorReportingProperties("workspace-id", "0 0 3 * * *", "Europe/Berlin",
                new ErrorReportingProperties.Admin(USER, PASSWORD));
        }

        @Bean
        UsageSectionRenderer usageSectionRenderer() {
            return new UsageSectionRenderer();
        }

        @Bean
        ReportHtmlGenerator reportHtmlGenerator(UsageSectionRenderer renderer) {
            return new ReportHtmlGenerator(renderer, statisticsRenderer(renderer));
        }

        @Bean
        ReportStore reportStore() {
            return new ReportStore();
        }

        @Bean
        UsageStatsStore usageStatsStore() {
            return new UsageStatsStore();
        }

        @Bean
        InsightsStore insightsStore() {
            return new InsightsStore();
        }

        @Bean
        CapacityStore capacityStore() {
            return new CapacityStore();
        }

        @Bean
        BriefingCalculator briefingCalculator() {
            return new BriefingCalculator();
        }

        @Bean
        SetupService setupService() {
            return setupServiceMock();
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneId.of("Europe/Berlin"));
        }
    }

    /** {@code usage.enabled=true}, throttle off so every manual refresh is allowed. */
    @Configuration
    static class UsageEnabledConfig {

        @Bean
        UsageStatsProperties usageStatsProperties() {
            return usageProperties(true, 0);
        }
    }

    /** {@code usage.enabled=true} with the production default throttle of 60 s (AC-21). */
    @Configuration
    static class ThrottledConfig {

        @Bean
        UsageStatsProperties usageStatsProperties() {
            return usageProperties(true, 60);
        }
    }

    /** {@code usage.enabled=false} — no scheduler bean at all, mirroring Alt Flow C. */
    @Configuration
    static class UsageDisabledConfig {

        @Bean
        UsageStatsProperties usageStatsProperties() {
            return usageProperties(false, 60);
        }
    }

    /** REQ-038 delivery state (AC-31): every new switch off / alle neuen Schalter aus. */
    @Configuration
    static class NewSourcesOffConfig {

        @Bean
        UsageInsightsProperties usageInsightsProperties() {
            return insightsProperties(false);
        }

        @Bean
        UsageFunnelProperties usageFunnelProperties() {
            return new UsageFunnelProperties(false, null);
        }

        @Bean
        UsageDropoffProperties usageDropoffProperties() {
            return new UsageDropoffProperties(false, null);
        }

        @Bean
        CapacityProperties capacityProperties() {
            return capacityFixture(false);
        }
    }

    /** §5.1 row 4: every switch on, throttle off / alle Schalter an. */
    @Configuration
    static class AllSourcesConfig {

        @Bean
        UsageStatsProperties usageStatsProperties() {
            return usageProperties(true, 0);
        }

        @Bean
        UsageInsightsProperties usageInsightsProperties() {
            return insightsProperties(true);
        }

        @Bean
        UsageFunnelProperties usageFunnelProperties() {
            return new UsageFunnelProperties(true, LocalDate.of(2026, 10, 1));
        }

        @Bean
        UsageDropoffProperties usageDropoffProperties() {
            return new UsageDropoffProperties(true, LocalDate.of(2026, 10, 1));
        }

        @Bean
        CapacityProperties capacityProperties() {
            return capacityFixture(true);
        }
    }

    /**
     * A real {@link UsageStatsScheduler} over a mocked aggregator and a mocked blob client / ein
     * echter Scheduler über gemocktem Aggregator und Blob-Client. Both backends are mocked so
     * AC-7 can assert on the <b>absence</b> of any call; an unstubbed {@link UsageBlobClient} mock
     * answers {@code isConfigured() == false}, exactly like the production client without a
     * storage account (E2), so one refresh still queries exactly one day. / Verhalten wie ohne
     * Storage Account, aber überprüfbar.
     */
    @Configuration
    static class SchedulerConfig {

        @Bean
        UsageStatsAggregator usageStatsAggregator() {
            return mock(UsageStatsAggregator.class);
        }

        @Bean
        UsageBlobClient usageBlobClient() {
            return mock(UsageBlobClient.class);
        }

        @Bean
        UsageStatsScheduler usageStatsScheduler(UsageStatsAggregator aggregator,
                                                UsageBlobClient blobClient,
                                                UsageStatsProperties properties,
                                                UsageStatsStore store) {
            return new UsageStatsScheduler(aggregator, blobClient,
                new UsageStatsService(properties), store, properties);
        }
    }

    /**
     * The REQ-038 backends as mocks (TC-8, TC-10) / die REQ-038-Backends als Mocks. The two job
     * schedulers are looked up by name: they are mocked when their classes exist, otherwise a
     * plain object stands in — either way nothing may touch them in the request path. / Die
     * Job-Scheduler werden gemockt, sofern die Klassen existieren.
     */
    @Configuration
    static class BackendMocksConfig {

        @Bean
        LogAnalyticsClient logAnalyticsClient() {
            return mock(LogAnalyticsClient.class);
        }

        @Bean
        InsightsBlobClient insightsBlobClient() {
            return mock(InsightsBlobClient.class);
        }

        @Bean
        CapacityBlobClient capacityBlobClient() {
            return mock(CapacityBlobClient.class);
        }

        @Bean
        SoftnetApiClient softnetApiClient() {
            return mock(SoftnetApiClient.class);
        }

        @Bean
        Object capacityScanSchedulerProbe() {
            return optionalMock("com.autohaus.backend.capacity.CapacityScanScheduler");
        }

        @Bean
        Object insightsSchedulerProbe() {
            return optionalMock("com.autohaus.backend.usage.insights.InsightsScheduler");
        }
    }

    // ===========================================================================================
    // TC-1 – TC-6, TC-8 – TC-10 — all switches on (§5.1 row 4)
    // ===========================================================================================

    @Nested
    @ExtendWith(SpringExtension.class)
    @WebAppConfiguration
    @ContextConfiguration(classes = {
        BaseConfig.class, AllSourcesConfig.class, SchedulerConfig.class, BackendMocksConfig.class,
        SecurityConfig.class, ReportController.class})
    class AllSourcesEnabled {

        @Autowired
        private WebApplicationContext context;

        @Autowired
        private ReportStore reportStore;

        @Autowired
        private UsageStatsStore usageStatsStore;

        @Autowired
        private InsightsStore insightsStore;

        @Autowired
        private CapacityStore capacityStore;

        @Autowired
        private UsageStatsAggregator aggregator;

        @Autowired
        private UsageBlobClient blobClient;

        @Autowired
        private LogAnalyticsClient logAnalyticsClient;

        @Autowired
        private InsightsBlobClient insightsBlobClient;

        @Autowired
        private CapacityBlobClient capacityBlobClient;

        @Autowired
        private SoftnetApiClient softnetApiClient;

        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            reportStore.update(REPORT_FRAGMENT, Instant.parse("2026-10-23T03:00:00Z"));
            usageStatsStore.update(snapshot());
            insightsStore.update(fullInsights(true, true), null);
            capacityStore.update(fullCapacity(), null);
            // Reset AFTER the stores are filled: the scheduler's startup run and preceding tests
            // have already talked to the backends, and TC-8 asserts on the absence of calls.
            reset(aggregator, blobClient, logAnalyticsClient, insightsBlobClient, capacityBlobClient,
                softnetApiClient);
            clearProbe("capacityScanSchedulerProbe");
            clearProbe("insightsSchedulerProbe");
        }

        /** TC-1: the default tab is the statistics tab — directly, without redirect (AC-1). */
        @ParameterizedTest
        @ValueSource(strings = {"/internal/report", "/internal/report/"})
        @DisplayName("TC-1: default tab renders statistics directly with 200, no Location, no .ai/.ov/.err/.nr")
        void rendersTheStatisticsTabByDefault(String path) throws Exception {
            String html = mockMvc.perform(get(path).with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().doesNotExist("Location"))
                .andReturn().getResponse().getContentAsString();

            assertTrue(html.contains("<nav class=\"tabs\""), "AC-2: tab bar present");
            assertTrue(html.contains("<a href=\"/internal/report/statistics\" aria-current=\"page\">Statistik</a>"),
                "AC-2: aria-current on the statistics link");
            assertTrue(html.contains("<section class=\"us\""), "S5 is rendered");
            assertFalse(html.contains("<section class=\"ai\""), "AC-3: no AI section");
            assertFalse(html.contains("<section class=\"ov\""), "AC-3: no status overview");
            assertFalse(html.contains("<section class=\"err\""), "AC-3: no error list");
            assertFalse(html.contains("<section class=\"nr\""), "AC-3: no missing-report hint");
        }

        /** TC-2: {@code /statistics} equals the default tab, {@code /bugs} carries the fragment. */
        @Test
        @DisplayName("TC-2: /statistics = default tab; /bugs has the fragment byte-identical and no statistics")
        void servesOneDocumentPerTab() throws Exception {
            String byDefault = page("/internal/report");
            String statistics = page("/internal/report/statistics");
            String bugs = page("/internal/report/bugs");

            assertEquals(sections(byDefault), sections(statistics), "same sections on both statistics paths");
            assertEquals(List.of("ib", "cap", "rk", "sg", "tm", "us", "fn", "st-limits"), sections(statistics));

            assertTrue(bugs.contains("<main id=\"content\">" + REPORT_FRAGMENT + "</main>"),
                "BR-3: the fragment byte-identical inside main");
            assertTrue(bugs.contains("<a href=\"/internal/report/bugs\" aria-current=\"page\">Bugs</a>"));
            assertTrue(bugs.contains("Fehler-Auswertung: letzte 24 Stunden"));
            assertFalse(bugs.contains("class=\"us\""), "BR-3: no usage section on the bugs tab");
            assertFalse(bugs.contains("class=\"rk\""), "BR-3: no ranking on the bugs tab");
            assertFalse(bugs.contains("class=\"st-toc\""), "no jump links on the bugs tab");
        }

        /** TC-3: without a report the hint appears on {@code /bugs} only (replaces REQ-037 :243/:272). */
        @Test
        @DisplayName("TC-3: missing report -> hint only on /bugs, both documents with one frame each")
        void showsTheMissingReportHintOnTheBugsTabOnly() throws Exception {
            reportStore.update(null, null);

            String statistics = page("/internal/report");
            String bugs = page("/internal/report/bugs");

            assertFalse(statistics.contains(NO_REPORT_HINT), "BR-3: the hint is not on the statistics tab");
            assertTrue(bugs.contains(NO_REPORT_HINT), "the previous hint text stays on the bugs tab");
            for (String html : List.of(statistics, bugs)) {
                assertEquals(1, countOccurrences(html, "<!doctype"), "exactly one document frame");
                assertEquals(1, countOccurrences(html, "<html"), "the fragment must not nest a document");
            }
        }

        /** TC-4 (row 4 here, row 3 in the matrix below): titles, H1, main and jump links. */
        @Test
        @DisplayName("TC-4: title per tab, one H1 'Statusseite', main without section wrapper, toc = sections")
        void framesBothTabs() throws Exception {
            String statistics = page("/internal/report/statistics");
            String bugs = page("/internal/report/bugs");

            assertTrue(statistics.contains("<title>Statusseite – Statistik</title>"));
            assertTrue(bugs.contains("<title>Statusseite – Bugs</title>"));
            for (String html : List.of(statistics, bugs)) {
                assertEquals(1, countOccurrences(html, "<h1>"), "exactly one h1");
                assertTrue(html.contains("<h1>Statusseite</h1>"));
                assertMainIsNotWrapped(html);
            }
            assertEquals(anchorsOf(statistics), tocLinks(statistics), "AC-4: toc lists exactly the sections");
        }

        /** TC-5: no script, form, image or role=tab; CSP byte-identical; no client-hint headers. */
        @ParameterizedTest
        @ValueSource(strings = {"/internal/report/statistics", "/internal/report/bugs", "/internal/report/xyz"})
        @DisplayName("TC-5: both tabs and the 404 page need nothing the CSP forbids")
        void needsNothingTheContentSecurityPolicyForbids(String path) throws Exception {
            String html = mockMvc.perform(get(path).with(httpBasic(USER, PASSWORD)))
                .andExpect(header().string("Content-Security-Policy", EXPECTED_CSP))
                .andExpect(header().doesNotExist("Accept-CH"))
                .andExpect(header().doesNotExist("Critical-CH"))
                .andExpect(header().doesNotExist("Permissions-Policy"))
                .andReturn().getResponse().getContentAsString();

            assertFalse(html.contains("<script"), "no script element");
            assertFalse(html.contains("<form"), "no form (form-action 'none')");
            assertFalse(html.contains("<img"), "no image (img-src 'none')");
            assertFalse(html.contains("<link "), "no external stylesheet");
            assertFalse(html.contains("role=\"tab"), "BR-2: no role=tab/tablist/tabpanel");
        }

        /** TC-6: unknown sub-paths answer 404 text/html, never the SPA and never the path. */
        @ParameterizedTest
        @ValueSource(strings = {"/internal/report/xyz", "/internal/report/a/b", "/internal/report/statistik"})
        @DisplayName("TC-6: catch-all -> 404 text/html 'Seite nicht gefunden', path not echoed")
        void answersUnknownSubPathsWith404(String path) throws Exception {
            String html = mockMvc.perform(get(path).with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn().getResponse().getContentAsString();

            assertTrue(html.contains("Seite nicht gefunden"));
            assertTrue(html.contains("<a href=\"/internal/report/statistics\">Zur Statistik</a>"));
            assertTrue(html.contains("<a href=\"/internal/report/bugs\">Zu den Bugs</a>"));
            assertFalse(html.contains("<app-root"), "E7: never the Angular index.html");
            assertFalse(html.contains(path), "no reflected path");
            assertFalse(html.contains("xyz"), "no reflected path segment");
        }

        /**
         * AC-5 / BR-1 / E7: {@code GET} on the POST-only refresh endpoint is an unknown GET path —
         * the 404 page, never 405/406, and nothing is refreshed. / GET auf refresh: 404.
         */
        @ParameterizedTest
        @ValueSource(strings = {"/internal/report/refresh", "/internal/report/refresh/"})
        @DisplayName("AC-5: GET /internal/report/refresh -> 404 text/html, nothing refreshed")
        void answersGetOnTheRefreshEndpointWith404(String path) throws Exception {
            String html = mockMvc.perform(get(path).with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn().getResponse().getContentAsString();

            assertTrue(html.contains("Seite nicht gefunden"));
            verifyNoInteractions(aggregator, blobClient, logAnalyticsClient);
        }

        /**
         * §12.4 / E7: a literal path whose content negotiation fails answers {@code 406} as before
         * REQ-038 — the catch-all must not turn it into a misleading 404. / Wörtlicher Pfad mit
         * unpassendem Accept: 406, nicht 404.
         */
        @ParameterizedTest(name = "{0} Accept {1}")
        @CsvSource({
            "/internal/report, application/json",
            "/internal/report/, application/json",
            "/internal/report/statistics, application/json",
            "/internal/report/bugs, text/plain",
            "/internal/report/usage.json, text/html",
            "/internal/report/insights.json, text/html",
            "/internal/report/capacity.json, text/csv"})
        @DisplayName("§12.4: wrong Accept on a literal path -> 406 without body, never the 404 page")
        void answersAWrongAcceptOnALiteralPathWith406(String path, String accept) throws Exception {
            String body = mockMvc.perform(get(path).accept(accept).with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isNotAcceptable())
                .andExpect(header().string("Content-Security-Policy", EXPECTED_CSP))
                .andReturn().getResponse().getContentAsString();

            assertEquals("", body, "no 404 page, no body");
        }

        /** Counter-check: the same literal paths answer 200 with a matching Accept. */
        @ParameterizedTest(name = "{0} Accept {1}")
        @CsvSource(delimiter = '|', value = {
            "/internal/report|text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "/internal/report/|text/html",
            "/internal/report/statistics|*/*",
            "/internal/report/bugs|text/html",
            "/internal/report/usage.json|application/json",
            "/internal/report/insights.json|application/json",
            "/internal/report/capacity.json|*/*"})
        @DisplayName("§12.4 counter-check: a matching Accept on a literal path -> 200")
        void answersAMatchingAcceptWith200(String path, String accept) throws Exception {
            mockMvc.perform(get(path).accept(accept).with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk());
        }

        /** E7: an unknown path stays 404 text/html whatever the client accepts. */
        @Test
        @DisplayName("E7: unknown path with Accept: application/json -> still the 404 page")
        void answersAnUnknownPathWith404WhateverTheAccept() throws Exception {
            mockMvc.perform(get("/internal/report/xyz").accept(MediaType.APPLICATION_JSON)
                    .with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        }

        /** TC-6 / E8: without credentials unknown sub-paths stay closed, too. */
        @ParameterizedTest
        @ValueSource(strings = {"/internal/report/xyz", "/internal/report/a/b", "/internal/report/statistics",
            "/internal/report/bugs", "/internal/report/insights.json", "/internal/report/capacity.json"})
        @DisplayName("TC-6 / TC-9 / E8: without credentials 401 with the internal realm")
        void refusesEveryPathWithoutCredentials(String path) throws Exception {
            mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("realm=\"internal\"")));
        }

        /**
         * TC-8 / AC-7 / BR-5: 20 requests across every GET path read the stores only — no Log
         * Analytics, no blob storage, no SoftNet. / Kein Backend-Aufruf im Request-Pfad.
         */
        @Test
        @DisplayName("TC-8: 20 GET requests without a single backend call")
        void readsOnlyInMemorySnapshots() throws Exception {
            for (int i = 0; i < 20; i++) {
                String path = GET_PATHS.get(i % GET_PATHS.size());
                mockMvc.perform(get(path).with(httpBasic(USER, PASSWORD)));
            }

            verifyNoInteractions(logAnalyticsClient, insightsBlobClient, capacityBlobClient,
                softnetApiClient, aggregator, blobClient);
            assertProbeUntouched("capacityScanSchedulerProbe");
            assertProbeUntouched("insightsSchedulerProbe");
        }

        /** TC-9: both JSON endpoints serve the already suppressed view models (§12.5, §12.6). */
        @Test
        @DisplayName("TC-9: insights.json — §12.5 keys, suppressed cell without any value, briefing")
        void servesTheInsightsViewAsJson() throws Exception {
            String json = mockMvc.perform(get("/internal/report/insights.json").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.asOf").value("2026-10-22"))
                .andExpect(jsonPath("$.bookingsEnabled").value(true))
                .andExpect(jsonPath("$.funnelEnabled").value(true))
                .andExpect(jsonPath("$.collectingSince").value("2026-09-01"))
                .andExpect(jsonPath("$.closedMonth").value("2026-09"))
                .andExpect(jsonPath("$.closedMonthIncomplete").value(false))
                .andExpect(jsonPath("$.missingDays").value(0))
                .andExpect(jsonPath("$.ranking", hasSize(1)))
                .andExpect(jsonPath("$.ranking[0].branchName").value("VW Nordtor"))
                .andExpect(jsonPath("$.ranking[0].bookings.value").value(24))
                .andExpect(jsonPath("$.belowThreshold[0].branchName").value("VW Neuhaus"))
                .andExpect(jsonPath("$.belowThreshold[0].bookings.state").value("SUPPRESSED"))
                .andExpect(jsonPath("$.belowThreshold[0].barWidth").value(0.0))
                .andExpect(jsonPath("$.funnel.stages[0].key").value("visit"))
                .andExpect(jsonPath("$.warnings", hasSize(0)))
                .andExpect(jsonPath("$.briefing.impulses").isArray())
                .andReturn().getResponse().getContentAsString();

            assertTrue(json.contains("\"bookings\":{\"value\":null,\"ratio\":null,\"percent\":null,"
                + "\"heat\":0,\"state\":\"SUPPRESSED\"}"), "AC-20: a suppressed cell carries no value");
            for (String key : List.of("\"funnelCollectingSince\":", "\"closedMonthPartialSince\":null",
                "\"month\":null", "\"year\":null", "\"matrix\":null", "\"closedMonthOffHoursShare\":")) {
                assertTrue(json.contains(key), "§12.5 key missing: " + key);
            }
        }

        @Test
        @DisplayName("TC-9: capacity.json — §12.6 keys, enum keys as strings, K-2 null while off")
        void servesTheCapacityViewAsJson() throws Exception {
            mockMvc.perform(get("/internal/report/capacity.json").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.scanDay").value("2026-10-22"))
                .andExpect(jsonPath("$.targetDay").value("2026-10-23"))
                .andExpect(jsonPath("$.state").value("partial"))
                .andExpect(jsonPath("$.showingFallbackScan").value(false))
                .andExpect(jsonPath("$.fallbackState").isEmpty())
                .andExpect(jsonPath("$.runningSince").isEmpty())
                .andExpect(jsonPath("$.collectingSince").value("2026-10-05"))
                .andExpect(jsonPath("$.last10ScanDays", hasSize(2)))
                .andExpect(jsonPath("$.rows[0].branchName").value("VW Nordtor"))
                .andExpect(jsonPath("$.rows[0].level").value("free-next-day"))
                .andExpect(jsonPath("$.rows[0].levelLabel").value("heute frei"))
                .andExpect(jsonPath("$.rows[0].freeSlotsTargetDay").isEmpty())
                .andExpect(jsonPath("$.rows[0].last10[0]").value("free"))
                .andExpect(jsonPath("$.rows[0].last10[1]").value("not-measurable"))
                .andExpect(jsonPath("$.rows[0].last10Reasons[1]").value("timeout"))
                .andExpect(jsonPath("$.rows[0].classification").value("mostly-free"))
                .andExpect(jsonPath("$.warnings[0]").value(containsString("unvollst")));
        }

        /** TC-10 / BR-16: refresh keeps its REQ-037 answer and triggers no REQ-038 job. */
        @Test
        @DisplayName("TC-10: refresh answers as in REQ-037 and never starts a scan or a rollup")
        void refreshTriggersNeitherScanNorRollup() throws Exception {
            InsightsViewModel insightsBefore = insightsStore.current();
            CapacityViewModel capacityBefore = capacityStore.current();

            mockMvc.perform(post("/internal/report/refresh").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Statistik neu erzeugt.")))
                .andExpect(content().string(containsString("Report-Feature nicht konfiguriert")));

            assertSame(insightsBefore, insightsStore.current(), "no insights rebuild");
            assertSame(capacityBefore, capacityStore.current(), "no capacity rebuild");
            verifyNoInteractions(insightsBlobClient, capacityBlobClient, softnetApiClient);
            assertProbeUntouched("capacityScanSchedulerProbe");
            assertProbeUntouched("insightsSchedulerProbe");
        }

        private String page(String path) throws Exception {
            return mockMvc.perform(get(path).with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        }

        private void clearProbe(String name) {
            Object probe = context.getBean(name);
            if (Mockito.mockingDetails(probe).isMock()) {
                Mockito.clearInvocations(probe);
            }
        }

        private void assertProbeUntouched(String name) {
            Object probe = context.getBean(name);
            if (Mockito.mockingDetails(probe).isMock()) {
                verifyNoInteractions(probe);
            }
        }
    }

    // ===========================================================================================
    // REQ-037 TC-13, TC-13c, TC-22 — §5.1 row 1 (usage on, every new switch off: AC-31)
    // ===========================================================================================

    @Nested
    @ExtendWith(SpringExtension.class)
    @WebAppConfiguration
    @ContextConfiguration(classes = {
        BaseConfig.class, UsageEnabledConfig.class, NewSourcesOffConfig.class, SchedulerConfig.class,
        SecurityConfig.class, ReportController.class})
    class UsageEnabled {

        @Autowired
        private WebApplicationContext context;

        @Autowired
        private ReportStore reportStore;

        @Autowired
        private UsageStatsStore usageStatsStore;

        @Autowired
        private InsightsStore insightsStore;

        @Autowired
        private CapacityStore capacityStore;

        @Autowired
        private UsageStatsAggregator aggregator;

        @Autowired
        private UsageBlobClient blobClient;

        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            reportStore.update(null, null);
            usageStatsStore.update(snapshot());
            // Filled stores must not leak while their switches are off — the switch decides.
            insightsStore.update(fullInsights(true, true), null);
            capacityStore.update(fullCapacity(), null);
            // Reset AFTER the store is filled: the scheduler's startup run and any preceding test
            // have already talked to both backends, and AC-7 asserts on the absence of calls. /
            // Ohne diesen Reset würde der Startlauf das Ergebnis verfälschen.
            reset(aggregator, blobClient);
        }

        /** TC-13 / §5.1 row 1: the statistics tab renders S5 and S7 without the LLM toggle. */
        @Test
        @DisplayName("§5.1 row 1: statistics tab with S5 and S7 only, the hint on /bugs")
        void rendersTheUsageStatisticsWithoutTheLlmToggle() throws Exception {
            String html = mockMvc.perform(get("/internal/report").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn().getResponse().getContentAsString();

            assertEquals(List.of("us", "st-limits"), sections(html), "AC-31: S5 and S7 only");
            assertEquals(List.of("us-h", "limits"), tocLinks(html));
            assertFalse(html.contains(NO_REPORT_HINT), "BR-3: the hint lives on the bugs tab");

            mockMvc.perform(get("/internal/report/bugs").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(NO_REPORT_HINT)));
        }

        /** TC-13: without credentials the page stays closed. */
        @Test
        void refusesTheStatusPageWithoutCredentials() throws Exception {
            mockMvc.perform(get("/internal/report"))
                .andExpect(status().isUnauthorized());
        }

        /** TC-13: the refresh endpoint no longer answers 503 without the LLM toggle (§12.3). */
        @Test
        void refreshUpdatesTheStatisticsWithoutTheLlmToggle() throws Exception {
            mockMvc.perform(post("/internal/report/refresh").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Statistik neu erzeugt.")));
        }

        /** TC-13c: the machine-readable aggregate (§12.2, AC-16). */
        @Test
        void servesTheUsageAggregateAsJson() throws Exception {
            mockMvc.perform(get("/internal/report/usage.json").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.month").exists())
                .andExpect(jsonPath("$.year").exists())
                .andExpect(jsonPath("$.months", hasSize(12)))
                .andExpect(jsonPath("$.liveAvailable").value(true))
                .andExpect(jsonPath("$.historyAvailable").value(true))
                .andExpect(jsonPath("$.collectingSince").value("2026-08-27"))
                .andExpect(jsonPath("$.lastRollupDay").value("2026-09-20"))
                .andExpect(jsonPath("$.year.visitCount").value(4195))
                // §12.2: the field is named rangeLabel — the record components ARE the JSON keys.
                .andExpect(jsonPath("$.month.rangeLabel").exists())
                .andExpect(jsonPath("$.month.range").doesNotExist());
        }

        /** TC-9 / AC-31: the new JSON endpoints do not exist while their switches are off. */
        @ParameterizedTest
        @ValueSource(strings = {"/internal/report/insights.json", "/internal/report/capacity.json"})
        void answers404ForSwitchedOffSources(String path) throws Exception {
            mockMvc.perform(get(path).with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isNotFound());
        }

        /**
         * AC-7: the aggregation runs exclusively in the scheduler — both read endpoints answer
         * from {@link UsageStatsStore} alone. Neither the {@link UsageStatsAggregator} (Log
         * Analytics) nor the {@link UsageBlobClient} (blob storage) is touched in the request
         * path. / Keine KQL- und keine Blob-Abfrage im Request-Pfad.
         *
         * <p>The response comes out of the snapshot {@code setUp()} put into the store, which is
         * why {@code year.visitCount} is asserted here as well: the endpoint serves stored data,
         * not freshly queried data. / Die Antwort stammt nachweislich aus dem Snapshot.
         *
         * <p><b>On the „&lt; 200 ms" half of AC-7:</b> it is deliberately not asserted as a
         * measured duration — a wall-clock threshold on a shared CI machine fails for reasons that
         * have nothing to do with this code. The absence of every backend call <b>is</b> the
         * proof: what remains is a map lookup plus HTML/JSON serialization, with no network hop
         * that could take 200 ms. / Die Zeitschranke wird nicht gemessen, sondern über die
         * Abwesenheit der Backend-Aufrufe belegt.
         */
        @Test
        void readsOnlyTheSnapshotWithoutAnyBackendQuery() throws Exception {
            mockMvc.perform(get("/internal/report").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"us\"")));

            mockMvc.perform(get("/internal/report/usage.json").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year.visitCount").value(4195));

            verify(aggregator, never()).aggregateDailyUsage(any());
            verify(blobClient, never()).loadUsageHistory();
            verify(blobClient, never()).saveUsageHistory(any(), any());
            // Not even isConfigured(): the request path does not talk to storage at all.
            verifyNoInteractions(blobClient);
        }

        /** TC-13c: without credentials 401 plus the realm from SecurityConfig. */
        @Test
        void refusesTheUsageAggregateWithoutCredentials() throws Exception {
            mockMvc.perform(get("/internal/report/usage.json"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Basic")))
                .andExpect(header().string("WWW-Authenticate", containsString("realm=\"internal\"")));
        }

        /** TC-22: the CSP of AC-20 on {@code /internal/**}. */
        @Test
        void sendsTheContentSecurityPolicyOnTheInternalChain() throws Exception {
            mockMvc.perform(get("/internal/report").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Content-Security-Policy",
                    containsString("style-src 'self' 'unsafe-inline'")))
                .andExpect(header().string("Content-Security-Policy", EXPECTED_CSP));
        }

        /** AC-3/AC-20: the page stays renderable under that policy — no script, no external asset. */
        @Test
        void thePageNeedsNothingTheContentSecurityPolicyForbids() throws Exception {
            String html = mockMvc.perform(get("/internal/report").with(httpBasic(USER, PASSWORD)))
                .andReturn().getResponse().getContentAsString();

            assertFalse(html.contains("<script"), "no script element");
            assertFalse(html.contains("<link "), "no external stylesheet");
            assertFalse(html.contains("<img"), "no image (img-src 'none')");
            assertFalse(html.contains("<form"), "no form (form-action 'none')");
        }

        /**
         * TC-3d: the application never asks the browser for client hints. {@code Sec-CH-UA-Mobile}
         * is read opportunistically as a tie breaker when a browser happens to send it, but no
         * response may carry an {@code Accept-CH} header — actively requesting hints would widen
         * the collected data beyond the purpose limitation of BR-3 / NFR-Datenschutz-3.
         *
         * <p>Es wird nie aktiv ein Client-Hint angefordert (kein {@code Accept-CH}).
         */
        @Test
        void neverRequestsClientHints() throws Exception {
            mockMvc.perform(get("/internal/report").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Accept-CH"))
                .andExpect(header().doesNotExist("Critical-CH"))
                .andExpect(header().doesNotExist("Permissions-Policy"));
        }
    }

    // ===========================================================================================
    // TC-16 / TC-11 — kill switch (REQ-037 AC-22, REQ-038 §5.1 row 8)
    // ===========================================================================================

    @Nested
    @ExtendWith(SpringExtension.class)
    @WebAppConfiguration
    @ContextConfiguration(classes = {
        BaseConfig.class, UsageDisabledConfig.class, NewSourcesOffConfig.class, SecurityConfig.class,
        ReportController.class})
    class UsageDisabled {

        @Autowired
        private WebApplicationContext context;

        @Autowired
        private ReportStore reportStore;

        @Autowired
        private UsageStatsStore usageStatsStore;

        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            reportStore.update(REPORT_FRAGMENT, Instant.parse("2026-09-21T03:00:00Z"));
            // A filled store must not leak into the page either — the switch, not the data, decides.
            usageStatsStore.update(snapshot());
        }

        /** TC-16: the section is dropped without replacement, the rest of the page is untouched. */
        @Test
        void dropsTheSectionWithoutAnyReplacement() throws Exception {
            String html = mockMvc.perform(get("/internal/report").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

            assertFalse(html.contains("class=\"us\""), "AC-22: no usage section");
            assertFalse(html.contains("Nutzungsstatistik"), "AC-22: not even the heading");
            assertFalse(html.contains("derzeit nicht verf"), "AC-22: and no availability hint");

            assertTrue(html.contains("<section class=\"ai\">"), "the AI section is unchanged");
            assertTrue(html.contains("<section class=\"ov\">"), "the status overview is unchanged");
            assertTrue(html.contains("<section class=\"err\">"), "the error list is unchanged");
        }

        /** TC-11 / AC-6: every statistics switch off → bugs content without any tab bar. */
        @Test
        @DisplayName("TC-11: all statistics switches off -> bugs content, no navigation, /statistics 404")
        void rendersTheBugsContentWithoutNavigation() throws Exception {
            String html = mockMvc.perform(get("/internal/report").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

            assertFalse(html.contains("<nav class=\"tabs\""), "AC-6: no navigation");
            assertFalse(html.contains("class=\"st-toc\""), "no jump links");
            assertTrue(html.contains("<title>Statusseite – Bugs</title>"));

            String notFound = mockMvc.perform(get("/internal/report/statistics").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
            assertFalse(notFound.contains("Zur Statistik"), "no link to a tab that does not exist");

            String bugs = mockMvc.perform(get("/internal/report/bugs").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            assertFalse(bugs.contains("<nav class=\"tabs\""), "the bugs tab has no tab bar either");
            assertTrue(bugs.contains(REPORT_FRAGMENT));
        }

        /** TC-16: the endpoint does not exist while the feature is off. */
        @Test
        void answers404ForTheUsageAggregate() throws Exception {
            mockMvc.perform(get("/internal/report/usage.json").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isNotFound());
        }

        /** TC-16: no scheduler bean — and the production class is gated by the same switch. */
        @Test
        void hasNoUsageSchedulerBean() {
            assertEquals(0, context.getBeanNamesForType(UsageStatsScheduler.class).length,
                "Alt Flow C: no aggregation runs while the feature is off");

            ConditionalOnProperty gate = UsageStatsScheduler.class.getAnnotation(ConditionalOnProperty.class);
            assertNotNull(gate, "the scheduler must be gated by usage.enabled");
            assertArrayEquals(new String[]{"usage.enabled"}, gate.name());
            assertEquals("true", gate.havingValue());
            assertTrue(gate.matchIfMissing(), "the feature is on by default");
        }

        /** §12.3: with both schedulers absent the endpoint keeps its previous 503. */
        @Test
        void refreshStaysUnavailableWhenBothSchedulersAreAbsent() throws Exception {
            mockMvc.perform(post("/internal/report/refresh").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isServiceUnavailable());
        }
    }

    // ===========================================================================================
    // TC-21 — refresh throttle (AC-21)
    // ===========================================================================================

    @Nested
    @ExtendWith(SpringExtension.class)
    @WebAppConfiguration
    @ContextConfiguration(classes = {
        BaseConfig.class, ThrottledConfig.class, NewSourcesOffConfig.class, SchedulerConfig.class,
        SecurityConfig.class, ReportController.class})
    class RefreshThrottle {

        @Autowired
        private WebApplicationContext context;

        @Autowired
        private UsageStatsAggregator aggregator;

        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            reset(aggregator);
        }

        /** TC-21: two calls inside the window → 200 then 429, and exactly one backend run. */
        @Test
        void throttlesTheSecondRefreshWithoutQueryingTheBackend() throws Exception {
            mockMvc.perform(post("/internal/report/refresh").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Statistik neu erzeugt.")));

            mockMvc.perform(post("/internal/report/refresh").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(containsString("Zu fr")))
                .andExpect(content().string(containsString("60 s")));

            // The rejected call must not cost a single Log Analytics query (AC-21).
            verify(aggregator, times(1)).aggregateDailyUsage(any());
        }
    }

    // ===========================================================================================
    // TC-7 — kill-switch matrix, all 8 rows of §5.1 (standalone MockMvc)
    // ===========================================================================================

    /**
     * One row of the §5.1 matrix / eine Zeile der Kill-Switch-Matrix.
     *
     * @param row row number of §5.1
     * @param sections expected section classes in order (empty = statistics tab absent)
     * @param toc expected jump-link anchors in order
     */
    record KillSwitchRow(int row, boolean usage, boolean insights, boolean capacity, boolean funnel,
                     boolean navigation, int statisticsStatus, int insightsStatus, int capacityStatus,
                     int usageStatus, List<String> sections, List<String> toc) {

        @Override
        public String toString() {
            return "§5.1 row " + row;
        }
    }

    static Stream<KillSwitchRow> matrix() {
        return Stream.of(
            new KillSwitchRow(1, true, false, false, false, true, 200, 404, 404, 200,
                List.of("us", "st-limits"), List.of("us-h", "limits")),
            new KillSwitchRow(2, true, true, false, false, true, 200, 200, 404, 200,
                List.of("ib", "rk", "sg", "tm", "us", "st-limits"),
                List.of("briefing", "branches", "service-groups", "timing", "us-h", "limits")),
            new KillSwitchRow(3, true, true, true, false, true, 200, 200, 200, 200,
                List.of("ib", "cap", "rk", "sg", "tm", "us", "st-limits"),
                List.of("briefing", "capacity", "branches", "service-groups", "timing", "us-h", "limits")),
            new KillSwitchRow(4, true, true, true, true, true, 200, 200, 200, 200,
                List.of("ib", "cap", "rk", "sg", "tm", "us", "fn", "st-limits"),
                List.of("briefing", "capacity", "branches", "service-groups", "timing", "us-h", "funnel",
                    "limits")),
            new KillSwitchRow(5, true, false, false, true, true, 200, 200, 404, 200,
                List.of("us", "fn", "st-limits"), List.of("us-h", "funnel", "limits")),
            // Rows 6–8: the funnel switch is „beliebig" — set to true to prove it has no effect.
            new KillSwitchRow(6, false, true, false, true, true, 200, 200, 404, 404,
                List.of("ib", "rk", "sg", "tm", "st-limits"),
                List.of("briefing", "branches", "service-groups", "timing", "limits")),
            new KillSwitchRow(7, false, false, true, true, true, 200, 404, 200, 404,
                List.of("ib", "cap", "st-limits"), List.of("briefing", "capacity", "limits")),
            new KillSwitchRow(8, false, false, false, true, false, 404, 404, 404, 404,
                List.of(), List.of()));
    }

    /**
     * TC-7 / AC-6 / BR-4: navigation, content of {@code /internal/report}, the status of every
     * path and the set of section classes follow the §5.1 matrix exactly; nowhere „nicht
     * verfügbar". / Navigation, Inhalt, Status und Sektionen exakt nach der Matrix.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("matrix")
    @DisplayName("TC-7: kill-switch matrix")
    void followsTheKillSwitchMatrix(KillSwitchRow row) throws Exception {
        Fixture fixture = new Fixture(row.usage(), row.insights(), row.capacity(), row.funnel(), true);
        fixture.fillStores();
        MockMvc mvc = fixture.mockMvc();

        String html = body(mvc, "/internal/report", 200);
        assertEquals(row.navigation(), html.contains("<nav class=\"tabs\""), "navigation");
        if (row.sections().isEmpty()) {
            assertTrue(html.contains("<section class=\"ai\">"), "row 8: bugs content on /internal/report");
            assertFalse(html.contains("class=\"st-limits\""), "row 8: no statistics tab");
        } else {
            assertEquals(row.sections(), sections(html), "sections of the statistics tab");
            assertEquals(row.toc(), tocLinks(html), "jump links = rendered sections");
            assertFalse(html.contains("<section class=\"ai\">"), "BR-3: no error report next to statistics");
            assertEquals(sections(html), sections(body(mvc, "/internal/report/statistics", 200)));
        }
        assertFalse(html.contains("nicht verf"), "BR-4: never 'nicht verfügbar'");

        expectStatus(mvc, "/internal/report/statistics", row.statisticsStatus());
        expectStatus(mvc, "/internal/report/insights.json", row.insightsStatus());
        expectStatus(mvc, "/internal/report/capacity.json", row.capacityStatus());
        expectStatus(mvc, "/internal/report/usage.json", row.usageStatus());
        String bugs = body(mvc, "/internal/report/bugs", 200);
        assertEquals(row.navigation(), bugs.contains("<nav class=\"tabs\""), "navigation on the bugs tab");

        if (row.row() == 5) {
            // TC-7 row 5: no .ib, .rk, .sg, .tm and no .st-empty for S2–S4.
            for (String cls : List.of("class=\"ib\"", "class=\"rk\"", "class=\"sg\"", "class=\"tm\"", "class=\"st-empty\"")) {
                assertFalse(html.contains(cls), "row 5 must not contain " + cls);
            }
        }
    }

    // ===========================================================================================
    // TC-4 (row 3), TC-46, TC-48 and the kill switch over a stale store — standalone
    // ===========================================================================================

    @Nested
    class StandaloneCases {

        /** TC-4 with the switches of §5.1 row 3. */
        @Test
        @DisplayName("TC-4 (row 3): titles, one H1, main without wrapper, toc = rendered section ids")
        void framesBothTabsForRowThree() throws Exception {
            Fixture fixture = new Fixture(true, true, true, false, false);
            fixture.fillStores();
            MockMvc mvc = fixture.mockMvc();

            String statistics = body(mvc, "/internal/report/statistics", 200);
            String bugs = body(mvc, "/internal/report/bugs", 200);

            assertTrue(statistics.contains("<title>Statusseite – Statistik</title>"));
            assertTrue(bugs.contains("<title>Statusseite – Bugs</title>"));
            for (String html : List.of(statistics, bugs)) {
                assertEquals(1, countOccurrences(html, "<h1>Statusseite</h1>"));
                assertEquals(1, countOccurrences(html, "<h1>"));
                assertMainIsNotWrapped(html);
            }
            assertEquals(anchorsOf(statistics), tocLinks(statistics));
            assertEquals(List.of("briefing", "capacity", "branches", "service-groups", "timing", "us-h",
                "limits"), tocLinks(statistics));
            assertTrue(statistics.contains("Auswertung bis gestern (22.10.2026)"), "META_STATISTICS");
            assertTrue(statistics.contains("Kapazitäts-Scan vom 22.10.2026"), "META_STATISTICS scan part");
        }

        /** TC-46 (controller part): funnel only, stores pre-filled (§5.1 row 5). */
        @Test
        @DisplayName("TC-46: funnel only -> S5–S7, insights.json with bookingsEnabled:false and a funnel")
        void servesTheFunnelOnlyView() throws Exception {
            Fixture fixture = new Fixture(true, false, false, true, false);
            fixture.insightsStore.update(funnelOnlyInsights(), null);
            fixture.capacityStore.update(fullCapacity(), null);
            fixture.usageStore.update(snapshot());
            MockMvc mvc = fixture.mockMvc();

            String html = body(mvc, "/internal/report/statistics", 200);
            assertEquals(List.of("us", "fn", "st-limits"), sections(html));
            assertEquals(List.of("us-h", "funnel", "limits"), tocLinks(html));
            for (String cls : List.of("class=\"ib\"", "class=\"rk\"", "class=\"sg\"", "class=\"tm\"",
                "class=\"cap\"", "class=\"st-empty\"")) {
                assertFalse(html.contains(cls), "TC-46: no " + cls);
            }

            mvc.perform(get("/internal/report/insights.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsEnabled").value(false))
                .andExpect(jsonPath("$.funnelEnabled").value(true))
                .andExpect(jsonPath("$.ranking").isEmpty())
                .andExpect(jsonPath("$.matrix").isEmpty())
                .andExpect(jsonPath("$.funnel.stages[0].reached.value").value(120))
                .andExpect(jsonPath("$.briefing").isEmpty());
        }

        /** TC-46 counter-check: row 2 (bookings only) has no funnel. */
        @Test
        @DisplayName("TC-46 counter-check (row 2): funnel:null and no .fn")
        void servesTheBookingsOnlyView() throws Exception {
            Fixture fixture = new Fixture(true, true, false, false, false);
            fixture.insightsStore.update(fullInsights(true, false), null);
            fixture.usageStore.update(snapshot());
            MockMvc mvc = fixture.mockMvc();

            String html = body(mvc, "/internal/report/statistics", 200);
            assertFalse(html.contains("class=\"fn\""));

            mvc.perform(get("/internal/report/insights.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsEnabled").value(true))
                .andExpect(jsonPath("$.funnel").isEmpty())
                .andExpect(jsonPath("$.briefing.impulses").isArray());
        }

        /**
         * TC-48 (controller part) / E4 / AC-32: nothing ever loaded → HTTP 200, S1–S4 each carry
         * „Historie derzeit nicht verfügbar", both JSON views name it in {@code warnings}; after a
         * later successful load the hint disappears. / Ohne Stand ein Hinweis, danach nicht mehr.
         */
        @Test
        @DisplayName("TC-48: empty stores -> 200 with 'Historie derzeit nicht verfügbar' in S1–S4 and JSON")
        void namesTheMissingHistoryInsteadOfNumbers() throws Exception {
            Fixture fixture = new Fixture(true, true, true, true, true);
            fixture.usageStore.update(snapshot());
            MockMvc mvc = fixture.mockMvc();

            String html = body(mvc, "/internal/report/statistics", 200);
            for (String id : List.of("capacity", "branches", "service-groups", "timing")) {
                String section = sectionById(html, id);
                assertTrue(section.contains("<p class=\"st-warn\">Historie derzeit nicht verf"),
                    "E4: warning in section " + id);
            }

            mvc.perform(get("/internal/report/insights.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings", hasItem(InsightsViewModel.WARN_HISTORY_UNAVAILABLE)));
            mvc.perform(get("/internal/report/capacity.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings", hasItem(CapacityViewModel.WARN_HISTORY_UNAVAILABLE)));

            // A later successful load replaces the view — the hint is gone.
            fixture.insightsStore.update(fullInsights(true, true), null);
            fixture.capacityStore.update(fullCapacity(), null);
            assertFalse(body(mvc, "/internal/report/statistics", 200).contains("Historie derzeit nicht verf"));
        }

        /** BR-4 / BR-13: a stored view built under other switches never leaks — the switch decides. */
        @Test
        @DisplayName("BR-4: a stale full view in the store is rebuilt under the current switches")
        void rebuildsAViewBuiltUnderOtherSwitches() throws Exception {
            Fixture fixture = new Fixture(true, false, false, true, false);
            fixture.insightsStore.update(fullInsights(true, true), null);
            fixture.usageStore.update(snapshot());
            MockMvc mvc = fixture.mockMvc();

            String html = body(mvc, "/internal/report/statistics", 200);
            assertFalse(html.contains("class=\"rk\""), "no booking data under a funnel-only switch set");
            assertTrue(html.contains("class=\"fn\""));

            mvc.perform(get("/internal/report/insights.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsEnabled").value(false))
                .andExpect(jsonPath("$.funnelEnabled").value(true));
        }

        /**
         * BR-4 / AC-35: a stored funnel view carrying drop-off data is rebuilt while
         * {@code usage.dropoff.enabled=false} — no {@code dropoff} in the response. / Ohne
         * Dropoff-Schalter kein Dropoff in der Antwort.
         */
        @Test
        @DisplayName("AC-35: a stored view with drop-off data is rebuilt when usage.dropoff.enabled=false")
        void rebuildsAViewWithDropoffDataWhileDropoffIsOff() throws Exception {
            Fixture fixture = new Fixture(true, false, false, true, false);
            FunnelView withDropoff = new FunnelView(null, funnelView().stages(), List.of(), List.of(), Map.of(),
                new com.autohaus.backend.usage.insights.DropoffView(LocalDate.of(2026, 10, 1), List.of(),
                    null, List.of(), List.of()));
            fixture.insightsStore.update(new InsightsViewModel(TODAY.minusDays(1), false, true,
                null, LocalDate.of(2026, 10, 1), null, null, false, 0, null, null, null, null, null, null,
                withDropoff, List.of()), null);
            fixture.usageStore.update(snapshot());
            MockMvc mvc = fixture.mockMvc();

            mvc.perform(get("/internal/report/insights.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.funnelEnabled").value(true))
                .andExpect(jsonPath("$.funnel.dropoff").isEmpty());
        }

        /** E4 robustness: without branch names the „no history" view still renders (HTTP 200). */
        @Test
        @DisplayName("E4: a failing or missing SetupService never breaks the page")
        void rendersWithoutBranchNames() throws Exception {
            SetupService broken = mock(SetupService.class);
            when(broken.getAllBranches()).thenThrow(new IllegalStateException("setup.json unreadable"));
            for (SetupService setup : java.util.Arrays.asList(broken, null)) {
                ReportController controller = new ReportController(new ReportStore(), new ReportHtmlGenerator(
                    new UsageSectionRenderer(), statisticsRenderer(new UsageSectionRenderer())),
                    new UsageStatsStore(), usageProperties(false, 0), emptyProvider(ErrorReportScheduler.class),
                    emptyProvider(UsageStatsScheduler.class), new InsightsStore(), new CapacityStore(),
                    new BriefingCalculator(), setup, insightsProperties(true),
                    new UsageFunnelProperties(false, null), new UsageDropoffProperties(false, null),
                    capacityFixture(true), null);
                MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

                String html = body(mvc, "/internal/report/statistics", 200);
                assertTrue(html.contains("Historie derzeit nicht verf"), "E4 hint instead of numbers");
                expectStatus(mvc, "/internal/report/insights.json", 200);
                expectStatus(mvc, "/internal/report/capacity.json", 200);
            }
        }

        /** E7 via the standalone setup: the literal JSON mappings win over the catch-all. */
        @Test
        @DisplayName("BR-1: literal mappings take precedence over the catch-all")
        void prefersLiteralMappingsOverTheCatchAll() throws Exception {
            Fixture fixture = new Fixture(true, true, true, true, true);
            fixture.fillStores();
            MockMvc mvc = fixture.mockMvc();

            expectStatus(mvc, "/internal/report/usage.json", 200);
            expectStatus(mvc, "/internal/report/insights.json", 200);
            expectStatus(mvc, "/internal/report/capacity.json", 200);
            expectStatus(mvc, "/internal/report/bugs/x", 404);
            expectStatus(mvc, "/internal/report/statistics/extra", 404);
        }

        /**
         * §12.4: the catch-all compares the path the literal mapping compared — decoded, without
         * {@code ;} parameters — so such a variant with a wrong Accept is a 406 as well. / Der
         * Vergleich folgt dem PathPattern-Abgleich.
         */
        @Test
        @DisplayName("§12.4: ';' parameters and encoded characters are compared like the literal match")
        void comparesThePathLikeTheLiteralMatch() throws Exception {
            Fixture fixture = new Fixture(true, true, true, true, true);
            fixture.fillStores();
            MockMvc mvc = fixture.mockMvc();

            mvc.perform(get(URI.create("/internal/report/usage.json;v=1")).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
            mvc.perform(get(URI.create("/internal/report/usage.json;v=1")).accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotAcceptable());
            mvc.perform(get(URI.create("/internal/report/usage%2Ejson")).accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotAcceptable());
            mvc.perform(get("/internal/report/usage.jsonx").accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound());
            mvc.perform(get("/internal/report/refresh")).andExpect(status().isNotFound());
        }

        /** The fallback without a parsed request path strips the context path and parameters. */
        @Test
        @DisplayName("§12.4: lookupPath without a parsed path: context path and ';' parameters removed")
        void derivesTheLookupPathWithoutAParsedPath() {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/internal/report/bugs;x=1");
            request.setContextPath("/app");

            assertEquals("/internal/report/bugs", ReportController.lookupPath(request));
            assertEquals("/internal/report/",
                ReportController.lookupPath(new MockHttpServletRequest("GET", "/internal/report/")));
        }
    }

    /**
     * §12.4: {@code LITERAL_GET_PATHS} is exactly the set of literal GET mappings — a new tab or
     * endpoint without an entry would answer a wrong Accept with the 404 page again. / Die Menge
     * entspricht genau den wörtlichen GET-Mappings.
     */
    @Test
    @DisplayName("§12.4: LITERAL_GET_PATHS = every literal @GetMapping of the controller, refresh not included")
    void knowsEveryLiteralGetPath() {
        Set<String> literal = new TreeSet<>();
        for (Method method : ReportController.class.getDeclaredMethods()) {
            GetMapping mapping = method.getAnnotation(GetMapping.class);
            if (mapping == null) {
                continue;
            }
            for (String path : mapping.value().length > 0 ? mapping.value() : mapping.path()) {
                if (!path.contains("*")) {
                    literal.add(path);
                }
            }
        }

        assertEquals(literal, new TreeSet<>(ReportController.LITERAL_GET_PATHS));
        assertEquals(new TreeSet<>(GET_PATHS.subList(0, 7)), literal, "the documented GET paths of TC-8");
        assertFalse(ReportController.LITERAL_GET_PATHS.contains(ReportController.PATH_REFRESH),
            "AC-5: GET /refresh is an unknown path");
    }

    /** §14.2: the controller has exactly one {@code @Autowired} constructor (Spring start-up). */
    @Test
    @DisplayName("§14.2: exactly one @Autowired constructor on the controller")
    void hasExactlyOneAutowiredConstructor() {
        List<Constructor<?>> autowired = Arrays.stream(ReportController.class.getConstructors())
            .filter(c -> c.isAnnotationPresent(Autowired.class)).toList();

        assertEquals(1, ReportController.class.getConstructors().length);
        assertEquals(1, autowired.size());
    }

    // ===========================================================================================
    // Standalone fixture / eigenständiger Aufbau
    // ===========================================================================================

    /** A directly constructed controller over fresh stores / Controller über frische Stores. */
    private static final class Fixture {

        final ReportStore reportStore = new ReportStore();
        final UsageStatsStore usageStore = new UsageStatsStore();
        final InsightsStore insightsStore = new InsightsStore();
        final CapacityStore capacityStore = new CapacityStore();
        private final boolean usage;
        private final boolean insights;
        private final boolean capacity;
        private final boolean funnel;
        private final boolean dropoff;

        Fixture(boolean usage, boolean insights, boolean capacity, boolean funnel, boolean dropoff) {
            this.usage = usage;
            this.insights = insights;
            this.capacity = capacity;
            this.funnel = funnel;
            this.dropoff = dropoff;
            reportStore.update(REPORT_FRAGMENT, Instant.parse("2026-10-23T03:00:00Z"));
        }

        /** Fills every store like the schedulers would under these switches / wie die Scheduler. */
        void fillStores() {
            usageStore.update(snapshot());
            insightsStore.update(fullInsights(insights, usage && funnel), null);
            capacityStore.update(fullCapacity(), null);
        }

        MockMvc mockMvc() {
            UsageSectionRenderer usageRenderer = new UsageSectionRenderer();
            ReportController controller = new ReportController(reportStore,
                new ReportHtmlGenerator(usageRenderer, statisticsRenderer(usageRenderer)), usageStore,
                usageProperties(usage, 0), emptyProvider(ErrorReportScheduler.class),
                emptyProvider(UsageStatsScheduler.class), insightsStore, capacityStore,
                new BriefingCalculator(), setupServiceMock(), insightsProperties(insights),
                new UsageFunnelProperties(funnel, null), new UsageDropoffProperties(dropoff, null),
                capacityFixture(capacity), clockProvider());
            return MockMvcBuilders.standaloneSetup(controller).build();
        }
    }

    private static <T> ObjectProvider<T> emptyProvider(Class<T> type) {
        return new DefaultListableBeanFactory().getBeanProvider(type);
    }

    private static ObjectProvider<Clock> clockProvider() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("clock", Clock.fixed(NOW, ZoneId.of("Europe/Berlin")));
        return factory.getBeanProvider(Clock.class);
    }

    private static String body(MockMvc mvc, String path, int expectedStatus) throws Exception {
        return mvc.perform(get(path))
            .andExpect(status().is(expectedStatus))
            .andReturn().getResponse().getContentAsString();
    }

    private static void expectStatus(MockMvc mvc, String path, int expectedStatus) throws Exception {
        mvc.perform(get(path)).andExpect(status().is(expectedStatus));
    }

    // ===========================================================================================
    // Marker renderers / Marker-Renderer
    // ===========================================================================================

    /**
     * The statistics composer with marker renderers for S1–S4 and S6 (see class Javadoc) and the
     * real S0, S5, S7 / Komposition mit Marker-Renderern für S1–S4 und S6.
     */
    static StatisticsPageRenderer statisticsRenderer(UsageSectionRenderer usageRenderer) {
        return new StatisticsPageRenderer(
            new BriefingSectionRenderer(),
            new CapacitySectionRenderer() {
                @Override
                public String render(CapacityViewModel viewModel) {
                    return viewModel == null ? "" : marker("capacity", "cap", viewModel.warnings());
                }
            },
            new BranchRankingSectionRenderer() {
                @Override
                public String render(InsightsViewModel viewModel) {
                    return viewModel == null || !viewModel.bookingsEnabled() ? ""
                        : marker("branches", "rk", viewModel.warnings());
                }
            },
            new ServiceGroupSectionRenderer() {
                @Override
                public String render(InsightsViewModel viewModel) {
                    return viewModel == null || !viewModel.bookingsEnabled() ? ""
                        : marker("service-groups", "sg", viewModel.warnings());
                }
            },
            new EntryVariantSectionRenderer() {
                @Override
                public String render(InsightsViewModel viewModel) {
                    return "";
                }
            },
            new TimingSectionRenderer() {
                @Override
                public String render(InsightsViewModel viewModel) {
                    return viewModel == null || !viewModel.bookingsEnabled() ? ""
                        : marker("timing", "tm", viewModel.warnings());
                }
            },
            usageRenderer,
            new FunnelSectionRenderer() {
                @Override
                public String render(InsightsViewModel viewModel) {
                    return viewModel == null || !viewModel.funnelEnabled() ? ""
                        : marker("funnel", "fn", viewModel.warnings());
                }
            },
            new LimitsSectionRenderer());
    }

    private static String marker(String id, String cssClass, List<String> warnings) {
        StringBuilder h = new StringBuilder();
        h.append("<section id=\"").append(id).append("\" class=\"").append(cssClass)
            .append("\" aria-labelledby=\"").append(id).append("-h\"><h2 id=\"").append(id).append("-h\">")
            .append(id).append("</h2>");
        for (String warning : warnings == null ? List.<String>of() : warnings) {
            h.append("<p class=\"st-warn\">").append(ReportHtmlGenerator.esc(warning)).append("</p>");
        }
        return h.append("</section>").toString();
    }

    // ===========================================================================================
    // Fixtures / Testdaten
    // ===========================================================================================

    private static UsageStatsProperties usageProperties(boolean enabled, int refreshMinInterval) {
        return new UsageStatsProperties(
            enabled,
            "0 */15 * * * *",
            "0 30 3 * * *",
            "Europe/Berlin",
            null,
            24,
            refreshMinInterval,
            new UsageStatsProperties.Storage("", "usage-stats", "usage-daily.json"));
    }

    private static UsageInsightsProperties insightsProperties(boolean enabled) {
        return new UsageInsightsProperties(enabled, null, "0 45 3 * * *", "usage-insights.json");
    }

    private static CapacityProperties capacityFixture(boolean enabled) {
        return new CapacityProperties(enabled, "0 30 22 * * *", "wiper-blades", false, "capacity-daily.json");
    }

    private static SetupService setupServiceMock() {
        SetupService setup = mock(SetupService.class);
        when(setup.getAllBranches()).thenReturn(List.of(
            new BranchSetup("aaaaaaaaaaaaaaaaaaaaaaaa", "VW Nordtor", null, List.of()),
            new BranchSetup("bbbbbbbbbbbbbbbbbbbbbbbb", "VW Neuhaus", null, List.of())));
        return setup;
    }

    private static Object optionalMock(String className) {
        try {
            return mock(Class.forName(className));
        } catch (ClassNotFoundException | LinkageError e) {
            return new Object();
        }
    }

    /**
     * A K2/K3 view with one visible and one suppressed ranking row, a funnel stage and no warning
     * — as the scheduler builds it under the given switches / wie der Scheduler sie baut.
     */
    private static InsightsViewModel fullInsights(boolean bookings, boolean funnel) {
        List<BranchRankingRow> ranking = bookings ? List.of(new BranchRankingRow("VW Nordtor", 1, "same", 1,
            Cell.of(24), Cell.percentOf(100.0), Cell.of(24), Cell.of(410), Cell.ratioOf(5.9),
            Cell.percentOf(38.0), Cell.percentOf(12.0), 100.0)) : null;
        List<BranchRankingRow> below = bookings ? List.of(new BranchRankingRow("VW Neuhaus", null, null, null,
            Cell.suppressed(), Cell.withheld(), Cell.withheld(), Cell.of(40), Cell.withheld(), Cell.withheld(),
            Cell.withheld(), 0.0)) : null;
        return new InsightsViewModel(TODAY.minusDays(1), bookings, funnel, LocalDate.of(2026, 9, 1),
            funnel ? LocalDate.of(2026, 10, 1) : null, bookings ? YearMonth.of(2026, 9) : null, null, false, 0,
            ranking, below, null, null, null, bookings ? Cell.percentOf(38.0) : null,
            funnel ? funnelView() : null, List.of());
    }

    /** §12.5 „nur Trichter": {@code bookingsEnabled=false}, booking parts {@code null}. */
    private static InsightsViewModel funnelOnlyInsights() {
        return fullInsights(false, true);
    }

    private static FunnelView funnelView() {
        return new FunnelView(null, List.of(new FunnelStage("visit", Cell.of(120), Cell.percentOf(100.0),
            Cell.percentOf(100.0), Cell.of(20))), List.of(), List.of(), Map.of(), null);
    }

    /** A partial scan of yesterday with one row „heute frei" / ein Teil-Scan von gestern. */
    private static CapacityViewModel fullCapacity() {
        List<CapacityDayState> last10 = List.of(CapacityDayState.FREE, CapacityDayState.NOT_MEASURABLE);
        List<String> reasons = new ArrayList<>(Collections.nCopies(2, (String) null));
        reasons.set(1, "timeout");
        CapacityRow row = new CapacityRow("VW Nordtor", CapacityLevel.FREE_NEXT_DAY, "heute frei", 0, null,
            null, last10, reasons, 1, 1, CapacityTrend.MOSTLY_FREE);
        return new CapacityViewModel(LocalDate.of(2026, 10, 22), TODAY, ScanState.PARTIAL, false, null, null,
            LocalDate.of(2026, 10, 5), List.of(LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 22)),
            List.of(row), List.of("Scan vom 22.10.2026 unvollständig: 1 Standorte nicht messbar."));
    }

    /**
     * The §12.2 example snapshot with ASCII-only labels / der Beispiel-Snapshot aus §12.2 mit
     * ASCII-Beschriftungen — the labels are data here, the encoding of the response is not part of
     * the contract under test.
     */
    private static UsageStats snapshot() {
        PeriodUsage month = new PeriodUsage(
            "Aktueller Monat (September 2026)", "01.09.2026 - 21.09.2026 Europe/Berlin",
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 21), true,
            new DeviceBreakdown(612, 44, 289, 9), 954,
            1180, 1141, 174, 168, 431, 143, 45.2, 15.0, 33.2);
        PeriodUsage year = new PeriodUsage(
            "Jahr 2026", "01.01.2026 - 21.09.2026 Europe/Berlin",
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 21), true,
            new DeviceBreakdown(2743, 198, 1204, 50), 4195,
            5210, 5041, 782, 755, 1901, 623, 45.3, 14.9, 32.8);

        List<PeriodUsage> months = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            LocalDate from = LocalDate.of(2026, m, 1);
            LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
            months.add(m == 9
                ? month
                : new PeriodUsage("Monat " + m, from + " - " + to, from, to, false,
                    new DeviceBreakdown(0, 0, 0, 0), 0, 0, 0, 0, 0, 0, 0, null, null, null));
        }

        return new UsageStats(month, year, List.copyOf(months),
            Instant.parse("2026-09-21T09:45:03Z"), true, true,
            LocalDate.of(2026, 8, 27), LocalDate.of(2026, 9, 20));
    }

    // ===========================================================================================
    // HTML helpers / HTML-Hilfen
    // ===========================================================================================

    /** Section classes in document order / Sektionsklassen in Dokumentreihenfolge. */
    private static List<String> sections(String html) {
        List<String> classes = new ArrayList<>();
        Matcher m = SECTION.matcher(html);
        while (m.find()) {
            classes.add(m.group(1));
        }
        return classes;
    }

    /** The jump-link anchors of {@code nav.st-toc} / die Sprungziele der Inhaltsliste. */
    private static List<String> tocLinks(String html) {
        int start = html.indexOf("<nav class=\"st-toc\"");
        if (start < 0) {
            return List.of();
        }
        String toc = html.substring(start, html.indexOf("</nav>", start));
        List<String> links = new ArrayList<>();
        Matcher m = TOC_LINK.matcher(toc);
        while (m.find()) {
            links.add(m.group(1));
        }
        return links;
    }

    /** The anchors the rendered sections offer (S5 via its {@code h2#us-h}) / Sektions-Anker. */
    private static List<String> anchorsOf(String html) {
        List<String> anchors = new ArrayList<>();
        Matcher m = Pattern.compile("<section (?:id=\"([^\"]+)\" )?class=\"([^\" ]+)").matcher(html);
        while (m.find()) {
            anchors.add(m.group(1) != null ? m.group(1) : "us".equals(m.group(2)) ? "us-h" : m.group(2));
        }
        return anchors;
    }

    /** The markup of one section by id / das Markup einer Sektion. */
    private static String sectionById(String html, String id) {
        int start = html.indexOf("<section id=\"" + id + "\"");
        assertTrue(start > -1, "section " + id + " missing");
        return html.substring(start, html.indexOf("</section>", start));
    }

    /** AC-4: {@code main#content} is the panel wrapper — no enclosing {@code <section>}. */
    private static void assertMainIsNotWrapped(String html) {
        int main = html.indexOf("<main id=\"content\">");
        assertTrue(main > -1, "main#content present");
        assertEquals(0, countOccurrences(html.substring(0, main), "<section"), "no section before main");
        assertTrue(html.indexOf("</main>") > main, "main closes");
        assertEquals(0, countOccurrences(html.substring(html.indexOf("</main>")), "</section>"),
            "no section closes after main");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i > -1; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
