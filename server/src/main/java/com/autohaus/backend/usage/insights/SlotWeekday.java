package com.autohaus.backend.usage.insights;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Weekday of the booked slot (REQ-038 §10.2, T-3) / Wochentag des gebuchten Termins. Derived from
 * {@code rawSnap.checkInTime} (UTC) converted to Europe/Berlin (REQ-031 AC-17/18, BR-12);
 * {@code UNKNOWN} when the snap carries no usable time (E12). / Aus der Slot-Zeit abgeleitet.
 */
public enum SlotWeekday {
    MO("MO"), TU("TU"), WE("WE"), TH("TH"), FR("FR"), SA("SA"), SU("SU"),
    UNKNOWN("unknown");

    private final String key;

    SlotWeekday(String key) {
        this.key = key;
    }

    @JsonValue
    public String key() {
        return key;
    }

    /** Buckets the slot instant in the given zone / ordnet die Slot-Zeit in der Zone ein. */
    public static SlotWeekday of(Instant slotInstant, ZoneId zone) {
        if (slotInstant == null) {
            return UNKNOWN;
        }
        DayOfWeek day = slotInstant.atZone(zone).getDayOfWeek();
        return switch (day) {
            case MONDAY -> MO;
            case TUESDAY -> TU;
            case WEDNESDAY -> WE;
            case THURSDAY -> TH;
            case FRIDAY -> FR;
            case SATURDAY -> SA;
            case SUNDAY -> SU;
        };
    }

    /** Foreign values become {@code UNKNOWN} (E11) / unbekannte Werte werden {@code UNKNOWN}. */
    @JsonCreator
    public static SlotWeekday fromKey(String key) {
        for (SlotWeekday value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
