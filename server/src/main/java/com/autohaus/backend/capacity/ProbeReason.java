package com.autohaus.backend.capacity;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Why a branch probe failed (REQ-038 §10.4, E1) / Grund eines fehlgeschlagenen Probes. Strict enum
 * (§10.2): a foreign key is E6. Shown as „nicht messbar: {Grund}" — never as 0 (BR-14).
 */
public enum ProbeReason {
    SOFTNET_UNAVAILABLE("softnet-unavailable"),
    SOFTNET_FORBIDDEN("softnet-forbidden"),   // HTTP 401/403
    TIMEOUT("timeout"),
    NO_REFERENCE_SERVICE("no-reference-service"),
    NO_BRAND("no-brand"),
    /** Snaps delivered, but none with a readable branch id (§12.9) — never mapped to NONE. */
    UNEXPECTED_RESPONSE("unexpected-response");

    private final String key;

    ProbeReason(String key) {
        this.key = key;
    }

    @JsonValue
    public String key() {
        return key;
    }

    /** Exact match or {@code null} — a foreign value is E6 (§10.2). */
    public static ProbeReason fromKey(String key) {
        for (ProbeReason value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return null;
    }
}
