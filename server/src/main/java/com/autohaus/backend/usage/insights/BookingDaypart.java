package com.autohaus.backend.usage.insights;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Daypart of the booking moment (REQ-038 §10.2, T-1/T-2) / Tagesabschnitt des Buchungszeitpunkts.
 * Deliberately three coarse buckets instead of hours (NFR-Datenschutz-5); the value is always
 * computable from the server clock, so there is no {@code unknown}. / Bewusst grob, immer
 * berechenbar.
 */
public enum BookingDaypart {
    DAY("day"),         // 07–18 Uhr
    EVENING("evening"), // 18–22 Uhr
    NIGHT("night");     // 22–07 Uhr

    private final String key;

    BookingDaypart(String key) {
        this.key = key;
    }

    @JsonValue
    public String key() {
        return key;
    }

    /** Buckets the instant in the given zone (BR-12) / ordnet den Zeitpunkt in der Zone ein. */
    public static BookingDaypart of(Instant instant, ZoneId zone) {
        int hour = instant.atZone(zone).getHour();
        if (hour >= 7 && hour < 18) {
            return DAY;
        }
        if (hour >= 18 && hour < 22) {
            return EVENING;
        }
        return NIGHT;
    }

    /**
     * Exact key match or {@code null} — a foreign value read back from KQL is discarded by the
     * caller (E11). / Unbekannter Schlüssel liefert {@code null}; der Aufrufer verwirft die Zeile.
     */
    @JsonCreator
    public static BookingDaypart fromKey(String key) {
        for (BookingDaypart value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return null;
    }
}
