package com.autohaus.backend.usage.insights;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Service groups of a booking (REQ-038 §10.2, BR-11) / Servicegruppen einer Buchung. The seven
 * tag-based keys mirror the SoftNet tag ranks 0–6 ({@code JobTypesService.TIRE_CHANGE_TAG_RANK} =
 * 3) and the frontend type {@code ServiceType}; {@code EXTRAS} = operation with two or more tags,
 * {@code PICKUP} = name contains „hol", {@code UNKNOWN} = not assignable (e.g. groups without
 * tags). / Sieben Tag-Gruppen plus Zusatzleistungen, Hol & Bring und „nicht zuordenbar".
 */
public enum ServiceGroup {
    INSPECTION("inspection"),          // rank 0
    TUV("tuv"),                        // rank 1
    BRAKE_FLUID("brake-fluid"),        // rank 2
    TIRE_CHANGE("tire-change"),        // rank 3
    ACTIONS_CHECKS("actions-checks"),  // rank 4
    REPAIR("repair"),                  // rank 5
    BODYWORK("bodywork"),              // rank 6
    EXTRAS("extras"),
    PICKUP("pickup"),
    UNKNOWN("unknown");

    private final String key;

    ServiceGroup(String key) {
        this.key = key;
    }

    @JsonValue
    public String key() {
        return key;
    }

    /** Tag rank 0–6 → group, anything else {@code UNKNOWN} (BR-11) / Rang → Gruppe. */
    public static ServiceGroup ofRank(int rank) {
        return switch (rank) {
            case 0 -> INSPECTION;
            case 1 -> TUV;
            case 2 -> BRAKE_FLUID;
            case 3 -> TIRE_CHANGE;
            case 4 -> ACTIONS_CHECKS;
            case 5 -> REPAIR;
            case 6 -> BODYWORK;
            default -> UNKNOWN;
        };
    }

    /** Foreign values become {@code UNKNOWN} (E11) / unbekannte Werte werden {@code UNKNOWN}. */
    @JsonCreator
    public static ServiceGroup fromKey(String key) {
        for (ServiceGroup value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
