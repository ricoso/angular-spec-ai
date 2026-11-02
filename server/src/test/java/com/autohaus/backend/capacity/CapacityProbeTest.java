package com.autohaus.backend.capacity;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.autohaus.backend.config.CapacityProperties;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.snaps.OperationIdMapper;
import com.autohaus.backend.snaps.SnapCache;
import com.autohaus.backend.snaps.SnapsService;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * One branch probe of the capacity scan (REQ-038 TC-31, TC-32, TC-33, BR-13, BR-14) / eine
 * Standort-Messung des Kapazitäts-Scans.
 *
 * <p>SoftNet and the {@code OperationIdMapper} are mocks; {@code SnapsService} and
 * {@code SnapCache} are mocks too, only to prove that the scan never touches them (BR-13). Zone
 * Europe/Berlin — the target day Mo 26.10.2026 lies right after the switch to winter time
 * (UTC+1). / Fester Zieltag direkt nach der Zeitumstellung.
 */
@ExtendWith(MockitoExtension.class)
class CapacityProbeTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final String BRANCH = "68f0a1b2c3d4e5f6a7b8c9d0";
    private static final String FOREIGN = "68f0a1b2c3d4e5f6a7b8c9d4";
    private static final String GROUP = "6908a1b2c3d4e5f6a7b8c9d1";
    private static final String OPERATION = "6908a1b2c3d4e5f6a7b8c9e7";
    /** Monday / Montag. */
    private static final LocalDate TARGET = LocalDate.of(2026, 10, 26);
    private static final String ADVISOR = "Herr Beispielberater";

    private final ObjectMapper json = new ObjectMapper();

    @Mock private SoftnetApiClient softnetApi;
    @Mock private OperationIdMapper operationIdMapper;
    @Mock private SnapsService snapsService;
    @Mock private SnapCache snapCache;

    // ---- TC-31: wishlist / Wishlist (§12.9, BR-13) ----

    /**
     * TC-31: the captured SoftNet request has exactly the six keys of §12.9 — no
     * {@code checkInTimePreference}, no {@code checkInDate}, no {@code client}/customer or vehicle
     * data — and the scan never goes through {@code SnapsService}/{@code SnapCache} or
     * {@code getGroup(...)}. / Exakt die Wishlist aus §12.9, nie über den Kunden-Pfad.
     */
    @Test
    @DisplayName("TC-31: wishlist has exactly the §12.9 keys; SnapsService/SnapCache untouched")
    void sendsExactlyTheSpecifiedWishlist() throws Exception {
        givenReferenceOperation();
        when(softnetApi.getSnaps(any(), eq(GROUP))).thenReturn(List.of());

        probe().probe(BRANCH, GROUP, group("Volkswagen", "Audi"), TARGET);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(softnetApi).getSnaps(captor.capture(), eq(GROUP));
        @SuppressWarnings("unchecked")
        Map<String, Object> wishlist = (Map<String, Object>) captor.getValue();
        assertEquals(Set.of("groupid", "brand", "car", "operations", "preferredBranch",
            "checkInDatePreference"), wishlist.keySet());
        JsonNode expected = json.readTree("""
            {
              "groupid": "6908a1b2c3d4e5f6a7b8c9d1",
              "brand": "Volkswagen",
              "car": { "brand": "Volkswagen" },
              "operations": ["6908a1b2c3d4e5f6a7b8c9e7"],
              "preferredBranch": "68f0a1b2c3d4e5f6a7b8c9d0",
              "checkInDatePreference": "asSoonAsPossible"
            }""");
        assertEquals(expected, json.valueToTree(wishlist), "brand = brand[0] of the group");
        String serialized = json.writeValueAsString(wishlist);
        assertFalse(serialized.contains("checkInTimePreference"));
        assertFalse(serialized.contains("checkInDate\""));
        assertFalse(serialized.contains("client"));
        verifyNoInteractions(snapsService, snapCache);
        verify(softnetApi, never()).getGroup(anyString());
    }

    // ---- TC-32: evaluation / Auswertung (§12.9, N2) ----

    /**
     * TC-32: snaps of other branches are ignored; the first own slot on Thu 29.10. gives
     * {@code workdaysToFirstFree = 3} (Mo, Di, Mi before it) and no slot on the target day. /
     * Fremde Snaps zählen nicht.
     */
    @Test
    @DisplayName("TC-32: foreign snaps ignored, first own slot Thu → 3 workdays")
    void countsWorkdaysUpToTheFirstOwnSlot() {
        givenReferenceOperation();
        when(softnetApi.getSnaps(any(), eq(GROUP))).thenReturn(List.of(
            snap(FOREIGN, "2026-10-26T07:00:00Z"),
            snap(objectBranch(FOREIGN), "2026-10-27T07:00:00Z"),
            snap(BRANCH, "2026-10-30T09:00:00Z"),
            snap(BRANCH, "2026-10-29T07:00:00Z")));

        ProbeResult result = probe().probe(BRANCH, GROUP, group("Volkswagen"), TARGET);

        assertEquals(ProbeStatus.OK, result.status());
        assertNull(result.reason());
        assertEquals(LocalDate.of(2026, 10, 29), result.firstFreeDate());
        assertEquals(3, result.workdaysToFirstFree());
        assertEquals(0, result.freeSlotsTargetDay());
    }

    /**
     * TC-32 second case: two distinct slots on the target day (14:10 and 16:00 Berlin, the 14:10
     * slot delivered twice) plus one slot whose UTC date is the target day but whose Berlin date is
     * the day after → {@code workdaysToFirstFree = 0}, {@code freeSlotsTargetDay = 2} — one total,
     * distinct {@code checkInTime}, only the target day (N2). Both forms of {@code snap.branch}
     * (string and object) are recognised; {@code checkInResource} appears nowhere. / Beide Formen
     * von {@code snap.branch}; der Berater taucht nirgends auf.
     */
    @Test
    @DisplayName("TC-32: two distinct slots on the target day, branch as string and object")
    void countsDistinctSlotsOnTheTargetDayOnly() {
        givenReferenceOperation();
        when(softnetApi.getSnaps(any(), eq(GROUP))).thenReturn(List.of(
            snap(BRANCH, "2026-10-26T13:10:00Z"),
            snap(objectBranch(BRANCH), "2026-10-26T13:10:00Z"),
            snap(objectBranch(BRANCH), "2026-10-26T15:00:00Z"),
            snap(BRANCH, "2026-10-26T23:30:00Z"),
            snap(BRANCH, "2026-10-27T08:00:00Z")));

        ProbeResult result = probe().probe(BRANCH, GROUP, group("Volkswagen"), TARGET);

        assertEquals(ProbeStatus.OK, result.status());
        assertEquals(TARGET, result.firstFreeDate());
        assertEquals(0, result.workdaysToFirstFree());
        assertEquals(2, result.freeSlotsTargetDay());
        assertFalse(result.toString().contains(ADVISOR), "checkInResource must be dropped");
    }

    /** TC-32: an empty list is {@code none} — SoftNet has nothing online. / Leere Liste → none. */
    @Test
    @DisplayName("TC-32: empty snap list → NONE")
    void emptyListIsNone() {
        givenReferenceOperation();
        when(softnetApi.getSnaps(any(), eq(GROUP))).thenReturn(List.of());

        ProbeResult result = probe().probe(BRANCH, GROUP, group("Volkswagen"), TARGET);

        assertEquals(new ProbeResult(ProbeStatus.NONE, null, null, null, null), result);
    }

    /**
     * TC-32: only snaps with a valid <b>foreign</b> branch id → {@code none}: SoftNet answered
     * readably, just not for this branch. / Nur gültige fremde IDs → none.
     */
    @Test
    @DisplayName("TC-32: only valid foreign branch ids → NONE")
    void onlyForeignSnapsAreNone() {
        givenReferenceOperation();
        when(softnetApi.getSnaps(any(), eq(GROUP))).thenReturn(List.of(
            snap(FOREIGN, "2026-10-26T07:00:00Z"), snap(objectBranch(FOREIGN), "2026-10-27T07:00:00Z")));

        ProbeResult result = probe().probe(BRANCH, GROUP, group("Volkswagen"), TARGET);

        assertEquals(ProbeStatus.NONE, result.status());
        assertNull(result.workdaysToFirstFree(), "none is never 0 workdays");
    }

    /**
     * A late slot on the scan evening (before the target day) says nothing about the target day and
     * is ignored; own snaps only before the target day are {@code none}. / Slots vor dem Zieltag
     * zählen nicht.
     */
    @Test
    void ignoresSlotsBeforeTheTargetDay() {
        givenReferenceOperation();
        when(softnetApi.getSnaps(any(), eq(GROUP)))
            .thenReturn(List.of(snap(BRANCH, "2026-10-24T08:00:00Z"), snap(BRANCH, "2026-10-28T08:00:00Z")))
            .thenReturn(List.of(snap(BRANCH, "2026-10-24T08:00:00Z")));

        ProbeResult later = probe().probe(BRANCH, GROUP, group("Volkswagen"), TARGET);
        ProbeResult onlyEarlier = probe().probe(BRANCH, GROUP, group("Volkswagen"), TARGET);

        assertEquals(2, later.workdaysToFirstFree(), "Mo + Di before Mi 28.10.");
        assertEquals(ProbeStatus.NONE, onlyEarlier.status());
    }

    /**
     * TC-32/§5.7: the target day is the next working day Mo–Sa after the scan day — a Saturday or
     * Sunday scan measures Monday; holidays are working days. / Samstag → Montag.
     */
    @Test
    @DisplayName("TC-32: scan on Saturday → target day Monday")
    void targetDayIsTheNextWorkingDay() {
        assertEquals(TARGET, CapacityCalendar.nextWorkday(LocalDate.of(2026, 10, 24)), "Sa → Mo");
        assertEquals(TARGET, CapacityCalendar.nextWorkday(LocalDate.of(2026, 10, 25)), "So → Mo");
        assertEquals(LocalDate.of(2026, 10, 24), CapacityCalendar.nextWorkday(LocalDate.of(2026, 10, 23)),
            "Fr → Sa (Saturday is a working day)");
        assertEquals(1, CapacityCalendar.workdaysBetween(LocalDate.of(2026, 10, 24), TARGET),
            "Sa counts, So does not");
        assertEquals(0, CapacityCalendar.workdaysBetween(TARGET, TARGET));
        assertEquals(0, CapacityCalendar.workdaysBetween(TARGET, TARGET.minusDays(3)));
        assertEquals("Mo", CapacityCalendar.weekdayShort(TARGET));
        assertEquals("So", CapacityCalendar.weekdayShort(TARGET.minusDays(1)));
        assertEquals(List.of(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 24)),
            CapacityCalendar.lastWorkdays(LocalDate.of(2026, 10, 25), 2), "a Sunday end starts at Sa");
    }

    // ---- TC-33: failure modes / Fehlerbilder (E1, BR-14) ----

    /** TC-33: HTTP 403 (and 401) → {@code softnet-forbidden}. / 403 → kein Zugriff. */
    @Test
    @DisplayName("TC-33: 403/401 → softnet-forbidden")
    void forbiddenIsMapped() {
        givenReferenceOperation();
        when(softnetApi.getSnaps(any(), eq(GROUP)))
            .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN))
            .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        assertError(ProbeReason.SOFTNET_FORBIDDEN, probe().probe(BRANCH, GROUP, group("VW"), TARGET));
        assertError(ProbeReason.SOFTNET_FORBIDDEN, probe().probe(BRANCH, GROUP, group("VW"), TARGET));
    }

    /** TC-33: HTTP 500 → {@code softnet-unavailable}. / 500 → nicht erreichbar. */
    @Test
    @DisplayName("TC-33: 500 → softnet-unavailable")
    void serverErrorIsUnavailable() {
        givenReferenceOperation();
        when(softnetApi.getSnaps(any(), eq(GROUP)))
            .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
            .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        assertError(ProbeReason.SOFTNET_UNAVAILABLE, probe().probe(BRANCH, GROUP, group("VW"), TARGET));
        assertError(ProbeReason.SOFTNET_UNAVAILABLE, probe().probe(BRANCH, GROUP, group("VW"), TARGET));
    }

    /**
     * TC-33: a read timeout of {@code SoftnetApiClient} surfaces as {@link ResourceAccessException}
     * with a {@link SocketTimeoutException} cause → {@code timeout}. / Lese-Timeout → timeout.
     */
    @Test
    @DisplayName("TC-33: ResourceAccessException(SocketTimeoutException) → timeout")
    void readTimeoutIsTimeout() {
        givenReferenceOperation();
        when(softnetApi.getSnaps(any(), eq(GROUP))).thenThrow(new ResourceAccessException(
            "I/O error on POST request", new SocketTimeoutException("Read timed out")));

        assertError(ProbeReason.TIMEOUT, probe().probe(BRANCH, GROUP, group("VW"), TARGET));
    }

    /**
     * TC-33/§5.6: no operation for the reference service → {@code no-reference-service}, and
     * {@code getSnaps} is not called at all (no silent substitute). / Ohne Referenz-Leistung kein
     * Snaps-Call.
     */
    @Test
    @DisplayName("TC-33: no reference operation → no-reference-service without getSnaps")
    void missingReferenceServiceSkipsTheSnapsCall() {
        when(operationIdMapper.mapToSoftnetOperations(GROUP, List.of("wiper-blades")))
            .thenReturn(List.of());

        assertError(ProbeReason.NO_REFERENCE_SERVICE,
            probe().probe(BRANCH, GROUP, group("Volkswagen"), TARGET));
        verify(softnetApi, never()).getSnaps(any(), any());
    }

    /** E1: the operations lookup failing (403 of the group) is mapped like a snaps failure. */
    @Test
    void failingOperationsLookupIsMapped() {
        when(operationIdMapper.mapToSoftnetOperations(GROUP, List.of("wiper-blades")))
            .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));

        assertError(ProbeReason.SOFTNET_FORBIDDEN, probe().probe(BRANCH, GROUP, group("VW"), TARGET));
        verify(softnetApi, never()).getSnaps(any(), any());
    }

    /**
     * TC-33: empty {@code brand}, a blank first brand, a group missing in the {@code getGroups()}
     * map and a branch without default group → {@code no-brand}, before any SoftNet call. /
     * Fehlende Marke → no-brand ohne SoftNet-Call.
     */
    @Test
    @DisplayName("TC-33: empty brand / group missing in the map → no-brand")
    void missingBrandIsNoBrand() {
        CapacityProbe probe = probe();
        ObjectNode withoutBrandField = json.createObjectNode().put("id", GROUP);
        ObjectNode textualBrand = json.createObjectNode().put("id", GROUP).put("brand", "VW");

        assertError(ProbeReason.NO_BRAND, probe.probe(BRANCH, GROUP, group(), TARGET));
        assertError(ProbeReason.NO_BRAND, probe.probe(BRANCH, GROUP, group(" "), TARGET));
        assertError(ProbeReason.NO_BRAND, probe.probe(BRANCH, GROUP, null, TARGET));
        assertError(ProbeReason.NO_BRAND, probe.probe(BRANCH, GROUP, withoutBrandField, TARGET));
        assertError(ProbeReason.NO_BRAND, probe.probe(BRANCH, GROUP, textualBrand, TARGET));
        assertError(ProbeReason.NO_BRAND, probe.probe(BRANCH, null, group("VW"), TARGET));
        verifyNoInteractions(softnetApi, operationIdMapper);
    }

    /**
     * TC-33/BR-14: SoftNet delivers snaps, but none carries a readable branch id ({@code branch}
     * missing, {@code branch = 42}, {@code branch.id} not 24-hex, upper-case hex) →
     * {@code unexpected-response}, never {@code none} and never 0 workdays. / Unlesbare IDs →
     * unexpected-response.
     */
    @Test
    @DisplayName("TC-33: snaps without readable branch id → unexpected-response, never NONE")
    void unreadableBranchIdsAreUnexpected() {
        givenReferenceOperation();
        ObjectNode missing = json.createObjectNode().put("checkInTime", "2026-10-26T07:00:00Z");
        ObjectNode numeric = json.createObjectNode().put("branch", 42)
            .put("checkInTime", "2026-10-26T07:00:00Z");
        when(softnetApi.getSnaps(any(), eq(GROUP))).thenReturn(List.of(missing, numeric,
            snap(objectBranch("not-a-branch-id"), "2026-10-26T07:00:00Z"),
            snap(BRANCH.toUpperCase(), "2026-10-26T07:00:00Z")));

        ProbeResult result = probe().probe(BRANCH, GROUP, group("VW"), TARGET);

        assertError(ProbeReason.UNEXPECTED_RESPONSE, result);
        verify(softnetApi, never()).getGroup(anyString());
    }

    /** BR-14: own snaps without any usable {@code checkInTime} are not "nothing found". */
    @Test
    void ownSnapsWithoutTimeAreUnexpected() {
        givenReferenceOperation();
        ObjectNode noTime = json.createObjectNode().put("branch", BRANCH);
        when(softnetApi.getSnaps(any(), eq(GROUP)))
            .thenReturn(List.of(noTime, snap(BRANCH, "yesterday evening")));

        assertError(ProbeReason.UNEXPECTED_RESPONSE, probe().probe(BRANCH, GROUP, group("VW"), TARGET));
    }

    /**
     * BR-13/NFR-Datenschutz-4: the DEBUG line of a probe names branch and outcome only — never the
     * advisor ({@code checkInResource}) or any snap content. / Der Berater wird nie geloggt.
     */
    @Test
    void logsNeitherAdvisorNorSnapContent() {
        givenReferenceOperation();
        when(softnetApi.getSnaps(any(), eq(GROUP)))
            .thenReturn(List.of(snap(BRANCH, "2026-10-26T13:10:00Z")))
            .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));
        Logger logger = (Logger) LoggerFactory.getLogger(CapacityProbe.class);
        Level previous = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            probe().probe(BRANCH, GROUP, group("VW"), TARGET);
            probe().probe(BRANCH, GROUP, group("VW"), TARGET);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
        }

        List<String> lines = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertEquals(List.of("capacity probe " + BRANCH + ": ok",
            "capacity probe " + BRANCH + ": error/softnet-forbidden"), lines);
        assertTrue(lines.stream().noneMatch(line -> line.contains(ADVISOR)));
    }

    /**
     * Without a target day (defensive) the first free day is its own reference; a first brand that
     * is no plain value is no brand. / Defensive Randfälle.
     */
    @Test
    void handlesDefensiveEdgeCases() {
        givenReferenceOperation();
        when(softnetApi.getSnaps(any(), eq(GROUP)))
            .thenReturn(List.of(snap(BRANCH, "2026-10-28T08:00:00Z")));
        ObjectNode objectBrand = json.createObjectNode().put("id", GROUP);
        objectBrand.putArray("brand").addObject().put("name", "VW");

        ProbeResult withoutTarget = probe().probe(BRANCH, GROUP, group("VW"), null);

        assertEquals(0, withoutTarget.workdaysToFirstFree());
        assertEquals(LocalDate.of(2026, 10, 28), withoutTarget.firstFreeDate());
        assertError(ProbeReason.NO_BRAND, probe().probe(BRANCH, GROUP, objectBrand, TARGET));
        assertEquals(ProbeStatus.NONE, probe().evaluate(null, BRANCH, TARGET).status());
    }

    /** §12.9: both forms of {@code snap.branch}, valid only as {@code ^[a-f0-9]{24}$}. */
    @Test
    void extractsTheBranchIdRobustly() {
        assertEquals(BRANCH, CapacityProbe.branchIdOf(snap(BRANCH, null)));
        assertEquals(BRANCH, CapacityProbe.branchIdOf(snap(objectBranch(BRANCH), null)));
        assertNull(CapacityProbe.branchIdOf(snap("abc", null)));
        assertNull(CapacityProbe.branchIdOf(json.createObjectNode()));
        assertNull(CapacityProbe.branchIdOf(json.createObjectNode().set("branch",
            json.createObjectNode().put("id", 7))));
        assertNull(CapacityProbe.branchIdOf(null));
    }

    /** E1: timeout and 401/403 are found anywhere in the cause chain. / Ursachenkette. */
    @Test
    void mapsReasonsThroughTheCauseChain() {
        assertEquals(ProbeReason.SOFTNET_FORBIDDEN, CapacityProbe.reasonOf(
            new RuntimeException(new HttpClientErrorException(HttpStatus.FORBIDDEN))));
        assertEquals(ProbeReason.TIMEOUT, CapacityProbe.reasonOf(
            new RuntimeException(new SocketTimeoutException("Connect timed out"))));
        assertEquals(ProbeReason.SOFTNET_UNAVAILABLE, CapacityProbe.reasonOf(
            new ResourceAccessException("Connection refused")));
        assertEquals(ProbeReason.SOFTNET_UNAVAILABLE, CapacityProbe.reasonOf(
            new IllegalStateException("token")));
    }

    /**
     * Production constructor: reference service and zone come from the properties; blank values
     * fall back to {@code wiper-blades} and Europe/Berlin. / Produktions-Konstruktor.
     */
    @Test
    void readsReferenceServiceAndZoneFromTheProperties() {
        CapacityProbe configured = new CapacityProbe(softnetApi, operationIdMapper,
            new CapacityProperties(true, "-", "ac-cleaning", false, null), usage("Europe/Berlin"));
        CapacityProbe defaults = new CapacityProbe(softnetApi, operationIdMapper,
            new CapacityProperties(true, "-", " ", false, null), usage(null));
        CapacityProbe nulls = new CapacityProbe(softnetApi, operationIdMapper, (String) null,
            (ZoneId) null);

        assertEquals("ac-cleaning", configured.referenceService());
        assertEquals("wiper-blades", defaults.referenceService());
        assertEquals("wiper-blades", nulls.referenceService());
        assertEquals("job.capacityprobe", CapacityProbe.TRACE_ID);
    }

    // ---- helpers / Hilfen ----

    private CapacityProbe probe() {
        return new CapacityProbe(softnetApi, operationIdMapper, "wiper-blades", BERLIN);
    }

    private void givenReferenceOperation() {
        when(operationIdMapper.mapToSoftnetOperations(GROUP, List.of("wiper-blades")))
            .thenReturn(List.of(OPERATION));
    }

    private static void assertError(ProbeReason reason, ProbeResult result) {
        assertEquals(ProbeStatus.ERROR, result.status());
        assertEquals(reason, result.reason());
        assertNull(result.workdaysToFirstFree(), "an error is never 0 workdays (BR-14)");
        assertNull(result.firstFreeDate());
        assertNull(result.freeSlotsTargetDay());
    }

    private JsonNode group(String... brands) {
        ObjectNode group = json.createObjectNode().put("id", GROUP);
        ArrayNode brandArray = group.putArray("brand");
        for (String brand : brands) {
            brandArray.add(brand);
        }
        return group;
    }

    private JsonNode objectBranch(String id) {
        return json.createObjectNode().put("id", id).put("name", "Filiale");
    }

    /** A snap as SoftNet returns it, including the advisor that must be dropped. */
    private JsonNode snap(Object branch, String checkInTime) {
        ObjectNode snap = json.createObjectNode();
        if (branch instanceof String text) {
            snap.put("branch", text);
        } else {
            snap.set("branch", (JsonNode) branch);
        }
        if (checkInTime != null) {
            snap.put("checkInTime", checkInTime);
        }
        snap.put("internal", "snap-internal-id");
        snap.put("checkInResource", ADVISOR);
        return snap;
    }

    private static UsageStatsProperties usage(String zone) {
        return new UsageStatsProperties(true, "-", "-", zone, null, 24, 60,
            new UsageStatsProperties.Storage("", "usage-stats", "usage-daily.json"));
    }
}
