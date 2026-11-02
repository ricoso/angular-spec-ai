package com.autohaus.backend.usage.insights;

import java.util.List;
import java.util.Map;

/**
 * The suppressed funnel view of S6 (REQ-038 §10.5, F-1..F-4) / die unterdrückte Trichter-Sicht.
 *
 * @param period the display period / Anzeigezeitraum
 * @param stages the five stages in order / die fünf Stufen
 * @param deviceColumns visible device classes in order plus {@code "other"} (BR-9 Nr. 7);
 *     empty = only „Alle" / sichtbare Geräteklassen
 * @param otherDevices classes merged into „Sonstige" (header text) / zusammengefasste Klassen
 * @param byDevice stage cells 1–4 per column, keyed like {@code deviceColumns} / Zellen je Spalte
 * @param dropoff drop-off scenarios (N1), {@code null} while {@code usage.dropoff.enabled=false}
 */
public record FunnelView(
    Period period,
    List<FunnelStage> stages,
    List<String> deviceColumns,
    List<String> otherDevices,
    Map<String, List<Cell>> byDevice,
    DropoffView dropoff
) {}
