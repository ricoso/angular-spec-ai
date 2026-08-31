package com.autohaus.backend.common.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REQ-034: proves the central log-payload sanitizer removes PII while keeping the technical
 * structure. Fixture values mirror the requirement's acceptance criteria.
 * / Beweist, dass der zentrale Log-Sanitizer PII entfernt, Technik aber erhält.
 */
class LogSanitizerTest {

    // TC-1: every sensitive field value is masked, structure and non-PII stay intact.
    @Test
    void redact_masksAllSensitiveJsonFields() {
        String json = "{\"wishlist\":{\"client\":{\"firstName\":\"Max\",\"lastName\":\"Mustermann\","
            + "\"email\":\"max@example.com\",\"mobile\":\"+49 151 1234567\",\"phone\":\"0201 123456\","
            + "\"address\":\"Hauptstraße 1\",\"zipCode\":\"45127\",\"city\":\"Essen\",\"salutation\":\"Herr\"},"
            + "\"car\":{\"brand\":\"volkswagen\",\"model\":\"Golf\",\"license\":\"AB-CD 1234\","
            + "\"VIN\":\"WVWZZZ1JZXW000001\"},\"note\":\"Bitte Herrn Mustermann anrufen\"}}";

        String out = LogSanitizer.redact(json);

        assertFalse(out.contains("Max"), out);
        assertFalse(out.contains("Mustermann"), out);
        assertFalse(out.contains("max@example.com"), out);
        assertFalse(out.contains("1234567"), out);
        assertFalse(out.contains("Hauptstraße"), out);
        assertFalse(out.contains("45127"), out);
        assertFalse(out.contains("Essen"), out);
        assertFalse(out.contains("AB-CD 1234"), out);
        assertFalse(out.contains("WVWZZZ1JZXW000001"), out);
        assertFalse(out.contains("Bitte Herrn"), out);
        // Non-PII technical fields survive.
        assertTrue(out.contains("volkswagen"), out);
        assertTrue(out.contains("Golf"), out);
        assertTrue(out.contains("***"), out);
    }

    // REQ-034: value is replaced by its char count so length stays observable (empty vs present).
    @Test
    void redact_replacesValueWithCharCount() {
        String json = "{\"email\":\"max@example.com\",\"lastName\":\"Mustermann\","
            + "\"firstName\":\"\",\"phone\":null}";

        String out = LogSanitizer.redact(json);

        assertTrue(out.contains("***(15)"), out);    // "max@example.com" = 15 chars
        assertTrue(out.contains("***(10)"), out);    // "Mustermann" = 10 chars
        assertTrue(out.contains("***(0)"), out);     // empty string stays distinguishable
        assertTrue(out.contains("***(null)"), out);  // null distinguishable from empty
        assertFalse(out.contains("max@example.com"), out);
        assertFalse(out.contains("Mustermann"), out);
    }

    // TC-1: masking is case-insensitive on the field NAME (VIN, LicensePlate, Email ...).
    @Test
    void redact_isCaseInsensitiveOnFieldName() {
        String json = "{\"Email\":\"a@b.de\",\"LicensePlate\":\"M-AB 12\",\"VIN\":\"WVWZZZ1JZXW000001\"}";

        String out = LogSanitizer.redact(json);

        assertFalse(out.contains("a@b.de"), out);
        assertFalse(out.contains("M-AB 12"), out);
        assertFalse(out.contains("WVWZZZ1JZXW000001"), out);
    }

    // TC-7: auth material is never logged in clear text.
    @Test
    void redact_masksAuthHeaders() {
        String json = "{\"Authorization\":\"Bearer eyJhbGciOi.secret.token\",\"Cookie\":\"SESSION=abc123\"}";

        String out = LogSanitizer.redact(json);

        assertFalse(out.contains("eyJhbGciOi.secret.token"), out);
        assertFalse(out.contains("SESSION=abc123"), out);
    }

    @Test
    void redact_masksNestedArrays() {
        String json = "{\"clients\":[{\"email\":\"a@b.de\"},{\"email\":\"c@d.de\"}]}";

        String out = LogSanitizer.redact(json);

        assertFalse(out.contains("a@b.de"), out);
        assertFalse(out.contains("c@d.de"), out);
        assertTrue(out.contains("***"), out);
    }

    // TC-5: non-JSON payload (exception message / octet-stream) → regex fallback masks identifiers.
    @Test
    void redact_nonJson_appliesRegexFallback() {
        String msg = "500 Internal Server Error: booking failed for max@example.com plate M-AB 1234E "
            + "vin WVWZZZ1JZXW000001 phone +49 151 1234567";

        String out = LogSanitizer.redact(msg);

        assertFalse(out.contains("max@example.com"), out);
        assertFalse(out.contains("M-AB 1234E"), out);
        assertFalse(out.contains("WVWZZZ1JZXW000001"), out);
        assertFalse(out.contains("1234567"), out);
        // Technical status text is preserved for analysis.
        assertTrue(out.contains("500 Internal Server Error"), out);
    }

    @Test
    void redact_nonPiiTextUnchanged_andBlankNullSafe() {
        assertEquals("connection reset by peer", LogSanitizer.redact("connection reset by peer"));
        assertEquals("", LogSanitizer.redact(""));
        assertNull(LogSanitizer.redact(null));
    }

    // TC-9: IP masking (GDPR).
    @Test
    void maskIp_keepsNetworkDropsHost() {
        assertEquals("203.0.113.0/24", LogSanitizer.maskIp("203.0.113.7"));
        assertEquals("2001::/64", LogSanitizer.maskIp("2001:db8::1"));
        assertNull(LogSanitizer.maskIp(null));
    }

    @Test
    void redactAndTruncate_masksThenBoundsLength() {
        String big = "{\"email\":\"leak@example.com\",\"filler\":\"" + "x".repeat(20_000) + "\"}";

        String out = LogSanitizer.redactAndTruncate(big);

        assertFalse(out.contains("leak@example.com"), "PII masked before truncation");
        assertTrue(out.length() <= LogPayloadUtil.MAX_PAYLOAD_LOG_CHARS + 80, "length bounded");
        assertTrue(out.contains("truncated"), out);
    }
}
