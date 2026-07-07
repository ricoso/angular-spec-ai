package com.autohaus.backend.common.logging;

public final class LogPayloadUtil {

    public static final int MAX_PAYLOAD_LOG_CHARS = 8_000;

    private LogPayloadUtil() {
    }

    public static String truncate(String payload) {
        if (payload == null) {
            return "";
        }
        // Upstream bodies may contain line breaks — collapse them so every
        // payload stays a single log line (KQL matches whole lines).
        String singleLine = payload.replaceAll("[\\r\\n]+", " ");
        if (singleLine.length() <= MAX_PAYLOAD_LOG_CHARS) {
            return singleLine;
        }
        return singleLine.substring(0, MAX_PAYLOAD_LOG_CHARS)
            + "...[truncated, total " + singleLine.length() + " chars]";
    }
}
