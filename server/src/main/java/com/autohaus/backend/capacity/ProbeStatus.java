package com.autohaus.backend.capacity;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Outcome of one branch probe (REQ-038 §10.4) / Ergebnis einer Standort-Messung. Strict enum
 * (§10.2): a foreign key is E6, never a catch-all — claim state and level must stay unambiguous.
 * / Strenges Enum; unbekannte Schlüssel sind E6.
 */
public enum ProbeStatus {
    OK("ok"),       // Snaps mit gültiger Branch-ID dieses Standorts gefunden
    NONE("none"),   // leere Liste bzw. nur gültige fremde Branch-IDs
    ERROR("error"); // Fehler, Grund in ProbeReason (BR-14: nie als 0 anzeigen)

    private final String key;

    ProbeStatus(String key) {
        this.key = key;
    }

    @JsonValue
    public String key() {
        return key;
    }

    /** Exact match or {@code null} — a foreign value is E6 (§10.2). */
    public static ProbeStatus fromKey(String key) {
        for (ProbeStatus value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return null;
    }
}
