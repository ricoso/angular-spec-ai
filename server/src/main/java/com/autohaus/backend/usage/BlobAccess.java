package com.autohaus.backend.usage;

import com.autohaus.backend.config.UsageStatsProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Managed-identity blob access with ETag for the REQ-038 blobs (§12.10, §14.1) / Blob-Zugriff mit
 * Managed Identity und ETag für die REQ-038-Dateien ({@code usage-insights.json},
 * {@code capacity-daily.json}).
 *
 * <p>Same mechanism as {@link UsageBlobClient} — system-assigned managed identity
 * ({@code IDENTITY_ENDPOINT}/{@code IDENTITY_HEADER}, {@code resource=https://storage.azure.com/}),
 * blob REST over {@code RestClient}, body read as raw {@code String} (Spring Boot 4 / Jackson 3
 * pitfall) — but <b>schema-agnostic</b>: it moves JSON text and ETags, parsing stays with the
 * callers. {@link UsageBlobClient} itself stays <b>unchanged</b> (BR-18). Not a Spring bean: each
 * blob client owns one instance for its blob name. / Schema-agnostisch; {@code UsageBlobClient}
 * bleibt unverändert.
 *
 * <p>Exceptions are the ones of {@link UsageBlobClient} so every caller handles one vocabulary:
 * {@link UsageBlobClient.BlobUnavailableException} (not configured, network, token, 403 — E4) and
 * {@link UsageBlobClient.BlobPreconditionFailedException} (412/409 — E5). / Dieselben Exceptions
 * wie der REQ-037-Client.
 */
public class BlobAccess {

    private static final Logger log = LoggerFactory.getLogger(BlobAccess.class);

    private static final String RESOURCE = "https://storage.azure.com/";
    private static final String BLOB_API_VERSION = "2021-08-06";
    private static final String DEFAULT_CONTAINER = "usage-stats";

    /** Token parsing only — never used for the payload / nur für das Token. */
    private static final ObjectMapper TOKEN_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final String account;
    private final String container;
    private final String blob;

    /**
     * Production wiring / Produktions-Verdrahtung.
     *
     * @param properties REQ-037 usage properties (account and container are shared, §3.1)
     * @param blob the blob name inside the container / Blob-Name im Container
     */
    public BlobAccess(UsageStatsProperties properties, String blob) {
        this(properties, blob, defaultRestClientBuilder());
    }

    /**
     * Visible for testing / für Tests: {@code MockRestServiceServer.bindTo(builder)} drives the
     * real HTTP layer (If-Match, 412, 404) without a network. / Nur der Builder wird
     * hereingereicht.
     */
    public BlobAccess(UsageStatsProperties properties, String blob, RestClient.Builder builder) {
        UsageStatsProperties.Storage storage = properties == null ? null : properties.storage();
        this.account = storage == null ? null : trimToNull(storage.account());
        this.container = storage == null || trimToNull(storage.container()) == null
            ? DEFAULT_CONTAINER : storage.container().trim();
        this.blob = trimToNull(blob);
        this.restClient = builder.build();
    }

    private static RestClient.Builder defaultRestClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().requestFactory(factory);
    }

    /**
     * Whether a storage account and a blob name are configured / ob Account und Blob-Name gesetzt
     * sind. Empty locally — without it no REQ-038 job persists and no scan runs (E4, AC-32). /
     * Lokal leer.
     */
    public boolean isConfigured() {
        return account != null && blob != null;
    }

    /** The blob name, for log lines only / Blob-Name, nur für Log-Zeilen. */
    public String blobName() {
        return blob;
    }

    /**
     * Reads the blob / liest den Blob.
     *
     * @return {@code 200} → body plus ETag; {@code 404} → {@link Loaded#missing()} (first run,
     *     §5.2) / 404 ist der Normalfall beim Erstlauf
     * @throws UsageBlobClient.BlobUnavailableException not configured, network, token or 403 (E4)
     */
    public Loaded get() {
        requireConfigured();
        String token = fetchToken();
        ResponseEntity<String> response;
        try {
            response = restClient.get()
                .uri(blobUrl())
                .header("Authorization", "Bearer " + token)
                .header("x-ms-version", BLOB_API_VERSION)
                .retrieve()
                .onStatus(status -> status.value() == 404, (request, res) -> { })
                .toEntity(String.class);
        } catch (RuntimeException e) {
            throw new UsageBlobClient.BlobUnavailableException(
                "Blob " + blob + " read failed: " + e.getMessage(), e);
        }
        if (response.getStatusCode().value() == 404) {
            log.debug("Blob {} does not exist yet — starting empty", blob);
            return Loaded.missing();
        }
        return new Loaded(response.getBody(), response.getHeaders().getETag(), true);
    }

    /**
     * Writes the blob conditionally / schreibt den Blob bedingt: {@code If-Match} with the ETag of
     * the preceding read, or {@code If-None-Match: *} for the initial creation (§12.10). Azure
     * answers a failed {@code If-None-Match: *} with 409, a failed {@code If-Match} with 412 —
     * both become {@link UsageBlobClient.BlobPreconditionFailedException} (E5). / 412 und 409 sind
     * beide Schreibkonflikte.
     *
     * @param json the complete file content / vollständiger Dateiinhalt
     * @param etag ETag of the preceding read, {@code null} when the blob did not exist
     * @return the new ETag, {@code null} when Azure sent none / neuer ETag
     */
    public String put(String json, String etag) {
        requireConfigured();
        String token = fetchToken();
        try {
            ResponseEntity<Void> response = restClient.put()
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
                        throw new UsageBlobClient.BlobPreconditionFailedException(
                            "Blob " + blob + " changed in the meantime (HTTP "
                                + res.getStatusCode().value() + ")");
                    })
                .toBodilessEntity();
            return response.getHeaders().getETag();
        } catch (UsageBlobClient.BlobPreconditionFailedException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new UsageBlobClient.BlobUnavailableException(
                "Blob " + blob + " write failed: " + e.getMessage(), e);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new UsageBlobClient.BlobUnavailableException(
                "Usage storage account not configured (USAGE_STORAGE_ACCOUNT unset) — "
                    + "expected locally, REQ-038 jobs do not persist and no scan runs (E4).");
        }
    }

    private String blobUrl() {
        return "https://" + account + ".blob.core.windows.net/" + container + "/" + blob;
    }

    /** Managed-identity token for the storage data plane (same as REQ-037) / MI-Token. */
    private String fetchToken() {
        String endpoint = identityEndpoint();
        String header = identityHeader();
        if (endpoint == null || endpoint.isBlank() || header == null || header.isBlank()) {
            throw new UsageBlobClient.BlobUnavailableException(
                "Managed identity endpoint not available (IDENTITY_ENDPOINT/IDENTITY_HEADER unset). "
                    + "This is expected outside Azure Container Apps.");
        }
        try {
            String json = restClient.get()
                .uri(endpoint + "?resource=" + RESOURCE + "&api-version=2019-08-01")
                .header("X-IDENTITY-HEADER", header)
                .retrieve()
                .body(String.class);
            String token = TOKEN_MAPPER.readTree(json == null ? "{}" : json)
                .path("access_token").asText("");
            if (token.isBlank()) {
                throw new UsageBlobClient.BlobUnavailableException(
                    "Managed identity returned no access_token");
            }
            return token;
        } catch (UsageBlobClient.BlobUnavailableException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new UsageBlobClient.BlobUnavailableException(
                "Managed identity token not parseable: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new UsageBlobClient.BlobUnavailableException(
                "Failed to obtain managed identity token: " + e.getMessage(), e);
        }
    }

    /** Overridable for tests only — production reads {@code IDENTITY_ENDPOINT}. */
    protected String identityEndpoint() {
        return System.getenv("IDENTITY_ENDPOINT");
    }

    /** Overridable for tests only — production reads {@code IDENTITY_HEADER}. */
    protected String identityHeader() {
        return System.getenv("IDENTITY_HEADER");
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Result of a read / Ergebnis eines Lesevorgangs.
     *
     * @param body raw JSON, {@code null} when the blob does not exist / roher Inhalt
     * @param etag ETag for the conditional write, {@code null} when the blob does not exist
     * @param exists whether the blob exists / ob der Blob existiert
     */
    public record Loaded(String body, String etag, boolean exists) {
        /** The 404 first-run case / der 404-Fall beim Erstlauf. */
        public static Loaded missing() {
            return new Loaded(null, null, false);
        }
    }
}
