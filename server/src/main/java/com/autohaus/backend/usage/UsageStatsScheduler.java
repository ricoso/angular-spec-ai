package com.autohaus.backend.usage;

import com.autohaus.backend.config.UsageStatsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Drives the anonymous usage statistics (REQ-037): live refresh, nightly rollup, catch-up and
 * retention purge / steuert die anonyme Nutzungsstatistik.
 *
 * <p>All aggregation happens here — {@code GET /internal/report} and {@code usage.json} only read
 * {@link UsageStatsStore} (AC-7). The bean exists whenever the kill switch is on (BR-13) and is
 * deliberately <b>not</b> registered in {@code ErrorReportingConfiguration}: the statistics must
 * work without {@code LLM_API_KEY}/{@code LLM_MODEL} (§18, AC-6). / Bewusst LLM-unabhängig.
 *
 * <p>Two schedules:
 * <ul>
 *   <li>{@code usage.refresh-cron} (default every 15 min) plus one run on startup →
 *       {@link #refreshCurrentUsage()}: reads the blob history and queries the live edge — today
 *       and every not-yet-rolled previous day (BR-9).</li>
 *   <li>{@code usage.rollup-cron} (default 03:30, deliberately after {@code REPORT_CRON}) →
 *       {@link #rollUpPreviousDay()}: writes yesterday's absolute day row, catches up the last
 *       {@value #CATCH_UP_DAYS} days (AC-9) and purges everything beyond the retention
 *       (NFR-Datenschutz-7).</li>
 * </ul>
 *
 * <p>{@code @Scheduled} runs in <b>every replica</b>. The rollup is therefore idempotent — it
 * replaces absolute day values and never increments (BR-8) — writes conditionally with
 * {@code If-Match} and retries after a conflict (E3, AC-10). A random jitter of up to
 * {@value #MAX_JITTER_SECONDS} s keeps parallel replicas from colliding on every single night. /
 * Läuft in jeder Replica; Idempotenz und Jitter verhindern Lost Updates.
 *
 * <p>Nothing here ever throws into the scheduler: a failing source is logged as {@code WARN} and
 * named on the page, never silently shown as „0 Besuche" (§9, AC-11). / Stille Ausfälle sind
 * unzulässig.
 */
@Component
@ConditionalOnProperty(name = "usage.enabled", havingValue = "true", matchIfMissing = true)
public class UsageStatsScheduler {

    private static final Logger log = LoggerFactory.getLogger(UsageStatsScheduler.class);

    /** How many days back the nightly catch-up looks (AC-9, within Log Analytics retention). */
    public static final int CATCH_UP_DAYS = 25;

    /**
     * REQ-043: how far back a day row without the newer counters is recomputed — every day that
     * started after „now minus 30 days" is still complete in Log Analytics (retention 30 days),
     * so 29 days never overwrite a row with a half-purged day. / Nachberechnung alter Zeilen,
     * solange Log Analytics den ganzen Tag noch hält.
     */
    public static final int BACKFILL_DAYS = 29;

    /** Conditional write attempts before giving up (E3, AC-10) / Schreibversuche. */
    public static final int MAX_WRITE_ATTEMPTS = 3;

    /** Days after which a missing rollup is reported as stale (§9) / Schwelle „Rollup veraltet". */
    public static final int ROLLUP_STALE_DAYS = 3;

    /** Upper bound of the random rollup jitter in seconds / Obergrenze des Zufalls-Jitters. */
    public static final int MAX_JITTER_SECONDS = 120;

    /**
     * Consecutive failed day queries after which the catch-up stops / aufeinanderfolgende
     * Fehlversuche, nach denen der Catch-up abbricht. Log Analytics is then down as a whole —
     * hammering it 25 times changes nothing and the next night retries anyway. / Kein Dauerfeuer.
     */
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    /** Fallback when the property is unset — same zone as the rest of the feature (BR-7). */
    private static final String DEFAULT_ZONE = "Europe/Berlin";

    private final UsageStatsAggregator aggregator;
    private final UsageBlobClient blobClient;
    private final UsageStatsService statsService;
    private final UsageStatsStore store;
    private final UsageStatsProperties properties;
    private final Clock clock;
    private final int maxJitterSeconds;

    /** Epoch millis of the last refresh — basis of the throttle (AC-21). / Basis der Drosselung. */
    private volatile long lastRefreshMillis;

    @Autowired
    public UsageStatsScheduler(UsageStatsAggregator aggregator, UsageBlobClient blobClient,
                               UsageStatsService statsService, UsageStatsStore store,
                               UsageStatsProperties properties) {
        this(aggregator, blobClient, statsService, store, properties,
            Clock.system(resolveZone(properties)), MAX_JITTER_SECONDS);
    }

    /**
     * Visible for testing / für Tests: pins "now" and switches the jitter off so TC-8 and TC-12 run
     * without sleeping. / Fixiert die Zeit und schaltet den Jitter ab.
     */
    UsageStatsScheduler(UsageStatsAggregator aggregator, UsageBlobClient blobClient,
                        UsageStatsService statsService, UsageStatsStore store,
                        UsageStatsProperties properties, Clock clock, int maxJitterSeconds) {
        this.aggregator = aggregator;
        this.blobClient = blobClient;
        this.statsService = statsService;
        this.store = store;
        this.properties = properties;
        this.clock = clock;
        this.maxJitterSeconds = Math.max(0, maxJitterSeconds);
    }

    // ---- schedules / Zeitpläne ----

    /** One run on startup — bridges container restarts (same pattern as REQ-030). */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("REQ-037 usage statistics active — initial refresh on startup");
        refreshCurrentUsage();
        backfillOnStartup();
    }

    /**
     * REQ-043: recomputes day rows without the newer counters right after a deploy instead of at
     * 03:30 — by then the oldest of them would have dropped out of the 30-day log retention. Runs
     * the regular, idempotent rollup (BR-8) and only when such a row exists, so every later start
     * costs one blob read. / Nachberechnung direkt nach dem Deploy, nur wenn nötig.
     */
    void backfillOnStartup() {
        if (!blobClient.isConfigured()) {
            return;
        }
        try {
            UsageBlobClient.LoadedHistory loaded = blobClient.loadUsageHistory();
            if (!loaded.usable()) {
                return;
            }
            LocalDate today = LocalDate.now(clock);
            long legacy = loaded.days().entrySet().stream()
                .filter(e -> !e.getKey().isBefore(today.minusDays(BACKFILL_DAYS))
                    && e.getKey().isBefore(today) && !e.getValue().hasAllCounters())
                .count();
            if (legacy == 0) {
                return;
            }
            log.info("usage backfill on startup: {} day row(s) without the REQ-043 counters", legacy);
            runRollup();
            refreshCurrentUsage();
        } catch (UsageBlobClient.BlobUnavailableException e) {
            log.warn("usage backfill on startup skipped — blob unavailable: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.error("usage backfill on startup failed unexpectedly: {}", e.getMessage(), e);
        }
    }

    /** Live refresh on the configured cadence (default every 15 min). */
    @Scheduled(cron = "${usage.refresh-cron}", zone = "${usage.zone}")
    public void scheduledRefresh() {
        refreshCurrentUsage();
    }

    // ---- live refresh ----

    /**
     * Rebuilds the in-memory snapshot from blob history plus the live edge / baut den Snapshot neu
     * auf.
     *
     * <p>Live days are today and every day the nightly rollup has not persisted yet, at most
     * {@value UsageStatsService#MAX_LIVE_DAYS} days back (BR-9) — that closes the gap between
     * midnight and the 03:30 rollup instead of losing a whole day every night. If the blob is
     * unavailable, only the running day is queried (E2); if Log Analytics is unavailable, the
     * history alone is shown and {@code liveAsOf} keeps the timestamp of the last successful
     * refresh (E1). Both cases are named on the page, never hidden behind zeros. / Ausfälle werden
     * benannt, nicht durch Nullen kaschiert.
     */
    public synchronized void refreshCurrentUsage() {
        long startedNanos = System.nanoTime();
        lastRefreshMillis = clock.millis();
        try {
            LocalDate today = LocalDate.now(clock);

            UsageHistory history = UsageHistory.empty();
            boolean historyAvailable = false;
            LocalDate lastPersisted = null;
            if (blobClient.isConfigured()) {
                try {
                    UsageBlobClient.LoadedHistory loaded = blobClient.loadUsageHistory();
                    if (loaded.usable()) {
                        history = loaded.history();
                        historyAvailable = true;
                        lastPersisted = loaded.days().isEmpty() ? null : loaded.days().lastKey();
                    } else {
                        log.warn("usage history unusable — showing the running day only (E4)");
                    }
                } catch (UsageBlobClient.BlobUnavailableException e) {
                    log.warn("usage history unavailable — showing the running day only: {}",
                        e.getMessage());
                }
            } else {
                log.debug("usage history disabled — no storage account configured");
            }

            Map<LocalDate, DailyUsage> liveDays = new LinkedHashMap<>();
            boolean liveFailed = false;
            for (LocalDate day : liveEdgeDays(today, lastPersisted, historyAvailable)) {
                Optional<DailyUsage> usage = aggregateSafely(day);
                if (usage.isPresent()) {
                    liveDays.put(day, usage.get());
                } else {
                    liveFailed = true;
                }
            }
            boolean liveAvailable = !liveFailed;
            Instant liveAsOf = liveAvailable && !liveDays.isEmpty() ? clock.instant() : store.liveAsOf();

            UsageStats stats = statsService.buildPeriods(
                history, historyAvailable, liveDays, liveAvailable, liveAsOf);
            store.update(stats);
            checkRollupFreshness(stats.lastRollupDay(), today, historyAvailable);

            DailyUsage todayUsage = liveDays.get(today);
            long visitsToday = todayUsage == null ? 0L : todayUsage.visits().total();
            log.info("usage stats refreshed: {} visits today ({} ms, live={}, history={})",
                visitsToday, (System.nanoTime() - startedNanos) / 1_000_000L,
                liveAvailable, historyAvailable);
        } catch (RuntimeException e) {
            // The snapshot keeps its last good state; the page stays reachable (AC-11).
            log.error("usage refresh failed unexpectedly: {}", e.getMessage(), e);
        }
    }

    /**
     * Refreshes unless the throttle says no (AC-21) / aktualisiert, sofern die Drosselung es
     * zulässt.
     *
     * <p>Used by {@code POST /internal/report/refresh}: {@code /internal/**} is publicly reachable
     * and the chain knows neither rate limiting nor lockout, so a manual refresh — which always
     * triggers 3-9 KQL runs — is allowed at most once per
     * {@code usage.refresh-min-interval-seconds}. The controller answers {@code 429} on
     * {@code false}, <b>without</b> any backend query. / Der Controller antwortet dann 429.
     *
     * @return {@code true} when a refresh ran, {@code false} when throttled / ob gelaufen
     */
    public synchronized boolean refreshIfAllowed() {
        long intervalMillis = Math.max(0, properties.refreshMinIntervalSeconds()) * 1000L;
        long elapsed = clock.millis() - lastRefreshMillis;
        if (lastRefreshMillis != 0L && elapsed < intervalMillis) {
            log.debug("usage refresh throttled — {} ms since the last run, {} ms required",
                elapsed, intervalMillis);
            return false;
        }
        refreshCurrentUsage();
        return true;
    }

    // ---- nightly rollup ----

    /**
     * Writes the finished days into the blob and enforces the retention / schreibt die fertigen
     * Tage und setzt die Aufbewahrung durch.
     *
     * <p>Yesterday is always recomputed (idempotent replace, BR-8), the {@value #CATCH_UP_DAYS}
     * days before it only when they are missing (AC-9). The <b>retention purge runs independently
     * of whether a new day value could be determined</b> — the deletion deadline must never pause
     * unnoticed (NFR-Datenschutz-7). An unusable file (E4) is left untouched so it can be analysed
     * manually; nothing new is written until the problem is fixed. / Purge läuft unabhängig vom
     * Rollup-Erfolg.
     */
    @Scheduled(cron = "${usage.rollup-cron}", zone = "${usage.zone}")
    public void rollUpPreviousDay() {
        applyJitter();
        try {
            runRollup();
        } catch (RuntimeException e) {
            log.error("usage rollup failed unexpectedly: {}", e.getMessage(), e);
        }
    }

    private void runRollup() {
        if (!blobClient.isConfigured()) {
            log.debug("usage rollup skipped — no storage account configured (E2)");
            return;
        }
        LocalDate today = LocalDate.now(clock);

        UsageBlobClient.LoadedHistory loaded;
        try {
            loaded = blobClient.loadUsageHistory();
        } catch (UsageBlobClient.BlobUnavailableException e) {
            log.warn("usage rollup skipped — blob unavailable, retrying next night: {}",
                e.getMessage());
            return;
        }
        if (!loaded.usable()) {
            log.error("usage rollup skipped — blob content unusable; the file is left untouched "
                + "for manual analysis and no day rows are written until it is fixed (E4)");
            return;
        }

        SortedMap<LocalDate, DailyUsage> days = new TreeMap<>(loaded.days());
        int written = 0;
        int consecutiveFailures = 0;
        for (LocalDate day : rollupDays(today, days)) {
            Optional<DailyUsage> usage = aggregateSafely(day);
            if (usage.isEmpty()) {
                if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    log.warn("usage rollup: giving up after {} consecutive failed day queries — "
                        + "the next night catches the days up (AC-9)", consecutiveFailures);
                    break;
                }
                continue;
            }
            consecutiveFailures = 0;
            // Absolute values replace the day row — never "+=" (BR-8, idempotent).
            days.put(day, usage.get());
            written++;
        }

        int purged = purgeExpired(days, today);
        if (written == 0 && purged == 0) {
            log.info("usage rollup: nothing to write (0 day(s), 0 purged)");
            return;
        }
        if (persistDailySnapshot(days, loaded.etag())) {
            log.info("usage rollup written: {} day(s) ({} day(s) purged beyond retention)",
                written, purged);
        }
    }

    /**
     * Writes the day rows with {@code If-Match} and merges on conflict (E3, AC-10) / schreibt mit
     * {@code If-Match} und merged bei Konflikt.
     *
     * <p>On HTTP 412/409 the blob is read again, the freshly computed absolute day rows are laid
     * over the other replica's state (BR-8 makes every outcome identical), the retention is
     * enforced again and the write is repeated — at most {@value #MAX_WRITE_ATTEMPTS} times. After
     * that: {@code WARN}, no abort — the next night pulls the day in via catch-up.
     *
     * <p>The naming glossary (§16) lists this as {@code persistDailySnapshot(days)}; the ETag of
     * the preceding read is the second argument because the conditional write cannot work without
     * it. / Der ETag stammt aus dem vorangehenden Lesevorgang.
     *
     * @param days the complete desired file content / vollständiger gewünschter Dateiinhalt
     * @param etag ETag of the preceding read, {@code null} when the blob does not exist yet
     * @return whether the write succeeded / ob der Schreibvorgang gelungen ist
     */
    boolean persistDailySnapshot(SortedMap<LocalDate, DailyUsage> days, String etag) {
        SortedMap<LocalDate, DailyUsage> pending = days;
        String currentEtag = etag;
        for (int attempt = 1; attempt <= MAX_WRITE_ATTEMPTS; attempt++) {
            try {
                blobClient.saveUsageHistory(
                    UsageBlobClient.toHistory(pending, clock.instant()), currentEtag);
                return true;
            } catch (UsageBlobClient.BlobPreconditionFailedException e) {
                log.warn("usage rollup write conflict (attempt {}/{}) — re-reading and merging (E3)",
                    attempt, MAX_WRITE_ATTEMPTS);
                if (attempt == MAX_WRITE_ATTEMPTS) {
                    break;
                }
                backOff(attempt);
                try {
                    UsageBlobClient.LoadedHistory fresh = blobClient.loadUsageHistory();
                    if (!fresh.usable()) {
                        log.error("usage rollup aborted — blob became unusable during the retry (E4)");
                        return false;
                    }
                    SortedMap<LocalDate, DailyUsage> merged = new TreeMap<>(fresh.days());
                    // Our absolute values win; days only the other replica has are kept (BR-8).
                    merged.putAll(days);
                    purgeExpired(merged, LocalDate.now(clock));
                    pending = merged;
                    currentEtag = fresh.etag();
                } catch (UsageBlobClient.BlobUnavailableException reread) {
                    log.warn("usage rollup aborted — blob unavailable during the retry: {}",
                        reread.getMessage());
                    return false;
                }
            } catch (UsageBlobClient.BlobUnavailableException e) {
                log.warn("usage rollup write failed: {}", e.getMessage());
                return false;
            }
        }
        log.warn("usage rollup gave up after {} attempts — the next night's catch-up picks the "
            + "day(s) up again (E3)", MAX_WRITE_ATTEMPTS);
        return false;
    }

    // ---- internals / Interna ----

    /**
     * The live edge per BR-9 / der Live-Rand: today plus every not-yet-persisted previous day,
     * at most {@value UsageStatsService#MAX_LIVE_DAYS} days back. Without a usable history only the
     * running day is queried (E2). / Ohne Historie nur der laufende Tag.
     */
    private static List<LocalDate> liveEdgeDays(LocalDate today, LocalDate lastPersisted,
                                                boolean historyAvailable) {
        List<LocalDate> days = new ArrayList<>();
        if (!historyAvailable) {
            days.add(today);
            return days;
        }
        for (int offset = UsageStatsService.MAX_LIVE_DAYS - 1; offset >= 0; offset--) {
            LocalDate day = today.minusDays(offset);
            if (lastPersisted == null || day.isAfter(lastPersisted)) {
                days.add(day);
            }
        }
        return days;
    }

    /**
     * The days the nightly run (re)computes / die Tage des nächtlichen Laufs: yesterday always
     * (idempotent replace, BR-8), the {@value #CATCH_UP_DAYS} days before it only when they are
     * missing (AC-9) — or, REQ-043, when their row predates the newer counters
     * ({@link DailyUsage#hasAllCounters()}): the day is then recomputed once from the same logs,
     * up to {@value #BACKFILL_DAYS} days back. Ascending order keeps the log readable. /
     * Aufsteigend sortiert; Tage ohne die neuen Zähler werden einmalig nachberechnet.
     */
    private static List<LocalDate> rollupDays(LocalDate today,
                                              SortedMap<LocalDate, DailyUsage> present) {
        List<LocalDate> days = new ArrayList<>();
        for (int offset = BACKFILL_DAYS; offset >= 1; offset--) {
            LocalDate day = today.minusDays(offset);
            DailyUsage row = present.get(day);
            boolean missing = row == null && offset <= CATCH_UP_DAYS;
            boolean legacy = row != null && !row.hasAllCounters();
            if (offset == 1 || missing || legacy) {
                days.add(day);
            }
        }
        return days;
    }

    /**
     * Removes every day beyond the retention window / entfernt alle Tage jenseits der
     * Aufbewahrungsfrist (NFR-Datenschutz-7).
     *
     * @return how many day rows were removed / Anzahl entfernter Tageszeilen
     */
    private int purgeExpired(SortedMap<LocalDate, DailyUsage> days, LocalDate today) {
        int retentionMonths = properties.retentionMonths();
        if (retentionMonths <= 0) {
            return 0;
        }
        LocalDate cutoff = today.minusMonths(retentionMonths);
        SortedMap<LocalDate, DailyUsage> expired = days.headMap(cutoff);
        int purged = expired.size();
        expired.clear();
        return purged;
    }

    /**
     * Logs {@code WARN usage rollup stale} and passes the flag to the store (§9) / meldet einen
     * veralteten Rollup. Logged once per state change so a permanently paused rollup is visible
     * without flooding the log every 15 minutes. An empty history (first commissioning, Alt Flow A)
     * is not stale. / Eine noch leere Historie ist nicht „veraltet".
     */
    private void checkRollupFreshness(LocalDate lastRollupDay, LocalDate today,
                                      boolean historyAvailable) {
        if (!historyAvailable) {
            return;
        }
        boolean stale = lastRollupDay != null
            && lastRollupDay.isBefore(today.minusDays(ROLLUP_STALE_DAYS));
        if (stale && !store.rollupStale()) {
            log.warn("usage rollup stale: last day {}", lastRollupDay);
        }
        store.markRollupStale(stale);
    }

    /**
     * One day from Log Analytics, degrading to {@link Optional#empty()} / ein Tag aus Log
     * Analytics. The aggregator already swallows
     * {@code LogAnalyticsClient.LogAnalyticsUnavailableException}; this guard covers everything
     * else so one bad day never kills the whole run (E1). / Ein schlechter Tag stoppt den Lauf nicht.
     */
    private Optional<DailyUsage> aggregateSafely(LocalDate day) {
        try {
            Optional<DailyUsage> usage = aggregator.aggregateDailyUsage(day);
            return usage == null ? Optional.empty() : usage;
        } catch (RuntimeException e) {
            log.warn("usage aggregation failed for {}: {}", day, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Random delay of 0-{@value #MAX_JITTER_SECONDS} s before the rollup / Zufalls-Jitter. Keeps
     * the replicas of a scaled-out or mid-deploy app from writing at the same instant every night
     * (E3). Tests switch it off via the package-private constructor. / Tests schalten ihn ab.
     */
    private void applyJitter() {
        if (maxJitterSeconds <= 0) {
            return;
        }
        int seconds = ThreadLocalRandom.current().nextInt(maxJitterSeconds + 1);
        if (seconds == 0) {
            return;
        }
        log.debug("usage rollup jitter: waiting {} s", seconds);
        sleep(seconds * 1000L);
    }

    /** Short backoff between two conditional-write attempts / kurzer Backoff. */
    private void backOff(int attempt) {
        sleep(200L * attempt);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ZoneId resolveZone(UsageStatsProperties properties) {
        String zone = properties == null || properties.zone() == null || properties.zone().isBlank()
            ? DEFAULT_ZONE : properties.zone().trim();
        return ZoneId.of(zone);
    }
}
