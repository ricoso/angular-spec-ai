package com.autohaus.backend.usage.insights;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.autohaus.backend.capacity.CapacityScanScheduler;
import com.autohaus.backend.common.filter.RequestIdFilter;
import com.autohaus.backend.config.UsageDropoffProperties;
import com.autohaus.backend.config.UsageFunnelProperties;
import com.autohaus.backend.config.UsageInsightsProperties;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.config.setup.BranchSetup;
import com.autohaus.backend.config.setup.GroupEntry;
import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.reporting.LogAnalyticsClient;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.autohaus.backend.usage.UsageBlobClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.web.client.HttpServerErrorException;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The nightly insights pipeline end to end (REQ-038 TC-21, TC-22, TC-23, TC-46, TC-62) / die
 * nächtliche Insights-Pipeline.
 *
 * <p>The blob is an in-memory fake behind the mocked {@link InsightsBlobClient} (load returns the
 * last saved state with its ETag), the Berlin clock can be moved, jitter and back-off are recorded
 * instead of slept. Most tests mock {@link InsightsAggregator}; the query-count and classification
 * tests run the real aggregator and classifier against mocked Log Analytics and SoftNet. / Blob als
 * Fake, verstellbare Berliner Uhr, kein Schlafen.
 */
@ExtendWith(MockitoExtension.class)
class InsightsSchedulerTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final String BRANCH_A = "68f0a1b2c3d4e5f6a7b8c9d0";
    private static final String GROUP_A = "6908a1b2c3d4e5f6a7b8c9d1";
    private static final String OP_1 = "6908a1b2c3d4e5f6a7b8c9e2";

    @Mock private InsightsAggregator aggregator;
    @Mock private InsightsBlobClient blobClient;
    @Mock private SetupService setupService;

    private final InsightsStore store = new InsightsStore();
    private final BerlinClock clock = new BerlinClock(LocalDateTime.of(2026, 10, 20, 3, 45));
    private final List<Duration> sleeps = new ArrayList<>();
    private final List<InsightsHistory> saved = new ArrayList<>();
    private final List<String> savedEtags = new ArrayList<>();
    private final List<LocalDate> aggregatedDays = new ArrayList<>();
    private final List<String> traceIds = new ArrayList<>();

    private InsightsHistory stored = InsightsHistory.empty();
    private String etag;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        MDC.clear();
        lenient().when(setupService.getAllBranches()).thenReturn(List.of(
            new BranchSetup(BRANCH_A, "Autohaus Nord", null, List.of(new GroupEntry("default", GROUP_A, "Nord")))));
        lenient().when(aggregator.aggregateDay(any(), anyBoolean(), anyBoolean(), anyBoolean()))
            .thenAnswer(invocation -> {
                LocalDate day = invocation.getArgument(0);
                aggregatedDays.add(day);
                traceIds.add(MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY));
                return Optional.of(aggregated(day, invocation.getArgument(1), invocation.getArgument(2),
                    invocation.getArgument(3)));
            });
        Logger logger = (Logger) LoggerFactory.getLogger(InsightsScheduler.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(InsightsScheduler.class)).detachAppender(logs);
        MDC.clear();
    }

    // ---- TC-22: idempotent rollup, catch-up, collecting start ----

    @Test
    @DisplayName("TC-22: yesterday plus the 3 gaps are computed, nothing before collecting-since 05.10.")
    void catchesUpTheGapsButNeverBeforeTheCollectingStart() {
        givenBlob(history(bookingDays(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 19),
            Set.of(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 15))),
            LocalDate.of(2026, 10, 5), null, null));
        InsightsScheduler scheduler = scheduler(Switches.bookings(LocalDate.of(2026, 10, 5)));

        scheduler.rollUp();

        assertEquals(List.of(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 12),
            LocalDate.of(2026, 10, 8)), aggregatedDays, "newest first, window never before 05.10.");
        assertEquals(1, saved.size());
        InsightsHistory written = saved.get(0);
        assertEquals(15, written.days().size(), "05.10.–19.10. complete");
        assertEquals(15L, written.days().get(LocalDate.of(2026, 10, 15)).bookings());
        // REQ-042: the entry-variant distribution survives the per-day booking/funnel merge.
        assertEquals(Map.of("default", 14L, "recall", 1L),
            written.days().get(LocalDate.of(2026, 10, 15)).variants(), "G-4 variants carried through the merge");
        assertEquals(12L, written.branchDays().get(LocalDate.of(2026, 10, 12)).get(BRANCH_A).bookings());
        assertEquals(clock.instant(), written.updatedAt());
        assertEquals(List.of("\"v0\""), savedEtags, "If-Match with the ETag of the read");
        assertTrue(messages(Level.INFO).contains("usage insights rollup written: 4 day(s), 54 booking(s), 0 purged"),
            messages(Level.INFO).toString());
    }

    @Test
    @DisplayName("AC-14: the catch-up window ends at today−25 — a gap at today−26 is never queried")
    void catchesUpAtMostTwentyFiveDays() {
        LocalDate today = LocalDate.of(2026, 10, 20);
        LocalDate lastInWindow = today.minusDays(InsightsScheduler.CATCH_UP_DAYS);
        LocalDate firstOutside = today.minusDays(InsightsScheduler.CATCH_UP_DAYS + 1L);
        assertEquals(LocalDate.of(2026, 9, 25), lastInWindow);
        givenBlob(history(bookingDays(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 19),
            Set.of(lastInWindow, firstOutside)), LocalDate.of(2026, 9, 1), null, null));

        scheduler(Switches.bookings(LocalDate.of(2026, 9, 1))).rollUp();

        assertEquals(List.of(LocalDate.of(2026, 10, 19), lastInWindow), aggregatedDays,
            "yesterday plus the gap at today−25 — nothing at today−26");
        InsightsHistory written = saved.get(0);
        assertEquals(25L, written.days().get(lastInWindow).bookings());
        assertFalse(written.days().containsKey(firstOutside), "today−26 stays a gap");
    }

    @Test
    @DisplayName("TC-22: two runs produce an identical file (absolute values, never +=)")
    void twoRunsProduceAnIdenticalFile() {
        givenBlob(history(bookingDays(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18), Set.of()),
            LocalDate.of(2026, 10, 5), null, null));
        InsightsScheduler scheduler = scheduler(Switches.bookings(LocalDate.of(2026, 10, 5)));

        scheduler.rollUp();
        scheduler.rollUp();

        assertEquals(2, saved.size());
        assertEquals(InsightsBlobClient.serialize(saved.get(0)), InsightsBlobClient.serialize(saved.get(1)));
        assertEquals(List.of(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 19)), aggregatedDays,
            "the second run only recomputes yesterday");
    }

    @Test
    @DisplayName("TC-22: a stored day with 0 bookings is queried again (repairs days of the broken KQL 1 day filter)")
    void requeriesStoredDaysWithZeroBookings() {
        SortedMap<LocalDate, InsightsDay> days = bookingDays(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18), Set.of());
        days.put(LocalDate.of(2026, 10, 12), bookingDay(0));
        days.put(LocalDate.of(2026, 10, 14), bookingDay(0));
        givenBlob(history(days, LocalDate.of(2026, 10, 5), null, null));

        scheduler(Switches.bookings(LocalDate.of(2026, 10, 5))).rollUp();

        assertEquals(List.of(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 12)),
            aggregatedDays, "yesterday plus both zero days — days with bookings stay untouched");
        assertEquals(14L, saved.get(0).days().get(LocalDate.of(2026, 10, 14)).bookings());
        assertEquals(12L, saved.get(0).days().get(LocalDate.of(2026, 10, 12)).bookings());
    }

    @Test
    @DisplayName("TC-22/§5.8: no day of a compacted month is queried again")
    void neverQueriesACompactedMonth() {
        clock.set(LocalDateTime.of(2026, 11, 10, 3, 45));
        SortedMap<LocalDate, InsightsDay> days = bookingDays(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 9),
            Set.of(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 3)));
        SortedMap<YearMonth, Map<String, BranchCounts>> months = new TreeMap<>();
        months.put(YearMonth.of(2026, 10), Map.of(BRANCH_A, counts(40L)));
        givenBlob(new InsightsHistory(1, null, LocalDate.of(2026, 10, 5), null, null, days, new TreeMap<>(), months));

        scheduler(Switches.bookings(LocalDate.of(2026, 10, 5))).rollUp();

        assertEquals(List.of(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 3)), aggregatedDays);
        assertFalse(saved.get(0).days().containsKey(LocalDate.of(2026, 10, 20)));
        assertEquals(40L, saved.get(0).branchMonths().get(YearMonth.of(2026, 10)).get(BRANCH_A).bookings());
    }

    @Test
    @DisplayName("TC-22/E3: Log Analytics down → no partial day, catch-up stops after 3 failures")
    void writesNoPartialDayWhenLogAnalyticsIsDown() {
        givenBlob(history(bookingDays(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18),
            Set.of(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 15))),
            LocalDate.of(2026, 10, 5), null, null));
        doAnswer(invocation -> {
            aggregatedDays.add(invocation.getArgument(0));
            return Optional.empty();
        }).when(aggregator).aggregateDay(any(), anyBoolean(), anyBoolean(), anyBoolean());

        scheduler(Switches.bookings(LocalDate.of(2026, 10, 5))).rollUp();

        assertEquals(3, aggregatedDays.size(), "gives up after 3 consecutive failures");
        verify(blobClient, never()).save(any(), any());
        List<String> warnings = messages(Level.WARN);
        assertTrue(warnings.contains("usage insights rollup skipped for 2026-10-19"), warnings.toString());
        assertTrue(warnings.stream().anyMatch(m -> m.contains("giving up after 3")), warnings.toString());
    }

    @Test
    @DisplayName("E3: a success in between resets the failure counter; exceptions and null count as failures")
    void aSuccessResetsTheFailureCounter() {
        givenBlob(history(bookingDays(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 10), Set.of()),
            LocalDate.of(2026, 10, 5), null, null));
        doAnswer(invocation -> {
            LocalDate day = invocation.getArgument(0);
            aggregatedDays.add(day);
            return switch (day.getDayOfMonth()) {
                case 18 -> Optional.of(aggregated(day, true, false, false));
                case 17 -> null;
                case 16 -> throw new IllegalStateException("boom");
                default -> Optional.empty();
            };
        }).when(aggregator).aggregateDay(any(), anyBoolean(), anyBoolean(), anyBoolean());

        scheduler(Switches.bookings(LocalDate.of(2026, 10, 5))).rollUp();

        assertEquals(List.of(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 17),
            LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 15)), aggregatedDays,
            "19 fails, 18 succeeds (reset), then 17/16/15 fail in a row → stop");
        assertEquals(1, saved.size());
        assertEquals(18L, saved.get(0).days().get(LocalDate.of(2026, 10, 18)).bookings());
        assertFalse(saved.get(0).days().containsKey(LocalDate.of(2026, 10, 19)), "no partial day");
    }

    @Test
    @DisplayName("TC-22: empty collecting-since, start 14.10. 10:00 → collectingSince 15.10., 14.10. never counted")
    void anEmptyCollectingStartBeginsTheDayAfterActivation() {
        assertActivationDayRule(LocalDateTime.of(2026, 10, 14, 10, 0));
    }

    @Test
    @DisplayName("TC-22: the same with a process start at 02:00 on 14.10.")
    void anEmptyCollectingStartAlsoHoldsForAnEarlyMorningStart() {
        assertActivationDayRule(LocalDateTime.of(2026, 10, 14, 2, 0));
    }

    private void assertActivationDayRule(LocalDateTime start) {
        clock.set(start);
        givenBlob(InsightsHistory.empty());
        etag = null;
        InsightsScheduler scheduler = scheduler(new Switches(true, true, true, true, null, null, null));
        scheduler.onStartup();
        assertEquals(LocalDate.of(2026, 10, 14), scheduler.activationDay());

        clock.set(LocalDateTime.of(2026, 10, 15, 3, 45));
        scheduler.rollUp();

        assertTrue(aggregatedDays.isEmpty(), "the activation day 14.10. is never queried: " + aggregatedDays);
        assertEquals(1, saved.size(), "the start is persisted by the first rollup (BR-19)");
        assertEquals(LocalDate.of(2026, 10, 15), saved.get(0).collectingSince());
        assertEquals(LocalDate.of(2026, 10, 15), saved.get(0).funnelCollectingSince());
        assertEquals(LocalDate.of(2026, 10, 15), saved.get(0).dropoffCollectingSince());
        verify(blobClient).save(any(), isNull());

        clock.set(LocalDateTime.of(2026, 10, 16, 3, 45));
        scheduler.rollUp();

        assertEquals(List.of(LocalDate.of(2026, 10, 15)), aggregatedDays);
        verify(aggregator).aggregateDay(LocalDate.of(2026, 10, 15), true, true, true);
    }

    @Test
    @DisplayName("BR-19: a configured collecting start wins over the persisted one")
    void aConfiguredCollectingStartWins() {
        givenBlob(history(new TreeMap<>(), LocalDate.of(2026, 10, 1), null, null));

        scheduler(Switches.bookings(LocalDate.of(2026, 10, 18))).rollUp();

        assertEquals(List.of(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 18)), aggregatedDays);
        assertEquals(LocalDate.of(2026, 10, 18), saved.get(0).collectingSince());
    }

    @Test
    @DisplayName("Step 3: nothing to compute and nothing changed → no write")
    void writesNothingWhenNothingChanged() {
        givenBlob(history(new TreeMap<>(), LocalDate.of(2026, 12, 1), null, null));

        scheduler(Switches.bookings(LocalDate.of(2026, 12, 1))).rollUp();

        assertTrue(aggregatedDays.isEmpty());
        verify(blobClient, never()).save(any(), any());
        assertTrue(messages(Level.INFO).stream().anyMatch(m -> m.contains("nothing to write")));
        assertNotNull(store.current(), "the view is published anyway");
    }

    // ---- TC-23: retention in the same write / Aufbewahrung im selben Schreibvorgang ----

    @Test
    @DisplayName("TC-23: 08.11. with Log Analytics down → compaction and purge in exactly one write")
    void compactsAndPurgesEvenWhenTheRollupFails() {
        clock.set(LocalDateTime.of(2026, 11, 8, 3, 45));
        givenBlob(octoberWithOldDays());
        doReturn(Optional.empty()).when(aggregator).aggregateDay(any(), anyBoolean(), anyBoolean(), anyBoolean());

        scheduler(Switches.bookings(LocalDate.of(2026, 10, 1))).rollUp();

        verify(blobClient, times(1)).save(any(), any());
        InsightsHistory written = saved.get(0);
        assertEquals(5L, written.branchMonths().get(YearMonth.of(2026, 10)).get(BRANCH_A).bookings());
        assertTrue(written.branchDays().isEmpty(), "October branch days deleted");
        assertFalse(written.days().containsKey(LocalDate.of(2024, 11, 1)), "older than 24 months purged");
        assertTrue(messages(Level.INFO).contains("usage insights rollup written: 0 day(s), 0 booking(s), 1 purged"),
            messages(Level.INFO).toString());
    }

    @Test
    @DisplayName("TC-23: on 07.11. only the purge happens, the October branch days stay")
    void purgesButDoesNotCompactOnTheSeventh() {
        clock.set(LocalDateTime.of(2026, 11, 7, 3, 45));
        givenBlob(octoberWithOldDays());
        doReturn(Optional.empty()).when(aggregator).aggregateDay(any(), anyBoolean(), anyBoolean(), anyBoolean());

        scheduler(Switches.bookings(LocalDate.of(2026, 10, 1))).rollUp();

        InsightsHistory written = saved.get(0);
        assertEquals(2, written.branchDays().size());
        assertTrue(written.branchMonths().isEmpty());
        assertFalse(written.days().containsKey(LocalDate.of(2024, 11, 1)));
    }

    // ---- TC-62 / TC-46: sources and query variants / Quellen und Abfrage-Fassungen ----

    @Test
    @DisplayName("TC-62: drop-off on → 3b for days from dropoffCollectingSince, older funnel days untouched")
    void usesTheDropoffVariantOnlyFromItsCollectingStart() {
        givenBlob(history(funnelDays(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 19)), null,
            LocalDate.of(2026, 10, 10), null));

        scheduler(new Switches(false, true, true, true, null, LocalDate.of(2026, 10, 10),
            LocalDate.of(2026, 10, 18))).rollUp();

        assertEquals(List.of(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 18)), aggregatedDays,
            "18.10. is recomputed because its dropoff is missing; 17.10. lies before the drop-off start");
        verify(aggregator).aggregateDay(LocalDate.of(2026, 10, 19), false, true, true);
        verify(aggregator).aggregateDay(LocalDate.of(2026, 10, 18), false, true, true);
        InsightsHistory written = saved.get(0);
        assertNotNull(written.days().get(LocalDate.of(2026, 10, 19)).funnel().dropoff());
        assertNull(written.days().get(LocalDate.of(2026, 10, 17)).funnel().dropoff());
        assertEquals(LocalDate.of(2026, 10, 18), written.dropoffCollectingSince());
        assertNull(written.collectingSince(), "K2 off → its start stays untouched");
    }

    @Test
    @DisplayName("TC-62: drop-off off → the funnel variant 3a (dropoff flag false)")
    void usesTheFunnelVariantWhileTheDropoffIsOff() {
        givenBlob(history(funnelDays(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 18)), null,
            LocalDate.of(2026, 10, 10), null));

        scheduler(new Switches(false, true, true, false, null, LocalDate.of(2026, 10, 10), null)).rollUp();

        verify(aggregator).aggregateDay(LocalDate.of(2026, 10, 19), false, true, false);
        assertNull(saved.get(0).days().get(LocalDate.of(2026, 10, 19)).funnel().dropoff());
    }

    @Test
    @DisplayName("§5.1: the funnel switch has no effect without usage.enabled")
    void theFunnelNeedsUsageEnabled() {
        givenBlob(history(new TreeMap<>(), LocalDate.of(2026, 10, 18), null, null));
        InsightsScheduler scheduler = scheduler(new Switches(true, false, true, true, LocalDate.of(2026, 10, 18),
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1)));

        scheduler.rollUp();

        assertFalse(scheduler.funnelEnabled());
        assertFalse(scheduler.dropoffEnabled());
        verify(aggregator).aggregateDay(LocalDate.of(2026, 10, 19), true, false, false);
    }

    @Test
    @DisplayName("BR-18: a source that is not computed keeps its stored values (per-source replace)")
    void keepsTheValuesOfASourceThatIsNotComputed() {
        SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
        days.put(LocalDate.of(2026, 10, 19), new InsightsDay(5L, Map.of("day", 5L), Map.of(), 0L, Map.of(),
            Map.of("inspection", 5L), 1L, 20L, null));
        givenBlob(history(days, null, LocalDate.of(2026, 10, 10), null));

        scheduler(new Switches(false, true, true, false, null, LocalDate.of(2026, 10, 10), null)).rollUp();

        InsightsDay written = saved.get(0).days().get(LocalDate.of(2026, 10, 19));
        assertEquals(5L, written.bookings(), "the bookings part survives a funnel-only run");
        assertEquals(190L, written.funnel().all().visits());
    }

    @Test
    @DisplayName("TC-46: only the funnel → exactly one KQL query (FUNNEL_KQL) per rollup with a complete history")
    void onlyTheFunnelRunsExactlyOneQuery() {
        LogAnalyticsClient logClient = mock(LogAnalyticsClient.class);
        List<String> queries = new ArrayList<>();
        when(logClient.query(anyString())).thenAnswer(invocation -> {
            queries.add(invocation.getArgument(0));
            return InsightsAggregatorTest.response(List.of(List.of(1, "mobile", 10), List.of(5, "desktop", 2)));
        });
        givenBlob(history(funnelDays(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 18)), null,
            LocalDate.of(2026, 9, 20), null));

        realScheduler(logClient, mock(ServiceGroupClassifier.class),
            new Switches(false, true, true, false, null, LocalDate.of(2026, 9, 20), null)).rollUp();

        assertEquals(1, queries.size());
        assertTrue(queries.get(0).contains("summarize sessions = count() by MaxStep, Device"));
        assertFalse(queries.get(0).contains("let From"));
        FunnelDay funnel = saved.get(0).days().get(LocalDate.of(2026, 10, 19)).funnel();
        assertEquals(12L, funnel.all().visits());
        assertNull(funnel.dropoff());
        assertNull(saved.get(0).days().get(LocalDate.of(2026, 10, 19)).bookings(), "no USAGE_BOOKING query");
    }

    @Test
    @DisplayName("TC-62: drop-off on → exactly one DROPOFF_KQL; both margins stored, invariant holds")
    void theDropoffRollupStoresBothMargins() {
        LogAnalyticsClient logClient = mock(LogAnalyticsClient.class);
        List<String> queries = new ArrayList<>();
        when(logClient.query(anyString())).thenAnswer(invocation -> {
            queries.add(invocation.getArgument(0));
            return InsightsAggregatorTest.response(List.of(
                List.of("booked", 5, "mobile", 3), List.of("no-slots", 4, "mobile", 6),
                List.of("quiet-1", 1, "desktop", 9), List.of("foo", 2, "mobile", 4)));
        });
        givenBlob(history(funnelDays(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 18)), null,
            LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 19)));

        realScheduler(logClient, mock(ServiceGroupClassifier.class),
            new Switches(false, true, true, true, null, LocalDate.of(2026, 9, 20), null)).rollUp();

        assertEquals(1, queries.size());
        assertTrue(queries.get(0).startsWith("let From"), "DROPOFF_KQL replaces FUNNEL_KQL");
        FunnelDay funnel = saved.get(0).days().get(LocalDate.of(2026, 10, 19)).funnel();
        assertEquals(18L, funnel.all().visits(), "the foo row is discarded");
        DropoffDay dropoff = funnel.dropoff();
        assertEquals(6L, dropoff.byScenarioStep().get("no-slots").get("4"));
        assertEquals(9L, dropoff.byScenarioDevice().get("quiet-1").get("desktop"));
        long sum = dropoff.byScenarioStep().values().stream().flatMap(m -> m.values().stream())
            .mapToLong(Long::longValue).sum();
        assertEquals(funnel.all().visits() - funnel.all().booking(), sum);
    }

    @Test
    @DisplayName("TC-46: only K2 → exactly two queries; TC-21: second day without SoftNet call, MDC job.insightsrollup")
    void theBookingRollupCachesTheClassificationAndRunsUnderTheJobMarker() {
        LogAnalyticsClient logClient = mock(LogAnalyticsClient.class);
        List<String> queries = new ArrayList<>();
        when(logClient.query(anyString())).thenAnswer(invocation -> {
            String kql = invocation.getArgument(0);
            queries.add(kql);
            traceIds.add(MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY));
            if (kql.contains("USAGE_BOOKING")) {
                return InsightsAggregatorTest.response(List.of(List.of(1, BRANCH_A, GROUP_A, "default",
                    "[\"" + OP_1 + "\"]", "evening", "MO", "late", "2-3", false)));
            }
            return InsightsAggregatorTest.response(List.of(List.of(BRANCH_A, 4)));
        });
        SoftnetApiClient softnet = mock(SoftnetApiClient.class);
        List<String> softnetTraceIds = new ArrayList<>();
        when(softnet.getList("/api/planning/tags", GROUP_A)).thenAnswer(invocation -> {
            softnetTraceIds.add(MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY));
            return json("[{\"id\":\"t3\",\"rank\":3}]");
        });
        when(softnet.getOperations(GROUP_A)).thenAnswer(invocation -> {
            softnetTraceIds.add(MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY));
            return json("[{\"id\":\"" + OP_1 + "\",\"name\":\"Radwechsel\",\"tags\":[\"t3\"]}]");
        });
        ServiceGroupClassifier classifier = new ServiceGroupClassifier(softnet,
            Clock.fixed(clock.instant(), BERLIN), sleeps::add);
        givenBlob(history(bookingDays(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 17), Set.of()),
            LocalDate.of(2026, 10, 5), null, null));

        realScheduler(logClient, classifier, Switches.bookings(LocalDate.of(2026, 10, 5))).rollUp();

        assertEquals(4, queries.size(), "two days × (KQL 1 + KQL 2)");
        verify(softnet, times(1)).getOperations(GROUP_A);
        verify(softnet, times(1)).getList("/api/planning/tags", GROUP_A);
        assertEquals(List.of(InsightsScheduler.TRACE_ID, InsightsScheduler.TRACE_ID), softnetTraceIds);
        assertTrue(traceIds.stream().allMatch(InsightsScheduler.TRACE_ID::equals), traceIds.toString());
        InsightsDay day = saved.get(0).days().get(LocalDate.of(2026, 10, 18));
        assertEquals(Map.of("tire-change", 1L), day.groups());
        assertNull(day.funnel(), "funnel: null while the funnel is off");
        assertNull(MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY), "MDC restored after the run");
    }

    @Test
    @DisplayName("TC-21/E9: SoftNet 500 also in the retry → that day is not written")
    void aTransientClassificationFailureSkipsTheDay() {
        LogAnalyticsClient logClient = mock(LogAnalyticsClient.class);
        when(logClient.query(anyString())).thenAnswer(invocation -> {
            String kql = invocation.getArgument(0);
            return kql.contains("USAGE_BOOKING")
                ? InsightsAggregatorTest.response(List.of(List.of(1, BRANCH_A, GROUP_A, "default",
                    "[\"" + OP_1 + "\"]", "day", "MO", "late", "2-3", false)))
                : InsightsAggregatorTest.response(List.of());
        });
        SoftnetApiClient softnet = mock(SoftnetApiClient.class);
        when(softnet.getList("/api/planning/tags", GROUP_A))
            .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
        ServiceGroupClassifier classifier = new ServiceGroupClassifier(softnet,
            Clock.fixed(clock.instant(), BERLIN), sleeps::add);
        givenBlob(history(bookingDays(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18), Set.of()),
            LocalDate.of(2026, 10, 5), null, null));

        realScheduler(logClient, classifier, Switches.bookings(LocalDate.of(2026, 10, 5))).rollUp();

        verify(blobClient, never()).save(any(), any());
        assertTrue(sleeps.contains(Duration.ofSeconds(30)), "one retry after 30 s");
        assertTrue(messages(Level.WARN).contains("usage insights rollup skipped for 2026-10-19"));
    }

    // ---- E5 / E6 / E4 ----

    @Test
    @DisplayName("TC-24/E5: 412 → re-read, own absolute values over the other replica's state, retention again")
    void mergesOverTheOtherReplicasStateAfterAConflict() {
        givenBlob(history(bookingDays(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18), Set.of()),
            LocalDate.of(2026, 10, 5), null, null));
        SortedMap<LocalDate, InsightsDay> theirs = bookingDays(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18), Set.of());
        theirs.put(LocalDate.of(2026, 10, 19), bookingDay(99L));
        theirs.put(LocalDate.of(2024, 1, 1), bookingDay(1L));
        InsightsHistory otherReplica = history(theirs, LocalDate.of(2026, 10, 5), null, null);
        doAnswer(invocation -> {
            stored = otherReplica;
            etag = "\"other\"";
            throw new UsageBlobClient.BlobPreconditionFailedException("412");
        }).doAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            savedEtags.add(invocation.getArgument(1));
            return null;
        }).when(blobClient).save(any(), any());

        scheduler(Switches.bookings(LocalDate.of(2026, 10, 5))).rollUp();

        assertEquals(1, saved.size());
        assertEquals(List.of("\"other\""), savedEtags, "the second attempt uses the fresh ETag");
        assertEquals(19L, saved.get(0).days().get(LocalDate.of(2026, 10, 19)).bookings(), "our absolute value wins");
        assertFalse(saved.get(0).days().containsKey(LocalDate.of(2024, 1, 1)), "retention applied again");
        assertEquals(1, sleeps.size(), "short back-off");
        assertEquals(List.of(LocalDate.of(2026, 10, 19)), aggregatedDays, "no second aggregation");
    }

    @Test
    @DisplayName("E5: at most 3 write attempts, then WARN — the next night catches up")
    void givesUpAfterThreeConflicts() {
        givenBlob(history(new TreeMap<>(), LocalDate.of(2026, 10, 5), null, null));
        doAnswer(invocation -> {
            throw new UsageBlobClient.BlobPreconditionFailedException("412");
        }).when(blobClient).save(any(), any());

        scheduler(Switches.bookings(LocalDate.of(2026, 10, 5))).rollUp();

        verify(blobClient, times(3)).save(any(), any());
        assertTrue(messages(Level.WARN).stream().anyMatch(m -> m.contains("gave up after 3 attempts")));
        assertNotNull(store.current());
    }

    @Test
    @DisplayName("E5/E6: a blob that becomes unusable or unreachable during the retry aborts the write")
    void abortsWhenTheRereadFails() {
        givenBlob(history(new TreeMap<>(), LocalDate.of(2026, 10, 5), null, null));
        doAnswer(invocation -> {
            throw new UsageBlobClient.BlobPreconditionFailedException("412");
        }).when(blobClient).save(any(), any());
        when(blobClient.load())
            .thenReturn(new InsightsBlobClient.Loaded(stored, "\"v0\"", true))
            .thenReturn(new InsightsBlobClient.Loaded(InsightsHistory.empty(), "\"x\"", false))
            .thenReturn(new InsightsBlobClient.Loaded(stored, "\"v0\"", true))
            .thenThrow(new UsageBlobClient.BlobUnavailableException("network"));
        InsightsScheduler scheduler = scheduler(Switches.bookings(LocalDate.of(2026, 10, 5)));

        scheduler.rollUp();
        scheduler.rollUp();

        verify(blobClient, times(2)).save(any(), any());
        assertTrue(messages(Level.ERROR).stream().anyMatch(m -> m.contains("became unusable")));
        assertTrue(messages(Level.WARN).stream().anyMatch(m -> m.contains("unavailable during the retry")));
    }

    @Test
    @DisplayName("E4: a failing write is reported, never retried blindly")
    void aFailingWriteIsReported() {
        givenBlob(history(new TreeMap<>(), LocalDate.of(2026, 10, 5), null, null));
        doAnswer(invocation -> {
            throw new UsageBlobClient.BlobUnavailableException("403");
        }).when(blobClient).save(any(), any());

        scheduler(Switches.bookings(LocalDate.of(2026, 10, 5))).rollUp();

        verify(blobClient, times(1)).save(any(), any());
        assertTrue(messages(Level.WARN).stream().anyMatch(m -> m.contains("write failed")));
    }

    @Test
    @DisplayName("E6: unusable content → no KQL, nothing written")
    void writesNothingForUnusableContent() {
        when(blobClient.isConfigured()).thenReturn(true);
        when(blobClient.load()).thenReturn(new InsightsBlobClient.Loaded(InsightsHistory.empty(), "\"e\"", false));

        scheduler(Switches.bookings(LocalDate.of(2026, 10, 5))).rollUp();

        assertTrue(aggregatedDays.isEmpty());
        verify(blobClient, never()).save(any(), any());
        assertTrue(store.current().warnings().contains(InsightsViewModel.WARN_HISTORY_UNAVAILABLE));
    }

    @Test
    @DisplayName("E4: blob unreachable or not configured → no KQL, nothing written")
    void skipsTheRollupWithoutABlob() {
        when(blobClient.isConfigured()).thenReturn(true);
        when(blobClient.load()).thenThrow(new UsageBlobClient.BlobUnavailableException("403"));
        scheduler(Switches.bookings(LocalDate.of(2026, 10, 5))).rollUp();

        when(blobClient.isConfigured()).thenReturn(false);
        scheduler(Switches.bookings(LocalDate.of(2026, 10, 5))).rollUp();

        assertTrue(aggregatedDays.isEmpty());
        verify(blobClient, never()).save(any(), any());
        verify(blobClient, times(1)).load();
    }

    // ---- startup and view / Start und Sicht ----

    @Test
    @DisplayName("Step 5: startup loads the blob (no KQL) and stores the view without warning")
    void startupLoadsTheHistoryAndStoresTheView() {
        InsightsHistory history = history(bookingDays(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 19), Set.of()),
            LocalDate.of(2026, 10, 5), null, null);
        givenBlob(history);

        scheduler(Switches.bookings(null)).onStartup();

        assertTrue(aggregatedDays.isEmpty(), "never KQL on startup");
        assertSame(history, store.history());
        assertNotNull(store.current());
        assertFalse(store.current().warnings().contains(InsightsViewModel.WARN_HISTORY_UNAVAILABLE));
    }

    @Test
    @DisplayName("TC-48/E4: load failure on startup → WARN_HISTORY_UNAVAILABLE; a later successful load removes it")
    void theHistoryWarningDisappearsAfterALaterSuccessfulLoad() {
        when(blobClient.isConfigured()).thenReturn(true);
        when(blobClient.load())
            .thenThrow(new UsageBlobClient.BlobUnavailableException("403"))
            .thenReturn(new InsightsBlobClient.Loaded(history(new TreeMap<>(), LocalDate.of(2026, 10, 5), null, null),
                "\"v1\"", true));
        lenient().doAnswer(invocation -> null).when(blobClient).save(any(), any());
        InsightsScheduler scheduler = scheduler(Switches.bookings(LocalDate.of(2026, 10, 5)));

        scheduler.onStartup();
        assertTrue(store.current().warnings().contains(InsightsViewModel.WARN_HISTORY_UNAVAILABLE));
        assertTrue(messages(Level.WARN).stream().anyMatch(m -> m.contains("unavailable on startup")));

        scheduler.rollUp();
        assertFalse(store.current().warnings().contains(InsightsViewModel.WARN_HISTORY_UNAVAILABLE));
    }

    @Test
    @DisplayName("E6 on startup → the same warning, the file is left alone")
    void unusableContentOnStartupShowsTheWarning() {
        when(blobClient.isConfigured()).thenReturn(true);
        when(blobClient.load()).thenReturn(new InsightsBlobClient.Loaded(InsightsHistory.empty(), "\"e\"", false));

        scheduler(Switches.bookings(null)).onStartup();

        assertTrue(store.current().warnings().contains(InsightsViewModel.WARN_HISTORY_UNAVAILABLE));
        verify(blobClient, never()).save(any(), any());
    }

    @Test
    @DisplayName("AC-32: no storage account → warning, no blob call; an e2e-seed view is kept")
    void withoutStorageTheViewCarriesTheWarningOrKeepsASeededView() {
        when(blobClient.isConfigured()).thenReturn(false);
        scheduler(Switches.bookings(null)).onStartup();
        assertTrue(store.current().warnings().contains(InsightsViewModel.WARN_HISTORY_UNAVAILABLE));
        verify(blobClient, never()).load();

        InsightsStore seeded = new InsightsStore();
        InsightsViewModel seed = InsightsViewModel.build(InsightsHistory.empty(), Map.of(), LocalDate.of(2026, 10, 20),
            true, false, false);
        seeded.update(seed, InsightsHistory.empty());
        new InsightsScheduler(aggregator, blobClient, seeded, retention(), setupService,
            usage(true), new UsageInsightsProperties(true, null, "-", null), new UsageFunnelProperties(false, null),
            new UsageDropoffProperties(false, null), clock, 0, sleeps::add).onStartup();
        assertSame(seed, seeded.current(), "the seed loader's view is not overwritten");
    }

    @Test
    @DisplayName("E4: a load failure after an earlier success keeps the loaded state without warning")
    void aLaterLoadFailureKeepsTheEarlierState() {
        InsightsHistory history = history(new TreeMap<>(), LocalDate.of(2026, 10, 5), null, null);
        when(blobClient.isConfigured()).thenReturn(true);
        when(blobClient.load())
            .thenReturn(new InsightsBlobClient.Loaded(history, "\"v1\"", true))
            .thenThrow(new UsageBlobClient.BlobUnavailableException("403"));
        InsightsScheduler scheduler = scheduler(Switches.bookings(null));

        scheduler.onStartup();
        scheduler.onStartup();

        assertSame(history, store.history());
        assertFalse(store.current().warnings().contains(InsightsViewModel.WARN_HISTORY_UNAVAILABLE));
    }

    @Test
    @DisplayName("Step 5: rebuildViewModel at 00:05 works from memory only — no blob, no KQL")
    void rebuildsTheViewWithoutIo() {
        givenBlob(history(new TreeMap<>(), LocalDate.of(2026, 10, 5), null, null));
        InsightsScheduler scheduler = scheduler(Switches.bookings(null));
        scheduler.onStartup();
        clearInvocations(blobClient, aggregator);

        clock.set(LocalDateTime.of(2026, 10, 21, 0, 5));
        scheduler.rebuildViewModel();

        verifyNoInteractions(blobClient, aggregator);
        assertEquals(LocalDate.of(2026, 10, 20), store.current().asOf(), "the day change is picked up");
    }

    @Test
    @DisplayName("§9: 'usage insights stale' is logged at most once per day")
    void logsTheStaleWarningOncePerDay() {
        givenBlob(history(bookingDays(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 14), Set.of()),
            LocalDate.of(2026, 10, 5), null, null));
        InsightsScheduler scheduler = scheduler(Switches.bookings(null));

        scheduler.onStartup();
        scheduler.rebuildViewModel();
        assertEquals(1, count(Level.WARN, "usage insights stale: last day 2026-10-14"));

        clock.set(LocalDateTime.of(2026, 10, 21, 0, 5));
        scheduler.rebuildViewModel();
        assertEquals(2, count(Level.WARN, "usage insights stale: last day 2026-10-14"));
    }

    @Test
    @DisplayName("§9: a history ending yesterday is not stale")
    void aCurrentHistoryIsNotStale() {
        givenBlob(history(bookingDays(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 17), Set.of()),
            LocalDate.of(2026, 10, 5), null, null));

        scheduler(Switches.bookings(null)).onStartup();

        assertEquals(0, count(Level.WARN, "usage insights stale"));
    }

    @Test
    @DisplayName("Step 3: jitter 0–120 s before the rollup")
    void waitsAJitterOfAtMostTwoMinutes() {
        when(blobClient.isConfigured()).thenReturn(false);
        InsightsScheduler scheduler = new InsightsScheduler(aggregator, blobClient, store, retention(), setupService,
            usage(true), new UsageInsightsProperties(true, null, "-", null), new UsageFunnelProperties(false, null),
            new UsageDropoffProperties(false, null), clock, InsightsScheduler.MAX_JITTER_SECONDS, sleeps::add);

        for (int i = 0; i < 20; i++) {
            scheduler.rollUp();
        }

        assertFalse(sleeps.isEmpty(), "a random jitter was applied");
        assertTrue(sleeps.stream().allMatch(d -> !d.isNegative() && d.compareTo(Duration.ofSeconds(120)) <= 0), sleeps.toString());
    }

    @Test
    @DisplayName("BR-15: the whole rollup runs under MDC traceId = job.insightsrollup, restored afterwards")
    void runsUnderTheJobTraceId() {
        givenBlob(history(new TreeMap<>(), LocalDate.of(2026, 10, 5), null, null));
        MDC.put(RequestIdFilter.TRACE_ID_MDC_KEY, "outer");

        scheduler(Switches.bookings(LocalDate.of(2026, 10, 5))).rollUp();

        assertFalse(traceIds.isEmpty());
        assertTrue(traceIds.stream().allMatch(InsightsScheduler.TRACE_ID::equals), traceIds.toString());
        assertEquals("outer", MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY));
        assertEquals("job.insightsrollup", InsightsScheduler.TRACE_ID);
    }

    @Test
    @DisplayName("robustness: an unexpected exception in the rollup never reaches the scheduler")
    void anUnexpectedExceptionIsContained() {
        when(blobClient.isConfigured()).thenThrow(new IllegalStateException("boom"));

        InsightsScheduler scheduler = scheduler(Switches.bookings(null));

        assertDoesNotThrow(scheduler::rollUp);
        assertDoesNotThrow(scheduler::onStartup);
        assertTrue(messages(Level.ERROR).stream().anyMatch(m -> m.contains("failed unexpectedly")));
    }

    @Test
    @DisplayName("production constructor and switch accessors are null-safe")
    void productionConstructorAndSwitches() {
        InsightsScheduler production = new InsightsScheduler(aggregator, blobClient, store, retention(), setupService,
            usage(true), new UsageInsightsProperties(true, null, "-", null), new UsageFunnelProperties(true, null),
            new UsageDropoffProperties(true, null));
        assertTrue(production.bookingsEnabled());
        assertTrue(production.funnelEnabled());
        assertTrue(production.dropoffEnabled());

        InsightsScheduler empty = new InsightsScheduler(aggregator, blobClient, store, retention(), null,
            null, null, null, null, clock, 0, sleeps::add);
        assertFalse(empty.bookingsEnabled());
        assertFalse(empty.funnelEnabled());
        assertFalse(empty.dropoffEnabled());
        assertNull(empty.activationDay());
    }

    // ---- activation condition / Aktivierungsbedingung ----

    @Test
    @DisplayName("§5.1: active ⇔ usage.insights.enabled ∨ (usage.enabled ∧ usage.funnel.enabled)")
    void theConditionFollowsTheKillSwitchMatrix() {
        assertFalse(active(Map.of()), "delivery default (AC-31)");
        assertTrue(active(Map.of("usage.insights.enabled", "true")));
        assertTrue(active(Map.of("usage.insights.enabled", "TRUE ", "usage.enabled", "false")));
        assertTrue(active(Map.of("usage.enabled", "true", "usage.funnel.enabled", "true")));
        assertTrue(active(Map.of("usage.funnel.enabled", "true")), "usage.enabled defaults to true (REQ-037)");
        assertFalse(active(Map.of("usage.enabled", "false", "usage.funnel.enabled", "true")));
        assertFalse(active(Map.of("usage.dropoff.enabled", "true", "usage.funnel.enabled", "false")),
            "drop-off alone never activates the bean");
        // §5.1/§14: the binding's boolean semantics, identical to UsageInsightsProperties.
        assertTrue(active(Map.of("usage.insights.enabled", "yes")));
        assertTrue(active(Map.of("usage.insights.enabled", "1")));
        assertTrue(active(Map.of("usage.funnel.enabled", "on", "usage.enabled", "1")));
        assertFalse(active(Map.of("usage.insights.enabled", "off")));
        assertFalse(active(Map.of("usage.insights.enabled", "0")));
        assertFalse(active(Map.of("usage.insights.enabled", "")));
        assertFalse(active(Map.of("usage.insights.enabled", "maybe")), "unknown value → off");
        assertFalse(active(Map.of("usage.funnel.enabled", "true", "usage.enabled", "no")));
        assertFalse(InsightsSchedulerCondition.isActive(null));

        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(new MockEnvironment().withProperty("usage.insights.enabled", "true"));
        assertTrue(new InsightsSchedulerCondition().matches(context, null));
    }

    @Test
    @DisplayName("Spring wiring: the beans start in a context (one @Autowired constructor each) and follow the condition")
    void theBeansWireInASpringContext() {
        ApplicationContextRunner runner = wiringRunner();

        runner.withPropertyValues("usage.insights.enabled=true").run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(InsightsScheduler.class).size());
            InsightsRetentionJob job = context.getBean(InsightsRetentionJob.class);
            assertFalse(job.run(), "the job defers to the active scheduler");
        });
        runner.withPropertyValues("usage.insights.enabled=1").run(context ->
            assertEquals(1, context.getBeansOfType(InsightsScheduler.class).size(),
                "§5.1: '1' switches the bean on exactly like it switches the page on"));
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.getBeansOfType(InsightsScheduler.class).isEmpty(), "delivery default: inactive");
            assertEquals(1, context.getBeansOfType(InsightsRetentionJob.class).size(), "retention runs unconditionally");
            assertEquals(1, context.getBeansOfType(InsightsAggregator.class).size());
        });
    }

    /**
     * NFR-Datenschutz-7 (security finding): scheduler active, {@code USAGE_INSIGHTS_ROLLUP_CRON=-}
     * — neither rollup nor the rollup-cron retention run is registered, yet the fixed daily
     * retention run is, and it compacts and purges. / Rollup-Cron aus, Aufbewahrung läuft trotzdem.
     */
    @Test
    @DisplayName("NFR-Datenschutz-7: rollup cron '-' + active scheduler → the daily retention still compacts and purges")
    void theRetentionRunsWhileTheRollupCronIsSwitchedOff() {
        // The production constructor uses the system clock — the fixture is relative to today.
        LocalDate today = LocalDate.now(BERLIN);
        YearMonth closed = YearMonth.from(today).minusMonths(2);
        LocalDate closedDay = closed.atDay(3);
        LocalDate expired = today.minusMonths(30);
        SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
        days.put(expired, bookingDay(1L));
        days.put(closedDay, bookingDay(4L));
        SortedMap<LocalDate, Map<String, BranchCounts>> branchDays = new TreeMap<>();
        branchDays.put(closedDay, Map.of(BRANCH_A, counts(4L)));
        givenBlob(new InsightsHistory(1, null, expired, null, null, days, branchDays, new TreeMap<>()));

        wiringRunner().withUserConfiguration(SchedulingOn.class)
            .withPropertyValues("usage.insights.enabled=true", "usage.insights.rollup-cron=-",
                "usage.zone=Europe/Berlin")
            .run(context -> {
                assertNull(context.getStartupFailure());
                assertEquals(1, context.getBeansOfType(InsightsScheduler.class).size(), "scheduler active");
                List<ScheduledTask> tasks = new ArrayList<>();
                context.getBeansOfType(ScheduledTaskHolder.class).values()
                    .forEach(holder -> tasks.addAll(holder.getScheduledTasks()));
                assertTrue(tasks.stream().noneMatch(t -> target(t).endsWith(".rollUp")
                    || target(t).endsWith(".scheduledRun")), "'-' registers neither: " + tasks);
                List<ScheduledTask> daily = tasks.stream()
                    .filter(t -> target(t).endsWith("InsightsRetentionJob.dailyRun")).toList();
                assertEquals(1, daily.size(), "the daily retention run is always registered: " + tasks);
                assertEquals(InsightsRetentionJob.DAILY_CRON, ((CronTask) daily.getFirst().getTask()).getExpression());

                daily.getFirst().getTask().getRunnable().run();
            });

        assertEquals(1, saved.size(), "compaction and purge written once");
        InsightsHistory written = saved.getFirst();
        assertEquals(4L, written.branchMonths().get(closed).get(BRANCH_A).bookings(), "closed month compacted");
        assertTrue(written.branchDays().isEmpty(), "no day-exact branch value of a compacted month");
        assertFalse(written.days().containsKey(expired), "older than 24 months purged");
        assertEquals(List.of("\"v0\""), savedEtags, "If-Match with the ETag of the read");
    }

    /**
     * §14.1: the startup load runs before the E2E seed loader ({@code LOWEST_PRECEDENCE}) — also
     * when the seed bean is registered first. Without the order the blob view would overwrite the
     * seed. / Der Start-Ladevorgang läuft deterministisch vor dem Seed.
     */
    @Test
    @DisplayName("§14.1: onStartup is ordered HIGHEST_PRECEDENCE — a lowest-precedence seed listener always wins")
    void theStartupLoadRunsBeforeTheSeedLoader() throws NoSuchMethodException {
        assertEquals(Ordered.HIGHEST_PRECEDENCE,
            InsightsScheduler.class.getMethod("onStartup").getAnnotation(Order.class).value());
        assertEquals(CapacityScanScheduler.class.getMethod("onStartup").getAnnotation(Order.class).value(),
            InsightsScheduler.class.getMethod("onStartup").getAnnotation(Order.class).value(),
            "same position as the capacity scheduler");
        givenBlob(history(bookingDays(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 18), Set.of()),
            LocalDate.of(2026, 10, 5), null, null));

        new ApplicationContextRunner()
            .withUserConfiguration(LateSeed.class)
            .withBean(InsightsBlobClient.class, () -> blobClient)
            .withBean(LogAnalyticsClient.class, () -> mock(LogAnalyticsClient.class))
            .withBean(SetupService.class, () -> setupService)
            .withBean(SoftnetApiClient.class, () -> mock(SoftnetApiClient.class))
            .withBean(InsightsStore.class, InsightsStore::new)
            .withBean(UsageStatsProperties.class, () -> usage(true))
            .withBean(UsageInsightsProperties.class, () -> new UsageInsightsProperties(true, null, "-", null))
            .withBean(UsageFunnelProperties.class, () -> new UsageFunnelProperties(false, null))
            .withBean(UsageDropoffProperties.class, () -> new UsageDropoffProperties(false, null))
            .withUserConfiguration(ServiceGroupClassifier.class, InsightsAggregator.class,
                InsightsRetentionJob.class, InsightsScheduler.class)
            .withPropertyValues("usage.insights.enabled=true")
            .run(context -> {
                assertNull(context.getStartupFailure());
                context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0],
                    context.getSourceApplicationContext(), Duration.ZERO));

                InsightsStore contextStore = context.getBean(InsightsStore.class);
                LateSeed seed = context.getBean(LateSeed.class);
                assertTrue(seed.sawTheSchedulerView, "the scheduler had already published its view");
                assertSame(seed.seeded, contextStore.current(), "the seed runs last and stays");
            });
        verify(blobClient).load();
    }

    // ---- fixtures / Testdaten ----

    /** Scheduling on, so {@code @Scheduled} tasks are registered in the runner. */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingOn {
    }

    /**
     * Stands in for {@code StatisticsE2eSeedLoader}: a lowest-precedence startup listener that
     * replaces the view. Registered before the scheduler on purpose. / Ersatz für den Seed-Loader.
     */
    static class LateSeed {
        private final InsightsStore store;
        private InsightsViewModel seeded;
        private boolean sawTheSchedulerView;

        LateSeed(InsightsStore store) {
            this.store = store;
        }

        @EventListener(ApplicationReadyEvent.class)
        @Order(Ordered.LOWEST_PRECEDENCE)
        public void seed() {
            sawTheSchedulerView = store.hasViewModel();
            seeded = InsightsViewModel.build(InsightsHistory.empty(), Map.of(), LocalDate.of(2026, 10, 20),
                true, false, false);
            store.update(seeded, null);
        }
    }

    /** The context of the wiring tests: real job beans over mocked clients. */
    private ApplicationContextRunner wiringRunner() {
        return new ApplicationContextRunner()
            .withBean(InsightsBlobClient.class, () -> blobClient)
            .withBean(LogAnalyticsClient.class, () -> mock(LogAnalyticsClient.class))
            .withBean(SetupService.class, () -> setupService)
            .withBean(SoftnetApiClient.class, () -> mock(SoftnetApiClient.class))
            .withBean(InsightsStore.class, InsightsStore::new)
            .withBean(UsageStatsProperties.class, () -> usage(true))
            .withBean(UsageInsightsProperties.class, () -> new UsageInsightsProperties(true, null, "-", null))
            .withBean(UsageFunnelProperties.class, () -> new UsageFunnelProperties(false, null))
            .withBean(UsageDropoffProperties.class, () -> new UsageDropoffProperties(false, null))
            .withUserConfiguration(ServiceGroupClassifier.class, InsightsAggregator.class,
                InsightsRetentionJob.class, InsightsScheduler.class);
    }

    /** Qualified name of a scheduled method, e.g. {@code …InsightsRetentionJob.dailyRun}. */
    private static String target(ScheduledTask task) {
        return String.valueOf(task.getTask().getRunnable());
    }

    /** Switch combination under test / Schalter-Kombination. */
    private record Switches(boolean insights, boolean usage, boolean funnel, boolean dropoff,
                            LocalDate insightsSince, LocalDate funnelSince, LocalDate dropoffSince) {
        static Switches bookings(LocalDate since) {
            return new Switches(true, true, false, false, since, null, null);
        }
    }

    private InsightsScheduler scheduler(Switches s) {
        return new InsightsScheduler(aggregator, blobClient, store, retention(), setupService, usage(s.usage()),
            new UsageInsightsProperties(s.insights(), s.insightsSince(), "-", null),
            new UsageFunnelProperties(s.funnel(), s.funnelSince()),
            new UsageDropoffProperties(s.dropoff(), s.dropoffSince()),
            clock, 0, sleeps::add);
    }

    private InsightsScheduler realScheduler(LogAnalyticsClient logClient, ServiceGroupClassifier classifier,
                                            Switches s) {
        InsightsAggregator real = new InsightsAggregator(logClient, setupService, classifier,
            InsightsAggregatorTest.properties("Europe/Berlin"));
        return new InsightsScheduler(real, blobClient, store, retention(), setupService, usage(s.usage()),
            new UsageInsightsProperties(s.insights(), s.insightsSince(), "-", null),
            new UsageFunnelProperties(s.funnel(), s.funnelSince()),
            new UsageDropoffProperties(s.dropoff(), s.dropoffSince()),
            clock, 0, sleeps::add);
    }

    @SuppressWarnings("unchecked")
    private InsightsRetentionJob retention() {
        return new InsightsRetentionJob(blobClient, usage(true), mock(ObjectProvider.class), clock, sleeps::add);
    }

    private static UsageStatsProperties usage(boolean enabled) {
        return new UsageStatsProperties(enabled, "0 */15 * * * *", "0 30 3 * * *", "Europe/Berlin", null, 24, 60,
            new UsageStatsProperties.Storage("acct", "usage-stats", "usage-daily.json"));
    }

    /** In-memory blob: load returns the last saved state / Blob-Fake im Speicher. */
    private void givenBlob(InsightsHistory initial) {
        stored = initial;
        etag = "\"v0\"";
        lenient().when(blobClient.isConfigured()).thenReturn(true);
        lenient().when(blobClient.load()).thenAnswer(invocation -> new InsightsBlobClient.Loaded(stored, etag, true));
        lenient().doAnswer(invocation -> {
            stored = invocation.getArgument(0);
            saved.add(stored);
            savedEtags.add(invocation.getArgument(1));
            etag = "\"v" + saved.size() + "\"";
            return null;
        }).when(blobClient).save(any(), any());
    }

    private static InsightsHistory history(SortedMap<LocalDate, InsightsDay> days, LocalDate since,
                                           LocalDate funnelSince, LocalDate dropoffSince) {
        SortedMap<LocalDate, Map<String, BranchCounts>> branchDays = new TreeMap<>();
        days.forEach((day, value) -> {
            if (value.bookings() != null) {
                branchDays.put(day, Map.of(BRANCH_A, counts(value.bookings())));
            }
        });
        return new InsightsHistory(1, Instant.parse("2026-10-01T01:50:00Z"), since, funnelSince, dropoffSince,
            days, branchDays, new TreeMap<>());
    }

    private static SortedMap<LocalDate, InsightsDay> bookingDays(LocalDate from, LocalDate to, Set<LocalDate> gaps) {
        SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (!gaps.contains(day)) {
                days.put(day, bookingDay(day.getDayOfMonth()));
            }
        }
        return days;
    }

    private static SortedMap<LocalDate, InsightsDay> funnelDays(LocalDate from, LocalDate to) {
        SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            days.put(day, aggregated(day, false, true, false).global());
        }
        return days;
    }

    private static InsightsDay bookingDay(long n) {
        return new InsightsDay(n, dayparts(n), Map.of("MO", slots(n)), 0L, leadTimes(n),
            Map.of("inspection", n), 0L, n * 3, null);
    }

    /** Deterministic aggregate per day: n = day of month / deterministisches Tagesergebnis. */
    private static AggregatedDay aggregated(LocalDate day, boolean bookings, boolean funnel, boolean dropoff) {
        long n = day.getDayOfMonth();
        FunnelDay funnelDay = null;
        if (funnel) {
            Map<String, FunnelCounts> devices = new LinkedHashMap<>();
            devices.put("mobile", new FunnelCounts(n * 10, n * 5, n * 4, n * 3, n));
            devices.put("tablet", new FunnelCounts(0L, 0L, 0L, 0L, 0L));
            devices.put("desktop", new FunnelCounts(0L, 0L, 0L, 0L, 0L));
            devices.put("unknown", new FunnelCounts(0L, 0L, 0L, 0L, 0L));
            DropoffDay dropoffDay = dropoff
                ? new DropoffDay(Map.of("quiet-1", Map.of("1", n * 9, "2", 0L, "3", 0L, "4", 0L)),
                    Map.of("quiet-1", Map.of("mobile", n * 9)))
                : null;
            funnelDay = new FunnelDay(new FunnelCounts(n * 10, n * 5, n * 4, n * 3, n), devices, dropoffDay);
        }
        InsightsDay global = bookings
            ? new InsightsDay(n, dayparts(n), Map.of("MO", slots(n)), 0L, leadTimes(n), Map.of("inspection", n), 0L,
                n * 3, funnelDay, Map.of("default", n - 1, "recall", 1L))
            : new InsightsDay(null, null, null, null, null, null, null, null, funnelDay);
        return new AggregatedDay(global, bookings ? Map.of(BRANCH_A, counts(n)) : Map.of());
    }

    private static Map<String, Long> dayparts(long n) {
        return Map.of("day", n, "evening", 0L, "night", 0L);
    }

    private static Map<String, Long> slots(long n) {
        return Map.of("early", 0L, "morning", n, "midday", 0L, "afternoon", 0L, "late", 0L);
    }

    private static Map<String, Long> leadTimes(long n) {
        return Map.of("0-1", 0L, "2-3", n, "4-7", 0L, "8-14", 0L, "15-30", 0L, "31+", 0L, "unknown", 0L);
    }

    private static BranchCounts counts(Long bookings) {
        return new BranchCounts(bookings, bookings * 3, Map.of("inspection", bookings), 0L, 0L, 0L);
    }

    private static InsightsHistory octoberWithOldDays() {
        SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
        days.put(LocalDate.of(2024, 11, 1), bookingDay(1L));
        days.put(LocalDate.of(2026, 10, 1), bookingDay(2L));
        days.put(LocalDate.of(2026, 10, 15), bookingDay(3L));
        SortedMap<LocalDate, Map<String, BranchCounts>> branchDays = new TreeMap<>();
        branchDays.put(LocalDate.of(2026, 10, 1), Map.of(BRANCH_A, counts(2L)));
        branchDays.put(LocalDate.of(2026, 10, 15), Map.of(BRANCH_A, counts(3L)));
        return new InsightsHistory(1, null, LocalDate.of(2026, 10, 1), null, null, days, branchDays, new TreeMap<>());
    }

    private static boolean active(Map<String, String> properties) {
        MockEnvironment environment = new MockEnvironment();
        properties.forEach(environment::setProperty);
        return InsightsSchedulerCondition.isActive(environment);
    }

    private static List<JsonNode> json(String array) {
        try {
            List<JsonNode> nodes = new ArrayList<>();
            new ObjectMapper().readTree(array).forEach(nodes::add);
            return nodes;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> messages(Level level) {
        return logs.list.stream()
            .filter(event -> event.getLevel() == level)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }

    private long count(Level level, String fragment) {
        return messages(level).stream().filter(m -> m.contains(fragment)).count();
    }

    /** A movable Europe/Berlin clock / eine verstellbare Berliner Uhr. */
    private static final class BerlinClock extends Clock {
        private Instant now;

        BerlinClock(LocalDateTime start) {
            set(start);
        }

        void set(LocalDateTime local) {
            now = local.atZone(BERLIN).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return BERLIN;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
