package com.autohaus.backend.capacity;

import com.autohaus.backend.common.logging.JobTrace;
import com.autohaus.backend.common.util.Sleeper;
import com.autohaus.backend.config.CapacityProperties;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.config.setup.BranchNames;
import com.autohaus.backend.config.setup.BranchSetup;
import com.autohaus.backend.config.setup.GroupEntry;
import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.autohaus.backend.usage.UsageBlobClient;
import com.autohaus.backend.usage.UsageZone;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.text.Collator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Drives the nightly capacity scan (REQ-038 K1, §4 Step 2 + 5, BR-13, BR-14, BR-17, BR-25) /
 * steuert den nächtlichen Kapazitäts-Scan.
 *
 * <p>Three entry points, none of them ever reachable from a request (BR-5, BR-16 — the refresh
 * endpoint triggers nothing here):
 * <ul>
 *   <li>{@link #onStartup()} — loads {@code capacity-daily.json} and builds the view model;
 *       <b>no SoftNet call</b>. A failed load (E4/E6) leaves a model with
 *       {@code WARN_HISTORY_UNAVAILABLE}.</li>
 *   <li>{@link #runScan()} — {@code CAPACITY_SCAN_CRON} (default 22:30 Europe/Berlin, {@code -}
 *       disables it) plus a jitter of 0–{@value #MAX_JITTER_SECONDS} s: claim, measure every
 *       branch, write the result.</li>
 *   <li>{@link #rebuildViewModel()} — daily at 00:05 from memory only, so „morgen frei" becomes
 *       „heute frei" without blob or SoftNet I/O (BR-25).</li>
 * </ul>
 *
 * <p><b>Once per calendar day across all replicas (BR-17, E5):</b> {@code @Scheduled} runs in
 * every replica, so the scan first writes a claim {@code scans[D].state = running} with
 * {@code If-Match} (initial creation {@code If-None-Match: *}). A scan day that is already
 * {@code complete|partial|failed} is skipped, a foreign claim younger than 30 min is skipped, an
 * older one counts as orphaned and is taken over; a 412/409 re-reads and re-checks, at most
 * {@value #MAX_WRITE_ATTEMPTS} attempts. Without blob storage there is no claim — and therefore no
 * scan and no SoftNet call at all (E4, AC-32). / Ohne Blob kein Claim, also kein Scan.
 *
 * <p><b>The loop:</b> one {@code getGroups()} per scan (24-h cache), the branches of
 * {@code setup.json} in alphabetical order with their {@code default} group and a pause of 1 s,
 * each measured by {@link CapacityProbe}. Failed branches are retried <b>once</b> after 60 s
 * (config errors {@code no-brand}/{@code no-reference-service} are not — a retry cannot change
 * them). After {@code MAX_SCAN_DURATION} (20 min) every branch not yet measured becomes
 * {@code error/timeout} and the scan ends {@code partial} (E2). The MDC {@code traceId} is
 * {@value CapacityProbe#TRACE_ID} for the whole run, so {@code SOFTNET_ERROR} lines of the scan stay
 * out of the Bugs tab (BR-15). / MDC für den ganzen Lauf, danach entfernt.
 *
 * <p><b>Result write:</b> absolute values, {@code If-Match}, merge on conflict (our scan day wins,
 * foreign days are kept), retention of {@code USAGE_RETENTION_MONTHS} applied in the same write
 * — only this scheduler writes {@code capacity-daily.json} (§12.10). / Nur dieser Scheduler
 * schreibt die Datei.
 */
@Component
@Conditional(CapacityScanCondition.class)
public class CapacityScanScheduler {

    private static final Logger log = LoggerFactory.getLogger(CapacityScanScheduler.class);

    /** MDC value of the whole run (BR-15) / MDC-Wert des gesamten Laufs. */
    public static final String TRACE_ID = CapacityProbe.TRACE_ID;

    /** Upper bound of the random start jitter in seconds (AC-8) / Obergrenze des Jitters. */
    public static final int MAX_JITTER_SECONDS = 300;

    /** Pause between two branches (AC-8) / Pause zwischen zwei Standorten. */
    public static final Duration PAUSE_BETWEEN_BRANCHES = Duration.ofSeconds(1);

    /** Delay before the single retry of the failed branches (E1) / Wartezeit vor dem Retry. */
    public static final Duration RETRY_DELAY = Duration.ofSeconds(60);

    /** Hard runtime limit of one scan (BR-17, E2) / Laufzeitgrenze. */
    public static final Duration MAX_SCAN_DURATION = Duration.ofMinutes(20);

    /** Age after which a foreign {@code running} claim counts as orphaned (E5) / verwaister Claim. */
    public static final Duration CLAIM_STALE_AFTER = Duration.ofMinutes(30);

    /** Cache lifetime of the {@code getGroups()} brand map (§12.9) / Cache der Group-Map. */
    public static final Duration GROUPS_CACHE_TTL = Duration.ofHours(24);

    /** Conditional write attempts for claim and result (E5) / Schreibversuche. */
    public static final int MAX_WRITE_ATTEMPTS = 3;

    private static final String DEFAULT_VARIANT = "default";

    private final CapacityProbe probe;
    private final CapacityBlobClient blobClient;
    private final CapacityStore store;
    private final SoftnetApiClient softnetApi;
    private final SetupService setupService;
    private final boolean showSlotCount;
    private final int retentionMonths;
    private final ZoneId zone;
    private final Clock clock;
    private final Sleeper sleeper;
    private final int maxJitterSeconds;

    /** Guards against overlapping runs in this replica / verhindert parallele Läufe. */
    private final AtomicBoolean scanning = new AtomicBoolean(false);

    /** Whether a history was ever loaded successfully (E4) / ob je ein Stand geladen wurde. */
    private volatile boolean historyLoaded;

    /** Start of the own replica's running scan (after the claim), else {@code null} (§5.2). */
    private volatile Instant runningSince;

    /** Day of the last {@code WARN capacity scan stale} line — once per day (TC-50). */
    private volatile LocalDate lastStaleWarning;

    private Map<String, JsonNode> groupsCache;
    private Instant groupsLoadedAt;

    @Autowired
    public CapacityScanScheduler(CapacityProbe probe, CapacityBlobClient blobClient,
                                 CapacityStore store, SoftnetApiClient softnetApi,
                                 SetupService setupService, CapacityProperties capacityProperties,
                                 UsageStatsProperties usageProperties) {
        this(probe, blobClient, store, softnetApi, setupService, capacityProperties,
            usageProperties, Clock.system(UsageZone.resolve(usageProperties)), Sleeper.THREAD,
            MAX_JITTER_SECONDS);
    }

    /**
     * Visible for testing / für Tests: pins "now", replaces sleeping (jitter, pause, retry delay)
     * and the jitter bound, so claim, retry and the 20-minute limit run without waiting. /
     * Fixiert die Zeit und ersetzt das Warten.
     */
    CapacityScanScheduler(CapacityProbe probe, CapacityBlobClient blobClient, CapacityStore store,
                          SoftnetApiClient softnetApi, SetupService setupService,
                          CapacityProperties capacityProperties,
                          UsageStatsProperties usageProperties, Clock clock, Sleeper sleeper,
                          int maxJitterSeconds) {
        this.probe = probe;
        this.blobClient = blobClient;
        this.store = store;
        this.softnetApi = softnetApi;
        this.setupService = setupService;
        this.showSlotCount = capacityProperties != null && capacityProperties.showSlotCount();
        this.retentionMonths = usageProperties == null ? 0 : usageProperties.retentionMonths();
        this.zone = UsageZone.resolve(usageProperties);
        this.clock = clock;
        this.sleeper = sleeper;
        this.maxJitterSeconds = Math.max(0, maxJitterSeconds);
    }

    // ---- schedules / Zeitpläne ----

    /**
     * Loads the persisted history and builds the view model — blob only, never SoftNet (Step 5).
     * Ordered first among the startup listeners so the E2E seed loader (lowest priority) can
     * overwrite the store afterwards. / Läuft vor dem Seed-Loader.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void onStartup() {
        log.info("REQ-038 capacity scan active — loading the capacity history (no SoftNet call)");
        try {
            loadIntoStore();
        } catch (RuntimeException e) {
            log.error("capacity history load failed unexpectedly: {}", e.getMessage(), e);
        }
    }

    /**
     * The nightly scan / der nächtliche Scan. Never throws into the scheduler; the MDC
     * {@code traceId} is {@value CapacityProbe#TRACE_ID} for the whole run and removed afterwards
     * (BR-15).
     */
    @Scheduled(cron = "${capacity.scan-cron}", zone = "${usage.zone}")
    public void runScan() {
        if (!blobClient.isConfigured()) {
            // E4/AC-32: no claim possible, so no scan and no SoftNet call — the local normal case.
            log.debug("capacity scan skipped — no storage account configured (E4)");
            return;
        }
        if (!scanning.compareAndSet(false, true)) {
            log.info("capacity scan skipped — a scan is already running in this replica");
            return;
        }
        String previousTraceId = JobTrace.enter(TRACE_ID);
        try {
            applyJitter();
            scan();
        } catch (RuntimeException e) {
            log.error("capacity scan failed unexpectedly: {}", e.getMessage(), e);
        } finally {
            if (runningSince != null) {
                // Aborted mid-run: drop the "running" hint instead of showing it forever.
                runningSince = null;
                try {
                    publish(store.history());
                } catch (RuntimeException e) {
                    log.error("capacity view rebuild after an aborted scan failed: {}",
                        e.getMessage());
                }
            }
            JobTrace.restore(previousTraceId);
            scanning.set(false);
        }
    }

    /**
     * Daily rebuild at 00:05 from memory only (BR-25, Step 5) / täglicher Neuaufbau ohne I/O: the
     * labels relative to the day of retrieval and the stale warning move with the calendar day.
     */
    @Scheduled(cron = "0 5 0 * * *", zone = "${usage.zone}")
    public void rebuildViewModel() {
        try {
            publish(store.history());
        } catch (RuntimeException e) {
            log.error("capacity view rebuild failed unexpectedly: {}", e.getMessage(), e);
        }
    }

    // ---- scan / Scan ----

    private void scan() {
        Instant startedAt = clock.instant();
        LocalDate scanDay = LocalDate.ofInstant(startedAt, zone);
        LocalDate targetDay = CapacityCalendar.nextWorkday(scanDay);

        Claim claim = claim(scanDay, startedAt, targetDay);
        if (claim == null) {
            return;
        }
        runningSince = startedAt;
        Map<String, String> names = BranchNames.byId(setupService);
        store.update(buildViewModel(claim.history(), names), claim.history());

        List<BranchSetup> branches = sortedBranches();
        Instant deadline = startedAt.plus(MAX_SCAN_DURATION);
        Map<String, ProbeResult> results = new LinkedHashMap<>();
        GroupsLookup groups = loadGroups();
        boolean stopped = measure(branches, groups, targetDay, deadline, results, false);
        if (stopped) {
            log.warn("capacity scan {}: runtime limit of {} min reached — the remaining branches "
                + "are recorded as timeout (E2)", scanDay, MAX_SCAN_DURATION.toMinutes());
        } else {
            List<BranchSetup> failed = retryable(branches, results);
            if (!failed.isEmpty()) {
                log.info("capacity scan {}: retrying {} failed branch(es) in {} s (E1)", scanDay,
                    failed.size(), RETRY_DELAY.toSeconds());
                if (pause(RETRY_DELAY)) {
                    if (groups.failure() != null) {
                        groups = loadGroups();
                    }
                    measure(failed, groups, targetDay, deadline, results, true);
                }
            }
        }

        Instant finishedAt = clock.instant();
        CapacityScan scan = new CapacityScan(stateOf(results.values()), startedAt, finishedAt,
            targetDay, Collections.unmodifiableSortedMap(new TreeMap<>(results)));
        CapacityHistory written = persistResult(scanDay, scan, claim);
        CapacityHistory current = written != null ? written
            : purgeExpired(withScan(claim.history(), scanDay, scan), scanDay);
        runningSince = null;
        store.update(buildViewModel(current, names), current);
        checkStale(current);

        long ok = count(results.values(), ProbeStatus.OK);
        long none = count(results.values(), ProbeStatus.NONE);
        long error = count(results.values(), ProbeStatus.ERROR);
        log.info("capacity scan {}: {} ok, {} none, {} error in {} s", scanDay, ok, none, error,
            Duration.between(startedAt, finishedAt).toSeconds());
    }

    /**
     * Measures the given branches in order; returns {@code true} when the runtime limit (or an
     * interrupt) stopped the loop. In the first pass every branch not measured any more becomes
     * {@code error/timeout} (E2); in the retry pass it keeps its first error. / Misst der Reihe nach.
     */
    private boolean measure(List<BranchSetup> branches, GroupsLookup groups, LocalDate targetDay,
                            Instant deadline, Map<String, ProbeResult> results, boolean retry) {
        boolean stopped = false;
        for (int i = 0; i < branches.size(); i++) {
            BranchSetup branch = branches.get(i);
            if (!stopped && i > 0 && !pause(PAUSE_BETWEEN_BRANCHES)) {
                stopped = true;
            }
            if (!stopped && !clock.instant().isBefore(deadline)) {
                stopped = true;
            }
            if (stopped) {
                if (!retry) {
                    results.put(branch.branchId(), error(ProbeReason.TIMEOUT));
                }
                continue;
            }
            results.put(branch.branchId(), probeBranch(branch, groups, targetDay));
        }
        return stopped;
    }

    private ProbeResult probeBranch(BranchSetup branch, GroupsLookup groups, LocalDate targetDay) {
        if (groups.failure() != null) {
            // getGroups() failed: every branch of this scan gets that reason (E1).
            return error(groups.failure());
        }
        String groupId = defaultGroupId(branch);
        JsonNode group = groupId == null ? null : groups.byId().get(groupId);
        try {
            return probe.probe(branch.branchId(), groupId, group, targetDay);
        } catch (RuntimeException e) {
            // The probe never throws; this guard keeps one branch from killing the scan.
            return error(CapacityProbe.reasonOf(e));
        }
    }

    /** Failed branches worth a retry — not the config errors no-brand/no-reference-service. */
    private static List<BranchSetup> retryable(List<BranchSetup> branches,
                                               Map<String, ProbeResult> results) {
        List<BranchSetup> failed = new ArrayList<>();
        for (BranchSetup branch : branches) {
            ProbeResult result = results.get(branch.branchId());
            if (result != null && result.status() == ProbeStatus.ERROR
                && result.reason() != ProbeReason.NO_BRAND
                && result.reason() != ProbeReason.NO_REFERENCE_SERVICE) {
                failed.add(branch);
            }
        }
        return failed;
    }

    /**
     * {@code complete} (no error) · {@code partial} (at least one error) · {@code failed} (no
     * {@code ok}/{@code none}, §10.6) / Zustand des Scans.
     */
    static ScanState stateOf(Collection<ProbeResult> results) {
        boolean anyError = false;
        boolean anyMeasured = false;
        for (ProbeResult result : results) {
            if (result.status() == ProbeStatus.ERROR) {
                anyError = true;
            } else {
                anyMeasured = true;
            }
        }
        if (!anyMeasured) {
            return ScanState.FAILED;
        }
        return anyError ? ScanState.PARTIAL : ScanState.COMPLETE;
    }

    // ---- claim and result write / Claim und Ergebnis ----

    /**
     * Writes the claim {@code scans[D].state = running} (BR-17, E5) / schreibt den Claim.
     *
     * @return the claimed history plus its new ETag, {@code null} when the scan must not run
     */
    private Claim claim(LocalDate scanDay, Instant startedAt, LocalDate targetDay) {
        for (int attempt = 1; attempt <= MAX_WRITE_ATTEMPTS; attempt++) {
            CapacityBlobClient.Loaded loaded;
            try {
                loaded = blobClient.load();
            } catch (UsageBlobClient.BlobUnavailableException e) {
                log.warn("capacity scan skipped — blob unavailable, no claim possible (E4): {}",
                    e.getMessage());
                return null;
            }
            if (!loaded.usable()) {
                log.error("capacity scan skipped — capacity blob unusable; the file is left "
                    + "untouched for manual analysis and no scan runs until it is fixed (E6)");
                return null;
            }
            historyLoaded = true;
            CapacityHistory history = loaded.history();
            CapacityScan existing = history.scans().get(scanDay);
            if (existing != null && existing.state() != ScanState.RUNNING) {
                log.info("capacity scan skipped — the scan of {} is already {} (BR-17)", scanDay,
                    existing.state().key());
                // The read was successful anyway: show the fresh state (e.g. another replica's).
                publish(history);
                return null;
            }
            if (existing != null) {
                boolean fresh = existing.startedAt() != null
                    && Duration.between(existing.startedAt(), startedAt)
                        .compareTo(CLAIM_STALE_AFTER) < 0;
                if (fresh) {
                    log.info("capacity scan skipped — claimed by another replica");
                    publish(history);
                    return null;
                }
                log.info("capacity scan {}: taking over an orphaned claim from {} (E5)", scanDay,
                    existing.startedAt());
            }
            CapacityHistory claimed = withScan(history, scanDay,
                new CapacityScan(ScanState.RUNNING, startedAt, null, targetDay, Map.of()));
            try {
                String etag = blobClient.save(claimed, loaded.etag());
                return new Claim(claimed, etag);
            } catch (UsageBlobClient.BlobPreconditionFailedException e) {
                log.warn("capacity scan claim conflict (attempt {}/{}) — re-reading (E5)", attempt,
                    MAX_WRITE_ATTEMPTS);
            } catch (UsageBlobClient.BlobUnavailableException e) {
                log.warn("capacity scan skipped — claim not writable (E4): {}", e.getMessage());
                return null;
            }
        }
        log.warn("capacity scan skipped — no claim after {} attempts (E5)", MAX_WRITE_ATTEMPTS);
        return null;
    }

    /**
     * Writes the scan result with absolute values, {@code If-Match} and retention; on 412/409
     * re-reads, lays the own scan day over the fresh state and retries — at most
     * {@value #MAX_WRITE_ATTEMPTS} attempts (E5) / schreibt das Ergebnis.
     *
     * @return the written history, {@code null} when it could not be written
     */
    private CapacityHistory persistResult(LocalDate scanDay, CapacityScan scan, Claim claim) {
        CapacityHistory base = claim.history();
        String etag = claim.etag();
        for (int attempt = 1; attempt <= MAX_WRITE_ATTEMPTS; attempt++) {
            CapacityHistory desired = purgeExpired(withScan(base, scanDay, scan), scanDay);
            try {
                blobClient.save(desired, etag);
                return desired;
            } catch (UsageBlobClient.BlobPreconditionFailedException e) {
                log.warn("capacity scan result write conflict (attempt {}/{}) — re-reading and "
                    + "merging (E5)", attempt, MAX_WRITE_ATTEMPTS);
                if (attempt == MAX_WRITE_ATTEMPTS) {
                    break;
                }
                try {
                    CapacityBlobClient.Loaded fresh = blobClient.load();
                    if (!fresh.usable()) {
                        log.error("capacity scan result not written — blob became unusable during "
                            + "the retry (E6)");
                        return null;
                    }
                    base = fresh.history();
                    etag = fresh.etag();
                } catch (UsageBlobClient.BlobUnavailableException reread) {
                    log.warn("capacity scan result not written — blob unavailable during the "
                        + "retry: {}", reread.getMessage());
                    return null;
                }
            } catch (UsageBlobClient.BlobUnavailableException e) {
                log.warn("capacity scan result not written (E4): {}", e.getMessage());
                return null;
            }
        }
        log.warn("capacity scan result not written after {} attempts (E5)", MAX_WRITE_ATTEMPTS);
        return null;
    }

    /** Copy with {@code scans[day] = scan} and the configured reference service / Kopie mit Scan. */
    private CapacityHistory withScan(CapacityHistory history, LocalDate day, CapacityScan scan) {
        SortedMap<LocalDate, CapacityScan> scans = new TreeMap<>(history.scans());
        scans.put(day, scan);
        return new CapacityHistory(CapacityHistory.CURRENT_SCHEMA_VERSION,
            probe.referenceService(), Collections.unmodifiableSortedMap(scans));
    }

    /**
     * Removes scans older than {@code USAGE_RETENTION_MONTHS} (NFR-Datenschutz-7) / entfernt Scans
     * jenseits der Aufbewahrung. {@code ≤ 0} disables the purge (same rule as REQ-037).
     */
    private CapacityHistory purgeExpired(CapacityHistory history, LocalDate today) {
        if (retentionMonths <= 0) {
            return history;
        }
        LocalDate cutoff = today.minusMonths(retentionMonths);
        SortedMap<LocalDate, CapacityScan> kept = new TreeMap<>(history.scans().tailMap(cutoff));
        int purged = history.scans().size() - kept.size();
        if (purged > 0) {
            log.info("capacity scan: {} scan(s) older than {} month(s) removed", purged,
                retentionMonths);
        }
        return new CapacityHistory(history.schemaVersion(), history.referenceService(),
            Collections.unmodifiableSortedMap(kept));
    }

    // ---- view model / Anzeigemodell ----

    /**
     * Loads the blob into the store (startup) / lädt den Blob in den Store. Without storage or on
     * E4/E6 the store gets a model from the empty history plus {@code WARN_HISTORY_UNAVAILABLE} —
     * unless the E2E seed loader already filled it.
     */
    private void loadIntoStore() {
        if (!blobClient.isConfigured()) {
            log.debug("capacity history unavailable — no storage account configured (E4)");
            if (!store.hasViewModel()) {
                publish(CapacityHistory.empty());
            }
            return;
        }
        try {
            CapacityBlobClient.Loaded loaded = blobClient.load();
            if (!loaded.usable()) {
                log.error("capacity history unusable on startup — the file is left untouched for "
                    + "manual analysis (E6)");
                publish(store.history());
                return;
            }
            historyLoaded = true;
            publish(loaded.history());
        } catch (UsageBlobClient.BlobUnavailableException e) {
            log.warn("capacity history unavailable on startup — S1 shows the history hint (E4): {}",
                e.getMessage());
            publish(store.history());
        }
    }

    /** Builds and stores the view for the given history, logs staleness once a day. */
    private void publish(CapacityHistory history) {
        store.update(buildViewModel(history, BranchNames.byId(setupService)), history);
        checkStale(history);
    }

    private CapacityViewModel buildViewModel(CapacityHistory history, Map<String, String> names) {
        CapacityViewModel viewModel = CapacityViewModel.build(history, names, showSlotCount,
            today(), runningSince);
        boolean available = historyLoaded || (history != null && !history.scans().isEmpty());
        return available ? viewModel
            : viewModel.withAdditionalWarnings(List.of(CapacityViewModel.WARN_HISTORY_UNAVAILABLE));
    }

    /** {@code WARN capacity scan stale: last scan D} at most once per day (§9, TC-50). */
    private void checkStale(CapacityHistory history) {
        LocalDate today = today();
        LocalDate latest = CapacityViewModelBuilder.latestFinalScanDay(history);
        if (CapacityViewModelBuilder.isStale(latest, today) && !today.equals(lastStaleWarning)) {
            lastStaleWarning = today;
            log.warn("capacity scan stale: last scan {}", latest);
        }
    }

    // ---- SoftNet groups and setup / Groups und Setup ----

    /**
     * The brand map of all groups, one {@code getGroups()} call per scan, cached for 24 h
     * (§12.9) / die Group-Map, 24 h gecacht. A failure (or an empty answer, which
     * {@code SoftnetApiClient} also returns for an unparseable body) is not cached and becomes the
     * reason of every branch.
     */
    private synchronized GroupsLookup loadGroups() {
        Instant now = clock.instant();
        if (groupsCache != null && groupsLoadedAt != null
            && Duration.between(groupsLoadedAt, now).compareTo(GROUPS_CACHE_TTL) < 0) {
            return new GroupsLookup(groupsCache, null);
        }
        try {
            List<JsonNode> groups = softnetApi.getGroups();
            if (groups == null || groups.isEmpty()) {
                log.warn("capacity scan: SoftNet returned no groups — every branch is recorded as "
                    + "unexpected-response (E1)");
                return new GroupsLookup(Map.of(), ProbeReason.UNEXPECTED_RESPONSE);
            }
            Map<String, JsonNode> byId = new HashMap<>();
            for (JsonNode group : groups) {
                String id = group == null ? "" : group.path("id").asText("");
                if (!id.isEmpty()) {
                    byId.putIfAbsent(id, group);
                }
            }
            groupsCache = Map.copyOf(byId);
            groupsLoadedAt = now;
            return new GroupsLookup(groupsCache, null);
        } catch (RuntimeException e) {
            ProbeReason reason = CapacityProbe.reasonOf(e);
            log.warn("capacity scan: SoftNet groups not loadable ({}) — every branch gets this "
                + "reason (E1)", reason.key());
            return new GroupsLookup(Map.of(), reason);
        }
    }

    /** {@code setup.json} branches, alphabetical by name ({@code Collator} DE) / alphabetisch. */
    private List<BranchSetup> sortedBranches() {
        List<BranchSetup> branches = new ArrayList<>(setupService.getAllBranches());
        Collator collator = Collator.getInstance(Locale.GERMAN);
        branches.sort(Comparator.comparing(
            (BranchSetup b) -> b.name() == null ? "" : b.name(), collator));
        return branches;
    }

    private static String defaultGroupId(BranchSetup branch) {
        if (branch.groups() == null) {
            return null;
        }
        return branch.groups().stream()
            .filter(g -> DEFAULT_VARIANT.equals(g.variant()))
            .map(GroupEntry::groupId)
            .filter(id -> id != null && !id.isBlank())
            .findFirst()
            .orElse(null);
    }

    // ---- helpers / Hilfen ----

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone);
    }

    private static ProbeResult error(ProbeReason reason) {
        return new ProbeResult(ProbeStatus.ERROR, reason, null, null, null);
    }

    private static long count(Collection<ProbeResult> results, ProbeStatus status) {
        return results.stream().filter(r -> r.status() == status).count();
    }

    /** Random delay of 0–{@value #MAX_JITTER_SECONDS} s / Zufalls-Jitter vor dem Claim. */
    private void applyJitter() {
        if (maxJitterSeconds <= 0) {
            return;
        }
        int seconds = ThreadLocalRandom.current().nextInt(maxJitterSeconds + 1);
        if (seconds > 0) {
            log.debug("capacity scan jitter: waiting {} s", seconds);
            pause(Duration.ofSeconds(seconds));
        }
    }

    /** Waits; {@code false} when interrupted (the interrupt flag is restored). */
    private boolean pause(Duration duration) {
        try {
            sleeper.sleep(duration);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** The claimed history and the ETag of the claim write / Claim mit ETag. */
    private record Claim(CapacityHistory history, String etag) {}

    /** The group map of this scan or the reason it could not be loaded / Group-Map oder Grund. */
    private record GroupsLookup(Map<String, JsonNode> byId, ProbeReason failure) {}
}
