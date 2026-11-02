package com.autohaus.backend.common.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Central PII redaction for log payloads (REQ-034). Every payload that would otherwise be written
 * to a log line — SoftNet request/response bodies, the raw snap, upstream exception messages —
 * passes through here first, so no clear-text name, e-mail, phone number, address, licence plate
 * or VIN ever reaches the console log / Azure Log Analytics.
 *
 * <p>Zentrale PII-Maskierung für Log-Payloads: ersetzt sensible Feldwerte durch {@code "***"},
 * bevor eine Zeile den Logger erreicht. Für gültiges JSON werden nur die als sensibel markierten
 * Felder maskiert (Struktur bleibt zur Fehleranalyse erhalten); für Nicht-JSON (z. B.
 * Exception-Messages, {@code application/octet-stream}-Bodies) greift ein Regex-Fallback.
 *
 * <p>This is the single source of truth for the log-path sensitive-field list. The report/LLM path
 * has its own equivalent ({@code PiiRedactor} in the {@code reporting} package, REQ-030), which is
 * intentionally left untouched by REQ-034 — only logging is changed. Both lists overlap; keep them
 * consistent when adding new sensitive fields. / {@code PiiRedactor} (Report-Pfad) bleibt bewusst
 * unverändert; beide Listen überschneiden sich.
 */
public final class LogSanitizer {

    /** Redaction marker / Maskierungs-Platzhalter. */
    public static final String MASK = "***";

    /**
     * Case-insensitive sensitive field names (BR-1) — never logged in clear text. Single source of
     * truth for the log path. / Verbotsliste: nie im Klartext loggen.
     */
    public static final Set<String> SENSITIVE_FIELDS = Set.of(
        "firstname", "lastname", "fullname", "customername", "drivername",
        "email", "phone", "mobile",
        "address", "street", "zipcode", "postalcode", "city",
        "license", "licenseplate", "registrationplate", "numberplate",
        "vin", "note", "salutation",
        "authorization", "cookie", "set-cookie");

    /**
     * Regex fallback for non-JSON payloads (exception messages, octet-stream bodies) — masks the
     * highest-risk direct identifiers. / Regex-Fallback für Nicht-JSON.
     */
    private static final List<Pattern> FALLBACK_PATTERNS = List.of(
        // e-mail addresses / E-Mail-Adressen
        Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
        // German licence plates / KFZ-Kennzeichen, e.g. "M-AB 1234E"
        Pattern.compile("\\b[A-ZÄÖÜ]{1,3}-[A-Z]{1,2}\\s?\\d{1,4}[EH]?\\b"),
        // vehicle identification number / Fahrgestellnummer (17 chars, no I/O/Q)
        Pattern.compile("\\b[A-HJ-NPR-Z0-9]{17}\\b"),
        // phone numbers / Telefonnummern (>= 9 digits, optional +, spaces, dashes, parens)
        Pattern.compile("(?<![\\dA-Za-z])\\+?\\d[\\d /()-]{7,}\\d"));

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LogSanitizer() {
    }

    /**
     * Returns the payload with all sensitive values masked. Valid JSON objects/arrays are masked
     * field-wise (structure preserved); anything else falls back to regex masking. Blank/null input
     * is returned unchanged. / Maskiert Payload; Nicht-JSON via Regex-Fallback.
     */
    public static String redact(String payload) {
        if (payload == null || payload.isBlank()) {
            return payload;
        }
        try {
            JsonNode root = MAPPER.readTree(payload);
            // readTree also accepts bare scalars ("foo" -> TextNode); only structured JSON is
            // field-redacted, everything else goes through the regex fallback.
            if (root != null && (root.isObject() || root.isArray())) {
                redactNode(root);
                return MAPPER.writeValueAsString(root);
            }
            return maskFallback(payload);
        } catch (Exception e) {
            return maskFallback(payload);
        }
    }

    /** {@link #redact(String)} followed by {@link LogPayloadUtil#truncate(String)}. */
    public static String redactAndTruncate(String payload) {
        return LogPayloadUtil.truncate(redact(payload));
    }

    /**
     * Masks an IP address so it is not logged in clear text (IP = personal data under GDPR).
     * IPv4 keeps the /24 network, IPv6 the first block. / Maskiert IP-Adressen (DSGVO).
     */
    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return ip;
        }
        int lastDot = ip.lastIndexOf('.');
        if (lastDot > 0) {
            return ip.substring(0, lastDot) + ".0/24";
        }
        int firstColon = ip.indexOf(':');
        if (firstColon >= 0) {
            return ip.substring(0, firstColon) + "::/64";
        }
        return MASK;
    }

    private static void redactNode(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            List<String> toMask = new ArrayList<>();
            for (Map.Entry<String, JsonNode> entry : obj.properties()) {
                if (SENSITIVE_FIELDS.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                    toMask.add(entry.getKey());
                } else {
                    redactNode(entry.getValue());
                }
            }
            // Overwrite after iterating to avoid concurrent modification. The value is replaced by
            // its character count — "***(12)" — so analysts still see the input length (empty vs.
            // present, plausible vs. suspicious) without the actual data (REQ-034).
            for (String field : toMask) {
                obj.set(field, maskedValue(obj.get(field)));
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                redactNode(child);
            }
        }
    }

    /**
     * Replaces a sensitive value with its character count ({@code "***(12)"}) so the input length
     * stays observable while the value is removed. Null → {@code "***(null)"}, nested
     * object/array → plain {@code "***"}. / Ersetzt den Wert durch seine Zeichenanzahl.
     */
    private static JsonNode maskedValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return TextNode.valueOf(MASK + "(null)");
        }
        if (value.isContainerNode()) {
            return TextNode.valueOf(MASK);
        }
        return TextNode.valueOf(MASK + "(" + value.asText("").length() + ")");
    }

    private static String maskFallback(String text) {
        String out = text;
        for (Pattern pattern : FALLBACK_PATTERNS) {
            // Annotate the length of the matched identifier, mirroring the JSON path.
            out = pattern.matcher(out).replaceAll(m -> MASK + "(" + m.group().length() + ")");
        }
        return out;
    }
}
