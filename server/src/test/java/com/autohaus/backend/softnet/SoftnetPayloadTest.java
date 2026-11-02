package com.autohaus.backend.softnet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-045: unknown values are omitted from SoftNet payloads, never replaced by a placeholder. */
class SoftnetPayloadTest {

    private final ObjectMapper om = new ObjectMapper();

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "", " ", "\t", "UNKNOWN", "Unknown", "unknown", "  unknown  ", "N/A", "n/a", "NA", "na",
        "null", "NULL", "undefined", "TBD", "tbd", "unbekannt", "Unbekannt", "-", " - "})
    void isPlaceholder_nullBlankAndKnownPlaceholders_areTrue(String value) {
        assertTrue(SoftnetPayload.isPlaceholder(value), "[" + value + "] must count as unknown");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Golf", "A4 Avant", "T-Roc", "Q5 40 TDI", "WVWZZZ1JZXW000001", "01", "2020", "0"})
    void isPlaceholder_realValues_areFalse(String value) {
        assertFalse(SoftnetPayload.isPlaceholder(value), "[" + value + "] is a real value");
    }

    @Test
    void putIfKnown_setsTheTrimmedValue() {
        ObjectNode node = om.createObjectNode();

        SoftnetPayload.putIfKnown(node, "model", "  Golf ");

        assertEquals("Golf", node.get("model").asText());
    }

    @Test
    void putIfKnown_realValue_overwritesAnExistingOne() {
        ObjectNode node = om.createObjectNode().put("model", "Polo");

        SoftnetPayload.putIfKnown(node, "model", "Golf");

        assertEquals("Golf", node.get("model").asText());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "UNKNOWN", "Unknown", "N/A", "-"})
    void putIfKnown_unknownValue_doesNotAddTheKey(String value) {
        ObjectNode node = om.createObjectNode();

        SoftnetPayload.putIfKnown(node, "VIN", value);

        assertFalse(node.has("VIN"), "an unknown value must not create the key");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "UNKNOWN", "n/a"})
    void putIfKnown_unknownValue_removesAnInheritedPlaceholder(String value) {
        ObjectNode node = om.createObjectNode().put("VIN", "UNKNOWN").put("brand", "Audi");

        SoftnetPayload.putIfKnown(node, "VIN", value);

        assertFalse(node.has("VIN"), "the inherited placeholder must be removed");
        assertEquals("Audi", node.get("brand").asText(), "other fields stay untouched");
    }
}
