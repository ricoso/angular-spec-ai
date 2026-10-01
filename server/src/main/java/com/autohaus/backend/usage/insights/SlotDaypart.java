package com.autohaus.backend.usage.insights;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Daypart of the booked slot (REQ-038 §10.2, T-3/T-4) / Tagesabschnitt des gebuchten Termins.
 * Five buckets aligned with the shop day; {@code UNKNOWN} when the snap carries no usable time
 * (E12). / Fünf Zeitfenster; ohne verwertbare Slot-Zeit {@code UNKNOWN}.
 */
public enum SlotDaypart {
    EARLY("early"),         // vor 9 Uhr
    MORNING("morning"),     // 9–12 Uhr
    MIDDAY("midday"),       // 12–14 Uhr
    AFTERNOON("afternoon"), // 14–16 Uhr
    LATE("late"),           // ab 16 Uhr
    UNKNOWN("unknown");

    private final String key;

    SlotDaypart(String key) {
        this.key = key;
    }

    @JsonValue
    public String key() {
        return key;
    }

    /** Buckets the slot instant in the given zone (BR-12) / ordnet die Slot-Zeit ein. */
    public static SlotDaypart of(Instant slotInstant, ZoneId zone) {
        if (slotInstant == null) {
            return UNKNOWN;
        }
        int hour = slotInstant.atZone(zone).getHour();
        if (hour < 9) {
            return EARLY;
        }
        if (hour < 12) {
            return MORNING;
        }
        if (hour < 14) {
            return MIDDAY;
        }
        if (hour < 16) {
            return AFTERNOON;
        }
        return LATE;
    }

    /** Foreign values become {@code UNKNOWN} (E11) / unbekannte Werte werden {@code UNKNOWN}. */
    @JsonCreator
    public static SlotDaypart fromKey(String key) {
        for (SlotDaypart value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
