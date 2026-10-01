package com.autohaus.backend.usage.insights;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Hit level of an appointment search — the access-line field {@code hits} (REQ-038 N1, §10.8,
 * BR-29) / Trefferstufe einer Terminsuche. Counted are the <b>days</b> of the response with
 * {@code date >= request.fromDate()}; the field carries no location, no date and no variant.
 *
 * <p><b>No {@code UNKNOWN} value (§10.2, second exception):</b> a foreign key is discarded by the
 * caller ({@link #fromKey(String)} returns {@code null}, E11) — a catch-all bucket would silently
 * distort the invariant „Σ scenarios = drop-offs". / Unbekannte Werte werden verworfen, nie
 * gesammelt.
 */
public enum SearchHits {
    NONE("none"),   // 0 Tage — „Keine Termine verfügbar"
    FEW("few"),     // 1–3 Tage — weniger als 4 Kacheln
    MANY("many");   // ≥ 4 Tage — volle Vorschlagsliste

    /** Upper bound of {@code FEW} in days (§19 Frage 10) / Obergrenze von {@code FEW} in Tagen. */
    public static final int FEW_MAX_DAYS = 3;

    private final String key;

    SearchHits(String key) {
        this.key = key;
    }

    @JsonValue
    public String key() {
        return key;
    }

    /** Buckets the count of response days ≥ fromDate (§10.8) / ordnet die Tages-Anzahl ein. */
    public static SearchHits ofDayCount(int days) {
        if (days <= 0) {
            return NONE;
        }
        return days <= FEW_MAX_DAYS ? FEW : MANY;
    }

    /** Exact match or {@code null} — the caller discards foreign values (E11). */
    public static SearchHits fromKey(String key) {
        for (SearchHits value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return null;
    }
}
