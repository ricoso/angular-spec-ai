package com.autohaus.backend.usage;

import com.autohaus.backend.config.UsageStatsProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Reads and writes the anonymous daily usage history in Azure Blob Storage (REQ-037 §12.5). /
 * Liest und schreibt die anonyme Tageshistorie im Azure Blob Storage.
 *
 * <p>Authentication reuses the proven pattern from
 * {@code reporting.LogAnalyticsClient#fetchToken()}: the container's <b>system-assigned managed
 * identity</b> ({@code IDENTITY_ENDPOINT} / {@code IDENTITY_HEADER}, header
 * {@code X-IDENTITY-HEADER}), only with {@code resource=https://storage.azure.com/}. No secret, no
 * Azure SDK — blob REST over {@code RestClient} keeps a single HTTP stack in the image (§18). /
 * Managed Identity statt Secret, kein Azure SDK.
 *
 * <p>Responses are read as raw {@code String} and parsed with a Jackson-2 mapper: under Spring
 * Boot 4 the default HTTP message converter is Jackson 3, which cannot bind to Jackson-2 types
 * ({@code Type definition error}). / Antwort als {@code String} lesen (Spring-Boot-4-Fallstrick).
 *
 * <p><b>Own local mapper — mandatory (§14.1):</b> the injectable {@code ObjectMapper} bean is a
 * bare {@code new ObjectMapper()} without registered modules ({@code config/WebConfig.java}). It
 * would fail on {@link UsageHistory#updatedAt()} ({@code Instant}) with
 * {@code InvalidDefinitionException: Java 8 date/time type not supported by default} — and only in
 * Azure, inside the nightly rollup, never locally. The global bean stays untouched so
 * {@code ReportAggregator}/{@code LogAnalyticsClient} keep their behaviour. / Eigener lokaler
 * Mapper ist zwingend; der globale Bean bleibt unverändert.
 *
 * <p>Blob schema: the calendar day is <b>exclusively the map key</b> under {@code days}; the day
 * objects carry no {@code day} field (§10.3). Unknown properties therefore fail deserialization on
 * purpose — a foreign schema is E4. / Tag ist nur der Map-Key, kein Feld „day".
 */
@Component
public class UsageBlobClient {

    private static final Logger log = LoggerFactory.getLogger(UsageBlobClient.class);

    /** Managed-identity resource of the storage data plane / Ressource der Storage-Data-Plane. */
    private static final String RESOURCE = "https://storage.azure.com/";

    /** Blob REST API version pinned per §12.5 / festgelegte Blob-REST-Version. */
    private static final String BLOB_API_VERSION = "2021-08-06";

    private static final String DEFAULT_CONTAINER = "usage-stats";
    private static final String DEFAULT_BLOB = "usage-daily.json";

    /**
     * Local mapper with {@code JavaTimeModule} — see the class javadoc; do not replace it with the
     * injected bean. / Nicht durch den injizierten Bean ersetzen.
     */
    private static final ObjectMapper BLOB_MAPPER = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build();

    private final RestClient restClient;
    private final String account;
    private final String container;
    private final String blob;

    @Autowired
    public UsageBlobClient(UsageStatsProperties properties) {
        this(properties, defaultRestClientBuilder());
    }

    /**
     * Visible for testing / für Tests: takes the {@link RestClient.Builder} instead of building it
     * internally, so {@code MockRestServiceServer.bindTo(builder)} can drive the real HTTP layer
     * (If-Match header, 412 handling, JSON round trip) without a network. Production semantics are
     * unchanged — the public constructor passes exactly the builder it used to build inline. /
     * Nur der Builder wird hereingereicht, das Verhalten bleibt identisch.
     */
    UsageBlobClient(UsageStatsProperties properties, RestClient.Builder restClientBuilder) {
        UsageStatsProperties.Storage storage = properties == null ? null : properties.storage();
        this.account = storage == null ? null : trimToNull(storage.account());
        this.container = storage == null || trimToNull(storage.container()) == null
            ? DEFAULT_CONTAINER : storage.container().trim();
        this.blob = storage == null || trimToNull(storage.blob()) == null
            ? DEFAULT_BLOB : storage.blob().trim();
        this.restClient = restClientBuilder.build();
    }

    /** The production HTTP stack with its two timeouts / der Produktions-HTTP-Stack. */
    private static RestClient.Builder defaultRestClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().requestFactory(factory);
    }

    /**
     * Whether a storage account is configured at all / ob überhaupt ein Storage Account
     * konfiguriert ist. Empty locally and in every environment without {@code USAGE_STORAGE_ACCOUNT}
     * — the page then runs without history (E2). / Leer → Seite läuft ohne Historie weiter.
     */
    public boolean isConfigured() {
        return account != null;
    }

    /**
     * Loads the history / lädt die Historie.
     *
     * <p>{@code 200} → parsed content plus {@code ETag}; {@code 404} → no history yet, an empty but
     * <b>usable</b> result with {@code etag == null} (the first write then uses
     * {@code If-None-Match: *}); unparseable content, an unknown {@code schemaVersion}, a
     * non-ISO day key or negative counters → an <b>unusable</b> result (E4): the caller must not
     * overwrite the file so it can be analysed manually. / Unbrauchbare Datei wird nicht
     * überschrieben.
     *
     * @throws BlobUnavailableException when storage is not configured or not reachable (E2) /
     *     wenn nicht konfiguriert oder nicht erreichbar
     */
    public LoadedHistory loadUsageHistory() {
        requireConfigured();
        String token = fetchToken();
        ResponseEntity<String> response;
        try {
            response = restClient.get()
                .uri(blobUrl())
                .header("Authorization", "Bearer " + token)
                .header("x-ms-version", BLOB_API_VERSION)
                .retrieve()
                // 404 is the normal first-run case, not an error / Normalfall beim Erstlauf
                .onStatus(status -> status.value() == 404, (request, res) -> { })
                .toEntity(String.class);
        } catch (RuntimeException e) {
            throw new BlobUnavailableException("Usage blob read failed: " + e.getMessage(), e);
        }

        if (response.getStatusCode().value() == 404) {
            log.debug("Usage blob {} does not exist yet — starting with an empty history", blob);
            return new LoadedHistory(UsageHistory.empty(), new TreeMap<>(), null, true);
        }

        String etag = response.getHeaders().getETag();
        return parseAndValidate(response.getBody(), etag);
    }

    /**
     * Writes the history back / schreibt die Historie zurück.
     *
     * <p>Conditional write per AC-10: {@code If-Match} with the {@code ETag} of the preceding read,
     * or {@code If-None-Match: *} when the blob does not exist yet. A lost update between two
     * replicas is signalled as {@link BlobPreconditionFailedException} so the caller can re-read,
     * merge and retry (E3). Azure answers the failed {@code If-None-Match: *} with {@code 409}
     * instead of {@code 412}, therefore both are mapped to the same exception. / Beide Statuscodes
     * werden als Schreibkonflikt behandelt.
     *
     * @param history the complete file content / vollständiger Dateiinhalt
     * @param etag ETag of the preceding read, {@code null} for the initial creation
     * @throws BlobPreconditionFailedException on a write conflict (E3) / bei Schreibkonflikt
     * @throws BlobUnavailableException when storage is not configured or not reachable (E2)
     */
    public void saveUsageHistory(UsageHistory history, String etag) {
        requireConfigured();
        String token = fetchToken();
        String json;
        try {
            json = BLOB_MAPPER.writeValueAsString(history);
        } catch (JsonProcessingException e) {
            throw new BlobUnavailableException("Usage history not serializable: " + e.getMessage(), e);
        }
        try {
            restClient.put()
                .uri(blobUrl())
                .header("Authorization", "Bearer " + token)
                .header("x-ms-version", BLOB_API_VERSION)
                .header("x-ms-blob-type", "BlockBlob")
                .header(etag == null ? "If-None-Match" : "If-Match", etag == null ? "*" : etag)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve()
                .onStatus(status -> status.value() == 412 || status.value() == 409,
                    (request, res) -> {
                        throw new BlobPreconditionFailedException(
                            "Usage blob changed in the meantime (HTTP " + res.getStatusCode().value() + ")");
                    })
                .toBodilessEntity();
        } catch (BlobPreconditionFailedException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BlobUnavailableException("Usage blob write failed: " + e.getMessage(), e);
        }
        log.debug("Usage blob {} written with {} day(s)", blob, history.days().size());
    }

    /**
     * Builds the file content from the day rows / baut den Dateiinhalt aus den Tageszeilen. Keys
     * are written as ISO dates in ascending order; the day objects themselves stay free of a
     * {@code day} field (§10.3). / Keys in ISO-Form, aufsteigend sortiert.
     *
     * @param days day rows keyed by calendar day / Tageszeilen je Kalendertag
     * @param updatedAt write timestamp, ISO-8601 in JSON / Schreibzeitpunkt
     */
    public static UsageHistory toHistory(SortedMap<LocalDate, DailyUsage> days, Instant updatedAt) {
        Map<String, DailyUsage> isoDays = new LinkedHashMap<>();
        days.forEach((day, usage) -> isoDays.put(day.toString(), usage));
        // unmodifiableMap, not Map.copyOf: the ascending key order must survive into the JSON /
        // Map.copyOf verliert die Sortierung
        return new UsageHistory(UsageHistory.CURRENT_SCHEMA_VERSION, updatedAt,
            Collections.unmodifiableMap(isoDays));
    }

    /**
     * Parses and validates the file / parst und validiert die Datei (E4). Anything unexpected makes
     * the result unusable instead of throwing — the status page must stay reachable (AC-11). /
     * Liefert im Zweifel ein unbrauchbares Ergebnis statt einer Exception.
     */
    private LoadedHistory parseAndValidate(String json, String etag) {
        if (json == null || json.isBlank()) {
            log.error("Usage blob {} is empty — treating the history as unusable (E4)", blob);
            return unusable(etag);
        }
        UsageHistory history;
        try {
            history = BLOB_MAPPER.readValue(json, UsageHistory.class);
        } catch (JsonProcessingException e) {
            log.error("Usage blob {} is not parseable — treating the history as unusable (E4): {}",
                blob, e.getOriginalMessage());
            return unusable(etag);
        }
        if (history.schemaVersion() != UsageHistory.CURRENT_SCHEMA_VERSION) {
            log.error("Usage blob {} has unsupported schemaVersion {} (expected {}) — "
                    + "treating the history as unusable (E4)",
                blob, history.schemaVersion(), UsageHistory.CURRENT_SCHEMA_VERSION);
            return unusable(etag);
        }
        if (history.days() == null) {
            log.error("Usage blob {} has no days — treating the history as unusable (E4)", blob);
            return unusable(etag);
        }

        SortedMap<LocalDate, DailyUsage> days = new TreeMap<>();
        for (Map.Entry<String, DailyUsage> entry : history.days().entrySet()) {
            LocalDate day;
            try {
                day = LocalDate.parse(entry.getKey());
            } catch (DateTimeParseException e) {
                log.error("Usage blob {} contains the non-ISO day key '{}' — "
                        + "treating the history as unusable (E4)", blob, entry.getKey());
                return unusable(etag);
            }
            DailyUsage usage = entry.getValue();
            if (!isPlausible(usage)) {
                log.error("Usage blob {} contains implausible counters for day {} — "
                        + "treating the history as unusable (E4)", blob, entry.getKey());
                return unusable(etag);
            }
            days.put(day, usage);
        }
        return new LoadedHistory(history, days, etag, true);
    }

    /** All counters present and non-negative / alle Zähler vorhanden und nicht negativ. */
    private static boolean isPlausible(DailyUsage usage) {
        if (usage == null || usage.visits() == null) {
            return false;
        }
        DeviceBreakdown visits = usage.visits();
        return visits.mobile() >= 0 && visits.tablet() >= 0 && visits.desktop() >= 0
            && visits.unknown() >= 0
            && usage.searchRequests() >= 0 && usage.searchRequestsOk() >= 0
            && usage.bookingRequests() >= 0 && usage.bookingRequestsOk() >= 0
            && usage.sessionsWithSearch() >= 0 && usage.sessionsWithBooking() >= 0;
    }

    private static LoadedHistory unusable(String etag) {
        return new LoadedHistory(UsageHistory.empty(), new TreeMap<>(), etag, false);
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new BlobUnavailableException(
                "Usage storage account not configured (USAGE_STORAGE_ACCOUNT unset). "
                    + "This is expected locally — the page runs without history.");
        }
    }

    private String blobUrl() {
        return "https://" + account + ".blob.core.windows.net/" + container + "/" + blob;
    }

    /**
     * Managed-identity token for the storage data plane / Managed-Identity-Token für die
     * Storage-Data-Plane — same mechanism as {@code LogAnalyticsClient.fetchToken()}, other
     * resource. / Gleiches Verfahren, andere Ressource.
     */
    private String fetchToken() {
        String endpoint = identityEndpoint();
        String header = identityHeader();
        if (endpoint == null || endpoint.isBlank() || header == null || header.isBlank()) {
            throw new BlobUnavailableException(
                "Managed identity endpoint not available (IDENTITY_ENDPOINT/IDENTITY_HEADER unset). "
                    + "This is expected outside Azure Container Apps.");
        }
        try {
            String json = restClient.get()
                .uri(endpoint + "?resource=" + RESOURCE + "&api-version=2019-08-01")
                .header("X-IDENTITY-HEADER", header)
                .retrieve()
                .body(String.class);
            String token = BLOB_MAPPER.readTree(json == null ? "{}" : json).path("access_token").asText("");
            if (token.isBlank()) {
                throw new BlobUnavailableException("Managed identity returned no access_token");
            }
            return token;
        } catch (BlobUnavailableException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new BlobUnavailableException("Managed identity token not parseable: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new BlobUnavailableException("Failed to obtain managed identity token: " + e.getMessage(), e);
        }
    }

    /**
     * The managed-identity endpoint injected by Azure Container Apps / der von Azure Container
     * Apps gesetzte Managed-Identity-Endpunkt. Overridable for tests only — production always
     * reads {@code IDENTITY_ENDPOINT}. / Nur für Tests überschreibbar.
     */
    protected String identityEndpoint() {
        return System.getenv("IDENTITY_ENDPOINT");
    }

    /**
     * The matching shared secret of that endpoint / das zugehörige Geheimnis des Endpunkts
     * ({@code IDENTITY_HEADER}). Overridable for tests only. / Nur für Tests überschreibbar.
     */
    protected String identityHeader() {
        return System.getenv("IDENTITY_HEADER");
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Result of a read / Ergebnis eines Lesevorgangs.
     *
     * @param history the raw file content (ISO keys) / roher Dateiinhalt mit ISO-Keys
     * @param days the same day rows keyed by {@link LocalDate} — parsed exactly once, here /
     *     dieselben Tageszeilen mit {@code LocalDate}-Key
     * @param etag ETag for the conditional write, {@code null} when the blob does not exist yet
     * @param usable {@code false} → E4: content unusable, the file must not be overwritten /
     *     Datei darf nicht überschrieben werden
     */
    public record LoadedHistory(UsageHistory history, SortedMap<LocalDate, DailyUsage> days,
                                String etag, boolean usable) {
    }

    /** Thrown when the blob cannot be reached or is not configured (E2) / Blob nicht erreichbar. */
    public static class BlobUnavailableException extends RuntimeException {
        public BlobUnavailableException(String message) {
            super(message);
        }

        public BlobUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Thrown on a conditional-write conflict (HTTP 412/409, E3) / bei Schreibkonflikt. The caller
     * re-reads, merges and writes again — at most 3 attempts (AC-10). Since the rollup replaces
     * absolute day values instead of incrementing them (BR-8), every outcome is identical. /
     * Ergebnis ist dank BR-8 identisch.
     */
    public static class BlobPreconditionFailedException extends BlobUnavailableException {
        public BlobPreconditionFailedException(String message) {
            super(message);
        }
    }
}
