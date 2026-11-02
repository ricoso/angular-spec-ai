package com.autohaus.backend.softnet;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Locale;
import java.util.Set;

/**
 * REQ-045: builds SoftNet payload fields without placeholder values. SoftNet takes the vehicle
 * fields (VIN, model, first registration) over into its vehicle master data; a placeholder such as
 * {@code "UNKNOWN"} or an invented default pollutes that data and the SoftNet staff complain. An
 * unknown value is therefore <b>omitted</b> — the JSON key is not set at all — and never replaced
 * by a placeholder, an empty string or a made-up default.
 *
 * <p>Hilfsfunktionen für SoftNet-Payloads ohne Platzhalter: eine unbekannte Angabe wird
 * <b>weggelassen</b> (der JSON-Key fehlt) und nie durch {@code "UNKNOWN"}, einen leeren String oder
 * einen erfundenen Default ersetzt. Das gilt auch für Platzhalter, die von außen hereinkommen
 * (Frontend, Snap-Objekt): sie werden nicht durchgereicht, sondern entfernt.
 */
public final class SoftnetPayload {

    /**
     * Lower-case spellings that mean "not known", compared trimmed and case-insensitive. /
     * Platzhalter-Schreibweisen für „nicht bekannt", getrimmt und ohne Groß-/Kleinschreibung.
     */
    private static final Set<String> PLACEHOLDERS =
        Set.of("unknown", "n/a", "na", "null", "undefined", "tbd", "unbekannt", "-");

    private SoftnetPayload() {
    }

    /**
     * {@code true} when the value carries no real information: {@code null}, blank, or a placeholder
     * such as {@code UNKNOWN}, {@code N/A} or {@code -} (trimmed, case-insensitive). / {@code true},
     * wenn der Wert keine echte Angabe ist: null, leer oder ein Platzhalter.
     */
    public static boolean isPlaceholder(String value) {
        if (value == null) {
            return true;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || PLACEHOLDERS.contains(trimmed.toLowerCase(Locale.ROOT));
    }

    /**
     * Sets {@code field} to the trimmed {@code value} only for a real value; otherwise the key is
     * <b>removed</b>. The removal also drops a placeholder that was inherited from a copied object
     * (e.g. the snap's car, copied with {@code deepCopy()}). / Setzt das Feld nur bei einer echten
     * Angabe; sonst wird der Key entfernt — auch ein aus einem kopierten Objekt geerbter Platzhalter.
     */
    public static void putIfKnown(ObjectNode node, String field, String value) {
        if (isPlaceholder(value)) {
            node.remove(field);
        } else {
            node.put(field, value.trim());
        }
    }
}
