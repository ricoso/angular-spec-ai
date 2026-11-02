package com.autohaus.backend.usage.insights;

import com.autohaus.backend.common.filter.RequestIdFilter;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.usage.BlobAccess;
import com.autohaus.backend.usage.UsageBlobClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Compaction and retention of {@code usage-insights.json} independent of the switches (REQ-038
 * TC-23, Step 4, NFR-Datenschutz-7, AC-24) / Verdichtung und Aufbewahrung unabhängig von den
 * Schaltern.
 *
 * <p>{@link InsightsRetentionJob#apply} is asserted as a pure function; {@link
 * InsightsRetentionJob#run()} against a mocked {@link InsightsBlobClient} and once over the wire
 * (only {@code usage-insights.json} is ever requested — never {@code capacity-daily.json}). The
 * job has no dependency on Log Analytics or SoftNet at all, so neither can be called. / Kein
 * KQL-, kein SoftNet-Aufruf — die Abhängigkeiten existieren gar nicht.
 *
 * <p>{@link InsightsRetentionJob#dailyRun()} is the non-disableable guarantee of
 * NFR-Datenschutz-7: a fixed cron that compacts and purges even while an insights scheduler is
 * active and its rollup cron is {@code "-"} (the Spring-level proof lives in
 * {@code InsightsSchedulerTest#theRetentionRunsWhileTheRollupCronIsSwitchedOff}). / Der feste
 * Tageslauf ist nicht abschaltbar.
 */
@ExtendWith(MockitoExtension.class)
class InsightsRetentionJobTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final String BRANCH_A = "68f0a1b2c3d4e5f6a7b8c9d0";
    private static final String BRANCH_B = "68f0a1b2c3d4e5f6a7b8c9d4";
    private static final String ETAG = "\"0x8DC3\"";

    @Mock private InsightsBlobClient blobClient;
    @Mock private ObjectProvider<InsightsScheduler> schedulerProvider;

    private final List<java.time.Duration> sleeps = new ArrayList<>();

    @BeforeEach
    void clearMdc() {
        MDC.clear();
    }

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    // ---- apply: compaction / Verdichtung ----

    @Test
    @DisplayName("TC-23: on 07.11. the October branch days stay untouched")
    void doesNotCompactBeforeTheEighth() {
        InsightsRetentionJob.Result result = job(24).apply(octoberHistory(), LocalDate.of(2026, 11, 7));

        assertFalse(result.changed());
        assertEquals(0, result.compactedMonths());
        assertEquals(3, result.history().branchDays().size());
        assertTrue(result.history().branchMonths().isEmpty());
        assertFalse(InsightsRetentionJob.isDue(YearMonth.of(2026, 10), LocalDate.of(2026, 11, 7)));
    }

    @Test
    @DisplayName("TC-23: on 08.11. branchMonths[2026-10] = sum, the October branch days are deleted")
    void compactsTheClosedMonthOnTheEighth() {
        InsightsRetentionJob.Result result = job(24).apply(octoberHistory(), LocalDate.of(2026, 11, 8));

        assertTrue(result.changed());
        assertEquals(1, result.compactedMonths());
        assertEquals(0, result.purged());
        InsightsHistory history = result.history();
        assertEquals(List.of(LocalDate.of(2026, 11, 1)), List.copyOf(history.branchDays().keySet()),
            "November stays day-exact until 08.12.");
        Map<String, BranchCounts> october = history.branchMonths().get(YearMonth.of(2026, 10));
        assertEquals(new BranchCounts(5L, 30L, Map.of("inspection", 3L, "extras", 1L, "pickup", 2L), 1L, 2L, 3L),
            october.get(BRANCH_A));
        assertEquals(new BranchCounts(4L, 7L, Map.of("tire-change", 4L), 0L, 1L, null), october.get(BRANCH_B),
            "null + null stays null („—\"), null + x = x");
        assertEquals(history.days(), octoberHistory().days(), "global days are not compacted");
        assertEquals(LocalDate.of(2026, 9, 1), history.collectingSince(), "other fields kept");
        assertTrue(InsightsRetentionJob.isDue(YearMonth.of(2026, 10), LocalDate.of(2026, 11, 8)));
    }

    @Test
    @DisplayName("§5.8: a month that was missed for longer is compacted late, never twice")
    void compactsOverdueMonthsAndNeverSumsAFrozenMonthTwice() {
        SortedMap<LocalDate, Map<String, BranchCounts>> branchDays = new TreeMap<>();
        branchDays.put(LocalDate.of(2026, 8, 30), Map.of(BRANCH_A, counts(2L)));
        branchDays.put(LocalDate.of(2026, 9, 3), Map.of(BRANCH_A, counts(1L)));
        SortedMap<YearMonth, Map<String, BranchCounts>> branchMonths = new TreeMap<>();
        branchMonths.put(YearMonth.of(2026, 9), Map.of(BRANCH_A, counts(40L)));
        InsightsHistory history = history(new TreeMap<>(), branchDays, branchMonths);

        InsightsRetentionJob.Result result = job(24).apply(history, LocalDate.of(2026, 11, 8));

        assertEquals(1, result.compactedMonths(), "only August is new");
        assertEquals(1, result.purged(), "the stray September day is removed");
        assertTrue(result.history().branchDays().isEmpty());
        assertEquals(2L, result.history().branchMonths().get(YearMonth.of(2026, 8)).get(BRANCH_A).bookings());
        assertEquals(40L, result.history().branchMonths().get(YearMonth.of(2026, 9)).get(BRANCH_A).bookings(),
            "frozen month keeps its value");
    }

    // ---- apply: retention / Aufbewahrung ----

    @Test
    @DisplayName("TC-23: days, branch days and branch months older than 24 months are purged")
    void purgesEverythingBeyondTheRetention() {
        SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
        days.put(LocalDate.of(2024, 11, 7), day(1L));
        days.put(LocalDate.of(2024, 11, 8), day(2L));
        days.put(LocalDate.of(2026, 11, 7), day(3L));
        SortedMap<LocalDate, Map<String, BranchCounts>> branchDays = new TreeMap<>();
        branchDays.put(LocalDate.of(2026, 11, 2), Map.of(BRANCH_A, counts(1L)));
        SortedMap<YearMonth, Map<String, BranchCounts>> branchMonths = new TreeMap<>();
        branchMonths.put(YearMonth.of(2024, 10), Map.of(BRANCH_A, counts(10L)));
        branchMonths.put(YearMonth.of(2024, 11), Map.of(BRANCH_A, counts(11L)));
        branchMonths.put(YearMonth.of(2026, 9), Map.of(BRANCH_A, counts(12L)));

        InsightsRetentionJob.Result result = job(24).apply(history(days, branchDays, branchMonths),
            LocalDate.of(2026, 11, 8));

        assertEquals(2, result.purged());
        assertEquals(List.of(LocalDate.of(2024, 11, 8), LocalDate.of(2026, 11, 7)),
            List.copyOf(result.history().days().keySet()));
        assertEquals(List.of(YearMonth.of(2024, 11), YearMonth.of(2026, 9)),
            List.copyOf(result.history().branchMonths().keySet()));
        assertEquals(1, result.history().branchDays().size());
    }

    @Test
    @DisplayName("NFR-Datenschutz-7: stray branch days beyond the retention are purged as well")
    void purgesOldBranchDaysOfAFrozenMonth() {
        SortedMap<LocalDate, Map<String, BranchCounts>> branchDays = new TreeMap<>();
        branchDays.put(LocalDate.of(2024, 1, 5), Map.of(BRANCH_A, counts(1L)));
        SortedMap<YearMonth, Map<String, BranchCounts>> months = new TreeMap<>();
        months.put(YearMonth.of(2024, 1), Map.of(BRANCH_A, counts(1L)));

        InsightsRetentionJob.Result result = job(24).apply(history(new TreeMap<>(), branchDays, months),
            LocalDate.of(2026, 11, 8));

        assertTrue(result.history().branchDays().isEmpty());
        assertTrue(result.history().branchMonths().isEmpty());
        assertEquals(2, result.purged());
    }

    @Test
    @DisplayName("retention 0 disables the purge (pattern UsageStatsScheduler)")
    void aRetentionOfZeroDisablesThePurge() {
        SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
        days.put(LocalDate.of(2020, 1, 1), day(1L));

        InsightsRetentionJob.Result result = job(0).apply(history(days, new TreeMap<>(), new TreeMap<>()),
            LocalDate.of(2026, 11, 8));

        assertFalse(result.changed());
        assertEquals(1, result.history().days().size());
        assertFalse(new InsightsRetentionJob(blobClient, null, schedulerProvider).apply(
            history(days, new TreeMap<>(), new TreeMap<>()), LocalDate.of(2026, 11, 8)).changed());
    }

    @Test
    @DisplayName("apply is null-safe for a missing history and missing maps")
    void applyIsNullSafe() {
        InsightsRetentionJob.Result empty = job(24).apply(null, LocalDate.of(2026, 11, 8));
        assertFalse(empty.changed());
        assertTrue(empty.history().days().isEmpty());

        java.util.HashMap<String, BranchCounts> withNull = new java.util.HashMap<>();
        withNull.put(BRANCH_A, null);
        SortedMap<LocalDate, Map<String, BranchCounts>> branchDays = new TreeMap<>();
        branchDays.put(LocalDate.of(2026, 10, 1), withNull);
        branchDays.put(LocalDate.of(2026, 10, 2), null);
        InsightsHistory odd = new InsightsHistory(1, null, null, null, null, null, branchDays, null);
        InsightsRetentionJob.Result result = job(24).apply(odd, LocalDate.of(2026, 11, 8));
        assertEquals(Map.of(), result.history().branchMonths().get(YearMonth.of(2026, 10)));
    }

    @Test
    @DisplayName("BR-18: summing merges group maps and keeps unknown keys")
    void sumsGroupMapsNullSafely() {
        BranchCounts a = new BranchCounts(1L, null, Map.of("inspection", 1L), null, 1L, 1L);
        BranchCounts b = new BranchCounts(2L, 3L, null, 1L, null, 0L);
        java.util.HashMap<String, Long> withNull = new java.util.HashMap<>();
        withNull.put("inspection", null);
        withNull.put("custom", 2L);
        BranchCounts c = new BranchCounts(null, null, withNull, null, null, null);

        BranchCounts sum = InsightsRetentionJob.add(InsightsRetentionJob.add(a, b), c);

        assertEquals(3L, sum.bookings());
        assertEquals(3L, sum.serviceViews());
        assertEquals(Map.of("inspection", 1L, "custom", 2L), sum.groups());
        assertEquals(1L, sum.mobility());
        assertEquals(1L, sum.offHours());
        assertEquals(1L, sum.slotLate());
    }

    // ---- run(): own run / eigener Lauf ----

    @Test
    @DisplayName("TC-23: with an active insights scheduler the job does nothing (no second write)")
    void doesNothingWhileTheSchedulerIsActive() {
        when(schedulerProvider.getIfAvailable()).thenReturn(mock(InsightsScheduler.class));

        assertFalse(job(24).run());

        verifyNoInteractions(blobClient);
    }

    @Test
    @DisplayName("E4: without a storage account the job does nothing")
    void doesNothingWithoutStorage() {
        when(blobClient.isConfigured()).thenReturn(false);

        assertFalse(job(24).run());

        verify(blobClient, never()).load();
    }

    @Test
    @DisplayName("TC-23: switches off, storage set → compacts and purges on its own (If-Match, updatedAt)")
    void compactsAndPurgesOnItsOwnWhenTheSwitchesAreOff() {
        givenBlob(octoberHistory());
        List<String> traceIds = new ArrayList<>();
        when(blobClient.load()).thenAnswer(invocation -> {
            traceIds.add(MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY));
            return new InsightsBlobClient.Loaded(octoberHistory(), ETAG, true);
        });

        InsightsRetentionJob job = job(24, LocalDate.of(2026, 11, 8));
        assertTrue(job.run());

        ArgumentCaptor<InsightsHistory> saved = ArgumentCaptor.forClass(InsightsHistory.class);
        verify(blobClient).save(saved.capture(), eq(ETAG));
        assertTrue(saved.getValue().branchMonths().containsKey(YearMonth.of(2026, 10)));
        assertEquals(clockAt(LocalDate.of(2026, 11, 8)).instant(), saved.getValue().updatedAt());
        assertEquals(List.of(InsightsScheduler.TRACE_ID), traceIds, "BR-15 marker during the run");
        assertNull(MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY), "restored afterwards");
    }

    @Test
    @DisplayName("Step 4: writes only on a change")
    void writesOnlyOnAChange() {
        givenBlob(octoberHistory());

        assertFalse(job(24, LocalDate.of(2026, 11, 7)).run());

        verify(blobClient, never()).save(any(), any());
    }

    @Test
    @DisplayName("E6: unusable content is never overwritten")
    void neverOverwritesUnusableContent() {
        when(blobClient.isConfigured()).thenReturn(true);
        when(blobClient.load()).thenReturn(new InsightsBlobClient.Loaded(InsightsHistory.empty(), ETAG, false));

        assertFalse(job(24, LocalDate.of(2026, 11, 8)).run());

        verify(blobClient, never()).save(any(), any());
    }

    @Test
    @DisplayName("E4: an unreachable blob is skipped without throwing")
    void skipsAnUnreachableBlob() {
        when(blobClient.isConfigured()).thenReturn(true);
        when(blobClient.load()).thenThrow(new UsageBlobClient.BlobUnavailableException("403"));

        assertFalse(assertDoesNotThrow(() -> job(24).run()));
    }

    @Test
    @DisplayName("E5: a 412 re-reads and applies the rule again; at most 3 attempts")
    void retriesAfterAConflictAtMostThreeTimes() {
        givenBlob(octoberHistory());
        doThrow(new UsageBlobClient.BlobPreconditionFailedException("412"))
            .doNothing()
            .when(blobClient).save(any(), any());

        assertTrue(job(24, LocalDate.of(2026, 11, 8)).run());
        verify(blobClient, times(2)).load();
        verify(blobClient, times(2)).save(any(), eq(ETAG));
        assertEquals(1, sleeps.size(), "short backoff between the attempts");

        doThrow(new UsageBlobClient.BlobPreconditionFailedException("412"))
            .when(blobClient).save(any(), any());
        assertFalse(job(24, LocalDate.of(2026, 11, 8)).run());
        verify(blobClient, times(5)).save(any(), any());
    }

    @Test
    @DisplayName("E4: a failing write is reported, never thrown")
    void aFailingWriteIsReported() {
        givenBlob(octoberHistory());
        doThrow(new UsageBlobClient.BlobUnavailableException("network")).when(blobClient).save(any(), any());

        assertFalse(job(24, LocalDate.of(2026, 11, 8)).run());
    }

    @Test
    @DisplayName("robustness: an unexpected exception keeps the MDC clean and returns false")
    void anUnexpectedExceptionIsContained() {
        when(blobClient.isConfigured()).thenReturn(true);
        when(blobClient.load()).thenThrow(new IllegalStateException("boom"));
        MDC.put(RequestIdFilter.TRACE_ID_MDC_KEY, "outer");

        assertFalse(job(24).run());

        assertEquals("outer", MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY), "previous trace id restored");
    }

    @Test
    @DisplayName("Step 4: the startup run and the cron run both delegate to run()")
    void startupAndCronDelegateToRun() {
        when(blobClient.isConfigured()).thenReturn(false);
        InsightsRetentionJob job = job(24);

        job.onStartup();
        job.scheduledRun();

        verify(blobClient, times(2)).isConfigured();
    }

    // ---- dailyRun(): the non-disableable run / der nicht abschaltbare Lauf ----

    @Test
    @DisplayName("NFR-Datenschutz-7: the daily run compacts and purges even while the insights scheduler is active")
    void theDailyRunIgnoresAnActiveScheduler() {
        SortedMap<LocalDate, InsightsDay> days = new TreeMap<>(octoberHistory().days());
        days.put(LocalDate.of(2024, 10, 1), day(9L));
        givenBlob(new InsightsHistory(1, null, LocalDate.of(2024, 10, 1), null, null, days,
            octoberHistory().branchDays(), new TreeMap<>()));
        when(schedulerProvider.getIfAvailable()).thenReturn(mock(InsightsScheduler.class));
        InsightsRetentionJob job = job(24, LocalDate.of(2026, 11, 8));

        assertFalse(job.run(), "the startup/rollup-cron run defers to the scheduler");
        verify(blobClient, never()).load();

        job.dailyRun();

        ArgumentCaptor<InsightsHistory> saved = ArgumentCaptor.forClass(InsightsHistory.class);
        verify(blobClient).save(saved.capture(), eq(ETAG));
        assertTrue(saved.getValue().branchMonths().containsKey(YearMonth.of(2026, 10)), "October compacted");
        assertFalse(saved.getValue().branchDays().containsKey(LocalDate.of(2026, 10, 15)),
            "no day-exact October branch value after the 8th");
        assertFalse(saved.getValue().days().containsKey(LocalDate.of(2024, 10, 1)), "older than 24 months purged");
    }

    @Test
    @DisplayName("NFR-Datenschutz-7: the daily cron is a fixed constant — no property can switch it off")
    void theDailyCronIsFixed() throws NoSuchMethodException {
        Scheduled scheduled = InsightsRetentionJob.class.getMethod("dailyRun").getAnnotation(Scheduled.class);

        assertEquals("0 15 4 * * *", InsightsRetentionJob.DAILY_CRON);
        assertEquals(InsightsRetentionJob.DAILY_CRON, scheduled.cron(), "no ${…} placeholder, no '-'");
        assertEquals("${usage.zone:Europe/Berlin}", scheduled.zone(), "Berlin day like every other job");
    }

    @Test
    @DisplayName("Step 4: the daily run is idempotent — a clean history is never written again")
    void theDailyRunWritesOnlyOnAChange() {
        InsightsRetentionJob.Result compacted = job(24).apply(octoberHistory(), LocalDate.of(2026, 11, 8));
        givenBlob(compacted.history());

        job(24, LocalDate.of(2026, 11, 8)).dailyRun();
        job(24, LocalDate.of(2026, 11, 9)).dailyRun();

        verify(blobClient, times(2)).load();
        verify(blobClient, never()).save(any(), any());
    }

    @Test
    @DisplayName("E4/E5: the daily run needs storage and keeps If-Match with at most 3 attempts")
    void theDailyRunKeepsTheWriteRules() {
        when(blobClient.isConfigured()).thenReturn(false);
        assertFalse(job(24).enforce());
        verify(blobClient, never()).load();

        givenBlob(octoberHistory());
        doThrow(new UsageBlobClient.BlobPreconditionFailedException("412")).when(blobClient).save(any(), any());
        assertFalse(job(24, LocalDate.of(2026, 11, 8)).enforce());
        verify(blobClient, times(InsightsRetentionJob.MAX_WRITE_ATTEMPTS)).save(any(), eq(ETAG));
        verifyNoInteractions(schedulerProvider);
    }

    @Test
    @DisplayName("TC-23: over the wire only usage-insights.json is read and written — never capacity-daily.json")
    void touchesOnlyTheInsightsBlobOverTheWire() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BlobAccess access = new BlobAccess(InsightsBlobClientTest.properties("acct"), "usage-insights.json", builder) {
            @Override
            protected String identityEndpoint() {
                return "http://identity.local/token";
            }

            @Override
            protected String identityHeader() {
                return "secret";
            }
        };
        String url = "https://acct.blob.core.windows.net/usage-stats/usage-insights.json";
        AtomicReference<String> written = new AtomicReference<>();
        server.expect(request -> assertTrue(request.getURI().toString().startsWith("http://identity.local/token")))
            .andRespond(withSuccess("{\"access_token\":\"t\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(url)).andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(InsightsBlobClient.serialize(octoberHistory()), MediaType.APPLICATION_JSON)
                .header("ETag", ETAG));
        server.expect(request -> assertTrue(request.getURI().toString().startsWith("http://identity.local/token")))
            .andRespond(withSuccess("{\"access_token\":\"t\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(url)).andExpect(method(HttpMethod.PUT))
            .andExpect(header("If-Match", ETAG))
            .andExpect(request -> written.set(((MockClientHttpRequest) request).getBodyAsString()))
            .andRespond(withStatus(HttpStatus.CREATED));

        InsightsRetentionJob job = new InsightsRetentionJob(new InsightsBlobClient(access), usage(24),
            schedulerProvider, clockAt(LocalDate.of(2026, 11, 8)), sleeps::add);
        assertTrue(job.run());

        server.verify();
        JsonNode json = new ObjectMapper().readTree(written.get());
        assertEquals(5, json.at("/branchMonths/2026-10/" + BRANCH_A + "/bookings").asInt());
        assertFalse(json.at("/branchDays").has("2026-10-01"));
    }

    // ---- fixtures / Testdaten ----

    private InsightsRetentionJob job(int retentionMonths) {
        return job(retentionMonths, LocalDate.of(2026, 11, 8));
    }

    private InsightsRetentionJob job(int retentionMonths, LocalDate today) {
        return new InsightsRetentionJob(blobClient, usage(retentionMonths), schedulerProvider, clockAt(today),
            sleeps::add);
    }

    private void givenBlob(InsightsHistory history) {
        when(blobClient.isConfigured()).thenReturn(true);
        when(blobClient.load()).thenAnswer(invocation -> new InsightsBlobClient.Loaded(history, ETAG, true));
    }

    private static Clock clockAt(LocalDate day) {
        return Clock.fixed(ZonedDateTime.of(day.atTime(3, 45), BERLIN).toInstant(), BERLIN);
    }

    static UsageStatsProperties usage(int retentionMonths) {
        return new UsageStatsProperties(true, "0 */15 * * * *", "0 30 3 * * *", "Europe/Berlin", null,
            retentionMonths, 60, new UsageStatsProperties.Storage("acct", "usage-stats", "usage-daily.json"));
    }

    /** Three October branch days and one November day / drei Oktober-Tage und ein November-Tag. */
    private static InsightsHistory octoberHistory() {
        SortedMap<LocalDate, Map<String, BranchCounts>> branchDays = new TreeMap<>();
        branchDays.put(LocalDate.of(2026, 10, 1), Map.of(
            BRANCH_A, new BranchCounts(2L, 10L, Map.of("inspection", 2L), 1L, 1L, 1L),
            BRANCH_B, new BranchCounts(1L, 3L, Map.of("tire-change", 1L), 0L, 0L, null)));
        branchDays.put(LocalDate.of(2026, 10, 15), Map.of(
            BRANCH_A, new BranchCounts(3L, 20L, Map.of("inspection", 1L, "extras", 1L, "pickup", 2L), 0L, 1L, 2L),
            BRANCH_B, new BranchCounts(3L, 4L, Map.of("tire-change", 3L), null, 1L, null)));
        branchDays.put(LocalDate.of(2026, 11, 1), Map.of(
            BRANCH_A, new BranchCounts(1L, 2L, Map.of(), 0L, 0L, 0L)));
        SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
        days.put(LocalDate.of(2026, 10, 1), day(3L));
        days.put(LocalDate.of(2026, 10, 15), day(6L));
        return new InsightsHistory(1, Instant.parse("2026-11-01T02:00:00Z"), LocalDate.of(2026, 9, 1), null,
            null, days, branchDays, new TreeMap<>());
    }

    private static InsightsHistory history(SortedMap<LocalDate, InsightsDay> days,
                                           SortedMap<LocalDate, Map<String, BranchCounts>> branchDays,
                                           SortedMap<YearMonth, Map<String, BranchCounts>> branchMonths) {
        return new InsightsHistory(1, null, null, null, null, days, branchDays, branchMonths);
    }

    private static InsightsDay day(Long bookings) {
        return new InsightsDay(bookings, Map.of(), Map.of(), 0L, Map.of(), Map.of(), 0L, 0L, null);
    }

    private static BranchCounts counts(Long bookings) {
        return new BranchCounts(bookings, 0L, Map.of(), 0L, 0L, 0L);
    }
}
