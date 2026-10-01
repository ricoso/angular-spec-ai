package com.autohaus.backend.usage.insights;

import com.autohaus.backend.common.logging.JobTrace;
import com.autohaus.backend.common.util.Sleeper;
import com.autohaus.backend.config.UsageDropoffProperties;
import com.autohaus.backend.config.UsageFunnelProperties;
import com.autohaus.backend.config.UsageInsightsProperties;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.config.setup.BranchNames;
import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.usage.UsageBlobClient;
import com.autohaus.backend.usage.UsageZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Drives the nightly insights pipeline (REQ-038 Step 3–5, AC-14) / steuert die nächtliche
 * Insights-Pipeline: load {@code usage-insights.json}, aggregate yesterday and the missing days,
 * compact and purge in the same write, and precompute the suppressed view for the request path.
 *
 * <p><b>Active</b> exactly while {@code usage.insights.enabled} or ({@code usage.enabled} and
 * {@code usage.funnel.enabled}) holds ({@link InsightsSchedulerCondition}); otherwise
 * {@link InsightsRetentionJob} runs the compaction on its own. / Aktiv bei K2 oder Trichter.
 *
 * <p><b>Three schedules / drei Zeitpunkte:</b>
 * <ul>
 *   <li>{@link #onStartup()} — load the blob (never KQL, never SoftNet) and store a view; on a load
 *       failure the view carries {@link InsightsViewModel#WARN_HISTORY_UNAVAILABLE} (E4, AC-32).
 *       The start day is remembered as activation day for an empty collecting start (BR-19).</li>
 *   <li>{@link #rollUp()} at {@code USAGE_INSIGHTS_ROLLUP_CRON} (03:45, after the REQ-037 rollup)
 *       with 0–{@value #MAX_JITTER_SECONDS} s jitter.</li>
 *   <li>{@link #rebuildViewModel()} at 00:05 — day and month change, from memory only (Step 5).</li>
 * </ul>
 *
 * <p><b>Rollup rules / Regeln des Rollups:</b> yesterday is always recomputed, the
 * {@value #CATCH_UP_DAYS} days before only when a source is missing there (a stored day with
 * {@code bookings == 0} counts as missing for the bookings source and is queried again, which
 * repairs days written by the broken KQL 1 day filter and is harmless for a real zero day) — never before that
 * source's collecting start and never inside a compacted month ({@code branchMonths} key present,
 * §5.8). Each source has its own start (BR-19): the configured value, else the one already
 * persisted, else activation day + 1 — written into the blob by the first rollup, so the
 * activation day itself (a partial day) is never counted. Newest days first; after
 * {@value #MAX_CONSECUTIVE_FAILURES} consecutive failed days the catch-up stops (E3). Day values
 * are replaced absolutely per source (idempotent, BR-18); the retention of
 * {@link InsightsRetentionJob#apply} runs in the <b>same</b> write; the write uses {@code If-Match}
 * and on 412/409 re-reads, lays its own absolute values over the other replica's state, applies
 * the retention again — at most {@value #MAX_WRITE_ATTEMPTS} attempts (E5, pattern
 * {@code UsageStatsScheduler.persistDailySnapshot}). Unusable content (E6) is never overwritten. /
 * Idempotent, Retention im selben Schreibvorgang, bei Konflikt neu lesen und mergen.
 *
 * <p><b>Bugs tab stays clean (BR-15):</b> the whole run carries MDC
 * {@code traceId=}{@value #TRACE_ID}; the dot lies outside the {@code X-Request-Id} whitelist, so no
 * client can forge it, and {@code ERRORS_KQL} excludes it. / Job-Marker für die gesamte Laufzeit.
 *
 * <p>Nothing here throws into the scheduler; every failure is a {@code WARN} plus a visible
 * {@code .st-warn} on the page, never a silent zero (§9 Observability). / Nie eine stille Null.
 */
@Component
@Conditional(InsightsSchedulerCondition.class)
public class InsightsScheduler {

    private static final Logger log = LoggerFactory.getLogger(InsightsScheduler.class);

    /** MDC marker of the rollup (BR-15, §16) / MDC-Marker des Rollups. */
    public static final String TRACE_ID = "job.insightsrollup";

    /** Days before yesterday the catch-up looks at (AC-14) / Catch-up-Fenster. */
    public static final int CATCH_UP_DAYS = 25;

    /** Conditional write attempts (E5) / Schreibversuche. */
    public static final int MAX_WRITE_ATTEMPTS = 3;

    /** Upper bound of the rollup jitter in seconds (Step 3) / Obergrenze des Jitters. */
    public static final int MAX_JITTER_SECONDS = 120;

    /** Days after which the newest insights day counts as stale (§9) / Schwelle „veraltet". */
    public static final int STALE_DAYS = 3;

    /** Consecutive failed days after which the catch-up stops (E3) / Abbruchschwelle. */
    public static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final InsightsAggregator aggregator;
    private final InsightsBlobClient blobClient;
    private final InsightsStore store;
    private final InsightsRetentionJob retentionJob;
    private final SetupService setupService;
    private final UsageStatsProperties usageProperties;
    private final UsageInsightsProperties insightsProperties;
    private final UsageFunnelProperties funnelProperties;
    private final UsageDropoffProperties dropoffProperties;
    private final Clock clock;
    private final int maxJitterSeconds;
    private final Sleeper sleeper;

    /** Berlin start day of this process with an active switch (BR-19) / Aktivierungstag. */
    private volatile LocalDate activationDay;

    /** Whether a history was ever loaded successfully (E4) / ob je ein Stand geladen wurde. */
    private volatile boolean historyLoaded;

    /** Day the stale warning was last logged — at most once per day (§9) / letzter Stale-Log. */
    private volatile LocalDate staleLoggedFor;

    @Autowired
    public InsightsScheduler(InsightsAggregator aggregator, InsightsBlobClient blobClient,
                             InsightsStore store, InsightsRetentionJob retentionJob,
                             SetupService setupService, UsageStatsProperties usageProperties,
                             UsageInsightsProperties insightsProperties,
                             UsageFunnelProperties funnelProperties,
                             UsageDropoffProperties dropoffProperties) {
        this(aggregator, blobClient, store, retentionJob, setupService, usageProperties,
            insightsProperties, funnelProperties, dropoffProperties,
            Clock.system(UsageZone.resolve(usageProperties)), MAX_JITTER_SECONDS, Sleeper.THREAD);
    }

    /**
     * Visible for testing / für Tests: pins "now", switches the jitter off and replaces sleeping.
     * / Fixiert die Zeit, schaltet Jitter und Schlafen ab.
     */
    InsightsScheduler(InsightsAggregator aggregator, InsightsBlobClient blobClient,
                      InsightsStore store, InsightsRetentionJob retentionJob,
                      SetupService setupService, UsageStatsProperties usageProperties,
                      UsageInsightsProperties insightsProperties,
                      UsageFunnelProperties funnelProperties,
                      UsageDropoffProperties dropoffProperties,
                      Clock clock, int maxJitterSeconds, Sleeper sleeper) {
        this.aggregator = aggregator;
        this.blobClient = blobClient;
        this.store = store;
        this.retentionJob = retentionJob;
        this.setupService = setupService;
        this.usageProperties = usageProperties;
        this.insightsProperties = insightsProperties;
        this.funnelProperties = funnelProperties;
        this.dropoffProperties = dropoffProperties;
        this.clock = clock;
        this.maxJitterSeconds = Math.max(0, maxJitterSeconds);
        this.sleeper = sleeper;
    }

    // ---- switches / Schalter ----

    /** K2 active ({@code usage.insights.enabled}) / Buchungs-Insights aktiv. */
    public boolean bookingsEnabled() {
        return insightsProperties != null && insightsProperties.enabled();
    }

    /** Funnel active — only together with {@code usage.enabled} (§5.1) / Trichter aktiv. */
    public boolean funnelEnabled() {
        return usageProperties != null && usageProperties.enabled()
            && funnelProperties != null && funnelProperties.enabled();
    }

    /** Drop-off scenarios active — only within an active funnel (N1) / Absprungszenarien aktiv. */
    public boolean dropoffEnabled() {
        return funnelEnabled() && dropoffProperties != null && dropoffProperties.enabled();
    }

    /** The remembered activation day, {@code null} before the first run / Aktivierungstag. */
    LocalDate activationDay() {
        return activationDay;
    }

    // ---- startup / Start ----

    /**
     * Loads the blob and stores the first view — no KQL, no SoftNet (Step 5) / lädt den Blob und
     * legt die erste Sicht ab. Remembers the activation day for an empty collecting start (BR-19).
     * Ordered first among the startup listeners (like {@code CapacityScanScheduler}) so the E2E
     * seed loader (lowest priority) deterministically overwrites the store afterwards. / Läuft
     * deterministisch vor dem Seed-Loader.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public synchronized void onStartup() {
        LocalDate today = LocalDate.now(clock);
        rememberActivationDay(today);
        log.info("REQ-038 usage insights active (bookings={}, funnel={}, dropoff={}) — loading the "
            + "history on startup", bookingsEnabled(), funnelEnabled(), dropoffEnabled());
        String previousTraceId = JobTrace.enter(TRACE_ID);
        try {
            loadOnStartup(today);
        } catch (RuntimeException e) {
            log.error("usage insights startup failed unexpectedly: {}", e.getMessage(), e);
        } finally {
            JobTrace.restore(previousTraceId);
        }
    }

    private void loadOnStartup(LocalDate today) {
        if (!blobClient.isConfigured()) {
            log.debug("usage insights history disabled — no storage account configured (E4)");
            if (store.hasViewModel()) {
                // Someone else (the e2e-seed loader) already provided a view — keep it.
                historyLoaded = true;
                return;
            }
            publishUnavailable(today);
            return;
        }
        InsightsBlobClient.Loaded loaded;
        try {
            loaded = blobClient.load();
        } catch (UsageBlobClient.BlobUnavailableException e) {
            log.warn("usage insights history unavailable on startup — the page shows '{}' (E4): {}",
                InsightsViewModel.WARN_HISTORY_UNAVAILABLE, e.getMessage());
            publishUnavailable(today);
            return;
        }
        if (!loaded.usable()) {
            log.error("usage insights history unusable on startup — the file is left untouched (E6)");
            publishUnavailable(today);
            return;
        }
        historyLoaded = true;
        publish(loaded.history(), today);
    }

    // ---- nightly rollup / nächtlicher Rollup ----

    /**
     * Aggregates yesterday plus missing days and writes them together with the retention (Step 3,
     * Step 4) / aggregiert gestern und fehlende Tage und schreibt sie zusammen mit der Aufbewahrung.
     */
    @Scheduled(cron = "${usage.insights.rollup-cron:0 45 3 * * *}", zone = "${usage.zone:Europe/Berlin}")
    public void rollUp() {
        applyJitter();
        String previousTraceId = JobTrace.enter(TRACE_ID);
        try {
            synchronized (this) {
                runRollup();
            }
        } catch (RuntimeException e) {
            log.error("usage insights rollup failed unexpectedly: {}", e.getMessage(), e);
        } finally {
            JobTrace.restore(previousTraceId);
        }
    }

    private void runRollup() {
        LocalDate today = LocalDate.now(clock);
        rememberActivationDay(today);
        if (!blobClient.isConfigured()) {
            log.debug("usage insights rollup skipped — no storage account configured (E4)");
            rebuild(today);
            return;
        }
        InsightsBlobClient.Loaded loaded;
        try {
            loaded = blobClient.load();
        } catch (UsageBlobClient.BlobUnavailableException e) {
            log.warn("usage insights rollup skipped — blob unavailable, retrying next night: {}",
                e.getMessage());
            rebuild(today);
            return;
        }
        if (!loaded.usable()) {
            log.error("usage insights rollup skipped — blob content unusable; the file is left "
                + "untouched and nothing is written until it is fixed (E6)");
            rebuild(today);
            return;
        }
        historyLoaded = true;
        InsightsHistory base = loaded.history();

        Sources sources = resolveSources(base);
        SortedMap<LocalDate, Computed> computed = new TreeMap<>();
        long bookings = 0L;
        int consecutiveFailures = 0;
        for (DayPlan plan : planDays(today, base, sources)) {
            Optional<AggregatedDay> result = aggregateSafely(plan);
            if (result.isEmpty()) {
                log.warn("usage insights rollup skipped for {}", plan.day());
                if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    log.warn("usage insights rollup: giving up after {} consecutive failed days — the "
                        + "next night catches them up (E3)", consecutiveFailures);
                    break;
                }
                continue;
            }
            consecutiveFailures = 0;
            AggregatedDay aggregated = result.orElseThrow();
            computed.put(plan.day(), new Computed(plan, aggregated));
            Long dayBookings = aggregated.global().bookings();
            bookings += dayBookings == null ? 0L : dayBookings;
        }

        Delta delta = new Delta(sources.bookingsSince(), sources.funnelSince(), sources.dropoffSince(),
            computed);
        InsightsRetentionJob.Result first = retentionJob.apply(applyDelta(base, delta), today);
        if (computed.isEmpty() && !first.changed() && !delta.changesSinceOf(base)) {
            log.info("usage insights rollup: nothing to write (0 day(s), 0 booking(s), 0 purged)");
            publish(base, today);
            return;
        }
        Persisted persisted = persist(first, loaded.etag(), delta, today, base);
        if (persisted.written()) {
            log.info("usage insights rollup written: {} day(s), {} booking(s), {} purged",
                computed.size(), bookings, persisted.purged());
        }
        publish(persisted.history(), today);
    }

    /**
     * Writes with {@code If-Match} and merges on conflict (E5) / schreibt bedingt und merged bei
     * Konflikt: re-read, lay the own absolute values over the fresh state, retention again — at
     * most {@value #MAX_WRITE_ATTEMPTS} attempts.
     */
    private Persisted persist(InsightsRetentionJob.Result first, String etag, Delta delta,
                              LocalDate today, InsightsHistory base) {
        InsightsHistory pending = stamp(first.history());
        int purged = first.purged();
        String currentEtag = etag;
        InsightsHistory latestKnown = base;
        for (int attempt = 1; attempt <= MAX_WRITE_ATTEMPTS; attempt++) {
            try {
                blobClient.save(pending, currentEtag);
                return new Persisted(true, pending, purged);
            } catch (UsageBlobClient.BlobPreconditionFailedException e) {
                log.warn("usage insights rollup write conflict (attempt {}/{}) — re-reading and merging (E5)",
                    attempt, MAX_WRITE_ATTEMPTS);
                if (attempt == MAX_WRITE_ATTEMPTS) {
                    break;
                }
                backOff(attempt);
                InsightsBlobClient.Loaded fresh;
                try {
                    fresh = blobClient.load();
                } catch (UsageBlobClient.BlobUnavailableException reread) {
                    log.warn("usage insights rollup aborted — blob unavailable during the retry: {}",
                        reread.getMessage());
                    return new Persisted(false, latestKnown, 0);
                }
                if (!fresh.usable()) {
                    log.error("usage insights rollup aborted — blob became unusable during the retry (E6)");
                    return new Persisted(false, latestKnown, 0);
                }
                latestKnown = fresh.history();
                InsightsRetentionJob.Result again = retentionJob.apply(applyDelta(fresh.history(), delta), today);
                pending = stamp(again.history());
                purged = again.purged();
                currentEtag = fresh.etag();
            } catch (UsageBlobClient.BlobUnavailableException e) {
                log.warn("usage insights rollup write failed: {}", e.getMessage());
                return new Persisted(false, latestKnown, 0);
            }
        }
        log.warn("usage insights rollup gave up after {} attempts — the next night's catch-up picks "
            + "the day(s) up again (E5)", MAX_WRITE_ATTEMPTS);
        return new Persisted(false, latestKnown, 0);
    }

    // ---- 00:05 rebuild / Neuaufbau um 00:05 ----

    /**
     * Rebuilds the view from memory — day and month change, no I/O (Step 5, BR-25) / baut die Sicht
     * aus dem Speicher neu, ohne I/O.
     */
    @Scheduled(cron = "0 5 0 * * *", zone = "${usage.zone:Europe/Berlin}")
    public synchronized void rebuildViewModel() {
        rebuild(LocalDate.now(clock));
    }

    // ---- planning / Planung ----

    /**
     * Effective collecting start per source (BR-19) / wirksamer Erfassungsbeginn je Quelle:
     * configured value, else persisted value, else activation day + 1. {@code null} while the
     * source is off.
     */
    private Sources resolveSources(InsightsHistory base) {
        LocalDate fallback = activationDay.plusDays(1);
        boolean bookings = bookingsEnabled();
        boolean funnel = funnelEnabled();
        boolean dropoff = dropoffEnabled();
        return new Sources(
            bookings ? firstNonNull(insightsProperties.collectingSince(), base.collectingSince(), fallback) : null,
            funnel ? firstNonNull(funnelProperties.collectingSince(), base.funnelCollectingSince(), fallback) : null,
            dropoff ? firstNonNull(dropoffProperties.collectingSince(), base.dropoffCollectingSince(), fallback) : null);
    }

    /**
     * The days to (re)compute, newest first / die zu berechnenden Tage, neueste zuerst: yesterday
     * always, older days of the window only where an active source is missing; never before the
     * source's start, never inside a compacted month.
     */
    static List<DayPlan> planDays(LocalDate today, InsightsHistory base, Sources sources) {
        List<DayPlan> plans = new ArrayList<>();
        SortedMap<LocalDate, InsightsDay> days = base.days() == null ? new TreeMap<>() : base.days();
        SortedMap<YearMonth, Map<String, BranchCounts>> months =
            base.branchMonths() == null ? new TreeMap<>() : base.branchMonths();
        for (int offset = 1; offset <= CATCH_UP_DAYS; offset++) {
            LocalDate day = today.minusDays(offset);
            if (months.containsKey(YearMonth.from(day))) {
                continue;
            }
            boolean yesterday = offset == 1;
            InsightsDay existing = days.get(day);
            boolean bookings = covers(sources.bookingsSince(), day)
                && (yesterday || existing == null || existing.bookings() == null
                    || existing.bookings() == 0L);
            boolean dropoff = covers(sources.funnelSince(), day) && covers(sources.dropoffSince(), day);
            boolean funnel = covers(sources.funnelSince(), day)
                && (yesterday || existing == null || existing.funnel() == null
                    || (dropoff && existing.funnel().dropoff() == null));
            if (bookings || funnel) {
                plans.add(new DayPlan(day, bookings, funnel, funnel && dropoff));
            }
        }
        return plans;
    }

    private static boolean covers(LocalDate since, LocalDate day) {
        return since != null && !day.isBefore(since);
    }

    /**
     * Lays the computed absolute values over a base history (BR-18) / legt die berechneten
     * absoluten Werte über eine Basis. Per source: a computed source replaces the stored one, a
     * source that was not computed keeps its stored values. Days of a month compacted in the
     * meantime are skipped (frozen, §5.8).
     */
    static InsightsHistory applyDelta(InsightsHistory base, Delta delta) {
        SortedMap<LocalDate, InsightsDay> days = new TreeMap<>(nonNull(base.days()));
        SortedMap<LocalDate, Map<String, BranchCounts>> branchDays = new TreeMap<>(nonNull(base.branchDays()));
        SortedMap<YearMonth, Map<String, BranchCounts>> branchMonths = new TreeMap<>(nonNull(base.branchMonths()));
        delta.days().forEach((day, computed) -> {
            if (branchMonths.containsKey(YearMonth.from(day))) {
                return;
            }
            days.put(day, merge(days.get(day), computed));
            if (computed.plan().bookings()) {
                branchDays.put(day, computed.result().branches());
            }
        });
        return new InsightsHistory(InsightsHistory.CURRENT_SCHEMA_VERSION, base.updatedAt(),
            delta.bookingsSince() != null ? delta.bookingsSince() : base.collectingSince(),
            delta.funnelSince() != null ? delta.funnelSince() : base.funnelCollectingSince(),
            delta.dropoffSince() != null ? delta.dropoffSince() : base.dropoffCollectingSince(),
            Collections.unmodifiableSortedMap(days), Collections.unmodifiableSortedMap(branchDays),
            Collections.unmodifiableSortedMap(branchMonths));
    }

    private static InsightsDay merge(InsightsDay existing, Computed computed) {
        InsightsDay fresh = computed.result().global();
        boolean bookings = computed.plan().bookings();
        boolean funnel = computed.plan().funnel();
        InsightsDay old = existing == null
            ? new InsightsDay(null, null, null, null, null, null, null, null, null) : existing;
        InsightsDay source = bookings ? fresh : old;
        return new InsightsDay(source.bookings(), source.bookingDaypart(), source.slotGrid(),
            source.slotUnknown(), source.leadTime(), source.groups(), source.mobility(),
            source.serviceViews(), funnel ? fresh.funnel() : old.funnel());
    }

    // ---- view / Sicht ----

    private void publish(InsightsHistory history, LocalDate today) {
        store.update(buildViewModel(history, today), history);
        checkStale(history, today);
    }

    /** View while no history is available (E4) — keeps a formerly loaded one / Sicht ohne Stand. */
    private void publishUnavailable(LocalDate today) {
        if (historyLoaded) {
            rebuild(today);
            return;
        }
        store.update(buildViewModel(InsightsHistory.empty(), today), null);
    }

    private void rebuild(LocalDate today) {
        InsightsHistory history = store.history();
        store.update(buildViewModel(history, today), null);
        checkStale(history, today);
    }

    /**
     * Builds the suppressed view; {@code null} keeps the previous one when the build fails / baut
     * die unterdrückte Sicht. Appends {@link InsightsViewModel#WARN_HISTORY_UNAVAILABLE} while no
     * history was ever loaded (E4, AC-32).
     */
    private InsightsViewModel buildViewModel(InsightsHistory history, LocalDate today) {
        try {
            InsightsViewModel viewModel = InsightsViewModel.build(history, BranchNames.byId(setupService),
                today, bookingsEnabled(), funnelEnabled(), dropoffEnabled());
            if (viewModel != null && !historyLoaded) {
                viewModel = viewModel.withAdditionalWarnings(
                    List.of(InsightsViewModel.WARN_HISTORY_UNAVAILABLE));
            }
            return viewModel;
        } catch (RuntimeException e) {
            log.error("usage insights view could not be built — keeping the previous one: {}",
                e.getMessage(), e);
            return null;
        }
    }

    /**
     * Logs {@code WARN usage insights stale: last day <D>} at most once per day when the newest day
     * is more than {@value #STALE_DAYS} days old (§9) / meldet veraltete Daten höchstens einmal je
     * Tag. The visible {@code WARN_STALE} comes from the view model.
     */
    private void checkStale(InsightsHistory history, LocalDate today) {
        if (!historyLoaded || history == null || history.days() == null || history.days().isEmpty()) {
            return;
        }
        LocalDate lastDay = history.days().lastKey();
        if (lastDay.isBefore(today.minusDays(STALE_DAYS)) && !today.equals(staleLoggedFor)) {
            log.warn("usage insights stale: last day {}", lastDay);
            staleLoggedFor = today;
        }
    }

    // ---- internals / Interna ----

    private void rememberActivationDay(LocalDate today) {
        if (activationDay == null) {
            activationDay = today;
        }
    }

    private Optional<AggregatedDay> aggregateSafely(DayPlan plan) {
        try {
            Optional<AggregatedDay> result = aggregator.aggregateDay(plan.day(), plan.bookings(),
                plan.funnel(), plan.dropoff());
            return result == null ? Optional.empty() : result;
        } catch (RuntimeException e) {
            log.warn("usage insights aggregation failed for {}: {}", plan.day(), e.getMessage());
            return Optional.empty();
        }
    }

    private InsightsHistory stamp(InsightsHistory history) {
        return new InsightsHistory(history.schemaVersion(), clock.instant(), history.collectingSince(),
            history.funnelCollectingSince(), history.dropoffCollectingSince(), history.days(),
            history.branchDays(), history.branchMonths());
    }

    private void applyJitter() {
        if (maxJitterSeconds <= 0) {
            return;
        }
        int seconds = ThreadLocalRandom.current().nextInt(maxJitterSeconds + 1);
        if (seconds > 0) {
            log.debug("usage insights rollup jitter: waiting {} s", seconds);
            sleep(Duration.ofSeconds(seconds));
        }
    }

    private void backOff(int attempt) {
        sleep(Duration.ofMillis(200L * attempt));
    }

    private void sleep(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static <K, V> Map<K, V> nonNull(Map<K, V> map) {
        return map == null ? Map.of() : map;
    }

    // ---- value types / Werttypen ----

    /**
     * Effective collecting starts of the active sources, {@code null} = source off (BR-19) /
     * wirksame Erfassungsbeginne.
     */
    record Sources(LocalDate bookingsSince, LocalDate funnelSince, LocalDate dropoffSince) {}

    /** What to compute for one day / was für einen Tag berechnet wird. */
    record DayPlan(LocalDate day, boolean bookings, boolean funnel, boolean dropoff) {}

    /** One computed day together with its plan / ein berechneter Tag mit seinem Plan. */
    record Computed(DayPlan plan, AggregatedDay result) {}

    /**
     * The rollup's own absolute values — re-applied after a write conflict (E5) / die eigenen
     * absoluten Werte des Laufs.
     */
    record Delta(LocalDate bookingsSince, LocalDate funnelSince, LocalDate dropoffSince,
                 SortedMap<LocalDate, Computed> days) {

        /** Whether the collecting starts differ from the base (first rollup, BR-19). */
        boolean changesSinceOf(InsightsHistory base) {
            return (bookingsSince != null && !Objects.equals(bookingsSince, base.collectingSince()))
                || (funnelSince != null && !Objects.equals(funnelSince, base.funnelCollectingSince()))
                || (dropoffSince != null && !Objects.equals(dropoffSince, base.dropoffCollectingSince()));
        }
    }

    private record Persisted(boolean written, InsightsHistory history, int purged) {}
}
