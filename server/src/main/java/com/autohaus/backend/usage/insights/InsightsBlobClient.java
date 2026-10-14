package com.autohaus.backend.usage.insights;

import com.autohaus.backend.config.UsageInsightsProperties;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.usage.BlobAccess;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Reads and writes {@code usage-insights.json} (REQ-038 §10.6, §12.10, BR-18) / liest und
 * schreibt die Insights-Historie. Tolerant reader: unknown fields ignored,
 * {@code schemaVersion ≤ CURRENT}, missing counters {@code null}; unusable content (E6) is never
 * overwritten. {@code usage-daily.json} (REQ-037) is never touched. / Toleranter Leser; die
 * REQ-037-Datei wird nie angefasst.
 *
 * <p><b>Parsing (E6) / Einlesen:</b> the file is walked as a JSON tree and every value is checked
 * explicitly — deliberately unlike the strict REQ-037 mapper, so a newer version with additional
 * fields stays readable after a rollback (BR-18). Unusable ({@code usable == false}, {@code ERROR}
 * with blob name and {@code schemaVersion}) are: no JSON, a {@code schemaVersion} missing or
 * greater than {@link InsightsHistory#CURRENT_SCHEMA_VERSION}, a negative or non-numeric counter, a
 * non-ISO day or month key, a non-ISO date value. <b>Not</b> an error: unknown fields (ignored), a
 * missing counter or map ({@code null} → „—", never 0). In the drop-off margins (N1) unknown
 * scenario, stage and device keys are discarded with one {@code WARN} <b>without values</b>
 * (E11) so the sums stay consistent (BR-27). / Unbekannte Szenario-Schlüssel werden verworfen.
 *
 * <p><b>Writing / Schreiben:</b> ISO keys in ascending order (the {@code SortedMap}s), {@code null}
 * written explicitly ({@code Include.ALWAYS}: {@code "funnel": null} means „not collected"),
 * timestamps as ISO-8601 strings. Both drop-off margins are nested like the record
 * ({@code scenario → stage|device → sessions}, §10.6). / Verschachtelt geschrieben.
 */
@Component
public class InsightsBlobClient {

    private static final Logger log = LoggerFactory.getLogger(InsightsBlobClient.class);

    /** Default blob name (§10.7) / Standard-Blob-Name. */
    public static final String DEFAULT_BLOB = "usage-insights.json";

    /**
     * Local tolerant mapper (§14.1): {@code FAIL_ON_UNKNOWN_PROPERTIES} off, {@code JavaTimeModule},
     * no timestamps, {@code Include.ALWAYS}. The injectable {@code ObjectMapper} bean has no modules
     * and stays untouched. / Eigener Mapper; der globale Bean bleibt unverändert.
     */
    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.ALWAYS,
            JsonInclude.Include.ALWAYS))
        .build();

    private final BlobAccess access;

    @Autowired
    public InsightsBlobClient(UsageStatsProperties usageProperties,
                              UsageInsightsProperties insightsProperties) {
        this(new BlobAccess(usageProperties,
            insightsProperties == null || insightsProperties.blob() == null
                || insightsProperties.blob().isBlank() ? DEFAULT_BLOB : insightsProperties.blob()));
    }

    /** Visible for testing / für Tests. */
    public InsightsBlobClient(BlobAccess access) {
        this.access = access;
    }

    /** Whether storage is configured at all (E4) / ob Storage konfiguriert ist. */
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
            return new Loaded(InsightsHistory.empty(), null, true);
        }
        return parse(raw.body(), raw.etag(), access.blobName());
    }

    /**
     * Writes the history conditionally (If-Match / If-None-Match: *) / schreibt bedingt.
     *
     * @throws com.autohaus.backend.usage.UsageBlobClient.BlobPreconditionFailedException E5
     * @throws com.autohaus.backend.usage.UsageBlobClient.BlobUnavailableException E4
     */
    public void save(InsightsHistory history, String etag) {
        access.put(serialize(history), etag);
    }

    /**
     * Tolerant parse (BR-18, E6) / toleranter Parser — shared with the E2E seed loader.
     *
     * @param json raw file content / roher Inhalt
     * @param etag the ETag of the read, passed through / durchgereichter ETag
     * @return usable history, or {@code usable == false} for E6 (never throws)
     */
    public static Loaded parse(String json, String etag) {
        return parse(json, etag, DEFAULT_BLOB);
    }

    /** Same as {@link #parse(String, String)} with the blob name for the log line / mit Blob-Name. */
    static Loaded parse(String json, String etag, String blobName) {
        String blob = blobName == null ? DEFAULT_BLOB : blobName;
        if (json == null || json.isBlank()) {
            return unusable(blob, "?", "empty content", etag);
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            return unusable(blob, "?", "not valid JSON", etag);
        }
        if (root == null || !root.isObject()) {
            return unusable(blob, "?", "not a JSON object", etag);
        }
        JsonNode version = root.get("schemaVersion");
        String versionText = version == null ? "missing" : version.asText();
        if (version == null || !version.isIntegralNumber() || !version.canConvertToInt()) {
            return unusable(blob, versionText, "schemaVersion missing or not an integer", etag);
        }
        if (version.asInt() > InsightsHistory.CURRENT_SCHEMA_VERSION) {
            return unusable(blob, versionText, "schemaVersion newer than "
                + InsightsHistory.CURRENT_SCHEMA_VERSION, etag);
        }
        try {
            Reader reader = new Reader();
            InsightsHistory history = reader.history(root, version.asInt());
            if (reader.discardedKeys > 0) {
                // E11 / BR-27: count only — the discarded keys themselves are never logged.
                log.warn("usage insights blob {}: {} unknown funnel or drop-off key(s) discarded while reading (E11)",
                    blob, reader.discardedKeys);
            }
            return new Loaded(history, etag, true);
        } catch (InvalidContentException e) {
            return unusable(blob, versionText, e.getMessage(), etag);
        }
    }

    /**
     * Serializes the history with ISO keys in ascending order / serialisiert mit ISO-Keys.
     *
     * @throws IllegalArgumentException for a {@code null} history / bei {@code null}
     */
    public static String serialize(InsightsHistory history) {
        if (history == null) {
            throw new IllegalArgumentException("history must not be null");
        }
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", history.schemaVersion());
        putText(root, "updatedAt", history.updatedAt() == null ? null : history.updatedAt().toString());
        putText(root, "collectingSince", isoOrNull(history.collectingSince()));
        putText(root, "funnelCollectingSince", isoOrNull(history.funnelCollectingSince()));
        putText(root, "dropoffCollectingSince", isoOrNull(history.dropoffCollectingSince()));

        ObjectNode days = root.putObject("days");
        new TreeMap<>(nonNull(history.days())).forEach((day, value) ->
            writeDay(days, day.toString(), value));
        ObjectNode branchDays = root.putObject("branchDays");
        new TreeMap<>(nonNull(history.branchDays())).forEach((day, branches) ->
            writeBranches(branchDays, day.toString(), branches));
        ObjectNode branchMonths = root.putObject("branchMonths");
        new TreeMap<>(nonNull(history.branchMonths())).forEach((month, branches) ->
            writeBranches(branchMonths, month.toString(), branches));
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("usage insights history not serializable", e);
        }
    }

    // ---- writing / Schreiben ----

    private static void writeDay(ObjectNode parent, String key, InsightsDay day) {
        if (day == null) {
            parent.putNull(key);
            return;
        }
        ObjectNode node = parent.putObject(key);
        putCount(node, "bookings", day.bookings());
        putCounts(node, "bookingDaypart", day.bookingDaypart());
        putNested(node, "slotGrid", day.slotGrid());
        putCount(node, "slotUnknown", day.slotUnknown());
        putCounts(node, "leadTime", day.leadTime());
        putCounts(node, "groups", day.groups());
        putCount(node, "mobility", day.mobility());
        putCount(node, "serviceViews", day.serviceViews());
        // REQ-042: only written once collected — keeps older blobs (and the §10.6 example) byte-stable.
        if (day.variants() != null) {
            putCounts(node, "variants", day.variants());
        }
        FunnelDay funnel = day.funnel();
        if (funnel == null) {
            node.putNull("funnel");
            return;
        }
        ObjectNode funnelNode = node.putObject("funnel");
        putFunnelCounts(funnelNode, "all", funnel.all());
        if (funnel.byDevice() == null) {
            funnelNode.putNull("byDevice");
        } else {
            ObjectNode devices = funnelNode.putObject("byDevice");
            funnel.byDevice().forEach((device, counts) -> putFunnelCounts(devices, device, counts));
        }
        DropoffDay dropoff = funnel.dropoff();
        if (dropoff == null) {
            funnelNode.putNull("dropoff");
            return;
        }
        ObjectNode dropoffNode = funnelNode.putObject("dropoff");
        putNested(dropoffNode, "byScenarioStep", dropoff.byScenarioStep());
        putNested(dropoffNode, "byScenarioDevice", dropoff.byScenarioDevice());
    }

    private static void writeBranches(ObjectNode parent, String key, Map<String, BranchCounts> branches) {
        if (branches == null) {
            parent.putNull(key);
            return;
        }
        ObjectNode node = parent.putObject(key);
        branches.forEach((branchId, counts) -> {
            if (counts == null) {
                node.putNull(branchId);
                return;
            }
            ObjectNode branch = node.putObject(branchId);
            putCount(branch, "bookings", counts.bookings());
            putCount(branch, "serviceViews", counts.serviceViews());
            putCounts(branch, "groups", counts.groups());
            putCount(branch, "mobility", counts.mobility());
            putCount(branch, "offHours", counts.offHours());
            putCount(branch, "slotLate", counts.slotLate());
        });
    }

    private static void putFunnelCounts(ObjectNode parent, String key, FunnelCounts counts) {
        if (counts == null) {
            parent.putNull(key);
            return;
        }
        ObjectNode node = parent.putObject(key);
        putCount(node, "visits", counts.visits());
        putCount(node, "services", counts.services());
        putCount(node, "notes", counts.notes());
        putCount(node, "search", counts.search());
        putCount(node, "booking", counts.booking());
    }

    private static void putNested(ObjectNode parent, String key, Map<String, Map<String, Long>> nested) {
        if (nested == null) {
            parent.putNull(key);
            return;
        }
        ObjectNode node = parent.putObject(key);
        nested.forEach((outer, inner) -> putCounts(node, outer, inner));
    }

    private static void putCounts(ObjectNode parent, String key, Map<String, Long> counts) {
        if (counts == null) {
            parent.putNull(key);
            return;
        }
        ObjectNode node = parent.putObject(key);
        counts.forEach((name, value) -> putCount(node, name, value));
    }

    private static void putCount(ObjectNode node, String key, Long value) {
        if (value == null) {
            node.putNull(key);
        } else {
            node.put(key, value.longValue());
        }
    }

    private static void putText(ObjectNode node, String key, String value) {
        if (value == null) {
            node.putNull(key);
        } else {
            node.put(key, value);
        }
    }

    private static String isoOrNull(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static <K, V> Map<K, V> nonNull(Map<K, V> map) {
        return map == null ? Map.of() : map;
    }

    private static Loaded unusable(String blob, String schemaVersion, String reason, String etag) {
        log.error("usage insights blob {} unusable (schemaVersion {}): {} — the file is left untouched "
            + "for manual analysis (E6)", blob, schemaVersion, reason);
        return new Loaded(InsightsHistory.empty(), etag, false);
    }

    // ---- reading / Lesen ----

    /** Signals E6 while walking the tree / signalisiert E6 beim Einlesen. */
    private static final class InvalidContentException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        InvalidContentException(String message) {
            super(message, null, false, false);
        }
    }

    /** Walks one file / liest eine Datei; counts discarded drop-off keys for one WARN. */
    private static final class Reader {

        private int discardedKeys;

        InsightsHistory history(JsonNode root, int schemaVersion) {
            SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
            forEachField(root.get("days"), "days", (key, value) -> {
                LocalDate day = isoDate(key, "days");
                if (value != null && !value.isNull()) {
                    days.put(day, day(value, "days"));
                }
            });
            SortedMap<LocalDate, Map<String, BranchCounts>> branchDays = new TreeMap<>();
            forEachField(root.get("branchDays"), "branchDays", (key, value) -> {
                LocalDate day = isoDate(key, "branchDays");
                if (value != null && !value.isNull()) {
                    branchDays.put(day, branches(value, "branchDays"));
                }
            });
            SortedMap<YearMonth, Map<String, BranchCounts>> branchMonths = new TreeMap<>();
            forEachField(root.get("branchMonths"), "branchMonths", (key, value) -> {
                YearMonth month;
                try {
                    month = YearMonth.parse(key);
                } catch (DateTimeParseException e) {
                    throw new InvalidContentException("non-ISO month key in branchMonths");
                }
                if (value != null && !value.isNull()) {
                    branchMonths.put(month, branches(value, "branchMonths"));
                }
            });
            return new InsightsHistory(schemaVersion,
                instant(root.get("updatedAt")),
                dateValue(root.get("collectingSince"), "collectingSince"),
                dateValue(root.get("funnelCollectingSince"), "funnelCollectingSince"),
                dateValue(root.get("dropoffCollectingSince"), "dropoffCollectingSince"),
                Collections.unmodifiableSortedMap(days),
                Collections.unmodifiableSortedMap(branchDays),
                Collections.unmodifiableSortedMap(branchMonths));
        }

        private InsightsDay day(JsonNode node, String path) {
            requireObject(node, path);
            return new InsightsDay(
                count(node.get("bookings"), path),
                counts(node.get("bookingDaypart"), path),
                nested(node.get("slotGrid"), path),
                count(node.get("slotUnknown"), path),
                counts(node.get("leadTime"), path),
                counts(node.get("groups"), path),
                count(node.get("mobility"), path),
                count(node.get("serviceViews"), path),
                funnel(node.get("funnel"), path + ".funnel"),
                counts(node.get("variants"), path));
        }

        private FunnelDay funnel(JsonNode node, String path) {
            if (node == null || node.isNull()) {
                return null;
            }
            requireObject(node, path);
            Map<String, FunnelCounts> byDevice = null;
            JsonNode devices = node.get("byDevice");
            if (devices != null && !devices.isNull()) {
                requireObject(devices, path + ".byDevice");
                Map<String, FunnelCounts> parsed = new LinkedHashMap<>();
                for (Map.Entry<String, JsonNode> field : devices.properties()) {
                    FunnelCounts counts = funnelCounts(field.getValue(), path + ".byDevice");
                    if (!InsightsAggregator.DEVICES.contains(field.getKey())) {
                        discardedKeys++;
                        continue;
                    }
                    if (counts != null) {
                        parsed.put(field.getKey(), counts);
                    }
                }
                byDevice = Collections.unmodifiableMap(parsed);
            }
            return new FunnelDay(funnelCounts(node.get("all"), path + ".all"), byDevice,
                dropoff(node.get("dropoff"), path + ".dropoff"));
        }

        private FunnelCounts funnelCounts(JsonNode node, String path) {
            if (node == null || node.isNull()) {
                return null;
            }
            requireObject(node, path);
            return new FunnelCounts(count(node.get("visits"), path), count(node.get("services"), path),
                count(node.get("notes"), path), count(node.get("search"), path),
                count(node.get("booking"), path));
        }

        private DropoffDay dropoff(JsonNode node, String path) {
            if (node == null || node.isNull()) {
                return null;
            }
            requireObject(node, path);
            Map<String, Map<String, Long>> byStep = null;
            JsonNode steps = node.get("byScenarioStep");
            if (steps != null && !steps.isNull()) {
                Map<String, Map<String, Long>> raw = nested(steps, path);
                byStep = new LinkedHashMap<>();
                for (Map.Entry<String, Map<String, Long>> scenario : raw.entrySet()) {
                    if (DropoffScenario.fromKey(scenario.getKey()) == null) {
                        discardedKeys++;
                        continue;
                    }
                    Map<String, Long> stages = new LinkedHashMap<>();
                    if (scenario.getValue() != null) {
                        for (Map.Entry<String, Long> stage : scenario.getValue().entrySet()) {
                            if (!InsightsAggregator.DROPOFF_STAGES.contains(stage.getKey())) {
                                discardedKeys++;
                                continue;
                            }
                            stages.put(stage.getKey(), stage.getValue());
                        }
                    }
                    byStep.put(scenario.getKey(), Collections.unmodifiableMap(stages));
                }
                byStep = Collections.unmodifiableMap(byStep);
            }
            Map<String, Map<String, Long>> byDevice = null;
            JsonNode devices = node.get("byScenarioDevice");
            if (devices != null && !devices.isNull()) {
                Map<String, Map<String, Long>> raw = nested(devices, path);
                byDevice = new LinkedHashMap<>();
                for (Map.Entry<String, Map<String, Long>> scenario : raw.entrySet()) {
                    if (DropoffScenario.fromKey(scenario.getKey()) == null) {
                        discardedKeys++;
                        continue;
                    }
                    if (scenario.getValue() == null) {
                        continue;
                    }
                    Map<String, Long> classes = new LinkedHashMap<>();
                    for (Map.Entry<String, Long> device : scenario.getValue().entrySet()) {
                        if (!InsightsAggregator.DEVICES.contains(device.getKey())) {
                            discardedKeys++;
                            continue;
                        }
                        classes.put(device.getKey(), device.getValue());
                    }
                    byDevice.put(scenario.getKey(), Collections.unmodifiableMap(classes));
                }
                byDevice = Collections.unmodifiableMap(byDevice);
            }
            return new DropoffDay(byStep, byDevice);
        }

        private Map<String, BranchCounts> branches(JsonNode node, String path) {
            requireObject(node, path);
            Map<String, BranchCounts> branches = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                JsonNode value = field.getValue();
                if (value == null || value.isNull()) {
                    continue;
                }
                requireObject(value, path);
                branches.put(field.getKey(), new BranchCounts(
                    count(value.get("bookings"), path),
                    count(value.get("serviceViews"), path),
                    counts(value.get("groups"), path),
                    count(value.get("mobility"), path),
                    count(value.get("offHours"), path),
                    count(value.get("slotLate"), path)));
            }
            return Collections.unmodifiableMap(branches);
        }

        /** {@code key → count}; a {@code null} value inside a present map is left out. */
        private Map<String, Long> counts(JsonNode node, String path) {
            if (node == null || node.isNull()) {
                return null;
            }
            requireObject(node, path);
            Map<String, Long> counts = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                Long value = count(field.getValue(), path);
                if (value != null) {
                    counts.put(field.getKey(), value);
                }
            }
            return Collections.unmodifiableMap(counts);
        }

        private Map<String, Map<String, Long>> nested(JsonNode node, String path) {
            if (node == null || node.isNull()) {
                return null;
            }
            requireObject(node, path);
            Map<String, Map<String, Long>> nested = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                Map<String, Long> inner = counts(field.getValue(), path);
                if (inner != null) {
                    nested.put(field.getKey(), inner);
                }
            }
            return Collections.unmodifiableMap(nested);
        }

        /** Non-negative integral counter, {@code null} when missing (BR-18) — else E6. */
        private static Long count(JsonNode node, String path) {
            if (node == null || node.isNull()) {
                return null;
            }
            if (!node.isIntegralNumber() || !node.canConvertToLong()) {
                throw new InvalidContentException("non-numeric counter in " + path);
            }
            long value = node.asLong();
            if (value < 0) {
                throw new InvalidContentException("negative counter in " + path);
            }
            return value;
        }

        private static LocalDate isoDate(String key, String path) {
            try {
                return LocalDate.parse(key);
            } catch (DateTimeParseException e) {
                throw new InvalidContentException("non-ISO day key in " + path);
            }
        }

        private static LocalDate dateValue(JsonNode node, String field) {
            if (node == null || node.isNull()) {
                return null;
            }
            if (!node.isTextual()) {
                throw new InvalidContentException("non-ISO date in " + field);
            }
            try {
                return LocalDate.parse(node.asText());
            } catch (DateTimeParseException e) {
                throw new InvalidContentException("non-ISO date in " + field);
            }
        }

        private static Instant instant(JsonNode node) {
            if (node == null || node.isNull()) {
                return null;
            }
            if (!node.isTextual()) {
                throw new InvalidContentException("non-ISO timestamp in updatedAt");
            }
            try {
                return Instant.parse(node.asText());
            } catch (DateTimeParseException e) {
                throw new InvalidContentException("non-ISO timestamp in updatedAt");
            }
        }

        private static void requireObject(JsonNode node, String path) {
            if (!node.isObject()) {
                throw new InvalidContentException("unexpected structure in " + path);
            }
        }

        private static void forEachField(JsonNode node, String path, FieldConsumer consumer) {
            if (node == null || node.isNull()) {
                return;
            }
            requireObject(node, path);
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                consumer.accept(field.getKey(), field.getValue());
            }
        }
    }

    @FunctionalInterface
    private interface FieldConsumer {
        void accept(String key, JsonNode value);
    }

    /**
     * Result of a read / Ergebnis eines Lesevorgangs.
     *
     * @param history the parsed history, {@link InsightsHistory#empty()} when unusable
     * @param etag ETag for the conditional write, {@code null} when the blob does not exist
     * @param usable {@code false} → E6: content unusable, must not be overwritten
     */
    public record Loaded(InsightsHistory history, String etag, boolean usable) {}
}
