package com.autohaus.backend.capacity;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Cell state of one scan day in the 10-day grid (REQ-038 N2, BR-26) / Zell-Zustand eines Scan-Tags
 * im 10-Tage-Raster. Mapping (§10.4): {@code free-next-day} → {@code FREE};
 * {@code within-4-workdays} → {@code TIGHT_DAY} („knapp"); {@code tight} ∪ {@code none-found} →
 * {@code BOOKED} („belegt"); error / no scan that day → {@code NOT_MEASURABLE} („—"). The cell
 * always carries its short text — colour is never the only carrier (BR-23). Strict enum: a foreign
 * key is E6.
 */
public enum CapacityDayState {
    FREE("free"),                     // am Zieltag online frei
    TIGHT_DAY("tight"),               // erster freier Termin in 1–4 Werktagen
    BOOKED("booked"),                 // ≥ 5 Werktage oder keine Online-Termine gefunden
    NOT_MEASURABLE("not-measurable"); // Fehler bzw. kein Scan (zählt nicht als messbarer Tag)

    private final String key;

    CapacityDayState(String key) {
        this.key = key;
    }

    @JsonValue
    public String key() {
        return key;
    }

    /** Whether the day counts towards the measurable days (BR-26) / zählt als messbarer Tag. */
    public boolean measurable() {
        return this != NOT_MEASURABLE;
    }

    /** Exact match or {@code null} — a foreign value is E6 (§10.2). */
    public static CapacityDayState fromKey(String key) {
        for (CapacityDayState value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return null;
    }
}
