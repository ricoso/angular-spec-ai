package com.autohaus.backend.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PiiRedactorTest {

    private final PiiRedactor redactor = new PiiRedactor(new ObjectMapper());

    @Test
    void masksClientContactData() {
        String json = "{\"client\":{\"firstName\":\"Max\",\"lastName\":\"Mustermann\","
            + "\"email\":\"max.mustermann@example.com\",\"mobile\":\"+49 170 1234567\","
            + "\"phone\":\"0201 123456\",\"address\":\"Hauptstraße 1\",\"zipCode\":\"45127\",\"city\":\"Essen\"}}";

        String out = redactor.redact(json);

        assertFalse(out.contains("Max"), "first name must be masked");
        assertFalse(out.contains("Mustermann"), "last name must be masked");
        assertFalse(out.contains("max.mustermann@example.com"), "email must be masked");
        assertFalse(out.contains("1234567"), "mobile must be masked");
        assertFalse(out.contains("Hauptstraße"), "address must be masked");
        assertFalse(out.contains("45127"), "zip code must be masked");
        assertFalse(out.contains("Essen"), "city must be masked");
        assertTrue(out.contains("***"), "masked values must appear as ***");
    }

    @Test
    void masksVehicleIdentifiersButKeepsBrand() {
        String json = "{\"car\":{\"brand\":\"volkswagen\",\"model\":\"Golf\","
            + "\"license\":\"E-AB 1234\",\"VIN\":\"WVWZZZ1JZXW000001\"}}";

        String out = redactor.redact(json);

        assertFalse(out.contains("E-AB 1234"), "licence plate must be masked");
        assertFalse(out.contains("WVWZZZ1JZXW000001"), "VIN must be masked (case-insensitive field)");
        assertTrue(out.contains("volkswagen"), "brand is not PII and must remain");
        assertTrue(out.contains("Golf"), "model is not PII and must remain");
    }

    @Test
    void leavesSnapsPayloadUnchanged() {
        // A typical /snaps request body contains no PII — nothing to mask.
        String json = "{\"groupid\":\"g1\",\"brand\":\"volkswagen\","
            + "\"operations\":[\"op1\"],\"preferredBranch\":\"b1\"}";

        String out = redactor.redact(json);

        assertTrue(out.contains("volkswagen"));
        assertTrue(out.contains("g1"));
        assertTrue(out.contains("op1"));
        assertTrue(out.contains("b1"));
        assertFalse(out.contains("***"), "no PII field present → nothing masked");
    }

    @Test
    void masksNestedObjectsAndArrays() {
        String json = "{\"wishlist\":{\"clients\":[{\"email\":\"a@b.de\"},{\"email\":\"c@d.de\"}]}}";

        String out = redactor.redact(json);

        assertFalse(out.contains("a@b.de"));
        assertFalse(out.contains("c@d.de"));
        assertTrue(out.contains("***"));
    }

    @Test
    void leavesNonJsonOrBlankInputUnchanged() {
        assertEquals("not json at all", redactor.redact("not json at all"));
        assertEquals("", redactor.redact(""));
        assertNull(redactor.redact(null));
    }
}
