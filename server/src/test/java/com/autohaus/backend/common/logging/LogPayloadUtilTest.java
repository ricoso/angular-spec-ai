package com.autohaus.backend.common.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LogPayloadUtilTest {

    @Test
    void truncate_null_returnsEmptyString() {
        assertEquals("", LogPayloadUtil.truncate(null));
    }

    @Test
    void truncate_shortPayload_returnedUnchanged() {
        assertEquals("{\"a\":1}", LogPayloadUtil.truncate("{\"a\":1}"));
    }

    @Test
    void truncate_exactLimit_returnedUnchanged() {
        String payload = "x".repeat(LogPayloadUtil.MAX_PAYLOAD_LOG_CHARS);
        assertEquals(payload, LogPayloadUtil.truncate(payload));
    }

    @Test
    void truncate_collapsesLineBreaksToSingleLine() {
        assertEquals("line1 line2 line3", LogPayloadUtil.truncate("line1\r\nline2\nline3"));
    }

    @Test
    void truncate_overLimit_cutsAtLimitAndAppendsSuffix() {
        String payload = "x".repeat(LogPayloadUtil.MAX_PAYLOAD_LOG_CHARS + 5);
        String result = LogPayloadUtil.truncate(payload);
        assertEquals(LogPayloadUtil.MAX_PAYLOAD_LOG_CHARS, result.indexOf("...[truncated"));
        assertTrue(result.endsWith("...[truncated, total " + payload.length() + " chars]"));
    }
}
