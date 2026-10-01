package com.autohaus.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kill switch and coordinates of the nightly capacity scan (REQ-038 K1, §10.7) / Kill-Switch und
 * Koordinaten des nächtlichen Kapazitäts-Scans.
 *
 * <p>{@code enabled == false} (the delivery default, gated by S-1): no SoftNet scan runs, S1 and
 * the impulses I-1..I-3 are omitted and {@code capacity.json} answers 404 (AC-31). / Default aus
 * (Gate S-1); ohne Schalter läuft kein SoftNet-Call.
 *
 * @param enabled {@code CAPACITY_SCAN_ENABLED}
 * @param scanCron {@code CAPACITY_SCAN_CRON} — Europe/Berlin; {@code -} disables the cron (E2E)
 * @param referenceService {@code CAPACITY_REFERENCE_SERVICE} — {@code OperationIdMapper} key of
 *     the reference service („Wischerblätter erneuern", the smallest unit)
 * @param showSlotCount {@code CAPACITY_SHOW_SLOT_COUNT} — K-2, only without SoftNet capping (S-1)
 * @param blob {@code CAPACITY_BLOB} — blob name inside {@code USAGE_STORAGE_CONTAINER}
 */
@ConfigurationProperties(prefix = "capacity")
public record CapacityProperties(
    boolean enabled,
    String scanCron,
    String referenceService,
    boolean showSlotCount,
    String blob
) {}
