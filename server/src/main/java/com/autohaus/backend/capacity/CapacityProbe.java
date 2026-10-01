package com.autohaus.backend.capacity;

import com.autohaus.backend.config.CapacityProperties;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.snaps.OperationIdMapper;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.autohaus.backend.usage.UsageZone;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Measures the next free online slot of <b>one</b> branch for the reference service (REQ-038 K1,
 * §12.9, BR-13) / misst den nächsten freien Online-Termin eines Standorts für die Referenz-Leistung.
 *
 * <p>Three steps, in this order — the cheap checks first, so a misconfigured branch never costs a
 * SoftNet call:
 * <ol>
 *   <li>Brand = {@code brand[0]} of the group taken from the scan's {@code getGroups()} map (read
 *       like {@code BranchesService.extractBrands}); group missing or brand empty →
 *       {@code error/no-brand}.</li>
 *   <li>Reference operation via {@link OperationIdMapper#mapToSoftnetOperations(String, List)}
 *       ({@code CAPACITY_REFERENCE_SERVICE}, default {@code wiper-blades}); empty →
 *       {@code error/no-reference-service} <b>without</b> a snaps call (§5.6) — there is no silent
 *       substitute service.</li>
 *   <li>{@link SoftnetApiClient#getSnaps(Object, String)} with exactly the wishlist of §12.9, then a
 *       post-filter on the branch id of every snap ({@code preferredBranch} is only a "should
 *       be").</li>
 * </ol>
 *
 * <p><b>Isolation (BR-13):</b> only {@code SoftnetApiClient} and {@code OperationIdMapper} — never
 * {@code SnapsService} or {@code SnapCache} (which would fill the customer cache with scan snaps and
 * produce DEBUG lines that look like customer searches). The wishlist carries no customer or
 * vehicle data, no {@code checkInTimePreference} and no {@code checkInDate}. {@code checkInResource}
 * (the service advisor) is never read, stored or logged (NFR-Datenschutz-4). / Nie
 * {@code SnapsService}/{@code SnapCache}; {@code checkInResource} wird nie gelesen.
 *
 * <p><b>Failure is not zero (BR-14, E1):</b> every exception becomes an {@code error} with a reason
 * ({@link #reasonOf(Throwable)}); snaps without any readable branch id are
 * {@code unexpected-response}, never {@code none}. The method itself never throws. / Fehler werden
 * nie als 0 oder „keine Termine" ausgegeben.
 */
@Component
public class CapacityProbe {

    private static final Logger log = LoggerFactory.getLogger(CapacityProbe.class);

    /**
     * MDC {@code traceId} of the whole scan (BR-15) / MDC-Wert des gesamten Scans. The dot lies
     * outside the {@code X-Request-Id} whitelist, so no client can hide its own errors from the
     * Bugs tab with it. / Außerhalb der Header-Whitelist.
     */
    public static final String TRACE_ID = "job.capacityprobe";

    /** Default reference service (§3.3) / Standard-Referenz-Leistung. */
    public static final String DEFAULT_REFERENCE_SERVICE = "wiper-blades";

    /** A readable SoftNet branch id (§12.9) / eine gültige Branch-ID. */
    private static final Pattern BRANCH_ID = Pattern.compile("^[a-f0-9]{24}$");

    private final SoftnetApiClient softnetApi;
    private final OperationIdMapper operationIdMapper;
    private final String referenceService;
    private final ZoneId zone;

    @Autowired
    public CapacityProbe(SoftnetApiClient softnetApi, OperationIdMapper operationIdMapper,
                         CapacityProperties capacityProperties,
                         UsageStatsProperties usageProperties) {
        this(softnetApi, operationIdMapper,
            capacityProperties == null ? null : capacityProperties.referenceService(),
            UsageZone.resolve(usageProperties));
    }

    /** Visible for testing / für Tests: reference service and zone directly. */
    CapacityProbe(SoftnetApiClient softnetApi, OperationIdMapper operationIdMapper,
                  String referenceService, ZoneId zone) {
        this.softnetApi = softnetApi;
        this.operationIdMapper = operationIdMapper;
        this.referenceService = referenceService == null || referenceService.isBlank()
            ? DEFAULT_REFERENCE_SERVICE : referenceService.trim();
        this.zone = zone == null ? UsageZone.DEFAULT_ZONE : zone;
    }

    /** The configured reference service key / der konfigurierte Referenz-Schlüssel. */
    public String referenceService() {
        return referenceService;
    }

    /**
     * Probes one branch / misst einen Standort. Never throws — every failure is an {@code error}
     * result with a reason (BR-14).
     *
     * @param branchId the branch id from {@code setup.json} (24 hex) / Standort
     * @param groupId its {@code default} group id, {@code null} when the branch has none
     * @param groupFromMap the group object of the scan's {@code getGroups()} map, {@code null} when
     *     the group is missing there / Group-Objekt aus der Group-Map
     * @param targetDay the target day — next working day after the scan day / Zieltag
     * @return the probe result without any advisor or customer data / Ergebnis
     */
    public ProbeResult probe(String branchId, String groupId, JsonNode groupFromMap,
                             LocalDate targetDay) {
        String brand = brandOf(groupFromMap);
        if (brand == null || groupId == null || groupId.isBlank()) {
            return logged(branchId, error(ProbeReason.NO_BRAND));
        }
        try {
            List<String> operations =
                operationIdMapper.mapToSoftnetOperations(groupId, List.of(referenceService));
            if (operations == null || operations.isEmpty()) {
                // §5.6: no silent substitute — and no snaps call at all.
                return logged(branchId, error(ProbeReason.NO_REFERENCE_SERVICE));
            }
            List<JsonNode> snaps = softnetApi.getSnaps(
                wishlist(groupId, brand, operations, branchId), groupId);
            return logged(branchId, evaluate(snaps, branchId, targetDay));
        } catch (RuntimeException e) {
            return logged(branchId, error(reasonOf(e)));
        }
    }

    /**
     * The exact wishlist of §12.9 / genau die Wishlist aus §12.9 — six keys, nothing else: no
     * {@code checkInTimePreference}, no {@code checkInDate}, no customer or vehicle data (BR-13).
     */
    static Map<String, Object> wishlist(String groupId, String brand, List<String> operations,
                                        String branchId) {
        Map<String, Object> wishlist = new LinkedHashMap<>();
        wishlist.put("groupid", groupId);
        wishlist.put("brand", brand);
        wishlist.put("car", Map.of("brand", brand));
        wishlist.put("operations", List.copyOf(operations));
        wishlist.put("preferredBranch", branchId);
        wishlist.put("checkInDatePreference", "asSoonAsPossible");
        return wishlist;
    }

    /**
     * Folds the SoftNet answer into a result (§12.9) / wertet die SoftNet-Antwort aus.
     *
     * <ul>
     *   <li>empty list → {@code none};</li>
     *   <li>no snap of this branch, but at least one with a valid <b>foreign</b> branch id →
     *       {@code none};</li>
     *   <li>snaps delivered, but none with a readable branch id → {@code error/unexpected-response}
     *       (BR-14 — never "keine Online-Termine gefunden");</li>
     *   <li>own snaps: {@code checkInTime} (UTC) → Europe/Berlin; only slots on or after the target
     *       day count — a late slot on the scan evening says nothing about the target day.
     *       {@code firstFreeDate}, {@code workdaysToFirstFree} (Mo–Sa days d with targetDay ≤ d &lt;
     *       firstFreeDate) and {@code freeSlotsTargetDay} (distinct {@code checkInTime} on the
     *       target day — one total, no daypart split, N2). Own snaps without any usable time →
     *       {@code error/unexpected-response}; own snaps only before the target day →
     *       {@code none}.</li>
     * </ul>
     * {@code checkInResource} is never read. / {@code checkInResource} wird nie gelesen.
     */
    ProbeResult evaluate(List<JsonNode> snaps, String branchId, LocalDate targetDay) {
        if (snaps == null || snaps.isEmpty()) {
            return new ProbeResult(ProbeStatus.NONE, null, null, null, null);
        }
        boolean anyReadable = false;
        boolean anyOwn = false;
        boolean anyOwnTimed = false;
        LocalDate firstFree = null;
        Set<Instant> slotsOnTarget = new HashSet<>();
        for (JsonNode snap : snaps) {
            String snapBranch = branchIdOf(snap);
            if (snapBranch == null) {
                continue;
            }
            anyReadable = true;
            if (!snapBranch.equals(branchId)) {
                continue;
            }
            anyOwn = true;
            Instant checkIn = checkInTimeOf(snap);
            if (checkIn == null) {
                continue;
            }
            anyOwnTimed = true;
            LocalDate day = LocalDate.ofInstant(checkIn, zone);
            if (targetDay != null && day.isBefore(targetDay)) {
                continue;
            }
            if (firstFree == null || day.isBefore(firstFree)) {
                firstFree = day;
            }
            if (day.equals(targetDay)) {
                slotsOnTarget.add(checkIn);
            }
        }
        if (!anyReadable) {
            return error(ProbeReason.UNEXPECTED_RESPONSE);
        }
        if (firstFree == null) {
            // Own snaps without any usable time are not "nothing found" (BR-14).
            return anyOwn && !anyOwnTimed
                ? error(ProbeReason.UNEXPECTED_RESPONSE)
                : new ProbeResult(ProbeStatus.NONE, null, null, null, null);
        }
        LocalDate from = targetDay == null ? firstFree : targetDay;
        int workdays = CapacityCalendar.workdaysBetween(from, firstFree);
        return new ProbeResult(ProbeStatus.OK, null, firstFree, workdays, slotsOnTarget.size());
    }

    /**
     * Robust branch-id extraction (§12.9) / robuste Extraktion der Branch-ID. The spec describes
     * {@code SnapResource.branch} ambiguously ({@code type: object}, "24 char branch id"), so both
     * forms are accepted: {@code branch.isTextual() ? branch.asText() : branch.path("id").asText()}.
     * Valid only as {@code ^[a-f0-9]{24}$}.
     *
     * @return the branch id, or {@code null} when none is readable / {@code null} wenn unlesbar
     */
    public static String branchIdOf(JsonNode snap) {
        if (snap == null) {
            return null;
        }
        JsonNode branch = snap.path("branch");
        String id;
        if (branch.isTextual()) {
            id = branch.asText();
        } else if (branch.isObject()) {
            JsonNode inner = branch.path("id");
            id = inner.isTextual() ? inner.asText() : null;
        } else {
            id = null;
        }
        return id != null && BRANCH_ID.matcher(id).matches() ? id : null;
    }

    /**
     * Maps a SoftNet failure to a reason (E1) / bildet einen SoftNet-Fehler auf einen Grund ab:
     * HTTP 401/403 anywhere in the cause chain → {@code softnet-forbidden}; a
     * {@link org.springframework.web.client.ResourceAccessException} caused by a {@link SocketTimeoutException} (read timeout after
     * {@code SOFTNET_TIMEOUT}, the {@code SimpleClientHttpRequestFactory} of
     * {@code SoftnetApiClient}) → {@code timeout}; everything else (5xx, connection refused, token
     * failure, parse errors) → {@code softnet-unavailable}. {@code SoftnetApiClient} itself has no
     * retry; the scheduler retries once after 60 s.
     */
    static ProbeReason reasonOf(Throwable failure) {
        boolean timeout = false;
        Set<Throwable> seen = new HashSet<>();
        for (Throwable t = failure; t != null && seen.add(t); t = t.getCause()) {
            if (t instanceof RestClientResponseException response) {
                int status = response.getStatusCode().value();
                if (status == 401 || status == 403) {
                    return ProbeReason.SOFTNET_FORBIDDEN;
                }
            }
            if (t instanceof SocketTimeoutException) {
                // Typically ResourceAccessException("I/O error … Read timed out") → cause.
                timeout = true;
            }
        }
        return timeout ? ProbeReason.TIMEOUT : ProbeReason.SOFTNET_UNAVAILABLE;
    }

    /** {@code brand[0]} of the group, {@code null} when missing or blank / erste Marke. */
    private static String brandOf(JsonNode group) {
        if (group == null || group.isMissingNode() || group.isNull()) {
            return null;
        }
        JsonNode brands = group.path("brand");
        if (!brands.isArray() || brands.isEmpty()) {
            return null;
        }
        JsonNode first = brands.get(0);
        String brand = first == null || !first.isValueNode() ? null : first.asText("").trim();
        return brand == null || brand.isEmpty() ? null : brand;
    }

    /** {@code checkInTime} as UTC instant, {@code null} when missing or unparseable (REQ-031). */
    private static Instant checkInTimeOf(JsonNode snap) {
        JsonNode time = snap.path("checkInTime");
        if (!time.isTextual() || time.asText().isBlank()) {
            return null;
        }
        try {
            return Instant.parse(time.asText().trim());
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static ProbeResult error(ProbeReason reason) {
        return new ProbeResult(ProbeStatus.ERROR, reason, null, null, null);
    }

    /** Technical DEBUG line — branch id and outcome only, never snap contents (BR-13). */
    private static ProbeResult logged(String branchId, ProbeResult result) {
        if (log.isDebugEnabled()) {
            log.debug("capacity probe {}: {}{}", branchId, result.status().key(),
                result.reason() == null ? "" : "/" + result.reason().key());
        }
        return result;
    }
}
