package com.autohaus.backend.capacity;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Text level of the most recent probe (REQ-038 §10.4, K-1) / Textstufe des jüngsten Probes.
 * Mapping (§10.4): OK + 0 workdays → {@code FREE_NEXT_DAY}; 1–4 → {@code WITHIN_4_WORKDAYS};
 * ≥ 5 → {@code TIGHT} (label „belegt: ≥ 5 Werktage", N2); NONE → {@code NONE_FOUND};
 * ERROR → {@code NOT_MEASURABLE}. The label of {@code FREE_NEXT_DAY} is built relative to the day
 * of retrieval (BR-25). Strict enum: a foreign key is E6.
 */
public enum CapacityLevel {
    FREE_NEXT_DAY("free-next-day"),
    WITHIN_4_WORKDAYS("within-4-workdays"),
    TIGHT("tight"),
    NONE_FOUND("none-found"),
    NOT_MEASURABLE("not-measurable");

    private final String key;

    CapacityLevel(String key) {
        this.key = key;
    }

    @JsonValue
    public String key() {
        return key;
    }

    /** Exact match or {@code null} — a foreign value is E6 (§10.2). */
    public static CapacityLevel fromKey(String key) {
        for (CapacityLevel value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return null;
    }
}
