package com.autohaus.backend.usage.insights;

/**
 * One stage of the S6 funnel (REQ-038 §10.5, F-1) / eine Stufe des Trichters. The percentage
 * cells are {@code WITHHELD} as soon as the stage or its previous stage is not {@code VALUE}
 * (BR-9 Nr. 5). / Prozentzellen sind unterdrückt, sobald eine Bezugsstufe unterdrückt ist.
 *
 * <p><b>Not applicable is {@code null}, not {@code NO_DATA} / nicht anwendbar ist {@code null}:</b>
 * stage 1 has no previous stage ({@code percentOfPrevious == null}) and stage 5 („Gebucht") has no
 * drop-out ({@code droppedHere == null}). A {@code NO_DATA} cell means „not collected" („—"). /
 * Stufe 1 ohne Vorstufe, Stufe 5 ohne Ausstieg; {@code NO_DATA} heißt „nicht erfasst".
 *
 * @param key stage key {@code visits|services|notes|search|booking} / Stufenschlüssel
 * @param reached sessions that reached at least this stage / erreicht(k)
 * @param percentOfStart share of stage 1 / % vom Start
 * @param percentOfPrevious share of the previous stage, {@code null} for stage 1 / % der Vorstufe
 * @param droppedHere sessions whose MaxStep equals this stage (stages 1–4, F-2), {@code null} for
 *     stage 5 / Ausstiege
 */
public record FunnelStage(String key, Cell reached, Cell percentOfStart, Cell percentOfPrevious,
                          Cell droppedHere) {}
