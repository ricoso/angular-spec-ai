package com.autohaus.backend.capacity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * One nightly scan (REQ-038 §10.4) / ein nächtlicher Scan. Scan timestamps are operational data
 * without personal reference (NFR-Datenschutz-2). / Scan-Zeitstempel sind Betriebsdaten.
 *
 * @param state scan state; {@code RUNNING} is the claim (BR-17) / Zustand
 * @param startedAt claim/start timestamp / Startzeitpunkt
 * @param finishedAt finish timestamp, {@code null} while running / Endzeitpunkt
 * @param targetDay the measured target day (next working day Mo–Sa after the scan day) / Zieltag
 * @param branches probe results keyed by branchId / Ergebnisse je Standort
 */
public record CapacityScan(
    ScanState state,
    Instant startedAt,
    Instant finishedAt,
    LocalDate targetDay,
    Map<String, ProbeResult> branches
) {}
