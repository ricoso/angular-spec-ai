package com.autohaus.backend.reporting;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.autohaus.backend.BackendApplication;
import com.autohaus.backend.appointments.AppointmentsService;
import com.autohaus.backend.capacity.CapacityBlobClient;
import com.autohaus.backend.capacity.CapacityProbe;
import com.autohaus.backend.capacity.CapacityScanScheduler;
import com.autohaus.backend.capacity.CapacityStore;
import com.autohaus.backend.common.filter.LoggingFilter;
import com.autohaus.backend.common.filter.OriginFilter;
import com.autohaus.backend.common.filter.RateLimitFilter;
import com.autohaus.backend.common.filter.RequestIdFilter;
import com.autohaus.backend.common.filter.SessionTokenFilter;
import com.autohaus.backend.config.CapacityProperties;
import com.autohaus.backend.config.UsageDropoffProperties;
import com.autohaus.backend.config.UsageFunnelProperties;
import com.autohaus.backend.config.UsageInsightsProperties;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.config.setup.BranchSetup;
import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.session.SessionTokenService;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.autohaus.backend.usage.insights.InsightsAggregator;
import com.autohaus.backend.usage.insights.InsightsBlobClient;
import com.autohaus.backend.usage.insights.InsightsRetentionJob;
import com.autohaus.backend.usage.insights.InsightsScheduler;
import com.autohaus.backend.usage.insights.InsightsStore;
import com.autohaus.backend.usage.insights.InsightsViewModel;
import com.autohaus.backend.usage.insights.ServiceGroupClassifier;
import com.autohaus.backend.usage.insights.UsageEventLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.http.MediaType;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockReset;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REQ-038 in the <b>real</b> Spring context / REQ-038 im echten Spring-Kontext: the delivery state
 * (TC-47, AC-31) and the E2E seed profile (§14.1, §14.2, prerequisite of TC-41–TC-44).
 *
 * <p>Both contexts boot {@link BackendApplication} with the shipped {@code application.yml} — every
 * bean, every filter, the real security chain and the real schedulers. This is the only test that
 * proves the Spring wiring as a whole: the classes with several constructors
 * ({@code AppointmentsService}, {@code LoggingFilter}, {@code ReportHtmlGenerator}, the blob clients,
 * the schedulers, the seed loader …) each carry exactly one {@code @Autowired} constructor — a
 * mistake there only surfaces when the application starts, never in a unit test. / Einziger Test,
 * der die gesamte Verdrahtung beweist; ein fehlendes {@code @Autowired} fiele sonst erst beim Start
 * auf.
 *
 * <p><b>Hermetic environment / abgeschottete Umgebung:</b> {@link HermeticEnvironment} removes the
 * process environment and the {@code dotenv} source of {@code DotenvConfig} (a developer's
 * {@code server/.env}) before the context refreshes. The delivery state must follow from
 * {@code application.yml} alone — an exported {@code USAGE_INSIGHTS_ENABLED} or
 * {@code USAGE_STORAGE_ACCOUNT} on the build machine must neither fake a green result nor make the
 * context reach out to Azure. The only test properties are the Basic-Auth credentials (the shipped
 * default password is empty, i.e. random). / Nur {@code application.yml} zählt; einzige
 * Test-Properties sind die Zugangsdaten.
 *
 * <p><b>No network / kein Netz:</b> SoftNet is a mock; Log Analytics and the two REQ-038 blob
 * clients are spies around the real beans, which fail fast outside Azure (no managed identity, no
 * storage account). The spies record every call from the start on ({@link MockReset#NONE}); the
 * assertions compare deltas, so they do not depend on the test order. / Deltas statt Reihenfolge.
 */
class Req038DeliveryStateTest {

    static final String USER = "report-admin";
    static final String PASSWORD = "report-secret";

    /** Credentials only — the shipped {@code REPORT_ADMIN_PASSWORD} default is empty (random). */
    static final String CREDENTIALS_USER = "reporting.admin.user=" + USER;
    static final String CREDENTIALS_PASSWORD = "reporting.admin.password=" + PASSWORD;

    /** Operation id of the searched service (24 hex, passed through by {@code OperationIdMapper}). */
    static final String OPERATION_ID = "6908a1b2c3d4e5f6a7b8c9e2";

    /** Frontend-style trace headers of the search (REQ-029 access line) / Trace-Header. */
    static final String CORRELATION_ID = "0123456789abcdef-0007";
    static final String SESSION_ID = "0123456789abcdef";

    /** The REQ-037 access line with device (AC-34: byte-identical without {@code hits}). */
    static final Pattern SEARCH_LINE_WITHOUT_HITS = Pattern.compile(
        "\\{\"correlationId\":\"" + CORRELATION_ID + "\",\"sessionId\":\"" + SESSION_ID
            + "\",\"method\":\"POST\",\"url\":\"/api/v1/snaps\",\"statusCode\":200,\"duration\":\"\\d+ms\","
            + "\"device\":\"(mobile|tablet|desktop|unknown)\"}");

    /** The same line with the N1 field at the very end (§10.8) / dieselbe Zeile mit {@code hits}. */
    static final Pattern SEARCH_LINE_WITH_FEW_HITS = Pattern.compile(
        "\\{\"correlationId\":\"" + CORRELATION_ID + "\",\"sessionId\":\"" + SESSION_ID
            + "\",\"method\":\"POST\",\"url\":\"/api/v1/snaps\",\"statusCode\":200,\"duration\":\"\\d+ms\","
            + "\"device\":\"(mobile|tablet|desktop|unknown)\",\"hits\":\"few\"}");

    private static final Pattern SECTION = Pattern.compile("<section[^>]*?class=\"([^\" ]+)");
    private static final Pattern TOC_LINK = Pattern.compile("<a href=\"#([^\"]+)\">");
    private static final Pattern CLASS_ATTRIBUTE = Pattern.compile("class=\"([^\"]*)\"");
    private static final Pattern CSS_CLASS = Pattern.compile("\\.([a-zA-Z][\\w-]*)");

    /** The S6 sub-section of N1 — nested inside {@code section.fn}, not a section of its own. */
    private static final String DROPOFF_SUBSECTION = "fn-dropoff";

    /**
     * Classes the page emits on purpose without a rule of their own: {@code hd} is the REQ-030
     * header hook (its children are styled). / Bewusst regellose Klassen.
     */
    private static final Set<String> STRUCTURAL_CLASSES = Set.of("hd");

    private static final ObjectMapper JSON = new ObjectMapper();

    // ===========================================================================================
    // Environment / Umgebung
    // ===========================================================================================

    /**
     * Makes the context depend on {@code application.yml} only / der Kontext hängt nur von
     * {@code application.yml} ab: drops the process environment and the {@code dotenv} source of
     * {@code DotenvConfig}. Runs before the refresh, so conditions, {@code @Value} and
     * {@code @ConfigurationProperties} already see the shipped defaults. / Läuft vor dem Refresh.
     */
    static class HermeticEnvironment implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        /** Property source name of {@code DotenvConfig} / Name der Dotenv-Quelle. */
        static final String DOTENV_SOURCE = "dotenv";

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            MutablePropertySources sources = context.getEnvironment().getPropertySources();
            sources.remove(DOTENV_SOURCE);
            String system = StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;
            if (sources.contains(system)) {
                sources.replace(system, new SystemEnvironmentPropertySource(system, Map.of()));
            }
        }
    }

    /**
     * Delivery state: additionally no active profile at all — an exported
     * {@code SPRING_PROFILES_ACTIVE=e2e-seed} must not leak in. / Zusätzlich kein aktives Profil.
     */
    static final class DeliveryEnvironment extends HermeticEnvironment {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            super.initialize(context);
            context.getEnvironment().setActiveProfiles();
        }
    }

    // ===========================================================================================
    // TC-47 — delivery state / Auslieferungszustand
    // ===========================================================================================

    /**
     * TC-47 (AC-31, BR-29, §5.1 row 1): the shipped {@code application.yml} without any override —
     * every new switch off, neither job scheduler exists, the statistics tab shows S5 and S7 only,
     * both new JSON endpoints answer 404, a search writes no {@code hits}, a booking no
     * {@code USAGE_BOOKING}, and the nightly cron slots 22:30 and 03:45 reach neither Log Analytics
     * nor SoftNet nor Blob Storage. / Auslieferungszustand ohne jeden Override.
     */
    @Nested
    @SpringBootTest(classes = BackendApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {CREDENTIALS_USER, CREDENTIALS_PASSWORD})
    @ContextConfiguration(initializers = DeliveryEnvironment.class)
    @DisplayName("TC-47: delivery state — the real application.yml without any environment override")
    class DeliveryState {

        /** Calls recorded until the context was ready — captured before the first test body. */
        private static List<String> softnetCallsAtStart;
        private static List<String> insightsBlobCallsAtStart;
        private static List<String> capacityBlobCallsAtStart;

        @Autowired
        private WebApplicationContext context;

        @Autowired
        private Environment environment;

        @MockitoSpyBean(reset = MockReset.NONE)
        private com.autohaus.backend.reporting.LogAnalyticsClient logAnalyticsClient;

        @MockitoBean(reset = MockReset.NONE)
        private SoftnetApiClient softnetApi;

        @MockitoSpyBean(reset = MockReset.NONE)
        private UsageEventLogger usageEventLogger;

        @MockitoSpyBean(reset = MockReset.NONE)
        private InsightsBlobClient insightsBlobClient;

        @MockitoSpyBean(reset = MockReset.NONE)
        private CapacityBlobClient capacityBlobClient;

        private MockMvc mockMvc;
        private final List<ListAppender<ILoggingEvent>> appenders = new ArrayList<>();

        @BeforeEach
        void setUp() {
            if (softnetCallsAtStart == null) {
                softnetCallsAtStart = methodNames(invocations(softnetApi));
                insightsBlobCallsAtStart = methodNames(invocations(insightsBlobClient));
                capacityBlobCallsAtStart = methodNames(invocations(capacityBlobClient));
            }
            mockMvc = mockMvc(context);
        }

        @AfterEach
        void detachAppenders() {
            detach(appenders);
        }

        /** TC-47 / AC-31 / TC-40: every REQ-038 switch is {@code false}, collecting starts are empty. */
        @Test
        @DisplayName("TC-47: all new switches are false and the empty collecting starts bind to null")
        void shipsEveryNewSwitchOff() {
            for (String key : List.of("usage.insights.enabled", "usage.funnel.enabled", "usage.dropoff.enabled",
                "capacity.enabled", "capacity.show-slot-count")) {
                assertEquals(Boolean.FALSE, environment.getProperty(key, Boolean.class), key);
            }
            assertEquals(Boolean.TRUE, environment.getProperty("usage.enabled", Boolean.class),
                "REQ-037 stays on by default (§5.1 row 1)");
            assertEquals(0, environment.getActiveProfiles().length, "no profile, in particular no e2e-seed");

            UsageInsightsProperties insights = context.getBean(UsageInsightsProperties.class);
            UsageFunnelProperties funnel = context.getBean(UsageFunnelProperties.class);
            UsageDropoffProperties dropoff = context.getBean(UsageDropoffProperties.class);
            CapacityProperties capacity = context.getBean(CapacityProperties.class);
            assertFalse(insights.enabled());
            assertFalse(funnel.enabled());
            assertFalse(dropoff.enabled());
            assertFalse(capacity.enabled());
            assertFalse(capacity.showSlotCount());
            assertNull(insights.collectingSince(), "TC-40: empty USAGE_INSIGHTS_COLLECTING_SINCE → null");
            assertNull(funnel.collectingSince(), "TC-40: empty USAGE_FUNNEL_COLLECTING_SINCE → null");
            assertNull(dropoff.collectingSince(), "TC-40: empty USAGE_DROPOFF_COLLECTING_SINCE → null");
            assertEquals("0 30 22 * * *", capacity.scanCron());
            assertEquals("wiper-blades", capacity.referenceService());
            assertEquals("0 45 3 * * *", insights.rollupCron());
            assertEquals("", context.getBean(UsageStatsProperties.class).storage().account(),
                "no storage account — nothing can reach Blob Storage (E4)");
        }

        /** TC-47: neither job scheduler exists; the switch-independent beans do (AC-24, §14.1). */
        @Test
        @DisplayName("TC-47: no CapacityScanScheduler, no InsightsScheduler, no seed loader — retention job exists")
        void createsNeitherJobScheduler() {
            assertEquals(0, context.getBeanNamesForType(CapacityScanScheduler.class).length,
                "no capacity scan without CAPACITY_SCAN_ENABLED");
            assertEquals(0, context.getBeanNamesForType(InsightsScheduler.class).length,
                "no insights rollup without USAGE_INSIGHTS_ENABLED / USAGE_FUNNEL_ENABLED");
            assertEquals(0, context.getBeanNamesForType(StatisticsE2eSeedLoader.class).length,
                "the seed profile is off");
            assertEquals(1, context.getBeanNamesForType(InsightsRetentionJob.class).length,
                "AC-24: retention runs independently of the switches");
            for (Class<?> bean : List.of(InsightsStore.class, CapacityStore.class, CapacityProbe.class,
                InsightsAggregator.class, ServiceGroupClassifier.class, BriefingCalculator.class)) {
                assertEquals(1, context.getBeanNamesForType(bean).length, bean.getSimpleName());
            }
        }

        /**
         * TC-12 / capture agent: the multi-constructor beans are wired through their single
         * {@code @Autowired} constructor — the context start itself is the proof, the fields show
         * which constructor ran. / Der Kontextstart beweist die eindeutigen Konstruktoren.
         */
        @Test
        @DisplayName("TC-12: the @Autowired constructors are used (ReportHtmlGenerator 2-arg, AppointmentsService 6-arg)")
        void wiresTheAutowiredConstructors() {
            ReportHtmlGenerator generator = context.getBean(ReportHtmlGenerator.class);
            assertSame(context.getBean(StatisticsPageRenderer.class),
                ReflectionTestUtils.getField(generator, "statisticsRenderer"),
                "the 2-argument constructor receives the StatisticsPageRenderer bean");
            AppointmentsService appointments = context.getBean(AppointmentsService.class);
            assertNotNull(ReflectionTestUtils.getField(appointments, "usageEventLogger"),
                "the 6-argument constructor receives the UsageEventLogger");
            assertEquals(Boolean.FALSE, ReflectionTestUtils.getField(appointments, "usageInsightsEnabled"));
            assertEquals(Boolean.FALSE,
                ReflectionTestUtils.getField(context.getBean(LoggingFilter.class), "searchHitsEnabled"));
        }

        /** TC-47 / §5.1 row 1: navigation, S5 and S7 only — on both statistics paths. */
        @Test
        @DisplayName("TC-47: /statistics equals §5.1 row 1 — navigation, S5 and S7 only, no REQ-038 section")
        void rendersRowOneOfTheKillSwitchMatrix() throws Exception {
            for (String path : List.of("/internal/report", "/internal/report/statistics")) {
                String html = page(mockMvc, path, 200);
                assertEquals(List.of("us", "st-limits"), sections(html), path);
                assertEquals(List.of("us-h", "limits"), tocLinks(html), path + ": jump links = sections");
                assertTrue(html.contains("<nav class=\"tabs\""), path + ": the statistics tab exists");
                assertTrue(html.contains("aria-current=\"page\">Statistik</a>"), path);
                // Markup only — the page stylesheet names every class, too.
                String markup = main(html);
                for (String absent : List.of("class=\"ib\"", "class=\"cap\"", "class=\"rk\"", "class=\"sg\"",
                    "class=\"tm\"", "class=\"fn\"", "class=\"st-empty", "class=\"st-warn", "class=\"ai\"")) {
                    assertFalse(markup.contains(absent), path + " must not contain " + absent);
                }
            }
            String bugs = page(mockMvc, "/internal/report/bugs", 200);
            assertTrue(bugs.contains("aria-current=\"page\">Bugs</a>"));
            assertTrue(bugs.contains("Es liegt noch kein Report vor."), "no LLM secrets → the REQ-030 hint");
            assertFalse(bugs.contains("class=\"us\""), "BR-3: no statistics on the bugs tab");
        }

        /** TC-47 / AC-25: {@code insights.json} and {@code capacity.json} 404, {@code usage.json} unchanged. */
        @Test
        @DisplayName("TC-47: insights.json and capacity.json answer 404, usage.json 200")
        void answersTheNewJsonEndpointsWith404() throws Exception {
            mockMvc.perform(get("/internal/report/insights.json").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isNotFound());
            mockMvc.perform(get("/internal/report/capacity.json").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isNotFound());
            mockMvc.perform(get("/internal/report/usage.json").with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk());
            mockMvc.perform(get("/internal/report/insights.json")).andExpect(status().isUnauthorized());
        }

        /**
         * TC-47 / AC-13 / AC-34 / BR-29: a successful search keeps the REQ-037 access line
         * byte-identical (no {@code hits}), a successful booking writes no {@code USAGE_BOOKING}, and
         * SoftNet sees exactly the customer's own calls. / Suche ohne {@code hits}, Buchung ohne
         * {@code USAGE_BOOKING}.
         */
        @Test
        @DisplayName("TC-47: search access line without hits, booking without USAGE_BOOKING, no REQ-038 SoftNet call")
        void leavesNoReq038TraceOfASearchAndABooking() throws Exception {
            BranchSetup branch = firstBranch(context);
            stubSearchAndBooking(softnetApi, branch);
            int softnetBefore = invocations(softnetApi).size();
            ListAppender<ILoggingEvent> access = attach(appenders, "HTTP");
            ListAppender<ILoggingEvent> all = attach(appenders, org.slf4j.Logger.ROOT_LOGGER_NAME);

            String token = context.getBean(SessionTokenService.class).generateToken().token();
            search(mockMvc, token, branch);
            book(mockMvc, token, branch);

            String searchLine = searchLine(access);
            assertTrue(SEARCH_LINE_WITHOUT_HITS.matcher(searchLine).matches(),
                "AC-34: byte-identical to ACCESS_PATTERN_WITH_DEVICE, got " + searchLine);
            assertFalse(searchLine.contains("hits"));
            assertTrue(messages(all).stream().anyMatch(m -> m.startsWith("BOOKING_CREATED")),
                "the booking really succeeded");
            assertEquals(0, usageEventLogger.flush(), "AC-13: nothing buffered — even the daily flush writes nothing");
            assertTrue(messages(all).stream().noneMatch(m -> m.contains(UsageEventLogger.MARKER)),
                "AC-13: no USAGE_BOOKING line while usage.insights.enabled=false");
            verify(usageEventLogger, never()).logBooking(any(), any(), any(), anyBoolean());

            List<String> softnetCalls = methodNames(invocations(softnetApi).subList(softnetBefore,
                invocations(softnetApi).size()));
            assertEquals(1, softnetCalls.stream().filter("getSnaps"::equals).count(),
                "only the customer's search — never a scan: " + softnetCalls);
            assertFalse(softnetCalls.contains("getGroups"), "the scan's getGroups() never runs");
            assertNoReq038Query(logAnalyticsClient);
        }

        /**
         * TC-47: the context starts without any backend call of REQ-038, and the nightly slots 22:30
         * (scan) and 03:45 (insights rollup / retention) are simulated by running exactly the
         * scheduled tasks registered for them. / Start und simulierte Cron-Zeitpunkte ohne Aufruf.
         */
        @Test
        @DisplayName("TC-47: start and the simulated crons 22:30 / 03:45 call neither Log Analytics (REQ-038) nor SoftNet nor Blob")
        void runsNoJobAtTheNightlySlots() {
            assertEquals(List.of(), softnetCallsAtStart, "no SoftNet call during the start");
            assertTrue(Set.of("isConfigured").containsAll(insightsBlobCallsAtStart),
                "the start only asks whether storage is configured (retention job): " + insightsBlobCallsAtStart);
            assertTrue(Set.of("isConfigured").containsAll(capacityBlobCallsAtStart),
                "nobody reads capacity-daily.json: " + capacityBlobCallsAtStart);

            List<ScheduledTask> tasks = scheduledTasks(context);
            assertTrue(tasks.stream().noneMatch(t -> target(t).contains("CapacityScanScheduler")
                || target(t).contains("InsightsScheduler")), "no job scheduler task: " + describe(tasks));
            String scanCron = environment.getProperty("capacity.scan-cron");
            String rollupCron = environment.getProperty("usage.insights.rollup-cron");
            assertTrue(tasks.stream().noneMatch(t -> scanCron.equals(cron(t))), "nothing is due at 22:30");
            List<ScheduledTask> at0345 = tasks.stream().filter(t -> rollupCron.equals(cron(t))).toList();
            assertEquals(1, at0345.size(), "only the retention job is due at 03:45: " + describe(tasks));
            assertTrue(target(at0345.getFirst()).contains("InsightsRetentionJob"), describe(at0345));

            int softnetBefore = invocations(softnetApi).size();
            int queriesBefore = queries(logAnalyticsClient).size();
            at0345.forEach(task -> task.getTask().getRunnable().run());

            assertEquals(softnetBefore, invocations(softnetApi).size(), "no SoftNet call at 03:45");
            assertEquals(queriesBefore, queries(logAnalyticsClient).size(), "no KQL at 03:45");
            verify(insightsBlobClient, never()).load();
            verify(insightsBlobClient, never()).save(any(), any());
            verify(capacityBlobClient, never()).load();
            verify(capacityBlobClient, never()).save(any(), any());
            assertNoReq038Query(logAnalyticsClient);
        }
    }

    // ===========================================================================================
    // e2e-seed profile / Seed-Profil
    // ===========================================================================================

    /**
     * The E2E configuration of {@code playwright-backend.config.ts} / {@code scripts/test-local.sh}
     * (§14.2): profile {@code e2e-seed}, every switch on, both crons disabled with {@code "-"}. The
     * property names are the environment variables the E2E run exports — {@code application.yml}
     * resolves them as placeholders. The context starts without network (SoftNet mocked, Log
     * Analytics and Blob fail fast), seeds the stores and renders every section with the real
     * renderers. / Wie der E2E-Lauf, ohne Netz; alle Sektionen mit den echten Renderern.
     */
    @Nested
    @SpringBootTest(classes = BackendApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {CREDENTIALS_USER, CREDENTIALS_PASSWORD,
            "USAGE_INSIGHTS_ENABLED=true", "USAGE_FUNNEL_ENABLED=true", "USAGE_DROPOFF_ENABLED=true",
            "CAPACITY_SCAN_ENABLED=true", "CAPACITY_SCAN_CRON=-", "USAGE_INSIGHTS_ROLLUP_CRON=-",
            "e2e.seed.set=full"})
    @ActiveProfiles(StatisticsE2eSeedLoader.PROFILE)
    @ContextConfiguration(initializers = HermeticEnvironment.class)
    @DisplayName("e2e-seed: the E2E configuration starts offline, seeds the stores and renders S0–S7")
    class SeededStatisticsTab {

        private static List<String> softnetCallsAtStart;

        @Autowired
        private WebApplicationContext context;

        @Autowired
        private InsightsStore insightsStore;

        @Autowired
        private CapacityStore capacityStore;

        @MockitoSpyBean(reset = MockReset.NONE)
        private com.autohaus.backend.reporting.LogAnalyticsClient logAnalyticsClient;

        @MockitoBean(reset = MockReset.NONE)
        private SoftnetApiClient softnetApi;

        private MockMvc mockMvc;
        private final List<ListAppender<ILoggingEvent>> appenders = new ArrayList<>();

        @BeforeEach
        void setUp() {
            if (softnetCallsAtStart == null) {
                softnetCallsAtStart = methodNames(invocations(softnetApi));
            }
            mockMvc = mockMvc(context);
        }

        @AfterEach
        void detachAppenders() {
            detach(appenders);
        }

        /** §14.1: the seed is loaded into both stores with the real switches (BR-4). */
        @Test
        @DisplayName("e2e-seed: both stores hold the seeded views — no 'Historie derzeit nicht verfügbar'")
        void seedsBothStores() {
            assertEquals(1, context.getBeanNamesForType(StatisticsE2eSeedLoader.class).length);
            InsightsViewModel insights = insightsStore.current();
            assertNotNull(insights, "insights seeded");
            assertTrue(insights.bookingsEnabled());
            assertTrue(insights.funnelEnabled());
            assertNotNull(insights.funnel(), "S6 seeded");
            assertNotNull(insights.funnel().dropoff(), "N1: drop-off view seeded");
            assertFalse(insights.warnings().contains(InsightsViewModel.WARN_HISTORY_UNAVAILABLE));
            assertNotNull(capacityStore.current(), "capacity seeded");
            assertNotNull(capacityStore.current().scanDay(), "a seeded scan is shown");
            assertEquals(List.of(), softnetCallsAtStart, "the start calls SoftNet never (no scan, no classification)");
            assertNoReq038Query(logAnalyticsClient);
        }

        /** The E2E switches create both schedulers, but {@code "-"} registers neither cron (AC-12). */
        @Test
        @DisplayName("e2e-seed: both job schedulers exist, but '-' disables the scan and the rollup cron")
        void disablesBothCrons() {
            assertEquals(1, context.getBeanNamesForType(CapacityScanScheduler.class).length);
            assertEquals(1, context.getBeanNamesForType(InsightsScheduler.class).length);
            assertEquals(Boolean.TRUE,
                ReflectionTestUtils.getField(context.getBean(LoggingFilter.class), "searchHitsEnabled"),
                "LoggingFilter wired through its 3-argument @Autowired constructor");
            List<ScheduledTask> tasks = scheduledTasks(context);
            // The fixed 00:05 rebuilds stay registered (Step 5) — they also pin the name format.
            for (String rebuild : List.of("CapacityScanScheduler.rebuildViewModel",
                "InsightsScheduler.rebuildViewModel")) {
                assertTrue(tasks.stream().anyMatch(t -> target(t).endsWith(rebuild)
                    && "0 5 0 * * *".equals(cron(t))), rebuild + " at 00:05: " + describe(tasks));
            }
            assertTrue(tasks.stream().noneMatch(t -> target(t).endsWith(".runScan")
                || target(t).endsWith(".rollUp") || target(t).endsWith(".scheduledRun")),
                "'-' registers neither the scan, the rollup nor the retention run at the rollup cron: "
                    + describe(tasks));
            // Deliberately independent of '-': the non-disableable retention run (NFR-Datenschutz-7,
            // a no-op without storage) and the daily booking flush (an empty buffer writes nothing).
            assertTrue(tasks.stream().anyMatch(t -> target(t).endsWith("InsightsRetentionJob.dailyRun")
                && InsightsRetentionJob.DAILY_CRON.equals(cron(t))),
                "retention dailyRun at " + InsightsRetentionJob.DAILY_CRON + ": " + describe(tasks));
            assertTrue(tasks.stream().anyMatch(t -> target(t).endsWith("UsageEventLogger.scheduledFlush")
                && UsageEventLogger.FLUSH_CRON.equals(cron(t))),
                "booking flush at " + UsageEventLogger.FLUSH_CRON + ": " + describe(tasks));
        }

        /** TC-41 precondition: every section S0–S7 on the default tab, never the bugs sections. */
        @Test
        @DisplayName("e2e-seed: /internal/report shows .ib .cap .rk .sg .tm .us .fn .st-limits, no .ai; /bugs no .rk")
        void rendersEverySectionOnTheDefaultTab() throws Exception {
            String html = page(mockMvc, "/internal/report", 200);
            assertEquals(List.of("ib", "cap", "rk", "sg", "ev", "tm", "us", "fn", "st-limits"), sections(html));
            assertEquals(List.of("briefing", "capacity", "branches", "service-groups", "entry-variant", "timing",
                "us-h", "funnel", "limits"), tocLinks(html), "AC-4: jump links = rendered sections");
            assertTrue(html.contains("<section class=\"" + DROPOFF_SUBSECTION + "\""), "N1 sub-section");
            assertFalse(html.contains("class=\"ai\""), "BR-3: no AI summary on the statistics tab");
            // S5 (REQ-037) names its own missing blob with the same words — only S0–S4, S6, S7 count.
            assertFalse(withoutUsageSection(main(html)).contains(InsightsViewModel.WARN_HISTORY_UNAVAILABLE),
                "the REQ-038 sections are seeded, not unavailable");
            assertFalse(html.contains("<script"), "BR-2: no script");

            String bugs = page(mockMvc, "/internal/report/bugs", 200);
            assertFalse(bugs.contains("class=\"rk\""), "BR-3: no ranking on the bugs tab");
            assertFalse(bugs.contains("class=\"st-toc\""), "no jump links on the bugs tab");
        }

        /**
         * TC-12 / TC-38 cross-check: every class the seeded statistics tab and the 404 page emit has
         * a rule in {@code ReportHtmlGenerator.css()} — structural hooks excepted. / Jede Klasse hat
         * eine CSS-Regel.
         */
        @Test
        @DisplayName("TC-12/TC-38: every emitted class of the seeded tab and the 404 page has a CSS rule")
        void emitsOnlyClassesWithACssRule() throws Exception {
            for (String path : List.of("/internal/report", "/internal/report/xyz")) {
                String html = page(mockMvc, path, path.endsWith("xyz") ? 404 : 200);
                Set<String> missing = classesWithoutRule(html);
                missing.removeAll(STRUCTURAL_CLASSES);
                assertTrue(missing.isEmpty(), path + ": classes without a CSS rule " + missing);
            }
        }

        /** AC-25 / TC-44: both JSON views answer 200 with the seeded content. */
        @Test
        @DisplayName("e2e-seed: insights.json and capacity.json answer 200 with the seeded views")
        void servesTheSeededJsonViews() throws Exception {
            JsonNode insights = json(mockMvc, "/internal/report/insights.json");
            assertTrue(insights.path("bookingsEnabled").asBoolean());
            assertTrue(insights.path("funnelEnabled").asBoolean());
            assertFalse(insights.path("funnel").path("dropoff").isNull(), "N1: dropoff in insights.json");
            assertTrue(insights.has("briefing"));
            JsonNode capacity = json(mockMvc, "/internal/report/capacity.json");
            assertFalse(capacity.path("scanDay").isNull());
            assertTrue(capacity.path("rows").size() > 0);
        }

        /**
         * TC-61 / AC-13 in the real context: with all three usage switches the search line ends with
         * {@code "hits":"few"} (2 days ≥ {@code fromDate}); a booking writes <b>no</b>
         * {@code USAGE_BOOKING} line in its request, the daily flush then exactly one — under MDC
         * {@code job.usagebooking}, with the Berlin booking day (§10.2, decoupling). / Mit allen
         * Schaltern: {@code hits}; keine Zeile im Buchungs-Request, genau eine nach dem Flush.
         */
        @Test
        @DisplayName("TC-61/AC-13: hits=few on the search line; the booking's USAGE_BOOKING only after the flush, under job.usagebooking")
        void writesHitsAndUsageBookingWithEverySwitchOn() throws Exception {
            BranchSetup branch = firstBranch(context);
            stubSearchAndBooking(softnetApi, branch);
            ListAppender<ILoggingEvent> access = attach(appenders, "HTTP");
            ListAppender<ILoggingEvent> all = attach(appenders, org.slf4j.Logger.ROOT_LOGGER_NAME);
            UsageEventLogger usageEventLogger = context.getBean(UsageEventLogger.class);
            ZoneId zone = ZoneId.of(context.getBean(UsageStatsProperties.class).zone());
            usageEventLogger.flush();   // start from an empty buffer / leerer Puffer
            LocalDate dayBefore = LocalDate.now(zone);

            String token = context.getBean(SessionTokenService.class).generateToken().token();
            search(mockMvc, token, branch);
            book(mockMvc, token, branch);

            String searchLine = searchLine(access);
            assertTrue(SEARCH_LINE_WITH_FEW_HITS.matcher(searchLine).matches(), "§10.8, got " + searchLine);
            assertTrue(messages(all).stream().anyMatch(m -> m.startsWith("BOOKING_CREATED")),
                "the booking really succeeded");
            assertTrue(messages(all).stream().noneMatch(m -> m.startsWith(UsageEventLogger.MARKER + " ")),
                "decoupled: no USAGE_BOOKING line in the booking request: " + messages(all));

            // The daily flush (23:59:59, scheduler thread) — MDC captured at logging time.
            ListAppender<ILoggingEvent> flushLog = new ListAppender<>() {
                @Override
                protected void append(ILoggingEvent event) {
                    event.prepareForDeferredProcessing();
                    super.append(event);
                }
            };
            flushLog.start();
            ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).addAppender(flushLog);
            appenders.add(flushLog);
            assertEquals(1, usageEventLogger.flush());

            List<ILoggingEvent> bookingLines = flushLog.list.stream()
                .filter(e -> e.getFormattedMessage().startsWith(UsageEventLogger.MARKER + " ")).toList();
            assertEquals(1, bookingLines.size(), "exactly one USAGE_BOOKING line after the flush");
            ILoggingEvent line = bookingLines.getFirst();
            assertEquals(UsageEventLogger.TRACE_ID, line.getMDCPropertyMap().get(RequestIdFilter.TRACE_ID_MDC_KEY),
                "the line carries the job marker, not the trace id of the booking request");
            JsonNode event = JSON.readTree(line.getFormattedMessage().substring(UsageEventLogger.MARKER.length() + 1));
            assertEquals(2, event.path("v").asInt());
            assertTrue(List.of(dayBefore.toString(), LocalDate.now(zone).toString()).contains(event.path("day").asText()),
                "the Berlin booking day (midnight-safe): " + event);
            assertEquals(branch.branchId(), event.path("branch").asText());
            assertEquals(OPERATION_ID, event.path("ops").path(0).asText());
            assertNoReq038Query(logAnalyticsClient);
        }
    }

    // ===========================================================================================
    // Shared helpers / gemeinsame Hilfsmethoden
    // ===========================================================================================

    /**
     * MockMvc over the real context with the application's own servlet filters (REQ-029/037 access
     * line, rate limit, origin, session token) and the real security chain / MockMvc mit den echten
     * Filtern und der echten Security-Chain.
     */
    static MockMvc mockMvc(WebApplicationContext context) {
        Filter[] filters = List.of(RequestIdFilter.class, LoggingFilter.class, RateLimitFilter.class,
            OriginFilter.class, SessionTokenFilter.class).stream()
            .map(type -> (Filter) context.getBean(type))
            .toArray(Filter[]::new);
        return MockMvcBuilders.webAppContextSetup(context).addFilters(filters).apply(springSecurity()).build();
    }

    static String page(MockMvc mvc, String path, int expectedStatus) throws Exception {
        return mvc.perform(get(path).with(httpBasic(USER, PASSWORD)))
            .andExpect(status().is(expectedStatus))
            .andReturn().getResponse().getContentAsString();
    }

    static JsonNode json(MockMvc mvc, String path) throws Exception {
        return JSON.readTree(mvc.perform(get(path).with(httpBasic(USER, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
    }

    /** First-level section classes inside {@code main} — the N1 sub-section of S6 excluded. */
    static List<String> sections(String html) {
        List<String> sections = new ArrayList<>();
        Matcher matcher = SECTION.matcher(main(html));
        while (matcher.find()) {
            if (!DROPOFF_SUBSECTION.equals(matcher.group(1))) {
                sections.add(matcher.group(1));
            }
        }
        return sections;
    }

    static List<String> tocLinks(String html) {
        String main = main(html);
        int start = main.indexOf("<nav class=\"st-toc\"");
        if (start < 0) {
            return List.of();
        }
        List<String> links = new ArrayList<>();
        Matcher matcher = TOC_LINK.matcher(main.substring(start, main.indexOf("</nav>", start)));
        while (matcher.find()) {
            links.add(matcher.group(1));
        }
        return links;
    }

    /** The content of {@code main#content} — markup only, without the page stylesheet. */
    static String main(String html) {
        int start = html.indexOf("<main id=\"content\">");
        int end = html.indexOf("</main>");
        return start < 0 || end < start ? "" : html.substring(start, end);
    }

    /** {@code markup} without the REQ-037 section S5 (it has no nested section) / ohne S5. */
    static String withoutUsageSection(String markup) {
        int start = markup.indexOf("<section class=\"us\"");
        if (start < 0) {
            return markup;
        }
        int end = markup.indexOf("</section>", start);
        return markup.substring(0, start) + markup.substring(end + "</section>".length());
    }

    /** Classes used in the document (outside the page stylesheet) without any selector in it. */
    static Set<String> classesWithoutRule(String html) {
        int styleStart = html.indexOf("<style>");
        int styleEnd = html.indexOf("</style>");
        Set<String> defined = new TreeSet<>();
        Matcher css = CSS_CLASS.matcher(html.substring(styleStart, styleEnd));
        while (css.find()) {
            defined.add(css.group(1));
        }
        Set<String> missing = new TreeSet<>();
        Matcher used = CLASS_ATTRIBUTE.matcher(html.substring(styleEnd));
        while (used.find()) {
            for (String name : used.group(1).trim().split("\\s+")) {
                if (!name.isEmpty() && !defined.contains(name)) {
                    missing.add(name);
                }
            }
        }
        return missing;
    }

    // ---- search and booking / Suche und Buchung ----

    static BranchSetup firstBranch(ApplicationContext context) {
        return context.getBean(SetupService.class).getAllBranches().getFirst();
    }

    /**
     * Two free slots on two different days from today on (UTC dates, like the frontend filter), and
     * a confirmed appointment for the first one. / Zwei freie Termine an zwei Tagen, eine Buchung.
     */
    static void stubSearchAndBooking(SoftnetApiClient softnetApi, BranchSetup branch) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        when(softnetApi.getSnaps(any(), anyString())).thenReturn(List.of(
            snap("tc47-snap-1", today.plusDays(3) + "T07:30:00Z", branch.branchId()),
            snap("tc47-snap-2", today.plusDays(6) + "T12:00:00Z", branch.branchId())));
        ObjectNode appointment = JSON.createObjectNode();
        appointment.put("id", "tc47-appointment");
        appointment.put("status", "confirmed");
        appointment.put("checkInTime", today.plusDays(3) + "T07:30:00Z");
        when(softnetApi.createAppointment(any(), anyString())).thenReturn(appointment);
    }

    private static JsonNode snap(String internal, String checkInTime, String branchId) {
        ObjectNode snap = JSON.createObjectNode();
        snap.put("internal", internal);
        snap.put("checkInTime", checkInTime);
        snap.putObject("branch").put("id", branchId).put("branchLabel", "Testbetrieb");
        ObjectNode wishlist = snap.putObject("wishlist");
        wishlist.putArray("operations").add(OPERATION_ID);
        wishlist.putObject("car").put("brand", "Volkswagen");
        return snap;
    }

    /** {@code POST /api/v1/snaps} as the frontend sends it (§10.8: fromDate = today). */
    static void search(MockMvc mvc, String token, BranchSetup branch) throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String body = "{\"branchId\":\"" + branch.branchId() + "\",\"jobTypeIds\":[\"" + OPERATION_ID + "\"],"
            + "\"fromDate\":\"" + today + "\",\"toDate\":\"" + today.plusDays(28) + "\","
            + "\"mode\":\"next-available\",\"brandName\":\"Volkswagen\"}";
        mvc.perform(post("/api/v1/snaps").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Session-Token", token)
                .header("X-Correlation-Id", CORRELATION_ID)
                .header("X-Session-Id", SESSION_ID))
            .andExpect(status().isOk());
    }

    /** {@code POST /api/v1/appointments} for the first slot of {@link #stubSearchAndBooking}. */
    static void book(MockMvc mvc, String token, BranchSetup branch) throws Exception {
        String body = "{\"snapId\":\"tc47-snap-1\",\"branchId\":\"" + branch.branchId() + "\","
            + "\"customer\":{\"email\":\"max.mustermann@example.com\",\"firstName\":\"Max\","
            + "\"lastName\":\"Mustermann\",\"mobile\":\"+49 170 1234567\",\"address\":\"Musterstraße 1\","
            + "\"zipCode\":\"12345\",\"city\":\"Musterstadt\"},"
            + "\"vehicle\":{\"licensePlate\":\"B-XY 123\",\"mileage\":12000}}";
        mvc.perform(post("/api/v1/appointments").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Session-Token", token)
                .header("X-Correlation-Id", "0123456789abcdef-0008")
                .header("X-Session-Id", SESSION_ID))
            .andExpect(status().isOk());
    }

    /** The access line of the search / die Access-Zeile der Suche. */
    static String searchLine(ListAppender<ILoggingEvent> access) {
        List<String> lines = messages(access).stream().filter(m -> m.contains("\"url\":\"/api/v1/snaps\"")).toList();
        assertEquals(1, lines.size(), "exactly one access line for the search: " + lines);
        return lines.getFirst();
    }

    // ---- logging / Logging ----

    static ListAppender<ILoggingEvent> attach(List<ListAppender<ILoggingEvent>> registry, String loggerName) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(loggerName)).addAppender(appender);
        registry.add(appender);
        return appender;
    }

    static void detach(List<ListAppender<ILoggingEvent>> registry) {
        for (ListAppender<ILoggingEvent> appender : registry) {
            ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(appender);
            ((Logger) LoggerFactory.getLogger("HTTP")).detachAppender(appender);
            appender.stop();
        }
        registry.clear();
    }

    static List<String> messages(ListAppender<ILoggingEvent> appender) {
        synchronized (appender.list) {
            return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        }
    }

    // ---- mocks and scheduled tasks / Mocks und geplante Tasks ----

    static List<Invocation> invocations(Object mockOrSpy) {
        Collection<Invocation> all = Mockito.mockingDetails(mockOrSpy).getInvocations();
        return new ArrayList<>(all);
    }

    static List<String> methodNames(List<Invocation> invocations) {
        return invocations.stream().map(i -> i.getMethod().getName()).toList();
    }

    /** Every KQL text the Log Analytics spy received so far / alle bisher gesendeten KQL-Texte. */
    static List<String> queries(com.autohaus.backend.reporting.LogAnalyticsClient spy) {
        return invocations(spy).stream()
            .filter(i -> "query".equals(i.getMethod().getName()))
            .map(i -> String.valueOf(i.getArguments()[0]))
            .toList();
    }

    /**
     * No query of REQ-038 (KQL 1, 2, 3a, 3b) was ever sent — recognised by markers that the
     * constants themselves are checked to contain, so the test follows query changes. / Keine
     * REQ-038-Abfrage; die Marker werden an den Konstanten selbst geprüft.
     */
    static void assertNoReq038Query(com.autohaus.backend.reporting.LogAnalyticsClient spy) {
        Map<String, String> markers = Map.of(
            "USAGE_BOOKING", InsightsAggregator.BOOKINGS_KQL,
            "/operations", InsightsAggregator.SERVICE_VIEWS_KQL,
            "MaxStep", InsightsAggregator.FUNNEL_KQL,
            "Scenario", InsightsAggregator.DROPOFF_KQL);
        markers.forEach((marker, constant) -> assertTrue(constant.contains(marker), "marker " + marker));
        for (String query : queries(spy)) {
            for (String marker : markers.keySet()) {
                assertFalse(query.contains(marker), "REQ-038 query sent (" + marker + "): " + query);
            }
        }
    }

    static List<ScheduledTask> scheduledTasks(ApplicationContext context) {
        List<ScheduledTask> tasks = new ArrayList<>();
        for (ScheduledTaskHolder holder : context.getBeansOfType(ScheduledTaskHolder.class).values()) {
            tasks.addAll(holder.getScheduledTasks());
        }
        return tasks;
    }

    /** Qualified name of the scheduled method, e.g. {@code …InsightsRetentionJob.scheduledRun}. */
    static String target(ScheduledTask task) {
        return String.valueOf(task.getTask().getRunnable());
    }

    static String cron(ScheduledTask task) {
        return task.getTask() instanceof CronTask cronTask ? cronTask.getExpression() : null;
    }

    static String describe(List<ScheduledTask> tasks) {
        return tasks.stream().map(t -> target(t) + " [" + cron(t) + "]").toList().toString();
    }
}
