package com.autohaus.backend.reporting;

import java.util.List;

/**
 * One rule-based action hint of S0 (REQ-038 BR-21, §10.5) / ein regelbasierter Handlungsimpuls.
 * No AI, no randomness, no time-of-day dependent choice (BR-21). / Deterministisch, ohne KI.
 *
 * @param key {@code free-target-day} (I-1) | {@code promo} (I-2) | {@code bottleneck} (I-3) |
 *     {@code top} (I-4)
 * @param title tile title, relative to the day of retrieval for I-1 (BR-25) / Kacheltitel
 * @param branchNames the named branches, already free of suppressed values (BR-9) / Standorte
 * @param percent I-4: global T-2 share of the closed month (n ≥ 20 only), else {@code null}
 * @param source source line of the tile, e.g. „Scan 19.11." or „Oktober 2026" ({@code .ib-src},
 *     §11.2) / Quellenangabe der Kachel
 * @param values I-1 while K-2 is shown ({@code CAPACITY_SHOW_SLOT_COUNT}): free online slots on the
 *     target day per named branch, aligned to {@code branchNames} (§17 {@code IMPULSE_FREE_TEXT});
 *     {@code null} otherwise. Capacity values are operational data — the „&lt; 5" rule does not
 *     apply (BR-26). / I-1 mit K-2: freie Online-Termine je genanntem Standort, sonst {@code null}.
 */
public record Impulse(String key, String title, List<String> branchNames, Integer percent,
                      String source, List<Integer> values) {

    /** Impulse without per-branch values (I-1 without K-2, I-2 … I-4) / ohne Werte je Standort. */
    public Impulse(String key, String title, List<String> branchNames, Integer percent, String source) {
        this(key, title, branchNames, percent, source, null);
    }
}
