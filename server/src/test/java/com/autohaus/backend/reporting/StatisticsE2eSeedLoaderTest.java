package com.autohaus.backend.reporting;

import com.autohaus.backend.capacity.CapacityBlobClient;
import com.autohaus.backend.capacity.CapacityHistory;
import com.autohaus.backend.capacity.CapacityScan;
import com.autohaus.backend.capacity.CapacityStore;
import com.autohaus.backend.capacity.CapacityViewModel;
import com.autohaus.backend.capacity.ProbeResult;
import com.autohaus.backend.capacity.ProbeStatus;
import com.autohaus.backend.capacity.ScanState;
import com.autohaus.backend.config.CapacityProperties;
import com.autohaus.backend.config.UsageDropoffProperties;
import com.autohaus.backend.config.UsageFunnelProperties;
import com.autohaus.backend.config.UsageInsightsProperties;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.config.setup.BranchSetup;
import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.usage.UsageStatsStore;
import com.autohaus.backend.usage.insights.BranchCounts;
import com.autohaus.backend.usage.insights.InsightsBlobClient;
import com.autohaus.backend.usage.insights.InsightsDay;
import com.autohaus.backend.usage.insights.InsightsHistory;
import com.autohaus.backend.usage.insights.InsightsStore;
import com.autohaus.backend.usage.insights.InsightsViewModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The E2E seed profile (REQ-038 §14.1, §14.2; prerequisite of TC-41–TC-44) / das E2E-Seed-Profil:
 * relative date tokens, the start guards, the store handling and the checked-in fixtures.
 *
 * <p>All tests pin „today" to Thursday 24.09.2026 (Europe/Berlin) unless a test needs a month,
 * year or week boundary. The fixture tests read the real files below
 * {@code playwright/fixtures/req038} through the shared production parsers. / Fester Clock; die
 * Fixture-Tests lesen die echten Dateien über die produktiven Parser.
 */
class StatisticsE2eSeedLoaderTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-24T07:00:00Z"), BERLIN);
    private static final String BRANCH_A = "68f603148d2bb4389ec92d75";
    private static final String BRANCH_B = "68f604138d2bb4389ecbacaf";
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    private final InsightsStore insightsStore = new InsightsStore();
    private final CapacityStore capacityStore = new CapacityStore();

    // ---- date tokens / Datums-Tokens ----

    @Test
    @DisplayName("§14.1: @day tokens resolve in keys, values and inside timestamps")
    void resolvesDayTokensInKeysValuesAndTimestamps() {
        String json = "{\"@day-1\":{\"at\":\"@day-1T20:31:12Z\"},\"today\":\"@day\",\"next\":\"@day+2\","
            + "\"range\":\"@day-10/@day-3\"}";

        String resolved = StatisticsE2eSeedLoader.resolveTokens(json, TODAY);

        assertEquals("{\"2026-09-23\":{\"at\":\"2026-09-23T20:31:12Z\"},\"today\":\"2026-09-24\","
            + "\"next\":\"2026-09-26\",\"range\":\"2026-09-14/2026-09-21\"}", resolved);
    }

    @Test
    @DisplayName("§14.1: @month/@year tokens cross the year boundary (today 04.01.2027)")
    void resolvesMonthAndYearTokensAcrossTheYearBoundary() {
        LocalDate monday = LocalDate.of(2027, 1, 4);

        assertEquals("2027-01", StatisticsE2eSeedLoader.resolveTokens("@month", monday));
        assertEquals("2026-12", StatisticsE2eSeedLoader.resolveTokens("@month-1", monday));
        assertEquals("2025-12", StatisticsE2eSeedLoader.resolveTokens("@month-13", monday));
        assertEquals("2027-02", StatisticsE2eSeedLoader.resolveTokens("@month+1", monday));
        assertEquals("2027", StatisticsE2eSeedLoader.resolveTokens("@year", monday));
        assertEquals("2026", StatisticsE2eSeedLoader.resolveTokens("@year-1", monday));
        assertEquals("2026-12-31", StatisticsE2eSeedLoader.resolveTokens("@day-4", monday));
    }

    @Test
    @DisplayName("§14.1: @month and @day tokens across a month end (today 31.03.2026)")
    void resolvesMonthTokensAtTheEndOfAMonth() {
        LocalDate endOfMarch = LocalDate.of(2026, 3, 31);

        assertEquals("2026-02", StatisticsE2eSeedLoader.resolveTokens("@month-1", endOfMarch));
        assertEquals("2026-02-28", StatisticsE2eSeedLoader.resolveTokens("@day-31", endOfMarch));
        assertEquals("2026-04-01", StatisticsE2eSeedLoader.resolveTokens("@day+1", endOfMarch));
    }

    @Test
    @DisplayName("§14.1 / BR-26: @wd tokens count Mo–Sa workdays and anchor on the next workday")
    void workdayTokensSkipSundays() {
        LocalDate sunday = LocalDate.of(2026, 11, 29);
        LocalDate monday = LocalDate.of(2026, 11, 30);

        // Sunday: index 0 is tomorrow's Monday, index -1 yesterday's Saturday (§5.7 scan → target).
        assertEquals("2026-11-30", StatisticsE2eSeedLoader.resolveTokens("@wd-0", sunday));
        assertEquals("2026-11-28", StatisticsE2eSeedLoader.resolveTokens("@wd-1", sunday));
        assertEquals("2026-11-27", StatisticsE2eSeedLoader.resolveTokens("@wd-2", sunday));
        assertEquals("2026-12-01", StatisticsE2eSeedLoader.resolveTokens("@wd+1", sunday));
        // Monday: index 0 is today, index -1 skips the Sunday, index -7 lands on the Saturday before.
        assertEquals("2026-11-30", StatisticsE2eSeedLoader.resolveTokens("@wd", monday));
        assertEquals("2026-11-28", StatisticsE2eSeedLoader.resolveTokens("@wd-1", monday));
        assertEquals("2026-11-21", StatisticsE2eSeedLoader.resolveTokens("@wd-7", monday));
        // Saturday + 1 workday = Monday.
        assertEquals("2026-11-30",
            StatisticsE2eSeedLoader.workday(LocalDate.of(2026, 11, 28), 1).toString());
    }

    @Test
    @DisplayName("§14.1: @wk tokens address a weekday of a relative ISO week")
    void weekTokensAddressAWeekdayOfARelativeIsoWeek() {
        assertEquals("2026-09-21", StatisticsE2eSeedLoader.resolveTokens("@wk-0-MO", TODAY));
        assertEquals("2026-09-27", StatisticsE2eSeedLoader.resolveTokens("@wk-0-SU", TODAY));
        assertEquals("2026-09-20", StatisticsE2eSeedLoader.resolveTokens("@wk-1-SU", TODAY));
        assertEquals("2026-09-29", StatisticsE2eSeedLoader.resolveTokens("@wk+1-TU", TODAY));
        assertEquals("2026-06-22", StatisticsE2eSeedLoader.resolveTokens("@wk-13-MO", TODAY));
        // Across the year boundary: Monday 04.01.2027, one week back is Monday 28.12.2026.
        assertEquals("2026-12-28",
            StatisticsE2eSeedLoader.resolveTokens("@wk-1-MO", LocalDate.of(2027, 1, 4)));
    }

    @Test
    @DisplayName("§14.1: a malformed token stops the start instead of producing a broken date")
    void rejectsMalformedTokens() {
        assertThrows(IllegalStateException.class,
            () -> StatisticsE2eSeedLoader.resolveTokens("{\"d\":\"@wk3-MO\"}", TODAY));
        assertThrows(IllegalStateException.class,
            () -> StatisticsE2eSeedLoader.resolveTokens("{\"d\":\"@wk-1-XX\"}", TODAY));
        assertEquals("no tokens here", StatisticsE2eSeedLoader.resolveTokens("no tokens here", TODAY));
    }

    // ---- start guards / Startschutz ----

    @Test
    @DisplayName("§14.1: an unknown E2E_SEED_SET refuses the start")
    void refusesToStartForAnUnknownSeedSet() throws IOException {
        Path root = fixtureRoot("full");

        IllegalStateException refused = assertThrows(IllegalStateException.class,
            () -> loader("huge", root.toString(), allOn(), capacityOn(), stubParsers(), CLOCK));

        assertTrue(refused.getMessage().contains("full | thin | fail"), refused.getMessage());
    }

    @Test
    @DisplayName("§19 risks: a configured USAGE_STORAGE_ACCOUNT refuses the start")
    void refusesToStartWhileAStorageAccountIsConfigured() throws IOException {
        Path root = fixtureRoot("full");
        UsageStatsProperties withAccount = usage(true, "gsprodstatistics");

        IllegalStateException refused = assertThrows(IllegalStateException.class,
            () -> new StatisticsE2eSeedLoader("full", root.toString(), tempDir, withAccount,
                insights(true), funnel(true), dropoff(true), capacityOn(), Map::of,
                insightsStore, capacityStore, CLOCK, stubParsers()));

        assertTrue(refused.getMessage().contains("USAGE_STORAGE_ACCOUNT"), refused.getMessage());
    }

    @Test
    @DisplayName("§14.1: the seed set defaults to full and is read case-insensitively")
    void defaultsToFullAndAcceptsTheSetCaseInsensitively() throws IOException {
        Path root = fixtureRoot("full", "thin");

        assertEquals("full", loader(null, root.toString(), allOn(), capacityOn(), stubParsers(), CLOCK).seedSet());
        assertEquals("full", loader("  ", root.toString(), allOn(), capacityOn(), stubParsers(), CLOCK).seedSet());
        StatisticsE2eSeedLoader thin = loader(" THIN ", root.toString(), allOn(), capacityOn(), stubParsers(), CLOCK);
        assertEquals("thin", thin.seedSet());
        assertEquals(root.resolve("thin"), thin.setDirectory());
    }

    @Test
    @DisplayName("§14.1: missing fixtures refuse the start (explicit dir, missing set, nothing found)")
    void refusesToStartWithoutFixtures() throws IOException {
        Path root = fixtureRoot("full");

        assertThrows(IllegalStateException.class,
            () -> loader("full", tempDir.resolve("nowhere").toString(), allOn(), capacityOn(), stubParsers(), CLOCK));
        assertThrows(IllegalStateException.class,
            () -> loader("fail", root.toString(), allOn(), capacityOn(), stubParsers(), CLOCK));
        Path isolated = Files.createDirectories(tempDir.resolve("a/b/c/d/e"));
        assertThrows(IllegalStateException.class,
            () -> StatisticsE2eSeedLoader.resolveFixtureRoot(null, isolated));
    }

    @Test
    @DisplayName("§14.2: bootRun works in server/ — the fixtures are found one level up")
    void findsTheFixturesFromTheServerWorkingDirectory() throws IOException {
        Path repo = Files.createDirectories(tempDir.resolve("repo"));
        Path fixtures = Files.createDirectories(repo.resolve(StatisticsE2eSeedLoader.FIXTURE_SUBDIR));
        Path server = Files.createDirectories(repo.resolve("server"));

        assertEquals(fixtures, StatisticsE2eSeedLoader.resolveFixtureRoot("", server));
        assertEquals(fixtures, StatisticsE2eSeedLoader.resolveFixtureRoot(null, repo));
        assertEquals(fixtures,
            StatisticsE2eSeedLoader.resolveFixtureRoot("../" + StatisticsE2eSeedLoader.FIXTURE_SUBDIR, server));
    }

    // ---- seeding / Befüllen ----

    @Test
    @DisplayName("§14.1: both stores are seeded, UsageStatsStore (REQ-037) stays untouched")
    void seedsBothStoresAndLeavesTheUsageStatsStoreUntouched() throws IOException {
        Path root = fixtureRoot("full");
        writeFixtures(root.resolve("full"), "{\"schemaVersion\":1}", "{\"schemaVersion\":1}");
        UsageStatsStore usageStore = new UsageStatsStore();
        assertFalse(usageStore.hasStats());

        StatisticsE2eSeedLoader loader = loader("full", root.toString(), allOn(), capacityOn(),
            parsers(insightsHistory(TODAY.minusDays(30)), capacityHistory()), CLOCK);
        loader.onApplicationReady();

        assertTrue(insightsStore.hasViewModel());
        assertTrue(capacityStore.hasViewModel());
        assertTrue(insightsStore.current().bookingsEnabled());
        assertTrue(insightsStore.current().funnelEnabled());
        assertFalse(usageStore.hasStats(), "REQ-037 E2E expects empty periods");
        assertFalse(usageStore.current().month().hasData());
        // Structural guard: the loader cannot reach UsageStatsStore at all.
        for (Constructor<?> constructor : StatisticsE2eSeedLoader.class.getDeclaredConstructors()) {
            assertFalse(Arrays.asList(constructor.getParameterTypes()).contains(UsageStatsStore.class));
        }
        for (Field field : StatisticsE2eSeedLoader.class.getDeclaredFields()) {
            assertFalse(UsageStatsStore.class.isAssignableFrom(field.getType()), field.getName());
        }
    }

    @Test
    @DisplayName("§14.1: tokens are resolved before parsing; days from today on and before the start are dropped")
    void resolvesTokensAndTrimsTheHistoryWindow() throws IOException {
        Path root = fixtureRoot("full");
        writeFixtures(root.resolve("full"),
            "{\"schemaVersion\":1,\"collectingSince\":\"@day-3\",\"days\":{\"@wk-0-MO\":{}}}",
            "{\"schemaVersion\":1,\"scans\":{\"@wd-1\":{}}}");
        LocalDate start = TODAY.minusDays(3);
        AtomicReference<String> insightsJson = new AtomicReference<>();
        AtomicReference<String> capacityJson = new AtomicReference<>();
        StatisticsE2eSeedLoader.Parsers parsers = new StatisticsE2eSeedLoader.Parsers(
            json -> {
                insightsJson.set(json);
                return new InsightsBlobClient.Loaded(insightsHistory(start), null, true);
            },
            json -> {
                capacityJson.set(json);
                return new CapacityBlobClient.Loaded(capacityHistory(), null, true);
            });

        loader("full", root.toString(), allOn(), capacityOn(), parsers, CLOCK).seed();

        assertEquals("{\"schemaVersion\":1,\"collectingSince\":\"2026-09-21\",\"days\":{\"2026-09-21\":{}}}",
            insightsJson.get());
        assertEquals("{\"schemaVersion\":1,\"scans\":{\"2026-09-23\":{}}}", capacityJson.get());
        InsightsHistory seeded = insightsStore.history();
        assertEquals(List.of(start, TODAY.minusDays(2), TODAY.minusDays(1)), List.copyOf(seeded.days().keySet()));
        assertEquals(seeded.days().keySet(), seeded.branchDays().keySet());
        assertEquals(List.of(TODAY.minusDays(1)), List.copyOf(capacityStore.history().scans().keySet()));
    }

    @Test
    @DisplayName("E3 in the fail set: e2eSeed.lastDay ends the day rows earlier (never later than yesterday)")
    void honoursThePendingDayDirective() throws IOException {
        Path root = fixtureRoot("fail");
        writeFixtures(root.resolve("fail"),
            "{\"schemaVersion\":1,\"e2eSeed\":{\"lastDay\":\"@day-2\"}}", "{\"schemaVersion\":1}");

        loader("fail", root.toString(), allOn(), capacityOn(),
            parsers(insightsHistory(TODAY.minusDays(10)), capacityHistory()), CLOCK).seed();

        assertEquals(TODAY.minusDays(2), insightsStore.history().days().lastKey());
        assertEquals(TODAY.minusDays(1), StatisticsE2eSeedLoader.lastDay(
            "{\"e2eSeed\":{\"lastDay\":\"2026-12-31\"}}", TODAY, root), "never later than yesterday");
        assertThrows(IllegalStateException.class, () -> StatisticsE2eSeedLoader.lastDay(
            "{\"e2eSeed\":{\"lastDay\":\"soon\"}}", TODAY, root));
        assertThrows(IllegalStateException.class, () -> StatisticsE2eSeedLoader.lastDay(
            "not json", TODAY, root));
    }

    @Test
    @DisplayName("BR-4: only switched-on sources are seeded; the funnel needs usage.enabled")
    void seedsOnlyTheSourcesThatAreSwitchedOn() throws IOException {
        Path root = fixtureRoot("full");
        writeFixtures(root.resolve("full"), "{\"schemaVersion\":1}", "{\"schemaVersion\":1}");
        StatisticsE2eSeedLoader.Parsers parsers = parsers(insightsHistory(TODAY.minusDays(5)), capacityHistory());

        // usage.enabled=false: the funnel switch alone has no effect (§5.1 rows 6-8).
        new StatisticsE2eSeedLoader("full", root.toString(), tempDir, usage(false, ""), insights(false),
            funnel(true), dropoff(true), capacityOff(), Map::of, insightsStore, capacityStore, CLOCK,
            parsers).seed();
        assertFalse(insightsStore.hasViewModel());
        assertFalse(capacityStore.hasViewModel());

        // Funnel only (§5.1 row 5): insights view without the bookings part, no capacity.
        new StatisticsE2eSeedLoader("full", root.toString(), tempDir, usage(true, ""), insights(false),
            funnel(true), dropoff(false), capacityOff(), Map::of, insightsStore, capacityStore, CLOCK,
            parsers).seed();
        InsightsViewModel view = insightsStore.current();
        assertNotNull(view);
        assertFalse(view.bookingsEnabled());
        assertTrue(view.funnelEnabled());
        assertFalse(capacityStore.hasViewModel());
    }

    @Test
    @DisplayName("§14.1: an unusable fixture (E6) or a failing parser refuses the start")
    void refusesToStartWhenAFixtureIsUnusable() throws IOException {
        Path root = fixtureRoot("full");
        writeFixtures(root.resolve("full"), "{\"schemaVersion\":1}", "{\"schemaVersion\":1}");
        StatisticsE2eSeedLoader.Parsers unusable = new StatisticsE2eSeedLoader.Parsers(
            json -> new InsightsBlobClient.Loaded(InsightsHistory.empty(), null, false),
            json -> new CapacityBlobClient.Loaded(capacityHistory(), null, true));
        StatisticsE2eSeedLoader.Parsers throwing = new StatisticsE2eSeedLoader.Parsers(
            json -> new InsightsBlobClient.Loaded(insightsHistory(TODAY), null, true),
            json -> {
                throw new UnsupportedOperationException("parser missing");
            });

        assertThrows(IllegalStateException.class,
            () -> loader("full", root.toString(), allOn(), capacityOn(), unusable, CLOCK).seed());
        IllegalStateException failed = assertThrows(IllegalStateException.class,
            () -> loader("full", root.toString(), allOn(), capacityOn(), throwing, CLOCK).seed());
        assertTrue(failed.getMessage().contains("parser missing"), failed.getMessage());
        Files.delete(root.resolve("full").resolve(StatisticsE2eSeedLoader.CAPACITY_FILE));
        IllegalStateException missing = assertThrows(IllegalStateException.class,
            () -> loader("full", root.toString(), allOn(), capacityOn(), stubParsers(), CLOCK).seed());
        assertTrue(missing.getMessage().contains(StatisticsE2eSeedLoader.CAPACITY_FILE), missing.getMessage());
    }

    @Test
    @DisplayName("§14.1: a startup listener that ran after the seed cannot keep its empty view")
    void reappliesTheSeedWhenAStartupListenerReplacedIt() throws IOException {
        Path root = fixtureRoot("full");
        writeFixtures(root.resolve("full"), "{\"schemaVersion\":1}", "{\"schemaVersion\":1}");
        StatisticsE2eSeedLoader loader = loader("full", root.toString(), allOn(), capacityOn(),
            parsers(insightsHistory(TODAY.minusDays(5)), capacityHistory()), CLOCK);
        loader.onApplicationReady();
        InsightsViewModel seededInsights = insightsStore.current();
        CapacityViewModel seededCapacity = capacityStore.current();

        insightsStore.update(InsightsViewModel.build(InsightsHistory.empty(), Map.of(), TODAY, true, true, true)
            .withAdditionalWarnings(List.of(InsightsViewModel.WARN_HISTORY_UNAVAILABLE)), null);
        capacityStore.update(CapacityViewModel.build(CapacityHistory.empty(), Map.of(), false, TODAY, null), null);
        loader.onReadinessChange(new AvailabilityChangeEvent<>(this, ReadinessState.REFUSING_TRAFFIC));
        assertFalse(insightsStore.current() == seededInsights, "only ACCEPTING_TRAFFIC re-applies");

        loader.onReadinessChange(new AvailabilityChangeEvent<>(this, ReadinessState.ACCEPTING_TRAFFIC));

        assertSame(seededInsights, insightsStore.current());
        assertSame(seededCapacity, capacityStore.current());
    }

    @Test
    @DisplayName("Spring start: exactly one @Autowired constructor, bean only in the e2e-seed profile")
    void springCreatesTheBeanOnlyInTheSeedProfile() throws IOException {
        Path root = fixtureRoot("thin");
        SetupService setupService = mock(SetupService.class);
        when(setupService.getAllBranches()).thenReturn(List.of(new BranchSetup(BRANCH_A, "Audi Zentrum Düsseldorf", null, List.of())));
        ApplicationContextRunner runner = new ApplicationContextRunner()
            .withPropertyValues("e2e.seed.set=thin", "e2e.seed.dir=" + root)
            .withBean(UsageStatsProperties.class, () -> usage(true, ""))
            .withBean(UsageInsightsProperties.class, () -> insights(true))
            .withBean(UsageFunnelProperties.class, () -> funnel(true))
            .withBean(UsageDropoffProperties.class, () -> dropoff(true))
            .withBean(CapacityProperties.class, StatisticsE2eSeedLoaderTest::capacityOn)
            .withBean(SetupService.class, () -> setupService)
            .withBean(InsightsStore.class)
            .withBean(CapacityStore.class)
            .withUserConfiguration(StatisticsE2eSeedLoader.class);

        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles(StatisticsE2eSeedLoader.PROFILE))
            .run(context -> {
                assertNull(context.getStartupFailure());
                assertEquals("thin", context.getBean(StatisticsE2eSeedLoader.class).seedSet());
            });
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.getBeansOfType(StatisticsE2eSeedLoader.class).isEmpty());
        });
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles(StatisticsE2eSeedLoader.PROFILE))
            .withBean(UsageStatsProperties.class, () -> usage(true, "gsprodstatistics"))
            .run(context -> assertNotNull(context.getStartupFailure(), "storage account → start refused"));
    }

    // ---- checked-in fixtures / eingecheckte Fixtures ----

    @ParameterizedTest(name = "set {0}")
    @ValueSource(strings = {"full", "thin", "fail"})
    @DisplayName("§14.1: the checked-in fixtures parse with the shared tolerant parsers and build views")
    void realFixturesParseWithTheSharedParsers(String set) {
        StatisticsE2eSeedLoader loader = new StatisticsE2eSeedLoader(set, null, Path.of(""),
            usage(true, ""), insights(true), funnel(true), dropoff(true), capacityOn(),
            StatisticsE2eSeedLoaderTest::setupBranchNames, insightsStore, capacityStore, CLOCK,
            StatisticsE2eSeedLoader.Parsers.SHARED);

        loader.seed();

        InsightsHistory insights = insightsStore.history();
        CapacityHistory capacity = capacityStore.history();
        assertTrue(insightsStore.hasViewModel());
        assertTrue(capacityStore.hasViewModel());
        assertFalse(insights.days().isEmpty());
        assertEquals(insights.days().keySet(), insights.branchDays().keySet());
        assertTrue(insights.days().lastKey().isBefore(TODAY));
        assertTrue(capacity.scans().lastKey().isBefore(TODAY));
        switch (set) {
            case "full" -> {
                assertEquals(TODAY.minusDays(1), insights.days().lastKey());
                assertEquals(insights.collectingSince(), insights.days().firstKey());
                assertEquals(12, capacity.scans().size());
                assertEquals(ScanState.PARTIAL, capacity.scans().get(capacity.scans().lastKey()).state());
                assertTrue(capacity.scans().values().stream().anyMatch(s -> s.state() == ScanState.FAILED));
            }
            case "thin" -> {
                assertEquals(List.of(TODAY.minusDays(1)), List.copyOf(insights.days().keySet()));
                assertEquals(TODAY.minusDays(1), insights.collectingSince());
                assertEquals(1, capacity.scans().size());
            }
            default -> {
                assertEquals(TODAY.minusDays(2), insights.days().lastKey(), "rollup of yesterday pending");
                CapacityScan latest = capacity.scans().get(capacity.scans().lastKey());
                assertEquals(ScanState.FAILED, latest.state());
                CapacityScan fallback = capacity.scans().get(capacity.scans().headMap(capacity.scans().lastKey()).lastKey());
                assertEquals(ScanState.PARTIAL, fallback.state());
            }
        }
    }

    @ParameterizedTest(name = "set {0}")
    @ValueSource(strings = {"2026-09-24", "2026-11-29", "2026-11-30", "2027-01-04", "2026-03-01"})
    @DisplayName("§14.1: the full set always ends yesterday and keeps a complete previous month")
    void fullFixtureCoversThePreviousMonthOnAnyDay(String day) {
        LocalDate today = LocalDate.parse(day);
        Clock clock = Clock.fixed(today.atTime(9, 0).atZone(BERLIN).toInstant(), BERLIN);
        StatisticsE2eSeedLoader loader = new StatisticsE2eSeedLoader("full", null, Path.of(""),
            usage(true, ""), insights(true), funnel(true), dropoff(true), capacityOn(),
            StatisticsE2eSeedLoaderTest::setupBranchNames, insightsStore, capacityStore, clock,
            StatisticsE2eSeedLoader.Parsers.SHARED);

        loader.seed();

        SortedMap<LocalDate, InsightsDay> days = insightsStore.history().days();
        LocalDate previousMonthStart = today.withDayOfMonth(1).minusMonths(1);
        assertEquals(today.minusDays(1), days.lastKey());
        assertTrue(!days.firstKey().isAfter(previousMonthStart), "previous month complete");
        LocalDate latestScan = capacityStore.history().scans().lastKey();
        assertEquals(StatisticsE2eSeedLoader.workday(today, -1), latestScan);
    }

    @ParameterizedTest(name = "set {0}")
    @ValueSource(strings = {"full", "thin", "fail"})
    @DisplayName("§10.6: fixture counters add up (BR-27 Σ drop-offs, branch sums, known branch ids)")
    void fixtureCountersAreConsistent(String set) throws IOException {
        Path setDir = StatisticsE2eSeedLoader.resolveFixtureRoot(null, Path.of("")).resolve(set);
        JsonNode insights = JSON.readTree(StatisticsE2eSeedLoader.resolveTokens(
            Files.readString(setDir.resolve(StatisticsE2eSeedLoader.INSIGHTS_FILE)), TODAY));
        JsonNode capacity = JSON.readTree(StatisticsE2eSeedLoader.resolveTokens(
            Files.readString(setDir.resolve(StatisticsE2eSeedLoader.CAPACITY_FILE)), TODAY));
        Set<String> knownBranches = setupBranchNames().keySet();
        assertEquals(27, knownBranches.size());

        for (Map.Entry<String, JsonNode> entry : insights.get("days").properties()) {
            JsonNode day = entry.getValue();
            JsonNode branches = insights.get("branchDays").get(entry.getKey());
            assertNotNull(branches, "branchDays for " + entry.getKey());
            assertTrue(knownBranches.containsAll(fieldNames(branches)), "unknown branch id");
            long bookings = day.get("bookings").asLong();
            assertEquals(bookings, sumField(branches, "bookings"), "branch bookings " + entry.getKey());
            assertEquals(day.get("serviceViews").asLong(), sumField(branches, "serviceViews"));
            assertEquals(day.get("mobility").asLong(), sumField(branches, "mobility"));
            assertEquals(bookings, sumValues(day.get("bookingDaypart")));
            assertEquals(bookings, sumValues(day.get("leadTime")));
            long grid = 0;
            for (JsonNode weekday : day.get("slotGrid")) {
                grid += sumValues(weekday);
            }
            assertEquals(bookings, grid + day.get("slotUnknown").asLong());
            JsonNode funnel = day.get("funnel");
            if (funnel != null && !funnel.isNull()) {
                assertEquals(bookings, funnel.get("all").get("booking").asLong());
                JsonNode dropoff = funnel.get("dropoff");
                if (dropoff != null && !dropoff.isNull()) {
                    long expected = funnel.get("all").get("visits").asLong() - bookings;
                    assertEquals(expected, sumNested(dropoff.get("byScenarioStep")), "BR-27 " + entry.getKey());
                    assertEquals(expected, sumNested(dropoff.get("byScenarioDevice")));
                }
            }
        }
        for (JsonNode scan : capacity.get("scans")) {
            assertTrue(knownBranches.containsAll(fieldNames(scan.get("branches"))), "unknown branch id");
            long errors = 0;
            long measured = 0;
            for (JsonNode result : scan.get("branches")) {
                if ("error".equals(result.get("status").asText())) {
                    errors++;
                } else {
                    measured++;
                }
            }
            String expected = measured == 0 ? "failed" : errors > 0 ? "partial" : "complete";
            assertEquals(expected, scan.get("state").asText());
        }
    }

    // ---- helpers / Hilfsmethoden ----

    private StatisticsE2eSeedLoader loader(String set, String dir, Switches switches,
                                           CapacityProperties capacity,
                                           StatisticsE2eSeedLoader.Parsers parsers, Clock clock) {
        return new StatisticsE2eSeedLoader(set, dir, tempDir, usage(switches.usage(), ""),
            insights(switches.bookings()), funnel(switches.funnel()), dropoff(switches.dropoff()),
            capacity, () -> Map.of(BRANCH_A, "Audi Zentrum Düsseldorf", BRANCH_B, "Volkswagen Zentrum Düsseldorf"),
            insightsStore, capacityStore, clock, parsers);
    }

    private record Switches(boolean usage, boolean bookings, boolean funnel, boolean dropoff) {}

    private static Switches allOn() {
        return new Switches(true, true, true, true);
    }

    private Path fixtureRoot(String... sets) throws IOException {
        Path root = Files.createDirectories(tempDir.resolve("fixtures"));
        for (String set : sets) {
            Files.createDirectories(root.resolve(set));
        }
        return root;
    }

    private static void writeFixtures(Path setDir, String insights, String capacity) throws IOException {
        Files.writeString(setDir.resolve(StatisticsE2eSeedLoader.INSIGHTS_FILE), insights, StandardCharsets.UTF_8);
        Files.writeString(setDir.resolve(StatisticsE2eSeedLoader.CAPACITY_FILE), capacity, StandardCharsets.UTF_8);
    }

    private static StatisticsE2eSeedLoader.Parsers stubParsers() {
        return parsers(InsightsHistory.empty(), CapacityHistory.empty());
    }

    private static StatisticsE2eSeedLoader.Parsers parsers(InsightsHistory insights, CapacityHistory capacity) {
        return new StatisticsE2eSeedLoader.Parsers(
            json -> new InsightsBlobClient.Loaded(insights, null, true),
            json -> new CapacityBlobClient.Loaded(capacity, null, true));
    }

    /** Days from {@code start - 1} to {@code today + 1}, one booking each at {@link #BRANCH_A}. */
    private static InsightsHistory insightsHistory(LocalDate start) {
        SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
        SortedMap<LocalDate, Map<String, BranchCounts>> branchDays = new TreeMap<>();
        for (LocalDate day = start.minusDays(1); !day.isAfter(TODAY.plusDays(1)); day = day.plusDays(1)) {
            days.put(day, new InsightsDay(1L, Map.of("day", 1L), Map.of("MO", Map.of("morning", 1L)), 0L,
                Map.of("2-3", 1L), Map.of("inspection", 1L), 0L, 9L, null));
            branchDays.put(day, Map.of(BRANCH_A, new BranchCounts(1L, 9L, Map.of("inspection", 1L), 0L, 0L, 0L)));
        }
        return new InsightsHistory(1, Instant.parse("2026-09-24T01:52:10Z"), start, start, start,
            days, branchDays, new TreeMap<>());
    }

    /** Scans yesterday and today — only yesterday survives the trim. */
    private static CapacityHistory capacityHistory() {
        SortedMap<LocalDate, CapacityScan> scans = new TreeMap<>();
        for (LocalDate day : List.of(TODAY.minusDays(1), TODAY)) {
            scans.put(day, new CapacityScan(ScanState.COMPLETE, day.atTime(22, 31).atZone(BERLIN).toInstant(),
                day.atTime(22, 35).atZone(BERLIN).toInstant(), day.plusDays(1),
                Map.of(BRANCH_A, new ProbeResult(ProbeStatus.OK, null, day.plusDays(1), 0, 7))));
        }
        return new CapacityHistory(1, "wiper-blades", scans);
    }

    private static UsageStatsProperties usage(boolean enabled, String account) {
        return new UsageStatsProperties(enabled, "0 */15 * * * *", "0 30 3 * * *", "Europe/Berlin", null, 24, 60,
            new UsageStatsProperties.Storage(account, "usage-stats", "usage-daily.json"));
    }

    private static UsageInsightsProperties insights(boolean enabled) {
        return new UsageInsightsProperties(enabled, null, "-", "usage-insights.json");
    }

    private static UsageFunnelProperties funnel(boolean enabled) {
        return new UsageFunnelProperties(enabled, null);
    }

    private static UsageDropoffProperties dropoff(boolean enabled) {
        return new UsageDropoffProperties(enabled, null);
    }

    private static CapacityProperties capacityOn() {
        return new CapacityProperties(true, "-", "wiper-blades", false, "capacity-daily.json");
    }

    private static CapacityProperties capacityOff() {
        return new CapacityProperties(false, "-", "wiper-blades", false, "capacity-daily.json");
    }

    /** Branch names from the real {@code setup.json} (tests run in {@code server/}). */
    private static Map<String, String> setupBranchNames() {
        Path setup = Files.exists(Path.of("config/setup.json"))
            ? Path.of("config/setup.json") : Path.of("server/config/setup.json");
        try {
            Map<String, String> names = new java.util.LinkedHashMap<>();
            for (JsonNode branch : JSON.readTree(Files.readString(setup)).get("branches")) {
                names.put(branch.get("branchId").asText(), branch.get("name").asText());
            }
            return names;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static long sumField(JsonNode branches, String field) {
        long sum = 0;
        for (JsonNode branch : branches) {
            sum += branch.get(field).asLong();
        }
        return sum;
    }

    private static long sumValues(JsonNode map) {
        long sum = 0;
        for (JsonNode value : map) {
            sum += value.asLong();
        }
        return sum;
    }

    private static long sumNested(JsonNode map) {
        List<JsonNode> inner = new ArrayList<>();
        map.forEach(inner::add);
        return inner.stream().mapToLong(StatisticsE2eSeedLoaderTest::sumValues).sum();
    }
}
