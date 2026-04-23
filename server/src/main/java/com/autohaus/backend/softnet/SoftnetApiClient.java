package com.autohaus.backend.softnet;

import com.autohaus.backend.config.SoftnetProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class SoftnetApiClient {

    private static final Logger log = LoggerFactory.getLogger(SoftnetApiClient.class);

    private final SoftnetAuthService authService;
    private final SoftnetProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public SoftnetApiClient(SoftnetAuthService authService, SoftnetProperties properties, ObjectMapper objectMapper) {
        this.authService = authService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofMillis(properties.timeout()));
        factory.setReadTimeout(java.time.Duration.ofMillis(properties.timeout()));
        this.restClient = RestClient.builder()
            .requestFactory(factory)
            .build();
    }

    // === Groups (NO group header) ===
    public List<JsonNode> getGroups() {
        return getList("/api/planning/groups", false);
    }

    public JsonNode getGroup(String groupId) {
        return get("/api/planning/groups/" + groupId, false);
    }

    // === Branches (NO group header) ===
    public List<JsonNode> getBranches() {
        return getList("/api/planning/branches", false);
    }

    public JsonNode getBranch(String branchId) {
        return get("/api/planning/branches/" + branchId, false);
    }

    // === Brands (WITH group header) ===
    public List<JsonNode> getBrands() {
        return getList("/api/planning/brands", true);
    }

    // === JobTypes (WITH group header) ===
    public List<JsonNode> getJobTypes() {
        return getList("/api/planning/jobtypes", true);
    }

    // === Operations (WITH group header) ===
    public List<JsonNode> getOperations() {
        return getList("/api/planning/operations", true);
    }

    // === Snaps (WITH group header) ===
    public List<JsonNode> getSnaps(Object snapRequest) {
        return postList("/api/planning/snaps", snapRequest, true);
    }

    // === Mobility Alternatives (WITH group header) ===
    public List<JsonNode> getMobilityAlternatives() {
        return getList("/api/planning/mobilityalternatives", true);
    }

    // === Rent Categories (WITH group header) ===
    public List<JsonNode> getRentCategories() {
        return getList("/api/planning/rentcategories", true);
    }

    // === Vault Items / Customers (WITH group header) ===
    public JsonNode createVaultItem(Map<String, Object> customer) {
        return post("/api/planning/vaultitems", customer, true);
    }

    public List<JsonNode> searchVaultItems(Map<String, String> query) {
        StringBuilder sb = new StringBuilder("/api/planning/vaultitems");
        if (!query.isEmpty()) {
            sb.append("?");
            query.forEach((k, v) -> sb.append(k).append("=").append(v).append("&"));
            sb.setLength(sb.length() - 1);
        }
        return getList(sb.toString(), true);
    }

    // === Cars (WITH group header) ===
    public JsonNode createCar(Map<String, Object> car) {
        return post("/api/planning/cars", car, true);
    }

    // === Appointments (WITH group header) ===
    public JsonNode createAppointment(Map<String, Object> appointment) {
        return post("/api/planning/appointments", appointment, true);
    }

    public JsonNode getAppointment(String appointmentId) {
        return get("/api/planning/appointments/" + appointmentId, true);
    }

    public JsonNode cancelAppointment(String appointmentId) {
        return put("/api/planning/appointments/" + appointmentId + "/cancel", true);
    }

    // === Health ping ===
    public PingResult ping() {
        long start = System.currentTimeMillis();
        try {
            getGroups();
            return new PingResult(true, System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new PingResult(false, System.currentTimeMillis() - start);
        }
    }

    public record PingResult(boolean healthy, long responseTime) {}

    // === Internal HTTP methods ===
    private JsonNode get(String endpoint, boolean useGroupHeader) {
        String body = executeRequest("GET", endpoint, null, useGroupHeader);
        return parseJson(body);
    }

    private List<JsonNode> getList(String endpoint, boolean useGroupHeader) {
        String body = executeRequest("GET", endpoint, null, useGroupHeader);
        return parseJsonList(body);
    }

    private JsonNode post(String endpoint, Object requestBody, boolean useGroupHeader) {
        String body = executeRequest("POST", endpoint, requestBody, useGroupHeader);
        return parseJson(body);
    }

    private List<JsonNode> postList(String endpoint, Object requestBody, boolean useGroupHeader) {
        String body = executeRequest("POST", endpoint, requestBody, useGroupHeader);
        return parseJsonList(body);
    }

    private JsonNode put(String endpoint, boolean useGroupHeader) {
        String body = executeRequest("PUT", endpoint, null, useGroupHeader);
        return parseJson(body);
    }

    private String executeRequest(String method, String endpoint, Object requestBody, boolean useGroupHeader) {
        long startTime = System.currentTimeMillis();
        String token = authService.getAccessToken();
        String url = (properties.baseUrl() != null ? properties.baseUrl() : "") + endpoint;

        try {
            String result = switch (method) {
                case "GET" -> {
                    var spec = restClient.get().uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                    if (useGroupHeader && properties.groupId() != null) {
                        spec = spec.header("x-nrg-group", properties.groupId());
                    }
                    yield spec.retrieve().body(String.class);
                }
                case "POST" -> {
                    var spec = restClient.post().uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON);
                    if (useGroupHeader && properties.groupId() != null) {
                        spec = spec.header("x-nrg-group", properties.groupId());
                    }
                    if (requestBody != null) {
                        String jsonBody = objectMapper.writeValueAsString(requestBody);
                        yield spec.body(jsonBody).retrieve().body(String.class);
                    }
                    yield spec.retrieve().body(String.class);
                }
                case "PUT" -> {
                    var spec = restClient.put().uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                    if (useGroupHeader && properties.groupId() != null) {
                        spec = spec.header("x-nrg-group", properties.groupId());
                    }
                    yield spec.retrieve().body(String.class);
                }
                case "DELETE" -> {
                    var spec = restClient.delete().uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                    if (useGroupHeader && properties.groupId() != null) {
                        spec = spec.header("x-nrg-group", properties.groupId());
                    }
                    yield spec.retrieve().body(String.class);
                }
                default -> throw new IllegalArgumentException("Unknown method: " + method);
            };

            log.debug("SoftNet {} {} - {}ms", method, endpoint, System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            log.error("SoftNet {} {} failed after {}ms: {}", method, endpoint, System.currentTimeMillis() - startTime, e.getMessage());
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }

    private JsonNode parseJson(String body) {
        try {
            return body != null ? objectMapper.readTree(body) : objectMapper.createObjectNode();
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private List<JsonNode> parseJsonList(String body) {
        try {
            if (body != null) {
                JsonNode node = objectMapper.readTree(body);
                if (node.isArray()) {
                    return objectMapper.convertValue(node, new TypeReference<List<JsonNode>>() {});
                }
            }
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}
