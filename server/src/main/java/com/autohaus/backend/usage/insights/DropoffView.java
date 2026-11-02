package com.autohaus.backend.usage.insights;

import java.time.LocalDate;
import java.util.List;

/**
 * The suppressed drop-off view of S6 (REQ-038 N1, §10.5) — input for HTML <b>and</b> JSON /
 * die unterdrückte Absprungszenarien-Sicht. {@code null} in {@code FunnelView.dropoff} while
 * {@code usage.dropoff.enabled=false} (AC-35).
 *
 * @param collectingSince first counted day (BR-19) / Erfassungsbeginn
 * @param rows sorted by total descending, ties in catalogue order (§8); the aggregated A-0 row
 *     last / Zeilen nach Gesamt absteigend, A-0 aggregiert am Ende
 * @param total the sum row — equals the funnel difference erreicht(1) − erreicht(5) (AC-33)
 * @param deviceColumns visible device classes in order plus {@code "other"} (BR-31 / BR-9 Nr. 7)
 * @param otherDevices classes merged into „Sonstige" (header text) / zusammengefasste Klassen
 */
public record DropoffView(
    LocalDate collectingSince,
    List<DropoffRow> rows,
    DropoffRow total,
    List<String> deviceColumns,
    List<String> otherDevices
) {}
