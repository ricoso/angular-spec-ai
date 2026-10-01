package com.autohaus.backend.usage.insights;

import java.util.Map;

/**
 * Drop-off scenarios of one day — only the two margins (REQ-038 N1, BR-30) / Absprungszenarien
 * eines Tages, ausschließlich die zwei Randverteilungen. The scenario × stage × device tuple is
 * never stored; no session ids, no timestamps. Per-day invariant:
 * Σ {@code byScenarioStep} = Σ {@code byScenarioDevice} = erreicht(1) − erreicht(5) (BR-27). /
 * Das volle Tupel wird nie gespeichert.
 *
 * <p>Both margins are nested exactly like the blob (§10.6): scenario key → stage resp. device class
 * → sessions. Keys missing inside a present inner map mean 0; a {@code null} margin means „not
 * collected" (→ „—"). / Beide Randverteilungen verschachtelt wie im Blob.
 *
 * @param byScenarioStep scenario key → stage {@code "1"}..{@code "4"} → sessions
 * @param byScenarioDevice scenario key → device class ({@code mobile|tablet|desktop|unknown}) →
 *     sessions
 */
public record DropoffDay(
    Map<String, Map<String, Long>> byScenarioStep,
    Map<String, Map<String, Long>> byScenarioDevice
) {}
