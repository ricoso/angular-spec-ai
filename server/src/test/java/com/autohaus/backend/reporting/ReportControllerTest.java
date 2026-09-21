package com.autohaus.backend.reporting;

import com.autohaus.backend.config.ErrorReportingProperties;
import com.autohaus.backend.config.SecurityConfig;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.usage.DeviceBreakdown;
import com.autohaus.backend.usage.PeriodUsage;
import com.autohaus.backend.usage.UsageBlobClient;
import com.autohaus.backend.usage.UsageSectionRenderer;
import com.autohaus.backend.usage.UsageStats;
import com.autohaus.backend.usage.UsageStatsAggregator;
import com.autohaus.backend.usage.UsageStatsScheduler;
import com.autohaus.backend.usage.UsageStatsService;
import com.autohaus.backend.usage.UsageStatsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The internal status page and its two sibling endpoints behind Basic Auth (REQ-037 §12) / die
 * interne Statusseite und ihre beiden Nachbar-Endpoints hinter Basic Auth.
 *
 * <p>Covers TC-13 (page renders without the LLM toggle), TC-13b (statistics before the AI
 * section), TC-13c ({@code usage.json}), TC-16 (kill switch), TC-21 (refresh throttle), TC-22
 * (CSP on {@code /internal/**}) and AC-7 (no aggregation in the request path).
 *
 * <p><b>Why a hand-built context:</b> the contexts below contain nothing but the controller, the
 * real {@link SecurityConfig} and the collaborators each test case needs. That keeps the
 * assertions about the security chain honest — the {@code WWW-Authenticate} realm, the CSP and the
 * {@code 401} come from production configuration, not from a test double — without booting the
 * whole application (SoftNet clients, schedulers, Angular resource handler). / Produktions-Chain,
 * minimaler Kontext.
 *
 * <p>Assertions deliberately use ASCII substrings only: the response character encoding of
 * {@code MockHttpServletResponse} is not part of this contract, so German umlauts are never
 * asserted on here. / Bewusst nur ASCII-Teilstrings.
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
            return new ReportHtmlGenerator(renderer);
        }

        @Bean
        ReportStore reportStore() {
            return new ReportStore();
        }

        @Bean
        UsageStatsStore usageStatsStore() {
            return new UsageStatsStore();
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

    private static UsageStatsProperties usageProperties(boolean enabled, int refreshMinInterval) {
        return new UsageStatsProperties(
            enabled,
            "0 */15 * * * *",
            "0 30 3 * * *",
            "Europe/Berlin",
            24,
            refreshMinInterval,
            new UsageStatsProperties.Storage("", "usage-stats", "usage-daily.json"));
    }

    // ===========================================================================================
    // TC-13, TC-13b, TC-13c, TC-22 — feature on
    // ===========================================================================================

    @Nested
    @ExtendWith(SpringExtension.class)
    @WebAppConfiguration
    @ContextConfiguration(classes = {
        BaseConfig.class, UsageEnabledConfig.class, SchedulerConfig.class,
        SecurityConfig.class, ReportController.class})
    class UsageEnabled {

        @Autowired
        private WebApplicationContext context;

        @Autowired
        private ReportStore reportStore;

        @Autowired
        private UsageStatsStore usageStatsStore;

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
            // Reset AFTER the store is filled: the scheduler's startup run and any preceding test
            // have already talked to both backends, and AC-7 asserts on the absence of calls. /
            // Ohne diesen Reset würde der Startlauf das Ergebnis verfälschen.
            reset(aggregator, blobClient);
        }

        /** TC-13: the page renders without {@code LLM_API_KEY}/{@code LLM_MODEL}. */
        @Test
        void rendersStatisticsAboveTheMissingReportHint() throws Exception {
            String html = mockMvc.perform(get("/internal/report").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn().getResponse().getContentAsString();

            int usage = html.indexOf("<section class=\"us\"");
            int hint = html.indexOf(NO_REPORT_HINT);
            assertTrue(usage > -1, "AC-6: the statistics render without the LLM toggle");
            assertTrue(hint > -1, "the previous hint text is kept when no report exists");
            assertTrue(usage < hint, "AC-6: statistics first, hint below");
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

        /** TC-13b: with a report present the statistics still come first (AC-2). */
        @Test
        void placesStatisticsBeforeTheAiSectionWhenAReportExists() throws Exception {
            reportStore.update(REPORT_FRAGMENT, Instant.parse("2026-09-21T03:00:00Z"));

            String html = mockMvc.perform(get("/internal/report").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

            int usage = html.indexOf("class=\"us\"");
            int ai = html.indexOf("<section class=\"ai\"");
            assertTrue(usage > -1, "the usage section must be present");
            assertTrue(ai > -1, "the AI section must be present");
            assertTrue(usage < ai, "AC-2: the statistics precede the AI summary");
            assertEquals(1, countOccurrences(html, "<!doctype"), "exactly one document frame");
            assertEquals(1, countOccurrences(html, "<html"), "the fragment must not nest a document");
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
    // TC-16 — kill switch (AC-22)
    // ===========================================================================================

    @Nested
    @ExtendWith(SpringExtension.class)
    @WebAppConfiguration
    @ContextConfiguration(classes = {
        BaseConfig.class, UsageDisabledConfig.class, SecurityConfig.class, ReportController.class})
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
        BaseConfig.class, ThrottledConfig.class, SchedulerConfig.class,
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
    // Fixtures / Testdaten
    // ===========================================================================================

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

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i > -1; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
