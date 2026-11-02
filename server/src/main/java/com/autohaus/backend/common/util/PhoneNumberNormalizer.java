package com.autohaus.backend.common.util;

/**
 * Normalizes German phone numbers to the international format SoftNet expects
 * for reliable SMS delivery — SoftNet does not reliably process numbers with a
 * leading "0" for SMS dispatch (silent failure, no error/exception).
 *
 * Normalisiert deutsche Telefonnummern in das internationale Format, das
 * SoftNet fuer eine zuverlaessige SMS-Zustellung erwartet — SoftNet
 * verarbeitet Nummern mit fuehrender "0" nicht zuverlaessig fuer den
 * SMS-Versand (silent failure, ohne Fehler/Exception).
 */
public final class PhoneNumberNormalizer {

    private static final String GERMAN_TRUNK_PREFIX = "0";
    private static final String GERMAN_COUNTRY_CODE = "+49";

    private PhoneNumberNormalizer() {
    }

    /**
     * Converts a raw phone number into SoftNet's expected format (REQ-033).
     * Wandelt eine rohe Telefonnummer in das von SoftNet erwartete Format um.
     *
     * Rules / Regeln:
     * - Starts with "+"        -&gt; returned unchanged (already international).
     * - Starts with "0"        -&gt; leading "0" replaced with "+49", rest unchanged.
     * - null / blank / other   -&gt; returned unchanged (defensive, no exception).
     *
     * No further cleanup (whitespace, dashes) is performed — out of scope (BR-3).
     */
    public static String toSoftnetFormat(String rawPhoneNumber) {
        if (rawPhoneNumber == null || rawPhoneNumber.isBlank()) {
            return rawPhoneNumber;
        }
        String trimmed = rawPhoneNumber.trim();
        if (trimmed.startsWith("+")) {
            return trimmed;
        }
        if (trimmed.startsWith(GERMAN_TRUNK_PREFIX)) {
            return GERMAN_COUNTRY_CODE + trimmed.substring(1);
        }
        return trimmed;
    }
}
