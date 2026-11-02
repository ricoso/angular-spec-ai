package com.autohaus.backend.reporting;

import com.autohaus.backend.config.ErrorReportingProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Queries the app's own logs in Azure Log Analytics (REQ-030).
 *
 * <p>Authentication uses the container's <b>system-assigned managed identity</b> — no
 * secret, no interactive login. In Azure Container Apps the identity token endpoint is
 * exposed via the {@code IDENTITY_ENDPOINT} / {@code IDENTITY_HEADER} environment
 * variables; we exchange them for a bearer token scoped to {@code api.loganalytics.io}
 * and run the KQL query against the workspace.
 *
 * <p>Responses are read as raw {@code String} and parsed with the Jackson-2 ObjectMapper:
 * under Spring Boot 4 the default HTTP message converter is Jackson 3, which cannot bind
 * directly to a Jackson-2 {@code JsonNode} ({@code Type definition error}).
 */
@Component
public class LogAnalyticsClient {

    private static final Logger log = LoggerFactory.getLogger(LogAnalyticsClient.class);
    private static final String RESOURCE = "https://api.loganalytics.io";
    private static final String QUERY_BASE = "https://api.loganalytics.io/v1/workspaces/";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String workspaceId;

    public LogAnalyticsClient(ErrorReportingProperties properties, ObjectMapper objectMapper) {
        this.workspaceId = properties.workspaceId();
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * Runs a KQL query against the workspace and returns the raw Log Analytics response
     * (tables / columns / rows). Callers parse the rows.
     *
     * @throws LogAnalyticsUnavailableException when the managed identity endpoint is not
     *         present (e.g. running locally) or the query fails — callers fall back gracefully.
     */
    public JsonNode query(String kql) {
        String token = fetchToken();
        String url = QUERY_BASE + workspaceId + "/query";
        log.debug("Querying Log Analytics workspace {} via managed identity", workspaceId);
        try {
            String json = restClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("query", kql))
                .retrieve()
                .body(String.class);
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (JsonProcessingException e) {
            throw new LogAnalyticsUnavailableException("Log Analytics response not parseable: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new LogAnalyticsUnavailableException("Log Analytics query failed: " + e.getMessage(), e);
        }
    }

    private String fetchToken() {
        String endpoint = System.getenv("IDENTITY_ENDPOINT");
        String header = System.getenv("IDENTITY_HEADER");
        if (endpoint == null || endpoint.isBlank() || header == null || header.isBlank()) {
            throw new LogAnalyticsUnavailableException(
                "Managed identity endpoint not available (IDENTITY_ENDPOINT/IDENTITY_HEADER unset). "
                    + "This is expected outside Azure Container Apps.");
        }
        try {
            String json = restClient.get()
                .uri(endpoint + "?resource=" + RESOURCE + "&api-version=2019-08-01")
                .header("X-IDENTITY-HEADER", header)
                .retrieve()
                .body(String.class);
            String token = objectMapper.readTree(json == null ? "{}" : json).path("access_token").asText("");
            if (token.isBlank()) {
                throw new LogAnalyticsUnavailableException("Managed identity returned no access_token");
            }
            return token;
        } catch (LogAnalyticsUnavailableException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new LogAnalyticsUnavailableException("Managed identity token not parseable: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new LogAnalyticsUnavailableException("Failed to obtain managed identity token: " + e.getMessage(), e);
        }
    }

    /** Extracts the first table's rows as a list of string-lists (column order preserved). */
    public static List<List<String>> rows(JsonNode response) {
        JsonNode rows = response.path("tables").path(0).path("rows");
        if (!rows.isArray()) {
            return List.of();
        }
        java.util.List<java.util.List<String>> out = new java.util.ArrayList<>();
        for (JsonNode row : rows) {
            java.util.List<String> cells = new java.util.ArrayList<>();
            for (JsonNode cell : row) {
                cells.add(cell.isNull() ? "" : cell.asText());
            }
            out.add(cells);
        }
        return out;
    }

    /** Thrown when Log Analytics cannot be reached — the report falls back gracefully. */
    public static class LogAnalyticsUnavailableException extends RuntimeException {
        public LogAnalyticsUnavailableException(String message) {
            super(message);
        }

        public LogAnalyticsUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
