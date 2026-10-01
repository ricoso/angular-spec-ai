package com.autohaus.backend.capacity;

import java.time.LocalDate;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Deserialized {@code capacity-daily.json} (REQ-038 §10.4, §10.6) / deserialisierte
 * Kapazitäts-Historie. Read tolerantly (unknown fields ignored, {@code schemaVersion ≤ CURRENT},
 * BR-18); only the {@code CapacityScanScheduler} ever writes this file (§12.10). / Tolerant
 * gelesen; nur der Scan-Scheduler schreibt.
 *
 * @param schemaVersion file schema, at most {@link #CURRENT_SCHEMA_VERSION}
 * @param referenceService the {@code OperationIdMapper} key the scans measured / Referenz-Leistung
 * @param scans scans keyed by scan day / Scans je Kalendertag
 */
public record CapacityHistory(
    int schemaVersion,
    String referenceService,
    SortedMap<LocalDate, CapacityScan> scans
) {
    /** Current blob schema version (BR-18) / aktuelle Schema-Version. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** An empty history — the first-run state (§5.2) / leere Historie für den Erstlauf. */
    public static CapacityHistory empty() {
        return new CapacityHistory(CURRENT_SCHEMA_VERSION, null,
            Collections.unmodifiableSortedMap(new TreeMap<>()));
    }
}
