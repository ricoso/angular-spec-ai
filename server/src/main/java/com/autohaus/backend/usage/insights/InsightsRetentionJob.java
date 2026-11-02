package com.autohaus.backend.usage.insights;

import com.autohaus.backend.common.logging.JobTrace;
import com.autohaus.backend.common.util.Sleeper;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.usage.UsageBlobClient;
import com.autohaus.backend.usage.UsageZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Monthly compaction and retention purge of {@code usage-insights.json}, independent of every
 * switch (REQ-038 Step 4, NFR-Datenschutz-7, AC-24) / Monatsverdichtung und Aufbewahrung von
 * {@code usage-insights.json}, unabhängig von allen Schaltern.
 *
 * <p><b>{@link #apply(InsightsHistory, LocalDate)} — the pure rule / die reine Regel:</b>
 * <ul>
 *   <li><b>Compaction:</b> every closed month M for which today is the
 *       {@value #COMPACTION_DAY_OF_MONTH}th of M+1 or later: {@code branchDays} of M are summed into
 *       {@code branchMonths[M]} and deleted — day-exact per-branch values live at most until the
 *       8th of the following month (≤ 38 days). A month that is already compacted (key present =
 *       frozen) is never summed twice; stray {@code branchDays} of it are only deleted. /
 *       Verdichtung; ein eingefrorener Monat wird nie doppelt gezählt.</li>
 *   <li><b>Purge:</b> global days, {@code branchDays} and {@code branchMonths} older than
 *       {@code usage.retention-months} (24) are removed — regardless of whether a rollup succeeded
 *       (pattern {@code UsageStatsScheduler.purgeExpired}). / Purge unabhängig vom Rollup-Erfolg.</li>
 * </ul>
 *
 * <p><b>The own runs / die eigenen Läufe:</b> blob I/O only — no KQL, no SoftNet —, written only on
 * a change, {@code If-Match} with at most {@value #MAX_WRITE_ATTEMPTS} attempts. It never touches
 * {@code capacity-daily.json} (only {@code CapacityScanScheduler} writes that file) nor
 * {@code usage-daily.json}. Without {@code USAGE_STORAGE_ACCOUNT} nothing happens. / Nur Blob-I/O,
 * schreibt nur bei Änderung, nie die Kapazitätsdatei.
 * <ul>
 *   <li>{@link #dailyRun()} — the <b>guarantee</b> of NFR-Datenschutz-7: a fixed, non-configurable
 *       daily cron ({@value #DAILY_CRON} in {@code usage.zone}) that runs whenever storage is
 *       configured — regardless of every switch, of an active {@code InsightsScheduler} and of
 *       {@code USAGE_INSIGHTS_ROLLUP_CRON=-}. Day-exact {@code branchDays} are therefore compacted
 *       on the {@value #COMPACTION_DAY_OF_MONTH}th of the following month at the latest (while the
 *       blob is reachable), even when the rollup is switched off or keeps failing. After a normal
 *       rollup (which already applied the rule) it finds nothing and writes nothing. / Fester,
 *       nicht abschaltbarer Tageslauf — unabhängig vom Rollup.</li>
 *   <li>{@link #run()} — on startup and at {@code USAGE_INSIGHTS_ROLLUP_CRON}, only while no
 *       {@code InsightsScheduler} is active: that scheduler calls {@code apply(...)} inside its
 *       <b>own</b> write (one PUT per rollup), so this run then defers to it. / Beim Start und zum
 *       Rollup-Cron nur ohne aktiven Insights-Scheduler.</li>
 * </ul>
 *
 * <p>Both runs and the rollup may meet on the same blob: every write is conditional and a 412/409
 * re-reads and applies the pure rule again, so the result is the same in any order (E5). / Alle
 * Schreibvorgänge sind bedingt; die Reihenfolge ist egal.
 */
@Component
public class InsightsRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(InsightsRetentionJob.class);

    /** Day of the following month from which a closed month is compacted (§16) / Stichtag. */
    public static final int COMPACTION_DAY_OF_MONTH = 8;

    /** Conditional write attempts (E5) / Schreibversuche. */
    public static final int MAX_WRITE_ATTEMPTS = 3;

    /**
     * Fixed cron of the daily retention run — deliberately <b>no</b> property, so it cannot be
     * switched off (NFR-Datenschutz-7); 04:15 lies after the rollup (03:45 + up to 2 min jitter) /
     * fester Cron des täglichen Aufbewahrungslaufs, bewusst nicht konfigurierbar.
     */
    public static final String DAILY_CRON = "0 15 4 * * *";

    private final InsightsBlobClient blobClient;
    private final UsageStatsProperties usageProperties;
    private final ObjectProvider<InsightsScheduler> schedulerProvider;
    private final Clock clock;
    private final Sleeper sleeper;

    @Autowired
    public InsightsRetentionJob(InsightsBlobClient blobClient, UsageStatsProperties usageProperties,
                                ObjectProvider<InsightsScheduler> schedulerProvider) {
        this(blobClient, usageProperties, schedulerProvider,
            Clock.system(UsageZone.resolve(usageProperties)), Sleeper.THREAD);
    }

    /** Visible for testing / für Tests: fixed clock, no backoff sleep. */
    InsightsRetentionJob(InsightsBlobClient blobClient, UsageStatsProperties usageProperties,
                         ObjectProvider<InsightsScheduler> schedulerProvider, Clock clock,
                         Sleeper sleeper) {
        this.blobClient = blobClient;
        this.usageProperties = usageProperties;
        this.schedulerProvider = schedulerProvider;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    // ---- schedules / Zeitpläne ----

    /** Own run on startup when no insights scheduler exists / eigener Lauf beim Start. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    /** Own run at the rollup cron when no insights scheduler exists / Lauf zum Rollup-Cron. */
    @Scheduled(cron = "${usage.insights.rollup-cron:0 45 3 * * *}", zone = "${usage.zone:Europe/Berlin}")
    public void scheduledRun() {
        run();
    }

    /**
     * The non-disableable daily run at {@value #DAILY_CRON} — independent of every switch, of the
     * insights scheduler and of the rollup cron (NFR-Datenschutz-7) / täglicher Lauf, nicht
     * abschaltbar.
     */
    @Scheduled(cron = DAILY_CRON, zone = "${usage.zone:Europe/Berlin}")
    public void dailyRun() {
        enforce();
    }

    /**
     * Compacts and purges the blob on its own — only while no {@code InsightsScheduler} is active
     * (it applies the rule inside its own write) / verdichtet und purgt eigenständig, solange kein
     * Insights-Scheduler aktiv ist.
     *
     * @return whether the blob was written / ob geschrieben wurde
     */
    public boolean run() {
        if (schedulerProvider != null && schedulerProvider.getIfAvailable() != null) {
            log.debug("usage insights retention: handled inside the insights rollup — no own run");
            return false;
        }
        return enforce();
    }

    /**
     * Compacts and purges the blob now, whether or not an {@code InsightsScheduler} exists — only
     * storage must be configured / verdichtet und purgt sofort, unabhängig vom Scheduler.
     * Idempotent: a history that is already clean is not written.
     *
     * @return whether the blob was written / ob geschrieben wurde
     */
    public synchronized boolean enforce() {
        if (!blobClient.isConfigured()) {
            log.debug("usage insights retention skipped — no storage account configured (E4)");
            return false;
        }
        String previousTraceId = JobTrace.enter(InsightsScheduler.TRACE_ID);
        try {
            return compactAndPurge();
        } catch (RuntimeException e) {
            log.error("usage insights retention failed unexpectedly: {}", e.getMessage(), e);
            return false;
        } finally {
            JobTrace.restore(previousTraceId);
        }
    }

    private boolean compactAndPurge() {
        LocalDate today = LocalDate.now(clock);
        for (int attempt = 1; attempt <= MAX_WRITE_ATTEMPTS; attempt++) {
            InsightsBlobClient.Loaded loaded;
            try {
                loaded = blobClient.load();
            } catch (UsageBlobClient.BlobUnavailableException e) {
                log.warn("usage insights retention skipped — blob unavailable, retrying at the next "
                    + "run: {}", e.getMessage());
                return false;
            }
            if (!loaded.usable()) {
                log.error("usage insights retention skipped — blob content unusable; the file is left "
                    + "untouched until it is fixed (E6)");
                return false;
            }
            Result result = apply(loaded.history(), today);
            if (!result.changed()) {
                log.debug("usage insights retention: nothing to compact or purge");
                return false;
            }
            InsightsHistory stamped = withUpdatedAt(result.history());
            try {
                blobClient.save(stamped, loaded.etag());
                log.info("usage insights retention written: {} month(s) compacted, {} purged",
                    result.compactedMonths(), result.purged());
                return true;
            } catch (UsageBlobClient.BlobPreconditionFailedException e) {
                // The rule is a pure function of the blob content — re-reading and applying it
                // again yields the correct merge (E5). / Neu lesen und erneut anwenden genügt.
                log.warn("usage insights retention write conflict (attempt {}/{}) — re-reading (E5)",
                    attempt, MAX_WRITE_ATTEMPTS);
                backOff(attempt);
            } catch (UsageBlobClient.BlobUnavailableException e) {
                log.warn("usage insights retention write failed: {}", e.getMessage());
                return false;
            }
        }
        log.warn("usage insights retention gave up after {} attempts — the next run retries (E5)",
            MAX_WRITE_ATTEMPTS);
        return false;
    }

    // ---- the rule / die Regel ----

    /**
     * Applies compaction and purge to a history without any I/O / wendet Verdichtung und Purge
     * ohne I/O an. Used by this job and inside the insights rollup's write.
     *
     * @param history the history to clean up / zu bereinigende Historie
     * @param today the Berlin calendar day / Berliner Kalendertag
     * @return the cleaned history plus what changed / bereinigte Historie und Änderungen
     */
    public Result apply(InsightsHistory history, LocalDate today) {
        InsightsHistory source = history == null ? InsightsHistory.empty() : history;
        SortedMap<LocalDate, InsightsDay> days = new TreeMap<>(nonNull(source.days()));
        SortedMap<LocalDate, Map<String, BranchCounts>> branchDays = new TreeMap<>(nonNull(source.branchDays()));
        SortedMap<YearMonth, Map<String, BranchCounts>> branchMonths = new TreeMap<>(nonNull(source.branchMonths()));

        int[] compaction = compact(branchDays, branchMonths, today);
        int compacted = compaction[0];
        // Stray branch days of an already frozen month count as removed entries.
        int purged = compaction[1] + purge(days, branchDays, branchMonths, today);

        InsightsHistory cleaned = new InsightsHistory(source.schemaVersion(), source.updatedAt(),
            source.collectingSince(), source.funnelCollectingSince(), source.dropoffCollectingSince(),
            Collections.unmodifiableSortedMap(days), Collections.unmodifiableSortedMap(branchDays),
            Collections.unmodifiableSortedMap(branchMonths));
        return new Result(cleaned, compacted, purged);
    }

    /**
     * Compacts every due month / verdichtet jeden fälligen Monat.
     *
     * @return {@code [newly compacted months, stray branch days removed from frozen months]}
     */
    private static int[] compact(SortedMap<LocalDate, Map<String, BranchCounts>> branchDays,
                               SortedMap<YearMonth, Map<String, BranchCounts>> branchMonths,
                               LocalDate today) {
        TreeSet<YearMonth> months = new TreeSet<>();
        branchDays.keySet().forEach(day -> months.add(YearMonth.from(day)));
        int compacted = 0;
        int strays = 0;
        for (YearMonth month : months) {
            if (!isDue(month, today)) {
                continue;
            }
            SortedMap<LocalDate, Map<String, BranchCounts>> monthDays =
                branchDays.subMap(month.atDay(1), month.plusMonths(1).atDay(1));
            if (!branchMonths.containsKey(month)) {
                branchMonths.put(month, sumMonth(monthDays.values()));
                compacted++;
            } else {
                // Frozen already (e.g. by another replica) — never sum twice (§5.8, BR-18).
                log.debug("usage insights retention: month {} already compacted — dropping {} stray "
                    + "branch day(s)", month, monthDays.size());
                strays += monthDays.size();
            }
            monthDays.clear();
        }
        return new int[] {compacted, strays};
    }

    /** Closed month whose compaction day has come (Step 4) / abgeschlossen und fällig. */
    static boolean isDue(YearMonth month, LocalDate today) {
        return !today.isBefore(month.plusMonths(1).atDay(COMPACTION_DAY_OF_MONTH));
    }

    /** Removes everything beyond the retention; returns the number of removed entries. */
    private int purge(SortedMap<LocalDate, InsightsDay> days,
                      SortedMap<LocalDate, Map<String, BranchCounts>> branchDays,
                      SortedMap<YearMonth, Map<String, BranchCounts>> branchMonths, LocalDate today) {
        int retentionMonths = usageProperties == null ? 0 : usageProperties.retentionMonths();
        if (retentionMonths <= 0) {
            return 0;
        }
        LocalDate cutoff = today.minusMonths(retentionMonths);
        int purged = 0;
        SortedMap<LocalDate, InsightsDay> expiredDays = days.headMap(cutoff);
        purged += expiredDays.size();
        expiredDays.clear();
        SortedMap<LocalDate, Map<String, BranchCounts>> expiredBranchDays = branchDays.headMap(cutoff);
        purged += expiredBranchDays.size();
        expiredBranchDays.clear();
        SortedMap<YearMonth, Map<String, BranchCounts>> expiredMonths =
            branchMonths.headMap(YearMonth.from(cutoff));
        purged += expiredMonths.size();
        expiredMonths.clear();
        return purged;
    }

    /** Sums the per-branch counters of one month (null-safe, BR-18) / summiert einen Monat. */
    private static Map<String, BranchCounts> sumMonth(Collection<Map<String, BranchCounts>> days) {
        Map<String, BranchCounts> sums = new LinkedHashMap<>();
        for (Map<String, BranchCounts> day : days) {
            if (day == null) {
                continue;
            }
            new TreeMap<>(day).forEach((branchId, counts) -> {
                if (counts != null) {
                    sums.merge(branchId, counts, InsightsRetentionJob::add);
                }
            });
        }
        return Collections.unmodifiableMap(sums);
    }

    /** Adds two counter sets; {@code null} + {@code null} stays {@code null} („—"). */
    static BranchCounts add(BranchCounts a, BranchCounts b) {
        return new BranchCounts(
            add(a.bookings(), b.bookings()),
            add(a.serviceViews(), b.serviceViews()),
            addMaps(a.groups(), b.groups()),
            add(a.mobility(), b.mobility()),
            add(a.offHours(), b.offHours()),
            add(a.slotLate(), b.slotLate()));
    }

    private static Long add(Long a, Long b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : a + b;
    }

    private static Map<String, Long> addMaps(Map<String, Long> a, Map<String, Long> b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        Map<String, Long> sum = new LinkedHashMap<>(a);
        b.forEach((key, value) -> sum.merge(key, value == null ? 0L : value,
            (x, y) -> (x == null ? 0L : x) + y));
        return Collections.unmodifiableMap(sum);
    }

    private InsightsHistory withUpdatedAt(InsightsHistory history) {
        return new InsightsHistory(history.schemaVersion(), clock.instant(), history.collectingSince(),
            history.funnelCollectingSince(), history.dropoffCollectingSince(), history.days(),
            history.branchDays(), history.branchMonths());
    }

    private void backOff(int attempt) {
        try {
            sleeper.sleep(Duration.ofMillis(200L * attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static <K, V> Map<K, V> nonNull(Map<K, V> map) {
        return map == null ? Map.of() : map;
    }

    /**
     * Outcome of {@link #apply} / Ergebnis der Regel.
     *
     * @param history the cleaned history / bereinigte Historie
     * @param compactedMonths months newly written to {@code branchMonths} / neu verdichtete Monate
     * @param purged removed entries (days, branch days, branch months) / entfernte Einträge
     */
    public record Result(InsightsHistory history, int compactedMonths, int purged) {

        /** Whether anything changed / ob sich etwas geändert hat. */
        public boolean changed() {
            return compactedMonths > 0 || purged > 0;
        }
    }
}
