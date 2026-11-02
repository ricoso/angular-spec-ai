package com.autohaus.backend.usage.insights;

/**
 * Sessions that reached at least stage k (REQ-038 BR-8) / Sitzungen, die mindestens Stufe k
 * erreicht haben. Cumulative counters, never a per-session tuple; {@code null} = not collected
 * („—", never 0). / Kumulierte Zähler, nie ein Sitzungs-Tupel.
 *
 * @param visits erreicht(1) — Besuch (App-Aufruf)
 * @param services erreicht(2) — Leistungsauswahl
 * @param notes erreicht(3) — Hinweise
 * @param search erreicht(4) — Terminsuche ausgeführt (BR-8, N1)
 * @param booking erreicht(5) — Gebucht
 */
public record FunnelCounts(Long visits, Long services, Long notes, Long search, Long booking) {}
