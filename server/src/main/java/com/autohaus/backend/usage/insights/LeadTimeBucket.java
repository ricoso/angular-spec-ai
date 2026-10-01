package com.autohaus.backend.usage.insights;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Lead time between booking day and slot day in Berlin calendar days (REQ-038 §10.2, T-5) /
 * Vorlauf zwischen Buchungstag und Termintag in Berliner Kalendertagen. Buckets instead of exact
 * days (NFR-Datenschutz-5); {@code UNKNOWN} without a usable slot time (E12) or an implausible
 * negative span. / Buckets statt exakter Tage.
 */
public enum LeadTimeBucket {
    D0_1("0-1"),
    D2_3("2-3"),
    D4_7("4-7"),
    D8_14("8-14"),
    D15_30("15-30"),
    D31_PLUS("31+"),
    UNKNOWN("unknown");

    private final String key;

    LeadTimeBucket(String key) {
        this.key = key;
    }

    @JsonValue
    public String key() {
        return key;
    }

    /** Buckets the span between the two Berlin calendar days / ordnet die Spanne ein. */
    public static LeadTimeBucket of(LocalDate bookingDay, LocalDate slotDay) {
        if (bookingDay == null || slotDay == null) {
            return UNKNOWN;
        }
        long days = ChronoUnit.DAYS.between(bookingDay, slotDay);
        if (days < 0) {
            return UNKNOWN;
        }
        if (days <= 1) {
            return D0_1;
        }
        if (days <= 3) {
            return D2_3;
        }
        if (days <= 7) {
            return D4_7;
        }
        if (days <= 14) {
            return D8_14;
        }
        if (days <= 30) {
            return D15_30;
        }
        return D31_PLUS;
    }

    /** Foreign values become {@code UNKNOWN} (E11) / unbekannte Werte werden {@code UNKNOWN}. */
    @JsonCreator
    public static LeadTimeBucket fromKey(String key) {
        for (LeadTimeBucket value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
