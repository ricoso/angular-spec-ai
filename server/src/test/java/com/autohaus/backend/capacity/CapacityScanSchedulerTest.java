package com.autohaus.backend.capacity;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.autohaus.backend.common.filter.RequestIdFilter;
import com.autohaus.backend.config.CapacityProperties;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.config.setup.BranchSetup;
import com.autohaus.backend.config.setup.GroupEntry;
import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.snaps.OperationIdMapper;
import com.autohaus.backend.snaps.SnapCache;
import com.autohaus.backend.snaps.SnapsService;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.autohaus.backend.usage.UsageBlobClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.HttpServerErrorException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The nightly capacity scan (REQ-038 TC-34, TC-35, TC-55, TC-50/TC-54 scheduler parts, TC-48
 * capacity part; AC-8, AC-12, AC-32, BR-13, BR-15, BR-17, E1, E2, E4–E6) / der nächtliche
 * Kapazitäts-Scan.
 *
 * <p>The real {@link CapacityProbe} runs against a mocked {@link SoftnetApiClient} and
 * {@link OperationIdMapper}; the blob client is a mock whose {@code save} answers are recorded.
 * Time is a {@link MutableClock} starting Thu 22.10.2026 22:30 Europe/Berlin, and the injected
 * sleeper only records the waits and advances that clock — so the 1-s pause, the 60-s retry and the
 * 20-min limit are asserted without sleeping. / Fester, steuerbarer Clock; nichts schläft wirklich.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CapacityScanSchedulerTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    /** Thu 22.10.2026 22:30 Berlin (CEST) / Donnerstag 22:30. */
    private static final Instant START = Instant.parse("2026-10-22T20:30:00Z");
    private static final LocalDate SCAN_DAY = LocalDate.of(2026, 10, 22);
    private static final LocalDate TARGET = LocalDate.of(2026, 10, 23);

    private static final String AUDI = "68f0a1b2c3d4e5f6a7b8c9a1";
    private static final String BMW = "68f0a1b2c3d4e5f6a7b8c9b2";
    private static final String VW = "68f0a1b2c3d4e5f6a7b8c9c3";
    private static final String OPERATION = "6908a1b2c3d4e5f6a7b8c9e7";
    private static final String ETAG = "\"0x8DC1\"";

    private final ObjectMapper json = new ObjectMapper();

    @Mock private SoftnetApiClient softnetApi;
    @Mock private OperationIdMapper operationIdMapper;
    @Mock private SetupService setupService;
    @Mock private CapacityBlobClient blobClient;
    @Mock private SnapsService snapsService;
    @Mock private SnapCache snapCache;

    private final CapacityStore store = new CapacityStore();
    private MutableClock clock;
    private final List<Duration> waits = new ArrayList<>();
    private final List<String> probedBranches = new ArrayList<>();
    private final List<CapacityHistory> saved = new ArrayList<>();
    private final Map<String, Function<String, List<JsonNode>>> snapAnswers = new HashMap<>();
    private ListAppender<ILoggingEvent> appender;
    private Logger schedulerLogger;
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        // setup.json delivers the branches unsorted on purpose — the scan sorts by name (AC-8).
        when(setupService.getAllBranches()).thenReturn(List.of(
            branch(VW, "VW Nordtor"), branch(AUDI, "Audi Hafenstadt"), branch(BMW, "BMW Altstadt")));
        when(softnetApi.getGroups()).thenReturn(List.of(group(AUDI, "Audi"), group(BMW, "BMW"),
            group(VW, "Volkswagen")));
        when(operationIdMapper.mapToSoftnetOperations(anyString(), eq(List.of("wiper-blades"))))
            .thenReturn(List.of(OPERATION));
        when(softnetApi.getSnaps(any(), anyString())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> wishlist = (Map<String, Object>) invocation.getArgument(0);
            String branchId = (String) wishlist.get("preferredBranch");
            probedBranches.add(branchId);
            Function<String, List<JsonNode>> answer = snapAnswers.get(branchId);
            return answer == null ? List.of(snap(branchId, "2026-10-23T07:00:00Z")) : answer.apply(branchId);
        });
        when(blobClient.isConfigured()).thenReturn(true);
        when(blobClient.save(any(), any())).thenAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return "\"0x8DC" + (saved.size() + 1) + "\"";
        });
        schedulerLogger = (Logger) LoggerFactory.getLogger(CapacityScanScheduler.class);
        previousLevel = schedulerLogger.getLevel();
        schedulerLogger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        schedulerLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        schedulerLogger.detachAppender(appender);
        schedulerLogger.setLevel(previousLevel);
        MDC.clear();
    }

    // ---- TC-35: no blob, no scan / ohne Blob kein Scan (E4, AC-32) ----

    /**
     * TC-35: without {@code USAGE_STORAGE_ACCOUNT} no claim is possible — {@code runScan()} makes
     * no SoftNet call and no blob call, logs {@code DEBUG} and leaves the store untouched. / Kein
     * SoftNet-Call, Store unverändert.
     */
    @Test
    @DisplayName("TC-35: without blob storage no scan and no SoftNet call")
    void doesNotScanWithoutBlobStorage() {
        when(blobClient.isConfigured()).thenReturn(false);

        scheduler().runScan();

        verifyNoInteractions(softnetApi, operationIdMapper, snapsService, snapCache);
        verify(blobClient, never()).load();
        verify(blobClient, never()).save(any(), any());
        assertNull(store.current(), "store unchanged");
        assertTrue(logged(Level.DEBUG, "no storage account configured"));
    }

    // ---- TC-34: claim / Claim (BR-17, E5) ----

    /** TC-34: a foreign claim 10 min old → skipped without any SoftNet call. / Frischer Claim. */
    @Test
    @DisplayName("TC-34: foreign claim 10 min old → skipped without SoftNet")
    void skipsAFreshForeignClaim() {
        givenBlob(historyWith(SCAN_DAY, running(START.minus(Duration.ofMinutes(10)))));

        scheduler().runScan();

        verifyNoInteractions(softnetApi, operationIdMapper);
        verify(blobClient, never()).save(any(), any());
        assertTrue(logged(Level.INFO, "claimed by another replica"));
    }

    /** TC-34: a foreign claim 45 min old is orphaned and taken over. / Verwaister Claim. */
    @Test
    @DisplayName("TC-34: foreign claim 45 min old → taken over")
    void takesOverAnOrphanedClaim() {
        givenBlob(historyWith(SCAN_DAY, running(START.minus(Duration.ofMinutes(45)))));

        scheduler().runScan();

        assertEquals(2, saved.size(), "claim + result");
        assertEquals(ScanState.RUNNING, saved.get(0).scans().get(SCAN_DAY).state());
        assertEquals(START, saved.get(0).scans().get(SCAN_DAY).startedAt(), "own claim time");
        assertEquals(ScanState.COMPLETE, saved.get(1).scans().get(SCAN_DAY).state());
        assertTrue(logged(Level.INFO, "orphaned claim"));
    }

    /** TC-34/E5 (3): a scan day that is already final is never scanned twice. / Kein Zweit-Scan. */
    @ParameterizedTest(name = "TC-34: scan of today already {0} → skipped")
    @EnumSource(value = ScanState.class, names = {"COMPLETE", "PARTIAL", "FAILED"})
    void skipsADayThatIsAlreadyScanned(ScanState state) {
        givenBlob(historyWith(SCAN_DAY, new CapacityScan(state, START.minusSeconds(60),
            START.minusSeconds(10), TARGET, Map.of(AUDI, ok(0)))));

        scheduler().runScan();

        verifyNoInteractions(softnetApi, operationIdMapper);
        verify(blobClient, never()).save(any(), any());
        assertEquals(SCAN_DAY, store.current().scanDay(), "the fresh history is shown");
    }

    /**
     * TC-34/E5: a 412 on the claim re-reads and re-checks; the second attempt claims. With three
     * conflicts in a row the scan does not run. / 412 → neu lesen.
     */
    @Test
    @DisplayName("TC-34: 412 on the claim → re-read and claim again")
    void reReadsAfterAClaimConflict() {
        givenBlob(CapacityHistory.empty());
        doThrow(new UsageBlobClient.BlobPreconditionFailedException("412"))
            .doAnswer(invocation -> {
                saved.add(invocation.getArgument(0));
                return "\"0x8DC9\"";
            })
            .when(blobClient).save(any(), any());

        scheduler().runScan();

        verify(blobClient, times(2)).load();
        verify(blobClient).save(any(), eq("\"0x8DC9\""));
        assertEquals(ScanState.COMPLETE, saved.get(saved.size() - 1).scans().get(SCAN_DAY).state());
    }

    /** E5: three claim conflicts in a row → no scan at all. / Drei Konflikte → kein Scan. */
    @Test
    void givesUpAfterThreeClaimConflicts() {
        givenBlob(CapacityHistory.empty());
        doThrow(new UsageBlobClient.BlobPreconditionFailedException("412"))
            .when(blobClient).save(any(), any());

        scheduler().runScan();

        verify(blobClient, times(3)).load();
        verifyNoInteractions(softnetApi);
        assertTrue(logged(Level.WARN, "no claim after 3 attempts"));
    }

    // ---- TC-34: the loop / die Schleife (AC-8, E1, E2) ----

    /**
     * TC-34/AC-8: exactly one {@code getGroups()} per scan, branches in alphabetical order with a
     * 1-s pause between them, the MDC {@code traceId = job.capacityprobe} during the SoftNet calls
     * and removed afterwards, the INFO summary line and the final view without
     * {@code runningSince}. / Ein getGroups, alphabetisch, MDC gesetzt und entfernt.
     */
    @Test
    @DisplayName("TC-34: one getGroups, alphabetical order, 1 s pause, MDC set and removed")
    void scansAllBranchesAlphabeticallyWithOneGroupsCall() {
        givenBlob(CapacityHistory.empty());
        List<String> traceIds = new ArrayList<>();
        snapAnswers.put(BMW, branchId -> {
            traceIds.add(MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY));
            return List.of(snap(branchId, "2026-10-26T07:00:00Z"));
        });

        scheduler().runScan();

        verify(softnetApi, times(1)).getGroups();
        verify(softnetApi, never()).getGroup(anyString());
        assertEquals(List.of(AUDI, BMW, VW), probedBranches, "Audi, BMW, VW");
        assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(1)), waits);
        assertEquals(List.of("job.capacityprobe"), traceIds);
        assertNull(MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY), "MDC removed after the run");
        CapacityScan result = saved.get(1).scans().get(SCAN_DAY);
        assertEquals(ScanState.COMPLETE, result.state());
        assertEquals(TARGET, result.targetDay(), "next working day after Thursday");
        assertEquals(START, result.startedAt());
        assertEquals(2, result.branches().get(BMW).workdaysToFirstFree(), "Fr + Sa before Mo 26.10.");
        assertEquals("wiper-blades", saved.get(1).referenceService());
        assertTrue(logged(Level.INFO, "capacity scan 2026-10-22: 3 ok, 0 none, 0 error in 2 s"));
        CapacityViewModel vm = store.current();
        assertNull(vm.runningSince());
        assertEquals(3, vm.rows().size());
        assertEquals(SCAN_DAY, vm.scanDay());
        verifyNoInteractions(snapsService, snapCache);
    }

    /**
     * TC-34/E1: two branches fail with 500 → exactly one retry after 60 s, only for them; the
     * retry succeeds for one, the other stays {@code error/softnet-unavailable} → {@code partial}.
     * / Genau ein Retry nach 60 s.
     */
    @Test
    @DisplayName("TC-34: two failed branches → exactly one retry after 60 s")
    void retriesFailedBranchesOnceAfterSixtySeconds() {
        givenBlob(CapacityHistory.empty());
        int[] audiCalls = {0};
        snapAnswers.put(AUDI, branchId -> {
            if (audiCalls[0]++ == 0) {
                throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return List.of(snap(branchId, "2026-10-23T08:00:00Z"));
        });
        snapAnswers.put(VW, branchId -> {
            throw new HttpServerErrorException(HttpStatus.BAD_GATEWAY);
        });

        scheduler().runScan();

        assertEquals(List.of(AUDI, BMW, VW, AUDI, VW), probedBranches);
        assertEquals(1, waits.stream().filter(CapacityScanScheduler.RETRY_DELAY::equals).count());
        CapacityScan result = saved.get(1).scans().get(SCAN_DAY);
        assertEquals(ScanState.PARTIAL, result.state());
        assertEquals(ProbeStatus.OK, result.branches().get(AUDI).status());
        assertEquals(new ProbeResult(ProbeStatus.ERROR, ProbeReason.SOFTNET_UNAVAILABLE, null, null,
            null), result.branches().get(VW));
        assertTrue(logged(Level.INFO, "1 error"));
    }

    /**
     * §5.6: config errors ({@code no-reference-service}, {@code no-brand}) are not retried — a
     * retry after 60 s cannot change them. / Konfigurationsfehler ohne Retry.
     */
    @Test
    void doesNotRetryConfigurationErrors() {
        givenBlob(CapacityHistory.empty());
        when(operationIdMapper.mapToSoftnetOperations(eq(AUDI), eq(List.of("wiper-blades"))))
            .thenReturn(List.of());
        when(softnetApi.getGroups()).thenReturn(List.of(group(AUDI, "Audi"), group(VW, "VW")));

        scheduler().runScan();

        assertFalse(waits.contains(CapacityScanScheduler.RETRY_DELAY));
        CapacityScan result = saved.get(1).scans().get(SCAN_DAY);
        assertEquals(ProbeReason.NO_REFERENCE_SERVICE, result.branches().get(AUDI).reason());
        assertEquals(ProbeReason.NO_BRAND, result.branches().get(BMW).reason(), "group missing");
        assertEquals(ScanState.PARTIAL, result.state());
    }

    /**
     * TC-34/E2: each snaps call takes 11 minutes → after two branches the 20-min limit is reached;
     * the rest becomes {@code error/timeout}, the scan ends {@code partial} and nothing is retried.
     * / Laufzeitgrenze → Rest timeout.
     */
    @Test
    @DisplayName("TC-34: runtime > 20 min → rest timeout, state partial")
    void stopsAtTheTwentyMinuteLimit() {
        givenBlob(CapacityHistory.empty());
        Function<String, List<JsonNode>> slow = branchId -> {
            clock.advance(Duration.ofMinutes(11));
            return List.of(snap(branchId, "2026-10-23T07:00:00Z"));
        };
        snapAnswers.put(AUDI, slow);
        snapAnswers.put(BMW, slow);

        scheduler().runScan();

        assertEquals(List.of(AUDI, BMW), probedBranches);
        CapacityScan result = saved.get(1).scans().get(SCAN_DAY);
        assertEquals(ScanState.PARTIAL, result.state());
        assertEquals(new ProbeResult(ProbeStatus.ERROR, ProbeReason.TIMEOUT, null, null, null),
            result.branches().get(VW));
        assertFalse(waits.contains(CapacityScanScheduler.RETRY_DELAY));
        assertTrue(logged(Level.WARN, "runtime limit of 20 min"));
    }

    /**
     * E1: {@code getGroups()} fails → every branch gets that reason without any snaps call; the
     * retry after 60 s reloads the groups once more; still failing → {@code failed}. / getGroups
     * scheitert → alle Standorte mit Grund.
     */
    @Test
    void recordsTheGroupsFailureForEveryBranch() {
        givenBlob(CapacityHistory.empty());
        when(softnetApi.getGroups()).thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

        scheduler().runScan();

        verify(softnetApi, times(2)).getGroups();
        verify(softnetApi, never()).getSnaps(any(), anyString());
        CapacityScan result = saved.get(1).scans().get(SCAN_DAY);
        assertEquals(ScanState.FAILED, result.state());
        result.branches().values().forEach(r ->
            assertEquals(ProbeReason.SOFTNET_UNAVAILABLE, r.reason()));
        assertTrue(store.current().showingFallbackScan() || store.current().state() == ScanState.FAILED);
    }

    /** E1: an empty groups answer is not cached and counts as unexpected-response. */
    @Test
    void treatsAnEmptyGroupsAnswerAsUnexpected() {
        givenBlob(CapacityHistory.empty());
        when(softnetApi.getGroups()).thenReturn(List.of());

        scheduler().runScan();

        CapacityScan result = saved.get(1).scans().get(SCAN_DAY);
        result.branches().values().forEach(r ->
            assertEquals(ProbeReason.UNEXPECTED_RESPONSE, r.reason()));
    }

    /**
     * §12.9: the group map is cached for 24 h — a second scan the next evening within the TTL
     * makes no further {@code getGroups()} call. / 24-h-Cache der Group-Map.
     */
    @Test
    void cachesTheGroupMapForTwentyFourHours() {
        givenBlob(CapacityHistory.empty());
        CapacityScanScheduler scheduler = scheduler();
        scheduler.runScan();
        clock.set(START.plus(Duration.ofHours(23)));

        scheduler.runScan();

        verify(softnetApi, times(1)).getGroups();
        assertEquals(4, saved.size(), "two scans: claim + result each");
    }

    /**
     * TC-34/NFR-Datenschutz-7: the result write removes scans older than
     * {@code USAGE_RETENTION_MONTHS} (24); the claim write keeps the file otherwise unchanged. /
     * Aufbewahrung im Ergebnis-Write.
     */
    @Test
    @DisplayName("TC-34: result write removes scans older than 24 months")
    void purgesScansBeyondTheRetention() {
        SortedMap<LocalDate, CapacityScan> old = new TreeMap<>();
        old.put(LocalDate.of(2024, 10, 21), complete(Map.of(AUDI, ok(0))));
        old.put(LocalDate.of(2024, 10, 22), complete(Map.of(AUDI, ok(0))));
        old.put(LocalDate.of(2026, 10, 21), complete(Map.of(AUDI, ok(0))));
        givenBlob(new CapacityHistory(1, "wiper-blades", old));

        scheduler().runScan();

        assertTrue(saved.get(0).scans().containsKey(LocalDate.of(2024, 10, 21)), "claim keeps");
        SortedMap<LocalDate, CapacityScan> written = saved.get(1).scans();
        assertFalse(written.containsKey(LocalDate.of(2024, 10, 21)), "older than 24 months");
        assertTrue(written.containsKey(LocalDate.of(2024, 10, 22)), "exactly 24 months is kept");
        assertTrue(written.containsKey(LocalDate.of(2026, 10, 21)));
        assertTrue(written.containsKey(SCAN_DAY));
    }

    /**
     * E5: a 412 on the result write re-reads, lays the own scan day over the fresh state (foreign
     * days are kept) and writes again with the fresh ETag. / Merge nach Konflikt.
     */
    @Test
    void mergesAfterAResultWriteConflict() {
        CapacityHistory fresh = historyWith(LocalDate.of(2026, 10, 20), complete(Map.of(AUDI, ok(3))));
        when(blobClient.load())
            .thenReturn(new CapacityBlobClient.Loaded(CapacityHistory.empty(), null, true))
            .thenReturn(new CapacityBlobClient.Loaded(fresh, "\"0x8DF0\"", true));
        int[] calls = {0};
        doAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            if (++calls[0] == 2) {
                throw new UsageBlobClient.BlobPreconditionFailedException("412");
            }
            return "\"0x8DC" + calls[0] + "\"";
        }).when(blobClient).save(any(), any());

        scheduler().runScan();

        assertEquals(3, saved.size());
        CapacityHistory merged = saved.get(2);
        assertTrue(merged.scans().containsKey(LocalDate.of(2026, 10, 20)), "foreign day kept");
        assertEquals(ScanState.COMPLETE, merged.scans().get(SCAN_DAY).state());
        verify(blobClient).save(any(), eq("\"0x8DF0\""));
    }

    /**
     * E4 during the result write: the measured result is still shown from memory, the "running"
     * hint disappears, nothing throws. / Ergebnis bleibt im Speicher sichtbar.
     */
    @Test
    void showsTheResultEvenIfTheResultWriteFails() {
        givenBlob(CapacityHistory.empty());
        int[] calls = {0};
        doAnswer(invocation -> {
            if (++calls[0] == 2) {
                throw new UsageBlobClient.BlobUnavailableException("network");
            }
            return ETAG;
        }).when(blobClient).save(any(), any());

        assertDoesNotThrow(() -> scheduler().runScan());

        assertEquals(SCAN_DAY, store.current().scanDay());
        assertNull(store.current().runningSince());
        assertTrue(logged(Level.WARN, "result not written"));
    }

    /** E4/E6 on the claim read: no scan, no write. / Blob-Ausfall beim Claim. */
    @Test
    void skipsTheScanWhenTheClaimReadFails() {
        when(blobClient.load())
            .thenThrow(new UsageBlobClient.BlobUnavailableException("403"))
            .thenReturn(new CapacityBlobClient.Loaded(CapacityHistory.empty(), ETAG, false));
        CapacityScanScheduler scheduler = scheduler();

        scheduler.runScan();
        scheduler.runScan();

        verifyNoInteractions(softnetApi);
        verify(blobClient, never()).save(any(), any());
        assertTrue(logged(Level.ERROR, "unusable"));
    }

    /**
     * An unexpected failure after the claim never reaches the scheduler: the running hint is
     * cleared and the MDC removed. / Unerwarteter Fehler nach dem Claim.
     */
    @Test
    void clearsTheRunningHintAfterAnUnexpectedFailure() {
        givenBlob(CapacityHistory.empty());
        when(setupService.getAllBranches()).thenThrow(new IllegalStateException("setup"));

        assertDoesNotThrow(() -> scheduler().runScan());

        assertNull(MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY));
        assertTrue(logged(Level.ERROR, "failed unexpectedly"));
    }

    /** A second trigger while this replica is still scanning is skipped. / Kein Doppellauf. */
    @Test
    void skipsAnOverlappingRunInTheSameReplica() {
        givenBlob(CapacityHistory.empty());
        CapacityScanScheduler scheduler = scheduler();
        snapAnswers.put(AUDI, branchId -> {
            scheduler.runScan();
            return List.of(snap(branchId, "2026-10-23T07:00:00Z"));
        });

        scheduler.runScan();

        verify(blobClient, times(1)).load();
        assertTrue(logged(Level.INFO, "already running"));
    }

    /** AC-8: the jitter waits at most {@value CapacityScanScheduler#MAX_JITTER_SECONDS} s. */
    @Test
    void waitsAtMostTheMaximumJitter() {
        givenBlob(CapacityHistory.empty());

        new CapacityScanScheduler(probe(), blobClient, store, softnetApi, setupService,
            capacity(false), usage(), clock, waits::add, 300).runScan();

        assertTrue(waits.stream().allMatch(d -> d.compareTo(Duration.ofSeconds(300)) <= 0), waits.toString());
        assertEquals(2, saved.size());
    }

    // ---- TC-55: first scan running / erster Scan läuft (§5.2) ----

    /**
     * TC-55: no scan history; after the claim the store holds a view with
     * {@code runningSince = start} and no rows (S1 „Der erste Kapazitäts-Scan läuft seit 22:30
     * Uhr."); after the scan {@code runningSince = null}. / Laufhinweis nach dem Claim.
     */
    @Test
    @DisplayName("TC-55: first scan running → runningSince set during, null after")
    void exposesTheRunningFirstScan() {
        givenBlob(CapacityHistory.empty());
        AtomicReference<CapacityViewModel> during = new AtomicReference<>();
        snapAnswers.put(AUDI, branchId -> {
            during.set(store.current());
            return List.of(snap(branchId, "2026-10-23T07:00:00Z"));
        });

        scheduler().runScan();

        assertEquals(START, during.get().runningSince());
        assertTrue(during.get().rows().isEmpty(), "no finished scan yet");
        assertEquals(SCAN_DAY, during.get().collectingSince());
        assertNull(store.current().runningSince());
        assertEquals(3, store.current().rows().size());
    }

    /**
     * TC-55 second case: with yesterday's scan present, the running view keeps showing it (the
     * renderer then shows no first-run hint). / Vortags-Scan bleibt sichtbar.
     */
    @Test
    void keepsYesterdaysScanVisibleWhileRunning() {
        givenBlob(historyWith(SCAN_DAY.minusDays(1), complete(Map.of(AUDI, ok(0), BMW, ok(2),
            VW, none()))));
        AtomicReference<CapacityViewModel> during = new AtomicReference<>();
        snapAnswers.put(AUDI, branchId -> {
            during.set(store.current());
            return List.of(snap(branchId, "2026-10-23T07:00:00Z"));
        });

        scheduler().runScan();

        assertEquals(SCAN_DAY.minusDays(1), during.get().scanDay());
        assertEquals(START, during.get().runningSince());
        assertEquals(3, during.get().rows().size());
        assertEquals(SCAN_DAY, store.current().scanDay());
        assertNull(store.current().runningSince());
    }

    // ---- startup, rebuild, stale / Start, Neuaufbau, veraltet ----

    /**
     * TC-48 (capacity part)/E4: the blob answers 403 on startup → a view with
     * {@code WARN_HISTORY_UNAVAILABLE} and no rows, no SoftNet call; after a later successful scan
     * the hint disappears. / Hinweis verschwindet nach erfolgreichem Laden.
     */
    @Test
    @DisplayName("TC-48: history unavailable on startup → hint, gone after a later load")
    void showsTheHistoryHintUntilALoadSucceeds() {
        when(blobClient.load())
            .thenThrow(new UsageBlobClient.BlobUnavailableException("403"))
            .thenReturn(new CapacityBlobClient.Loaded(CapacityHistory.empty(), null, true));
        CapacityScanScheduler scheduler = scheduler();

        scheduler.onStartup();

        assertEquals(List.of(CapacityViewModel.WARN_HISTORY_UNAVAILABLE), store.current().warnings());
        assertTrue(store.current().rows().isEmpty());
        verifyNoInteractions(softnetApi);

        scheduler.runScan();

        assertFalse(store.current().warnings().contains(CapacityViewModel.WARN_HISTORY_UNAVAILABLE));
        assertEquals(3, store.current().rows().size());
    }

    /**
     * TC-48 second case/AC-32: without storage the startup view carries the hint, nothing is
     * loaded; a view the E2E seed loader already stored is not overwritten. / Seed bleibt.
     */
    @Test
    void startsWithTheHintWithoutStorageButKeepsASeededView() {
        when(blobClient.isConfigured()).thenReturn(false);
        scheduler().onStartup();
        assertEquals(List.of(CapacityViewModel.WARN_HISTORY_UNAVAILABLE), store.current().warnings());

        CapacityStore seeded = new CapacityStore();
        CapacityViewModel seed = CapacityViewModel.build(historyWith(SCAN_DAY, complete(Map.of(
            AUDI, ok(0)))), Map.of(AUDI, "Audi Hafenstadt"), false, SCAN_DAY, null);
        seeded.update(seed, null);
        new CapacityScanScheduler(probe(), blobClient, seeded, softnetApi, setupService,
            capacity(false), usage(), clock, waits::add, 0).onStartup();

        assertSame(seed, seeded.current());
        verify(blobClient, never()).load();
        verifyNoInteractions(softnetApi);
    }

    /** Step 5/E6: a usable blob is shown on startup; an unusable one gives the hint. */
    @Test
    void loadsTheHistoryOnStartup() {
        givenBlob(historyWith(SCAN_DAY, complete(Map.of(AUDI, ok(0), BMW, ok(1), VW, ok(6)))));
        scheduler().onStartup();
        assertEquals(SCAN_DAY, store.current().scanDay());
        assertTrue(store.current().warnings().isEmpty());
        assertEquals(3, store.history().scans().get(SCAN_DAY).branches().size());

        CapacityStore other = new CapacityStore();
        when(blobClient.load()).thenReturn(new CapacityBlobClient.Loaded(CapacityHistory.empty(), ETAG, false));
        new CapacityScanScheduler(probe(), blobClient, other, softnetApi, setupService,
            capacity(true), usage(), clock, waits::add, 0).onStartup();
        assertEquals(List.of(CapacityViewModel.WARN_HISTORY_UNAVAILABLE), other.current().warnings());
        verifyNoInteractions(softnetApi);
    }

    /**
     * TC-54 (scheduler part)/BR-25: scan Thu 19.11., target Fri 20.11. — loaded on 19.11. 22:40
     * „morgen frei"; the 00:05 rebuild on 20.11. relabels to „heute frei" without blob, KQL or
     * SoftNet call; the key stays {@code free-next-day}. / Neuaufbau ohne I/O.
     */
    @Test
    @DisplayName("TC-54: 00:05 rebuild relabels morgen → heute without I/O")
    void relabelsAtMidnightWithoutIo() {
        clock.set(Instant.parse("2026-11-19T21:40:00Z"));
        givenBlob(historyWith(LocalDate.of(2026, 11, 19), complete(Map.of(AUDI, ok(0)))));
        CapacityScanScheduler scheduler = scheduler();
        scheduler.onStartup();
        assertEquals("morgen frei", row("Audi Hafenstadt").levelLabel());

        clock.set(Instant.parse("2026-11-19T23:05:00Z"));
        scheduler.rebuildViewModel();

        assertEquals("heute frei", row("Audi Hafenstadt").levelLabel());
        assertEquals(CapacityLevel.FREE_NEXT_DAY, row("Audi Hafenstadt").level());
        verify(blobClient, times(1)).load();
        verifyNoInteractions(softnetApi, operationIdMapper);
    }

    /**
     * TC-50 (scheduler part): the most recent scan 3 days old → {@code WARN capacity scan stale}
     * exactly once per day, even with two rebuilds; the next day once more; 2 days old → never. /
     * Einmal je Tag.
     */
    @Test
    @DisplayName("TC-50: stale scan logged once per day")
    void logsAStaleScanOncePerDay() {
        clock.set(Instant.parse("2026-10-25T22:05:00Z"));
        givenBlob(historyWith(SCAN_DAY, complete(Map.of(AUDI, ok(0)))));
        CapacityScanScheduler scheduler = scheduler();
        scheduler.onStartup();
        scheduler.rebuildViewModel();
        scheduler.rebuildViewModel();
        assertEquals(1, count(Level.WARN, "capacity scan stale: last scan 2026-10-22"));
        assertTrue(store.current().warnings().get(0).startsWith("Letzte Auswertung: 22.10.2026"));

        clock.set(Instant.parse("2026-10-26T22:05:00Z"));
        scheduler.rebuildViewModel();
        assertEquals(2, count(Level.WARN, "capacity scan stale"));

        appender.list.clear();
        clock.set(Instant.parse("2026-10-24T08:00:00Z"));
        new CapacityScanScheduler(probe(), blobClient, new CapacityStore(), softnetApi,
            setupService, capacity(false), usage(), clock, waits::add, 0).onStartup();
        assertEquals(0, count(Level.WARN, "capacity scan stale"));
    }

    // ---- further failure paths / weitere Fehlerpfade (E4–E6) ----

    /**
     * E5: three result-write conflicts in a row → {@code WARN}, the result stays visible from
     * memory. / Drei Konflikte beim Ergebnis.
     */
    @Test
    void givesUpTheResultWriteAfterThreeConflicts() {
        givenBlob(CapacityHistory.empty());
        int[] calls = {0};
        doAnswer(invocation -> {
            if (++calls[0] > 1) {
                throw new UsageBlobClient.BlobPreconditionFailedException("412");
            }
            return ETAG;
        }).when(blobClient).save(any(), any());

        scheduler().runScan();

        assertEquals(4, calls[0], "claim + 3 result attempts");
        verify(blobClient, times(3)).load();
        assertTrue(logged(Level.WARN, "result not written after 3 attempts"));
        assertEquals(SCAN_DAY, store.current().scanDay());
    }

    /** E6/E4 during the result retry: the re-read fails → no further write. / Re-read scheitert. */
    @Test
    void stopsTheResultRetryWhenTheReReadFails() {
        when(blobClient.load())
            .thenReturn(new CapacityBlobClient.Loaded(CapacityHistory.empty(), ETAG, true))
            .thenReturn(new CapacityBlobClient.Loaded(CapacityHistory.empty(), ETAG, false))
            .thenReturn(new CapacityBlobClient.Loaded(CapacityHistory.empty(), ETAG, true))
            .thenThrow(new UsageBlobClient.BlobUnavailableException("network"));
        int[] calls = {0};
        doAnswer(invocation -> {
            if (++calls[0] % 2 == 0) {
                throw new UsageBlobClient.BlobPreconditionFailedException("412");
            }
            return ETAG;
        }).when(blobClient).save(any(), any());
        CapacityScanScheduler scheduler = scheduler();

        scheduler.runScan();
        clock.set(START.plus(Duration.ofDays(1)));
        scheduler.runScan();

        assertEquals(4, calls[0], "per scan: claim + one result attempt");
        assertTrue(logged(Level.ERROR, "became unusable during the retry"));
        assertTrue(logged(Level.WARN, "blob unavailable during the retry"));
    }

    /** E4: the claim itself cannot be written → no scan. / Claim nicht schreibbar. */
    @Test
    void skipsTheScanWhenTheClaimCannotBeWritten() {
        givenBlob(CapacityHistory.empty());
        doThrow(new UsageBlobClient.BlobUnavailableException("403"))
            .when(blobClient).save(any(), any());

        scheduler().runScan();

        verifyNoInteractions(softnetApi);
        assertTrue(logged(Level.WARN, "claim not writable"));
    }

    /**
     * An interrupt (shutdown) during the pause stops the loop: the remaining branches are
     * recorded as {@code timeout}, the result is still written, the interrupt flag survives. /
     * Unterbrechung beendet die Schleife.
     */
    @Test
    void stopsTheLoopWhenInterrupted() {
        givenBlob(CapacityHistory.empty());
        CapacityScanScheduler scheduler = new CapacityScanScheduler(probe(), blobClient, store,
            softnetApi, setupService, capacity(false), usage(), clock, duration -> {
                throw new InterruptedException("shutdown");
            }, 0);

        scheduler.runScan();

        assertTrue(Thread.interrupted(), "interrupt flag restored (and cleared here)");
        assertEquals(List.of(AUDI), probedBranches);
        CapacityScan result = saved.get(1).scans().get(SCAN_DAY);
        assertEquals(ProbeReason.TIMEOUT, result.branches().get(VW).reason());
        assertEquals(ScanState.PARTIAL, result.state());
    }

    /** {@code USAGE_RETENTION_MONTHS ≤ 0} disables the purge (same rule as REQ-037). */
    @Test
    void keepsEverythingWithoutRetention() {
        givenBlob(historyWith(LocalDate.of(2020, 1, 6), complete(Map.of(AUDI, ok(0)))));
        UsageStatsProperties noRetention = new UsageStatsProperties(true, "-", "-", null, null, 0,
            60, new UsageStatsProperties.Storage("account", "usage-stats", "usage-daily.json"));

        new CapacityScanScheduler(probe(), blobClient, store, softnetApi, setupService,
            capacity(false), noRetention, clock, waits::add, 0).runScan();

        assertTrue(saved.get(1).scans().containsKey(LocalDate.of(2020, 1, 6)));
    }

    /** BR-15: a trace id already present on the thread is restored after the run. */
    @Test
    void restoresAPreviousTraceId() {
        givenBlob(CapacityHistory.empty());
        MDC.put(RequestIdFilter.TRACE_ID_MDC_KEY, "outer");

        scheduler().runScan();

        assertEquals("outer", MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY));
    }

    /** Neither startup nor the 00:05 rebuild ever throws into Spring. / Nie eine Exception. */
    @Test
    void neverThrowsFromStartupOrRebuild() {
        givenBlob(CapacityHistory.empty());
        when(setupService.getAllBranches()).thenThrow(new IllegalStateException("setup"));
        CapacityScanScheduler scheduler = scheduler();

        assertDoesNotThrow(scheduler::onStartup);
        assertDoesNotThrow(scheduler::rebuildViewModel);

        assertTrue(logged(Level.ERROR, "capacity history load failed unexpectedly"));
        assertTrue(logged(Level.ERROR, "capacity view rebuild failed unexpectedly"));
    }

    /**
     * Branches without a default group, without a name and groups without id are handled: the
     * branch becomes {@code no-brand}, its id is its display name. / Robuste Setup-Daten.
     */
    @Test
    void handlesIncompleteSetupAndGroupData() {
        givenBlob(CapacityHistory.empty());
        String lonely = "68f0a1b2c3d4e5f6a7b8c9d7";
        when(setupService.getAllBranches()).thenReturn(List.of(
            new BranchSetup(lonely, null, null, List.of(new GroupEntry("tireChange", lonely, "x"))),
            new BranchSetup(AUDI, "Audi Hafenstadt", null, null),
            branch(VW, "VW Nordtor")));
        ObjectNode withoutId = json.createObjectNode();
        withoutId.putArray("brand").add("Seat");
        when(softnetApi.getGroups()).thenReturn(java.util.Arrays.asList(
            group(VW, "Volkswagen"), withoutId, null));

        scheduler().runScan();

        CapacityScan result = saved.get(1).scans().get(SCAN_DAY);
        assertEquals(ProbeReason.NO_BRAND, result.branches().get(lonely).reason());
        assertEquals(ProbeReason.NO_BRAND, result.branches().get(AUDI).reason());
        assertEquals(ProbeStatus.OK, result.branches().get(VW).status());
        assertTrue(store.current().rows().stream().anyMatch(r -> r.branchName().equals(lonely)));
    }

    /**
     * Spring wiring (project memory: a class with two constructors needs exactly one
     * {@code @Autowired} one, otherwise only the smoke test notices): the bean exists only with
     * {@code capacity.enabled=true}, Spring picks the production constructor and the cron
     * placeholders resolve ({@code -} disables the scan cron, AC-8). / Spring-Verdrahtung.
     */
    @Test
    void isWiredBySpringOnlyWhenEnabled() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingOn.class, CapacityScanScheduler.class)
            .withBean(CapacityProbe.class, this::probe)
            .withBean(CapacityBlobClient.class, () -> blobClient)
            .withBean(CapacityStore.class, CapacityStore::new)
            .withBean(SoftnetApiClient.class, () -> softnetApi)
            .withBean(SetupService.class, () -> setupService)
            .withBean(CapacityProperties.class, () -> capacity(false))
            .withBean(UsageStatsProperties.class, CapacityScanSchedulerTest::usage)
            .withPropertyValues("capacity.scan-cron=-", "usage.zone=Europe/Berlin");

        runner.withPropertyValues("capacity.enabled=true").run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(CapacityScanScheduler.class).size());
        });
        runner.withPropertyValues("capacity.enabled=false").run(context ->
            assertTrue(context.getBeansOfType(CapacityScanScheduler.class).isEmpty()));
        runner.run(context ->
            assertTrue(context.getBeansOfType(CapacityScanScheduler.class).isEmpty(),
                "delivered off (AC-31)"));
    }

    /** Scheduling on, so the {@code @Scheduled} placeholders are resolved in the wiring test. */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingOn {
    }

    /** §10.6 state rule / Zustandsregel. */
    @Test
    void derivesTheScanState() {
        assertEquals(ScanState.COMPLETE, CapacityScanScheduler.stateOf(List.of(ok(0), none())));
        assertEquals(ScanState.PARTIAL, CapacityScanScheduler.stateOf(List.of(ok(0),
            error(ProbeReason.TIMEOUT))));
        assertEquals(ScanState.FAILED, CapacityScanScheduler.stateOf(List.of(
            error(ProbeReason.TIMEOUT))));
        assertEquals(ScanState.FAILED, CapacityScanScheduler.stateOf(List.of()));
    }

    /**
     * The Spring constructor wires the production clock and sleeper; its constants match §16. /
     * Produktions-Konstruktor und Konstanten.
     */
    @Test
    void exposesTheGlossaryConstants() {
        CapacityScanScheduler production = new CapacityScanScheduler(probe(), blobClient, store,
            softnetApi, setupService, capacity(false), usage());
        when(blobClient.isConfigured()).thenReturn(false);
        production.runScan();

        assertEquals(300, CapacityScanScheduler.MAX_JITTER_SECONDS);
        assertEquals(Duration.ofSeconds(1), CapacityScanScheduler.PAUSE_BETWEEN_BRANCHES);
        assertEquals(Duration.ofSeconds(60), CapacityScanScheduler.RETRY_DELAY);
        assertEquals(Duration.ofMinutes(20), CapacityScanScheduler.MAX_SCAN_DURATION);
        assertEquals(Duration.ofMinutes(30), CapacityScanScheduler.CLAIM_STALE_AFTER);
        assertEquals(Duration.ofHours(24), CapacityScanScheduler.GROUPS_CACHE_TTL);
        assertEquals("job.capacityprobe", CapacityScanScheduler.TRACE_ID);
        verifyNoInteractions(softnetApi);
    }

    // ---- helpers / Hilfen ----

    private CapacityScanScheduler scheduler() {
        return new CapacityScanScheduler(probe(), blobClient, store, softnetApi, setupService,
            capacity(false), usage(), clock, duration -> {
                waits.add(duration);
                clock.advance(duration);
            }, 0);
    }

    private CapacityProbe probe() {
        return new CapacityProbe(softnetApi, operationIdMapper, "wiper-blades", BERLIN);
    }

    private void givenBlob(CapacityHistory history) {
        when(blobClient.load()).thenReturn(new CapacityBlobClient.Loaded(history, ETAG, true));
    }

    private CapacityRow row(String name) {
        return store.current().rows().stream().filter(r -> r.branchName().equals(name)).findFirst()
            .orElseThrow();
    }

    private boolean logged(Level level, String fragment) {
        return count(level, fragment) > 0;
    }

    private long count(Level level, String fragment) {
        return appender.list.stream()
            .filter(e -> e.getLevel() == level && e.getFormattedMessage().contains(fragment))
            .count();
    }

    private static CapacityHistory historyWith(LocalDate day, CapacityScan scan) {
        SortedMap<LocalDate, CapacityScan> scans = new TreeMap<>();
        scans.put(day, scan);
        return new CapacityHistory(1, "wiper-blades", scans);
    }

    private static CapacityScan running(Instant startedAt) {
        return new CapacityScan(ScanState.RUNNING, startedAt, null, TARGET, Map.of());
    }

    private static CapacityScan complete(Map<String, ProbeResult> branches) {
        return new CapacityScan(ScanState.COMPLETE, START.minus(Duration.ofDays(1)),
            START.minus(Duration.ofDays(1)).plusSeconds(200), null, branches);
    }

    private static ProbeResult ok(int workdays) {
        return new ProbeResult(ProbeStatus.OK, null, null, workdays, 1);
    }

    private static ProbeResult none() {
        return new ProbeResult(ProbeStatus.NONE, null, null, null, null);
    }

    private static ProbeResult error(ProbeReason reason) {
        return new ProbeResult(ProbeStatus.ERROR, reason, null, null, null);
    }

    private static BranchSetup branch(String id, String name) {
        return new BranchSetup(id, name, null, List.of(
            new GroupEntry("default", id, name),
            new GroupEntry("tireChange", "6908a1b2c3d4e5f6a7b8c9ff", name + " Räder")));
    }

    /** The default group of a branch reuses the branch id as group id for simplicity. */
    private JsonNode group(String id, String brand) {
        ObjectNode group = json.createObjectNode().put("id", id);
        group.putArray("brand").add(brand);
        return group;
    }

    private JsonNode snap(String branchId, String checkInTime) {
        return json.createObjectNode().put("branch", branchId).put("checkInTime", checkInTime)
            .put("checkInResource", "Herr Beispielberater");
    }

    private static CapacityProperties capacity(boolean showSlotCount) {
        return new CapacityProperties(true, "-", "wiper-blades", showSlotCount, null);
    }

    private static UsageStatsProperties usage() {
        return new UsageStatsProperties(true, "-", "-", "Europe/Berlin", null, 24, 60,
            new UsageStatsProperties.Storage("gsterminbuchungstats", "usage-stats", "usage-daily.json"));
    }

    /** A settable clock in Europe/Berlin / ein stellbarer Clock. */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        void set(Instant instant) {
            now = instant;
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
