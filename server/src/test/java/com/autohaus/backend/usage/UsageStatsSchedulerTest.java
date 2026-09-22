package com.autohaus.backend.usage;

import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.reporting.LogAnalyticsClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The scheduler's two jobs (REQ-037 TC-8, TC-12) / die beiden Aufgaben des Schedulers: the live
 * refresh against Log Analytics and the nightly rollup with catch-up and retention purge.
 *
 * <p>All tests run with a fixed {@link Clock} and the jitter switched off through the
 * package-private constructor, so nothing sleeps and „today" is 21.09.2026. / Fester Clock, kein
 * Jitter.
 */
@ExtendWith(MockitoExtension.class)
class UsageStatsSchedulerTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /** 21.09.2026, 03:30 Europe/Berlin — just after the nightly rollup window. */
    private static final Instant NOW = Instant.parse("2026-09-21T01:30:12Z");

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);
    private static final String ETAG = "\"0x8DC1\"";

    @Mock private UsageStatsAggregator aggregator;
    @Mock private UsageBlobClient blobClient;

    private final UsageStatsStore store = new UsageStatsStore();

    /** Flips the mocked Log Analytics between reachable and unreachable / E1 an- und abschalten. */
    private boolean liveAvailable = true;

    // ---- TC-8: Log Analytics not reachable / Log Analytics nicht erreichbar (E1) ----

    /**
     * TC-8/E1: the aggregator degrades to {@link Optional#empty()}, the scheduler must not rethrow.
     * The page keeps showing the history numbers and flags {@code liveAvailable == false}, which
     * renders as „Live-Daten derzeit nicht verfügbar" (asserted in {@code UsageSectionRendererTest})
     * — the missing source is named, never hidden behind zeros. / Kein Rethrow, Historie bleibt
     * sichtbar.
     */
    @Test
    void keepsTheHistoryWhenLogAnalyticsIsUnavailable() {
        SortedMap<LocalDate, DailyUsage> persisted = new TreeMap<>();
        persisted.put(LocalDate.of(2026, 9, 18), day(30));
        persisted.put(LocalDate.of(2026, 9, 19), day(40));
        givenHistory(persisted);
        when(aggregator.aggregateDailyUsage(any())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> scheduler(24).refreshCurrentUsage());

        UsageStats stats = store.current();
        assertFalse(stats.liveAvailable());
        assertTrue(stats.historyAvailable());
        assertEquals(70L, stats.month().visitCount(), "the history numbers stay visible");
        assertEquals(70L, stats.year().visitCount());
        assertEquals(LocalDate.of(2026, 9, 19), stats.lastRollupDay());
        assertNull(stats.liveAsOf(), "there has never been a successful refresh");
        // BR-9: the live edge is today plus every not-yet-rolled previous day — 19.09. is persisted.
        verify(aggregator).aggregateDailyUsage(LocalDate.of(2026, 9, 20));
        verify(aggregator).aggregateDailyUsage(TODAY);
        verify(aggregator, never()).aggregateDailyUsage(LocalDate.of(2026, 9, 19));
    }

    /**
     * TC-8/E1: even a {@code LogAnalyticsUnavailableException} escaping the aggregator must not
     * reach the scheduled method — one bad day never kills a run. / Eine durchgereichte Exception
     * stoppt den Lauf nicht.
     */
    @Test
    void doesNotRethrowWhenTheAggregatorItselfThrows() {
        givenHistory(new TreeMap<>());
        when(aggregator.aggregateDailyUsage(any()))
            .thenThrow(new LogAnalyticsClient.LogAnalyticsUnavailableException("token endpoint down"));

        assertDoesNotThrow(() -> scheduler(24).refreshCurrentUsage());

        assertFalse(store.current().liveAvailable());
    }

    /**
     * TC-8/E1: {@code liveAsOf} survives a failed refresh — the page says „Live-Daten derzeit nicht
     * verfügbar — Stand: &lt;letzter erfolgreicher Abruf&gt;" instead of silently presenting stale
     * numbers as current. / Der Zeitstempel des letzten erfolgreichen Abrufs bleibt erhalten.
     */
    @Test
    void keepsTheTimestampOfTheLastSuccessfulRefresh() {
        givenHistory(new TreeMap<>());
        when(aggregator.aggregateDailyUsage(any()))
            .thenAnswer(invocation -> liveAvailable ? Optional.of(day(12)) : Optional.empty());

        scheduler(24).refreshCurrentUsage();
        assertEquals(NOW, store.liveAsOf());

        liveAvailable = false;
        Instant later = NOW.plusSeconds(3600);
        scheduler(24, later).refreshCurrentUsage();

        UsageStats stats = store.current();
        assertFalse(stats.liveAvailable());
        assertEquals(NOW, stats.liveAsOf(), "the failed refresh must not advance the timestamp");
        assertEquals(NOW, store.liveAsOf());
    }

    // ---- TC-12: catch-up and retention / Catch-up und Retention (AC-9, NFR-Datenschutz-7) ----

    /**
     * TC-12/AC-9: the nightly run recomputes yesterday (idempotent replace, BR-8) and fills the
     * gaps of the last {@value UsageStatsScheduler#CATCH_UP_DAYS} days. Days outside that window
     * are <b>not</b> queried again — they are beyond the Log Analytics retention and would only
     * cost query time. At the same time every row older than {@code usage.retention-months} is
     * removed from the file. / Lücken im Fenster werden gefüllt, ältere Tage nicht erneut abgefragt.
     */
    @Test
    void catchesUpMissingDaysAndPurgesBeyondRetention() {
        SortedMap<LocalDate, DailyUsage> persisted = new TreeMap<>();
        // Continuous history 01.08.–20.09. with four holes; 15.08. lies outside the 25-day window.
        for (LocalDate day = LocalDate.of(2026, 8, 1); !day.isAfter(LocalDate.of(2026, 9, 20));
             day = day.plusDays(1)) {
            persisted.put(day, day(5));
        }
        Set<LocalDate> gaps = Set.of(
            LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 15));
        gaps.forEach(persisted::remove);
        LocalDate gapOutsideWindow = LocalDate.of(2026, 8, 15);
        persisted.remove(gapOutsideWindow);
        LocalDate expired = LocalDate.of(2026, 5, 10);
        persisted.put(expired, day(99));
        givenHistory(persisted);
        when(aggregator.aggregateDailyUsage(any())).thenReturn(Optional.of(day(7)));

        // retention 2 months → cutoff 21.07.2026: 10.05. must go, 01.08. must stay.
        scheduler(2).rollUpPreviousDay();

        // Exactly the three gaps plus yesterday — nothing older than 25 days.
        verify(aggregator, times(4)).aggregateDailyUsage(any());
        for (LocalDate gap : gaps) {
            verify(aggregator).aggregateDailyUsage(gap);
        }
        verify(aggregator).aggregateDailyUsage(LocalDate.of(2026, 9, 20));
        verify(aggregator, never()).aggregateDailyUsage(gapOutsideWindow);
        verify(aggregator, never()).aggregateDailyUsage(expired);

        Map<String, DailyUsage> written = captureWrittenHistory().days();
        for (LocalDate gap : gaps) {
            assertEquals(7L, written.get(gap.toString()).visits().total(), "gap " + gap + " filled");
        }
        assertEquals(7L, written.get("2026-09-20").visits().total(), "yesterday is always replaced");
        assertFalse(written.containsKey(expired.toString()), "rows beyond the retention are removed");
        assertTrue(written.containsKey("2026-08-01"), "rows inside the retention stay");
        assertFalse(written.containsKey(gapOutsideWindow.toString()),
            "a gap outside the window is not invented either");
    }

    /**
     * TC-12/NFR-Datenschutz-7: the retention purge runs <b>independently</b> of whether a new day
     * value could be determined. The deletion deadline must never pause unnoticed just because Log
     * Analytics is down. / Der Purge hängt nicht am Rollup-Erfolg.
     */
    @Test
    void purgesBeyondRetentionEvenWhenNoDayCouldBeAggregated() {
        SortedMap<LocalDate, DailyUsage> persisted = new TreeMap<>();
        persisted.put(LocalDate.of(2026, 5, 10), day(99));
        persisted.put(LocalDate.of(2026, 9, 19), day(30));
        persisted.put(LocalDate.of(2026, 9, 20), day(40));
        givenHistory(persisted);
        when(aggregator.aggregateDailyUsage(any())).thenReturn(Optional.empty());

        scheduler(2).rollUpPreviousDay();

        // The catch-up stops after three consecutive failed day queries instead of hammering Log
        // Analytics 25 times — the next night retries anyway.
        verify(aggregator, times(3)).aggregateDailyUsage(any());

        Map<String, DailyUsage> written = captureWrittenHistory().days();
        assertFalse(written.containsKey("2026-05-10"));
        assertEquals(30L, written.get("2026-09-19").visits().total(), "untouched days keep their value");
        assertEquals(40L, written.get("2026-09-20").visits().total());
    }

    /**
     * E4: an unusable file is left frozen for manual analysis — nothing is written and no day rows
     * are invented. / Eine unbrauchbare Datei wird nicht überschrieben.
     */
    @Test
    void leavesAnUnusableBlobUntouched() {
        when(blobClient.isConfigured()).thenReturn(true);
        when(blobClient.loadUsageHistory()).thenReturn(new UsageBlobClient.LoadedHistory(
            UsageHistory.empty(), new TreeMap<>(), ETAG, false));

        assertDoesNotThrow(() -> scheduler(24).rollUpPreviousDay());

        verify(aggregator, never()).aggregateDailyUsage(any());
        verify(blobClient, never()).saveUsageHistory(any(), any());
    }

    /** E2: without a storage account the rollup is a no-op — the page runs without history. */
    @Test
    void skipsTheRollupWithoutAStorageAccount() {
        when(blobClient.isConfigured()).thenReturn(false);

        assertDoesNotThrow(() -> scheduler(24).rollUpPreviousDay());

        verify(blobClient, never()).loadUsageHistory();
        verify(blobClient, never()).saveUsageHistory(any(), any());
    }

    // ---- helpers ----

    private void givenHistory(SortedMap<LocalDate, DailyUsage> days) {
        when(blobClient.isConfigured()).thenReturn(true);
        when(blobClient.loadUsageHistory()).thenReturn(new UsageBlobClient.LoadedHistory(
            UsageBlobClient.toHistory(days, NOW), days, ETAG, true));
    }

    private UsageHistory captureWrittenHistory() {
        ArgumentCaptor<UsageHistory> written = ArgumentCaptor.forClass(UsageHistory.class);
        verify(blobClient).saveUsageHistory(written.capture(), eq(ETAG));
        return written.getValue();
    }

    private UsageStatsScheduler scheduler(int retentionMonths) {
        return scheduler(retentionMonths, NOW);
    }

    private UsageStatsScheduler scheduler(int retentionMonths, Instant now) {
        UsageStatsProperties properties = properties(retentionMonths);
        Clock clock = Clock.fixed(now, BERLIN);
        // Jitter 0: the nightly run must not sleep in a test.
        return new UsageStatsScheduler(aggregator, blobClient,
            new UsageStatsService(properties, clock), store, properties, clock, 0);
    }

    private static UsageStatsProperties properties(int retentionMonths) {
        return new UsageStatsProperties(true, "0 */15 * * * *", "0 30 3 * * *", "Europe/Berlin", null,
            retentionMonths, 60,
            new UsageStatsProperties.Storage("gsterminbuchungstats", "usage-stats", "usage-daily.json"));
    }

    /** A day row with {@code visits} mobile visits and a plausible funnel. */
    private static DailyUsage day(long visits) {
        return new DailyUsage(new DeviceBreakdown(visits, 0L, 0L, 0L),
            visits, visits, visits / 4, visits / 4, visits / 2, visits / 4);
    }
}
