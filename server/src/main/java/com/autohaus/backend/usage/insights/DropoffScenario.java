package com.autohaus.backend.usage.insights;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Whitelist of the primary drop-off scenarios (REQ-038 N1, scenario catalogue §8, BR-27) /
 * Whitelist der primären Absprungszenarien. Every session with MaxStep &lt; 5 gets exactly one
 * scenario — the first matching catalogue row wins; the classification itself lives in the KQL
 * ({@code DROPOFF_KQL}, §12.8), Java only validates against this whitelist.
 *
 * <p><b>No {@code UNKNOWN} value (§10.2, second exception):</b> {@link #fromKey(String)} returns
 * {@code null} for a foreign key and the caller discards the row/counter with a {@code WARN}
 * without values (E11) — a catch-all bucket would silently break the invariant
 * „Σ scenarios = erreicht(1) − erreicht(5)" (BR-27). / Unbekannte Schlüssel werden verworfen.
 */
public enum DropoffScenario {
    SERVER_ERROR("server-error"),         // A-1: letzte Anfrage ≥ 500
    ACCESS_DENIED("access-denied"),       // A-2: letzte Anfrage 403/429
    SLOT_EXPIRED("slot-expired"),         // A-3: Buchung 404 (SnapCache-TTL 30 min)
    SLOT_CONFLICT("slot-conflict"),       // A-4: Buchung 410
    BOOKING_REJECTED("booking-rejected"), // A-5: letzte Anfrage Buchung 4xx
    SEARCH_REJECTED("search-rejected"),   // A-6: letzte Anfrage Suche 4xx
    ERROR_THEN_QUIT("error-then-quit"),   // A-7: Fehler in der Sitzung, letzte Anfrage ok
    REQUESTS_LOST("requests-lost"),       // A-8: Lücke im correlationId-Zähler
    NO_SLOTS("no-slots"),                 // A-9: letzte erfolgreiche Suche hits=none
    FEW_SLOTS("few-slots"),               // A-10: letzte erfolgreiche Suche hits=few
    SLOW_RESPONSE("slow-response"),       // A-11: letzte Anfrage ok, Dauer ≥ 8 s
    MANY_SEARCHES("many-searches"),       // A-12: ≥ 4 erfolgreiche Suchen
    QUIET_1("quiet-1"),                   // A-0.1: ohne erkennbaren Anlass nach Stufe 1
    QUIET_2("quiet-2"),
    QUIET_3("quiet-3"),
    QUIET_4("quiet-4");

    /** Last-response latency that counts as „langsam" (A-11, §19 Frage 10) / Latenzschwelle. */
    public static final int SLOW_RESPONSE_MS = 8_000;

    /** Successful searches that count as „viele Suchen" (A-12, §19 Frage 10) / Suchschwelle. */
    public static final int MANY_SEARCHES_MIN = 4;

    private final String key;

    DropoffScenario(String key) {
        this.key = key;
    }

    @JsonValue
    public String key() {
        return key;
    }

    /**
     * Whether the scenario carries the extra hint „Messartefakt möglich" (BR-32) / ob der Hinweis
     * „Messartefakt möglich" gilt (A-0 und A-8).
     */
    public boolean measurementArtefactPossible() {
        return this == REQUESTS_LOST || this == QUIET_1 || this == QUIET_2
            || this == QUIET_3 || this == QUIET_4;
    }

    /** Exact match or {@code null} — the caller discards foreign values (E11, BR-27). */
    public static DropoffScenario fromKey(String key) {
        for (DropoffScenario value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return null;
    }
}
