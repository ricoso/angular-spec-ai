package com.autohaus.backend.reporting;

import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.snaps.OperationIdMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the deterministic part of the daily report (REQ-030): the per-category status
 * overview (snaps + appointments), the brand/location error distribution and the error list.
 * Brands come straight from the logs; branch IDs are resolved to location names via
 * {@link SetupService}; operation IDs are resolved to plain-text service names via
 * {@link OperationIdMapper}; request/response payloads are PII-masked via {@link PiiRedactor}
 * before they enter an {@link ErrorEntry}. No LLM involved here — the summary is attached later.
 */
@Component
public class ReportAggregator {

    private static final Logger log = LoggerFactory.getLogger(ReportAggregator.class);

    private static final Pattern TRACE_ID = Pattern.compile("- ([0-9a-fA-F-]{36}):");
    private static final Pattern ENDPOINT = Pattern.compile("(POST|GET|PUT|DELETE) (/api/planning/[a-zA-Z]+)");
    /** SoftNet operation IDs are 24-char hex ObjectIds — used to catch any that survive name resolution. */
    private static final Pattern OPERATION_ID = Pattern.compile("^[a-f0-9]{24}$");
    /** Shown in place of a raw operation ID when no plain-text service name can be resolved. */
    private static final String UNRESOLVED_SERVICE = "Service (unbekannt)";

    private static final String BOOKING_STATS_KQL = """
        ContainerAppConsoleLogs_CL
        | where TimeGenerated > ago(24h)
        | where Log_s has 'INFO  HTTP' and Log_s has '"method":"POST"' and Log_s has '/api/v1/appointments'
        | extend Status = toint(extract('"statusCode":([0-9]+)', 1, Log_s))
        | where Status > 0
        | summarize started=count(), succeeded=countif(Status < 400), failed=countif(Status >= 400)
        """;

    private static final String SNAPS_STATS_KQL = """
        ContainerAppConsoleLogs_CL
        | where TimeGenerated > ago(24h)
        | where Log_s has 'INFO  HTTP' and Log_s has '"method":"POST"' and Log_s has '/api/v1/snaps'
        | extend Status = toint(extract('"statusCode":([0-9]+)', 1, Log_s))
        | where Status > 0
        | summarize started=count(), succeeded=countif(Status < 400), failed=countif(Status >= 400)
        """;

    // High safety cap rather than a small page size, so the error list matches the tile
    // counts (a small "take" would make "Fehler (N)" contradict the failure totals).
    private static final String ERRORS_KQL = """
        ContainerAppConsoleLogs_CL
        | where TimeGenerated > ago(24h)
        | where Log_s has 'SOFTNET_ERROR' and Log_s has 'requestBody'
        | project TimeGenerated, Log_s
        | order by TimeGenerated desc
        | take 1000
        """;

    private final LogAnalyticsClient logClient;
    private final SetupService setupService;
    private final OperationIdMapper operationIdMapper;
    private final ObjectMapper objectMapper;
    private final PiiRedactor piiRedactor;

    public ReportAggregator(LogAnalyticsClient logClient, SetupService setupService,
                            OperationIdMapper operationIdMapper, ObjectMapper objectMapper,
                            PiiRedactor piiRedactor) {
        this.logClient = logClient;
        this.setupService = setupService;
        this.operationIdMapper = operationIdMapper;
        this.objectMapper = objectMapper;
        this.piiRedactor = piiRedactor;
    }

    /**
     * Queries Log Analytics and builds the model (summary left null — added by the caller).
     * Propagates {@link LogAnalyticsClient.LogAnalyticsUnavailableException} so the scheduler
     * can produce a graceful fallback report.
     */
    public ReportModel aggregate() {
        BookingStats snaps = queryStats(SNAPS_STATS_KQL);
        BookingStats appointments = queryStats(BOOKING_STATS_KQL);
        List<ErrorEntry> errors = queryErrors();
        Map<String, Long> errorsByBrand = new LinkedHashMap<>();
        Map<String, Long> errorsByLocation = new LinkedHashMap<>();
        for (ErrorEntry e : errors) {
            addCount(errorsByBrand, e.brand());
            addCount(errorsByLocation, e.location());
        }
        return new ReportModel(Instant.now(), snaps, appointments,
            sortByCountDesc(errorsByBrand), sortByCountDesc(errorsByLocation), errors, null);
    }

    private BookingStats queryStats(String kql) {
        List<List<String>> rows = LogAnalyticsClient.rows(logClient.query(kql));
        if (rows.isEmpty() || rows.get(0).size() < 3) {
            return new BookingStats(0, 0, 0);
        }
        List<String> r = rows.get(0);
        return new BookingStats(parseLong(r.get(0)), parseLong(r.get(1)), parseLong(r.get(2)));
    }

    /** Adds one to the count for {@code key} in the given map; blank/null keys are skipped. */
    private static void addCount(Map<String, Long> counts, String key) {
        if (key != null && !key.isBlank()) {
            counts.put(key, counts.getOrDefault(key, 0L) + 1L);
        }
    }

    /**
     * Returns the counts as an insertion-ordered map, sorted by descending count, so the HTML
     * top-list for brands / locations keeps that order.
     */
    private static Map<String, Long> sortByCountDesc(Map<String, Long> counts) {
        List<Map.Entry<String, Long>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        Map<String, Long> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, Long> en : entries) {
            sorted.put(en.getKey(), en.getValue());
        }
        return sorted;
    }

    private List<ErrorEntry> queryErrors() {
        List<List<String>> rows = LogAnalyticsClient.rows(logClient.query(ERRORS_KQL));
        List<ErrorEntry> entries = new ArrayList<>();
        for (List<String> row : rows) {
            if (row.size() < 2) {
                continue;
            }
            try {
                ErrorEntry entry = parseLine(row.get(0), row.get(1));
                if (entry != null) {
                    entries.add(entry);
                }
            } catch (RuntimeException e) {
                log.debug("Skipping unparseable error line: {}", e.getMessage());
            }
        }
        return entries;
    }

    private ErrorEntry parseLine(String timeGenerated, String logLine) {
        String traceId = firstGroup(TRACE_ID, logLine);
        String endpoint = secondGroup(ENDPOINT, logLine);

        String requestBody = between(logLine, "requestBody=", " | responseBody=");
        String responseBody = after(logLine, "responseBody=");

        String brand = "";
        String branchId = "";
        String groupId = "";
        List<String> operationIds = new ArrayList<>();
        if (!requestBody.isBlank()) {
            try {
                JsonNode req = objectMapper.readTree(requestBody);
                brand = req.path("brand").asText("");
                branchId = req.path("preferredBranch").asText("");
                groupId = req.path("groupid").asText("");
                JsonNode ops = req.path("operations");
                if (ops.isArray()) {
                    for (JsonNode op : ops) {
                        operationIds.add(op.asText());
                    }
                }
            } catch (JsonProcessingException e) {
                log.debug("Could not parse requestBody JSON: {}", e.getMessage());
            }
        }

        String location = resolveLocation(branchId);
        List<String> operations = resolveOperations(groupId, operationIds);
        String errorType = classifyError(responseBody);

        // Mask PII for display only — the metadata above (brand / branch / operations /
        // error type) was already extracted from the original, unmasked payloads.
        String safeRequestBody = piiRedactor.redact(requestBody);
        String safeResponseBody = piiRedactor.redact(responseBody);

        return new ErrorEntry(
            parseInstant(timeGenerated),
            traceId,
            brand,
            location,
            operations,
            errorType,
            endpoint,
            safeRequestBody,
            safeResponseBody
        );
    }

    private String resolveLocation(String branchId) {
        if (branchId == null || branchId.isBlank()) {
            return "Unbekannt";
        }
        try {
            return setupService.findByBranchId(branchId).name();
        } catch (RuntimeException e) {
            return "Unbekannt (" + branchId + ")";
        }
    }

    private List<String> resolveOperations(String groupId, List<String> operationIds) {
        if (operationIds.isEmpty()) {
            return List.of();
        }
        List<String> resolved;
        try {
            List<String> names = operationIdMapper.resolveOperationNames(groupId, operationIds);
            resolved = names.isEmpty() ? operationIds : names;
        } catch (RuntimeException e) {
            log.debug("Operation name resolution failed for group {}: {}", groupId, e.getMessage());
            resolved = operationIds;
        }
        return sanitizeServiceNames(resolved);
    }

    /**
     * Safety net: the report must never surface a raw SoftNet operation ID. Any entry that still
     * looks like a 24-hex ObjectId — because the groupid was missing, the ID was absent from the
     * group, or the SoftNet lookup failed — is replaced with a neutral placeholder. The placeholder
     * is collapsed to a single entry (the opaque IDs carry no distinguishing meaning for a reader;
     * the raw request payload below the card still holds them for tracing).
     */
    private static List<String> sanitizeServiceNames(List<String> operations) {
        List<String> out = new ArrayList<>(operations.size());
        boolean placeholderAdded = false;
        for (String op : operations) {
            if (op != null && OPERATION_ID.matcher(op).matches()) {
                if (!placeholderAdded) {
                    out.add(UNRESOLVED_SERVICE);
                    placeholderAdded = true;
                }
            } else {
                out.add(op);
            }
        }
        return out;
    }

    private String classifyError(String responseBody) {
        String lower = responseBody == null ? "" : responseBody.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("no match")) {
            return "No match (Operation/Marke)";
        }
        if (lower.contains("save to database validation")) {
            return "Validierungsfehler (DB)";
        }
        if (lower.contains("timed out") || lower.contains("did not respond")) {
            return "Timeout";
        }
        if (lower.contains("internalservererror")) {
            return "SoftNet InternalServerError";
        }
        return "SoftNet-Fehler";
    }

    // ---- small parsing helpers ----

    private static String between(String s, String start, String end) {
        int i = s.indexOf(start);
        if (i < 0) {
            return "";
        }
        int from = i + start.length();
        int j = s.indexOf(end, from);
        return j < 0 ? s.substring(from).trim() : s.substring(from, j).trim();
    }

    private static String after(String s, String start) {
        int i = s.indexOf(start);
        if (i < 0) {
            return "";
        }
        int from = i + start.length();
        int j = s.indexOf(" | ", from);
        return j < 0 ? s.substring(from).trim() : s.substring(from, j).trim();
    }

    private static String firstGroup(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : "";
    }

    private static String secondGroup(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(2) : "";
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Instant parseInstant(String s) {
        try {
            return Instant.parse(s.trim());
        } catch (RuntimeException e) {
            return Instant.now();
        }
    }
}
