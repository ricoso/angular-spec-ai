package com.autohaus.backend.softnet;

import com.autohaus.backend.common.logging.LogPayloadUtil;
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
import org.springframework.web.client.RestClientResponseException;

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
        return getList("/api/planning/groups", null);
    }

    public JsonNode getGroup(String groupId) {
        return get("/api/planning/groups/" + groupId, null);
    }

    // === Branches (NO group header) ===
    public List<JsonNode> getBranches() {
        return getList("/api/planning/branches", null);
    }

    public JsonNode getBranch(String branchId) {
        return get("/api/planning/branches/" + branchId, null);
    }

    // === Brands (WITH group header) ===
    public List<JsonNode> getBrands(String groupId) {
        return getList("/api/planning/brands", groupId);
    }

    // === JobTypes (WITH group header) ===
    public List<JsonNode> getJobTypes(String groupId) {
        return getList("/api/planning/jobtypes", groupId);
    }

    // === Operations (WITH group header) ===
    public List<JsonNode> getOperations(String groupId) {
        return getList("/api/planning/operations", groupId);
    }

    // === Snaps (WITH group header) ===
    public List<JsonNode> getSnaps(Object snapRequest, String groupId) {
        return postList("/api/planning/snaps", snapRequest, groupId);
    }

    // === Mobility Alternatives (WITH group header) ===
    public List<JsonNode> getMobilityAlternatives(String groupId) {
        return getList("/api/planning/mobilityalternatives", groupId);
    }

    // === Rent Categories (WITH group header) ===
    public List<JsonNode> getRentCategories(String groupId) {
        return getList("/api/planning/rentcategories", groupId);
    }

    // === Vault Items / Customers (WITH group header) ===
    public JsonNode createVaultItem(Map<String, Object> customer, String groupId) {
        return post("/api/planning/vaultitems", customer, groupId);
    }

    public List<JsonNode> searchVaultItems(Map<String, String> query, String groupId) {
        StringBuilder sb = new StringBuilder("/api/planning/vaultitems");
        if (!query.isEmpty()) {
            sb.append("?");
            query.forEach((k, v) -> sb.append(k).append("=").append(v).append("&"));
            sb.setLength(sb.length() - 1);
        }
        return getList(sb.toString(), groupId);
    }

    // === Cars (WITH group header) ===
    public JsonNode createCar(Map<String, Object> car, String groupId) {
        return post("/api/planning/cars", car, groupId);
    }

    // === Appointments (WITH group header) ===
    public JsonNode createAppointment(Map<String, Object> appointment, String groupId) {
        return post("/api/planning/appointments", appointment, groupId);
    }

    public JsonNode getAppointment(String appointmentId, String groupId) {
        return get("/api/planning/appointments/" + appointmentId, groupId);
    }

    public JsonNode cancelAppointment(String appointmentId, String groupId) {
        return put("/api/planning/appointments/" + appointmentId + "/cancel", groupId);
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
    private JsonNode get(String endpoint, String groupId) {
        String body = executeRequest("GET", endpoint, null, groupId);
        return parseJson(body);
    }

    public List<JsonNode> getList(String endpoint, String groupId) {
        String body = executeRequest("GET", endpoint, null, groupId);
        return parseJsonList(body);
    }

    private JsonNode post(String endpoint, Object requestBody, String groupId) {
        String body = executeRequest("POST", endpoint, requestBody, groupId);
        return parseJson(body);
    }

    private List<JsonNode> postList(String endpoint, Object requestBody, String groupId) {
        String body = executeRequest("POST", endpoint, requestBody, groupId);
        return parseJsonList(body);
    }

    private JsonNode put(String endpoint, String groupId) {
        String body = executeRequest("PUT", endpoint, null, groupId);
        return parseJson(body);
    }

    private String executeRequest(String method, String endpoint, Object requestBody, String groupId) {
        long startTime = System.currentTimeMillis();
        String url = (properties.baseUrl() != null ? properties.baseUrl() : "") + endpoint;
        boolean useGroupHeader = groupId != null && !groupId.isBlank();
        String jsonBody = null;

        try {
            if (requestBody != null) {
                jsonBody = objectMapper.writeValueAsString(requestBody);
            }
            String token = authService.getAccessToken();
            String result = switch (method) {
                case "GET" -> {
                    var spec = restClient.get().uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                    if (useGroupHeader) {
                        spec = spec.header("x-nrg-group", groupId);
                    }
                    yield spec.retrieve().body(String.class);
                }
                case "POST" -> {
                    var spec = restClient.post().uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON);
                    if (useGroupHeader) {
                        spec = spec.header("x-nrg-group", groupId);
                    }
                    if (jsonBody != null) {
                        yield spec.body(jsonBody).retrieve().body(String.class);
                    }
                    yield spec.retrieve().body(String.class);
                }
                case "PUT" -> {
                    var spec = restClient.put().uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                    if (useGroupHeader) {
                        spec = spec.header("x-nrg-group", groupId);
                    }
                    yield spec.retrieve().body(String.class);
                }
                case "DELETE" -> {
                    var spec = restClient.delete().uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                    if (useGroupHeader) {
                        spec = spec.header("x-nrg-group", groupId);
                    }
                    yield spec.retrieve().body(String.class);
                }
                default -> throw new IllegalArgumentException("Unknown method: " + method);
            };

            log.debug("SoftNet {} {} - {}ms", method, endpoint, System.currentTimeMillis() - startTime);
            return result;
        } catch (Exception e) {
            String responseBody = e instanceof RestClientResponseException rcre
                ? rcre.getResponseBodyAsString() : "";
            log.error("SOFTNET_ERROR {} {} failed after {}ms: {} | requestBody={} | responseBody={}",
                method, endpoint, System.currentTimeMillis() - startTime, e.getMessage(),
                LogPayloadUtil.truncate(jsonBody), LogPayloadUtil.truncate(responseBody));
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
