package com.autohaus.backend.capacity;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * State of one nightly scan (REQ-038 §10.4, BR-17) / Zustand eines nächtlichen Scans. Strict enum
 * (§10.2): a foreign key is E6 — the claim mechanism depends on unambiguous states.
 */
public enum ScanState {
    RUNNING("running"),   // Claim geschrieben, Scan läuft (BR-17)
    COMPLETE("complete"), // alle Standorte ok/none
    PARTIAL("partial"),   // mindestens ein Standort error
    FAILED("failed");     // kein Standort ok/none

    private final String key;

    ScanState(String key) {
        this.key = key;
    }

    @JsonValue
    public String key() {
        return key;
    }

    /** Exact match or {@code null} — a foreign value is E6 (§10.2). */
    public static ScanState fromKey(String key) {
        for (ScanState value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return null;
    }
}
