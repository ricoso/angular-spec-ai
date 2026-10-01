package com.autohaus.backend.capacity;

import com.autohaus.backend.config.CapacityProperties;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.usage.BlobAccess;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Reads and writes {@code capacity-daily.json} (REQ-038 §10.6, §12.10, BR-18) / liest und
 * schreibt die Kapazitäts-Historie. Tolerant reader (unknown fields ignored,
 * {@code schemaVersion ≤ CURRENT}); the capacity enums are strict — an unknown status, reason or
 * state is E6 (§10.2). Only {@code CapacityScanScheduler} writes this file. / Toleranter Leser mit
 * strengen Enums; nur der Scan-Scheduler schreibt.
 *
 * <p><b>Why a tree walk instead of data binding:</b> the file mixes tolerated gaps (a missing
 * {@code finishedAt} of a running claim, a missing {@code freeSlotsTargetDay}) with hard errors
 * (a foreign enum key, a negative counter, a non-ISO day key). Walking the {@link JsonNode} tree
 * with its own local Jackson-2 mapper states both explicitly and needs no {@code JavaTimeModule};
 * the injectable {@code ObjectMapper} bean is deliberately not used (same reason as
 * {@code UsageBlobClient}). / Eigener lokaler Mapper, der globale Bean bleibt unberührt.
 *
 * <p><b>E6 (unusable content):</b> {@link #parse(String, String)} never throws; it returns
 * {@code usable == false}, logs {@code ERROR} with blob name and {@code schemaVersion} and the
 * scheduler then leaves the file untouched for manual analysis. / Unbrauchbarer Inhalt wird nie
 * überschrieben.
 */
@Component
public class CapacityBlobClient {

    private static final Logger log = LoggerFactory.getLogger(CapacityBlobClient.class);

    /** Default blob name (§10.7) / Standard-Blob-Name. */
    public static final String DEFAULT_BLOB = "capacity-daily.json";

    /** Local mapper — trees only, no data binding (see class javadoc) / lokaler Mapper. */
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final BlobAccess access;

    @Autowired
    public CapacityBlobClient(UsageStatsProperties usageProperties,
                              CapacityProperties capacityProperties) {
        this(new BlobAccess(usageProperties,
            capacityProperties == null || capacityProperties.blob() == null
                || capacityProperties.blob().isBlank() ? DEFAULT_BLOB : capacityProperties.blob()));
    }

    /** Visible for testing / für Tests. */
    public CapacityBlobClient(BlobAccess access) {
        this.access = access;
    }

    /** Whether storage is configured at all (E4) — without it no scan runs (AC-32). */
    public boolean isConfigured() {
        return access.isConfigured();
    }

    /**
     * Loads the history / lädt die Historie. 404 → empty but usable (first run, §5.2).
     *
     * @throws com.autohaus.backend.usage.UsageBlobClient.BlobUnavailableException E4
     */
    public Loaded load() {
        BlobAccess.Loaded raw = access.get();
        if (!raw.exists()) {
            return new Loaded(CapacityHistory.empty(), null, true);
        }
        return parse(raw.body(), raw.etag(), access.blobName());
    }

    /**
     * Writes the history conditionally and returns the new ETag (needed between claim and result
     * write, §12.10) / schreibt bedingt und liefert den neuen ETag.
     *
     * @throws com.autohaus.backend.usage.UsageBlobClient.BlobPreconditionFailedException E5
     * @throws com.autohaus.backend.usage.UsageBlobClient.BlobUnavailableException E4
     */
    public String save(CapacityHistory history, String etag) {
        return access.put(serialize(history), etag);
    }

    /**
     * Tolerant parse with strict enums (BR-18, §10.2, E6) — shared with the E2E seed loader.
     *
     * <p>Tolerated / toleriert: unknown fields at every level, {@code schemaVersion ≤ CURRENT},
     * missing optional values ({@code referenceService}, {@code startedAt}, {@code finishedAt},
     * {@code targetDay}, {@code reason}, {@code firstFreeDate}, {@code workdaysToFirstFree},
     * {@code freeSlotsTargetDay}) → {@code null}; missing {@code scans}/{@code branches} → empty.
     *
     * <p>E6 / unbrauchbar: no JSON object, missing or non-integral {@code schemaVersion}, a version
     * greater than {@link CapacityHistory#CURRENT_SCHEMA_VERSION}, a non-ISO day key, an unknown or
     * missing {@code state}/{@code status}, an unknown {@code reason}, unparseable timestamps or
     * dates, negative or non-numeric counters.
     *
     * @return usable history, or {@code usable == false} for E6 (never throws)
     */
    public static Loaded parse(String json, String etag) {
        return parse(json, etag, DEFAULT_BLOB);
    }

    private static Loaded parse(String json, String etag, String blobName) {
        JsonNode root;
        try {
            root = json == null ? null : MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            log.error("capacity blob {} unusable (schemaVersion unknown): no valid JSON — the file "
                + "is left untouched for manual analysis (E6)", blobName);
            return unusable(etag);
        }
        try {
            return new Loaded(readHistory(root), etag, true);
        } catch (UnusableContentException e) {
            log.error("capacity blob {} unusable (schemaVersion {}): {} — the file is left "
                + "untouched for manual analysis (E6)", blobName, e.schemaVersion, e.getMessage());
            return unusable(etag);
        }
    }

    /**
     * Serializes the history with ISO keys in ascending order / serialisiert mit ISO-Keys
     * aufsteigend. Compact JSON; {@code null} values are omitted (a running claim has no
     * {@code finishedAt}, a {@code none} result only {@code status}); the schema version written is
     * always {@link CapacityHistory#CURRENT_SCHEMA_VERSION}. No field carries
     * {@code checkInResource}, operation names or customer data (§10.6).
     */
    public static String serialize(CapacityHistory history) {
        CapacityHistory source = history == null ? CapacityHistory.empty() : history;
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", CapacityHistory.CURRENT_SCHEMA_VERSION);
        if (source.referenceService() != null) {
            root.put("referenceService", source.referenceService());
        }
        ObjectNode scans = root.putObject("scans");
        SortedMap<LocalDate, CapacityScan> sorted =
            source.scans() == null ? new TreeMap<>() : new TreeMap<>(source.scans());
        for (Map.Entry<LocalDate, CapacityScan> entry : sorted.entrySet()) {
            CapacityScan scan = entry.getValue();
            if (scan == null) {
                continue;
            }
            ObjectNode node = scans.putObject(entry.getKey().toString());
            if (scan.state() != null) {
                node.put("state", scan.state().key());
            }
            putIfPresent(node, "startedAt", scan.startedAt());
            putIfPresent(node, "finishedAt", scan.finishedAt());
            putIfPresent(node, "targetDay", scan.targetDay());
            ObjectNode branches = node.putObject("branches");
            Map<String, ProbeResult> results =
                scan.branches() == null ? Map.of() : new TreeMap<>(scan.branches());
            for (Map.Entry<String, ProbeResult> result : results.entrySet()) {
                ProbeResult probe = result.getValue();
                if (probe == null || probe.status() == null) {
                    continue;
                }
                ObjectNode branch = branches.putObject(result.getKey());
                branch.put("status", probe.status().key());
                if (probe.reason() != null) {
                    branch.put("reason", probe.reason().key());
                }
                putIfPresent(branch, "firstFreeDate", probe.firstFreeDate());
                if (probe.workdaysToFirstFree() != null) {
                    branch.put("workdaysToFirstFree", probe.workdaysToFirstFree());
                }
                if (probe.freeSlotsTargetDay() != null) {
                    branch.put("freeSlotsTargetDay", probe.freeSlotsTargetDay());
                }
            }
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            // A tree of strings and ints cannot fail to serialize; guard anyway.
            throw new IllegalStateException("capacity history not serializable", e);
        }
    }

    // ---- reading / Lesen ----

    private static CapacityHistory readHistory(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new UnusableContentException("root is not a JSON object", "unknown");
        }
        JsonNode versionNode = root.get("schemaVersion");
        if (versionNode == null || !versionNode.isIntegralNumber() || !versionNode.canConvertToInt()) {
            throw new UnusableContentException("schemaVersion missing or not an integer",
                versionNode == null ? "missing" : versionNode.toString());
        }
        int version = versionNode.intValue();
        String versionText = String.valueOf(version);
        if (version < 1 || version > CapacityHistory.CURRENT_SCHEMA_VERSION) {
            throw new UnusableContentException("unsupported schemaVersion (current "
                + CapacityHistory.CURRENT_SCHEMA_VERSION + ")", versionText);
        }
        JsonNode referenceNode = root.get("referenceService");
        String referenceService = referenceNode != null && referenceNode.isTextual()
            ? referenceNode.asText() : null;

        SortedMap<LocalDate, CapacityScan> scans = new TreeMap<>();
        JsonNode scansNode = root.get("scans");
        if (scansNode != null && !scansNode.isNull()) {
            if (!scansNode.isObject()) {
                throw new UnusableContentException("scans is not an object", versionText);
            }
            for (Map.Entry<String, JsonNode> entry : scansNode.properties()) {
                LocalDate day = isoDay(entry.getKey(), "scan day key", versionText);
                scans.put(day, readScan(entry.getValue(), entry.getKey(), versionText));
            }
        }
        return new CapacityHistory(version, referenceService,
            Collections.unmodifiableSortedMap(scans));
    }

    private static CapacityScan readScan(JsonNode node, String day, String version) {
        if (node == null || !node.isObject()) {
            throw new UnusableContentException("scan " + day + " is not an object", version);
        }
        ScanState state = ScanState.fromKey(text(node.get("state")));
        if (state == null) {
            throw new UnusableContentException("scan " + day + ": unknown or missing state", version);
        }
        Instant startedAt = optionalInstant(node.get("startedAt"), day + ".startedAt", version);
        Instant finishedAt = optionalInstant(node.get("finishedAt"), day + ".finishedAt", version);
        LocalDate targetDay = optionalDay(node.get("targetDay"), day + ".targetDay", version);

        SortedMap<String, ProbeResult> branches = new TreeMap<>();
        JsonNode branchesNode = node.get("branches");
        if (branchesNode != null && !branchesNode.isNull()) {
            if (!branchesNode.isObject()) {
                throw new UnusableContentException("scan " + day + ": branches is not an object",
                    version);
            }
            for (Map.Entry<String, JsonNode> entry : branchesNode.properties()) {
                branches.put(entry.getKey(), readResult(entry.getValue(), day, version));
            }
        }
        return new CapacityScan(state, startedAt, finishedAt, targetDay,
            Collections.unmodifiableSortedMap(branches));
    }

    private static ProbeResult readResult(JsonNode node, String day, String version) {
        if (node == null || !node.isObject()) {
            throw new UnusableContentException("scan " + day + ": branch result is not an object",
                version);
        }
        ProbeStatus status = ProbeStatus.fromKey(text(node.get("status")));
        if (status == null) {
            throw new UnusableContentException("scan " + day + ": unknown or missing status",
                version);
        }
        ProbeReason reason = null;
        JsonNode reasonNode = node.get("reason");
        if (reasonNode != null && !reasonNode.isNull()) {
            reason = ProbeReason.fromKey(text(reasonNode));
            if (reason == null) {
                throw new UnusableContentException("scan " + day + ": unknown reason", version);
            }
        }
        LocalDate firstFree = optionalDay(node.get("firstFreeDate"), day + ".firstFreeDate",
            version);
        Integer workdays = optionalCounter(node.get("workdaysToFirstFree"),
            day + ".workdaysToFirstFree", version);
        Integer slots = optionalCounter(node.get("freeSlotsTargetDay"),
            day + ".freeSlotsTargetDay", version);
        return new ProbeResult(status, reason, firstFree, workdays, slots);
    }

    private static LocalDate isoDay(String value, String what, String version) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException e) {
            throw new UnusableContentException(what + " is not an ISO date", version);
        }
    }

    private static LocalDate optionalDay(JsonNode node, String what, String version) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new UnusableContentException(what + " is not an ISO date", version);
        }
        return isoDay(node.asText(), what, version);
    }

    private static Instant optionalInstant(JsonNode node, String what, String version) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new UnusableContentException(what + " is not an ISO instant", version);
        }
        try {
            return Instant.parse(node.asText());
        } catch (DateTimeException e) {
            throw new UnusableContentException(what + " is not an ISO instant", version);
        }
    }

    /** Missing → {@code null}; negative or non-numeric → E6 / fehlend → null, negativ → E6. */
    private static Integer optionalCounter(JsonNode node, String what, String version) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() < 0) {
            throw new UnusableContentException(what + " is not a non-negative integer", version);
        }
        return node.intValue();
    }

    private static String text(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private static void putIfPresent(ObjectNode node, String field, Object value) {
        if (value != null) {
            node.put(field, value.toString());
        }
    }

    private static Loaded unusable(String etag) {
        return new Loaded(CapacityHistory.empty(), etag, false);
    }

    /** Internal E6 signal carrying the schema version for the log / internes E6-Signal. */
    private static final class UnusableContentException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final String schemaVersion;

        private UnusableContentException(String message, String schemaVersion) {
            super(message);
            this.schemaVersion = schemaVersion;
        }
    }

    /**
     * Result of a read / Ergebnis eines Lesevorgangs.
     *
     * @param history the parsed history, {@link CapacityHistory#empty()} when unusable
     * @param etag ETag for the conditional write, {@code null} when the blob does not exist
     * @param usable {@code false} → E6: content unusable, must not be overwritten
     */
    public record Loaded(CapacityHistory history, String etag, boolean usable) {}
}
