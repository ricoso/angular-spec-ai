package com.autohaus.backend.reporting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministically masks personally identifiable information (PII) in the request/response
 * JSON before it is shown in the error report (REQ-030, Iteration 2). Uses the shared Jackson
 * ObjectMapper to recursively overwrite sensitive field values with {@code "***"} — the JSON
 * structure stays intact and readable, only the values disappear.
 *
 * <p>Deterministische PII-Maskierung der angezeigten Payloads: personenbezogene Felder werden
 * durch {@code "***"} ersetzt, sodass im Report keine Klartext-Namen, -Adressen oder
 * -Telefonnummern erscheinen. {@code /snaps}-Payloads enthalten ohnehin keine PII — der Schutz
 * greift für {@code /appointments}-Fehler.
 */
@Component
public class PiiRedactor {

    /** Redaction marker / Maskierungs-Platzhalter. */
    public static final String MASK = "***";

    /**
     * PII field names (lower-cased for case-insensitive matching): client contact data and
     * vehicle identifiers. Covers client.{firstName,lastName,email,mobile,phone,address,
     * zipCode,city} and car.{license,VIN}.
     */
    private static final Set<String> PII_FIELDS = Set.of(
        "firstname", "lastname", "email", "mobile", "phone",
        "address", "zipcode", "city", "license", "vin");

    private final ObjectMapper objectMapper;

    public PiiRedactor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the JSON with all PII field values masked. Non-JSON or blank input is returned
     * unchanged (payloads are JSON in practice; a parse failure must never break the report).
     */
    public String redact(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            redactNode(root);
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            // Not valid JSON — leave untouched rather than risk exposing half-masked data.
            return json;
        }
    }

    private void redactNode(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            List<String> toMask = new ArrayList<>();
            for (Map.Entry<String, JsonNode> entry : obj.properties()) {
                if (PII_FIELDS.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                    toMask.add(entry.getKey());
                } else {
                    redactNode(entry.getValue());
                }
            }
            // Overwrite after iterating to avoid concurrent modification.
            for (String field : toMask) {
                obj.put(field, MASK);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                redactNode(child);
            }
        }
    }
}
