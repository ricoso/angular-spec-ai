package com.autohaus.backend.usage.insights;

import java.util.List;
import java.util.Map;

/**
 * One scenario row of the S6 drop-off tables (REQ-038 N1, §10.5, BR-31) / eine Szenario-Zeile der
 * Absprungszenarien-Tabellen. Already suppressed — cells follow BR-9 („&lt; 5", „k. A.").
 *
 * @param scenarioKey scenario key from the catalogue (§8), or {@code "quiet"} for the aggregated
 *     A-0 row / Szenario-Schlüssel
 * @param hint the fixed action hint {@code SC_*_HINT} (BR-32) / fester Handlungsimpuls
 * @param byStep cells for stages 1–4 / Zellen je Stufe
 * @param total total drop-offs of the scenario / Gesamt
 * @param share share of all drop-offs, ≥ 20 only / Anteil an allen Abbrüchen
 * @param byDevice cells keyed like {@code DropoffView.deviceColumns} / Zellen je Geräteklasse
 */
public record DropoffRow(
    String scenarioKey,
    String hint,
    List<Cell> byStep,
    Cell total,
    Cell share,
    Map<String, Cell> byDevice
) {}
