package com.autohaus.backend.capacity;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 10-day classification of one branch (REQ-038 N2, BR-26) / Einordnung eines Standorts über die
 * letzten 10 Scans. From {@code MIN_MEASURABLE_DAYS} measurable days on: „meist frei"
 * (≥ 70 % free), „meist belegt" (≤ 20 % free), otherwise „wechselnd"; below the threshold
 * „zu wenig Daten" — such branches get neither I-2 nor I-3 (BR-21). Strict enum: a foreign key is
 * E6.
 */
public enum CapacityTrend {
    MOSTLY_FREE("mostly-free"),
    MIXED("mixed"),
    MOSTLY_BOOKED("mostly-booked"),
    TOO_FEW_DAYS("too-few-days");

    private final String key;

    CapacityTrend(String key) {
        this.key = key;
    }

    @JsonValue
    public String key() {
        return key;
    }

    /** Exact match or {@code null} — a foreign value is E6 (§10.2). */
    public static CapacityTrend fromKey(String key) {
        for (CapacityTrend value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return null;
    }
}
