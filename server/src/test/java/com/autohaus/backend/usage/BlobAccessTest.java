package com.autohaus.backend.usage;

import com.autohaus.backend.config.UsageStatsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The shared blob access of the two REQ-038 blobs (§12.10, E4, E5) / der gemeinsame Blob-Zugriff.
 *
 * <p>Every failure must surface as {@link UsageBlobClient.BlobUnavailableException} — the
 * schedulers rely on exactly that to fall back to „Historie derzeit nicht verfügbar" instead of a
 * 5xx (E4, AC-32) — and a write conflict as {@link UsageBlobClient.BlobPreconditionFailedException}
 * (E5). The HTTP layer is real, driven by {@link MockRestServiceServer}; the managed identity is
 * stubbed through the two protected getters. The happy paths run in the blob-client tests. /
 * Jeder Fehler wird zur „nicht verfügbar"-Ausnahme, ein Konflikt zur Precondition-Ausnahme.
 */
class BlobAccessTest {

    private static final String ACCOUNT = "gsterminbuchungstats";
    private static final String BLOB = "capacity-daily.json";
    private static final String BLOB_URL = "https://" + ACCOUNT + ".blob.core.windows.net/usage-stats/" + BLOB;
    private static final String IDENTITY_ENDPOINT = "http://169.254.169.254/metadata/identity/oauth2/token";
    private static final String IDENTITY_SECRET = "identity-header-secret";

    @Test
    @DisplayName("E4: no account, no blob name or no properties → not configured, get/put refuse")
    void refusesWithoutConfiguration() {
        for (BlobAccess access : new BlobAccess[] {
            new BlobAccess(properties(""), BLOB), new BlobAccess(properties(ACCOUNT), " "),
            new BlobAccess(null, BLOB, RestClient.builder())}) {
            assertFalse(access.isConfigured());
            assertThrows(UsageBlobClient.BlobUnavailableException.class, access::get);
            assertThrows(UsageBlobClient.BlobUnavailableException.class, () -> access.put("{}", null));
        }
        assertTrue(new BlobAccess(properties(ACCOUNT), BLOB).isConfigured());
        assertEquals(BLOB, new BlobAccess(properties(ACCOUNT), " " + BLOB + " ").blobName(), "trimmed");
    }

    @Test
    @DisplayName("§12.10: a blank container falls back to usage-stats")
    void fallsBackToTheDefaultContainer() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BlobAccess access = access(new UsageStatsProperties(true, "-", "-", "Europe/Berlin", null, 24, 60,
            new UsageStatsProperties.Storage(ACCOUNT, " ", "usage-daily.json")), builder);
        expectToken(server, "{\"access_token\":\"token\"}");
        server.expect(requestTo(BLOB_URL)).andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer token"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON).header("ETag", "\"0x1\""));

        BlobAccess.Loaded loaded = access.get();

        server.verify();
        assertTrue(loaded.exists());
        assertEquals("\"0x1\"", loaded.etag());
        assertEquals("{}", loaded.body());
    }

    @Test
    @DisplayName("E4: outside Azure (no IDENTITY_ENDPOINT/IDENTITY_HEADER) the token is unavailable")
    void failsWithoutManagedIdentity() {
        BlobAccess noEndpoint = new BlobAccess(properties(ACCOUNT), BLOB, RestClient.builder()) {
            @Override
            protected String identityEndpoint() {
                return " ";
            }

            @Override
            protected String identityHeader() {
                return IDENTITY_SECRET;
            }
        };
        BlobAccess noHeader = new BlobAccess(properties(ACCOUNT), BLOB, RestClient.builder()) {
            @Override
            protected String identityEndpoint() {
                return IDENTITY_ENDPOINT;
            }

            @Override
            protected String identityHeader() {
                return null;
            }
        };

        for (BlobAccess access : new BlobAccess[] {noEndpoint, noHeader}) {
            UsageBlobClient.BlobUnavailableException e =
                assertThrows(UsageBlobClient.BlobUnavailableException.class, access::get);
            assertTrue(e.getMessage().contains("Managed identity endpoint not available"), e.getMessage());
        }
    }

    @Test
    @DisplayName("E4: the production getters read the process environment (unset locally)")
    void readsTheIdentityFromTheEnvironment() {
        assumeTrue(System.getenv("IDENTITY_ENDPOINT") == null, "only outside Azure Container Apps");

        BlobAccess access = new BlobAccess(properties(ACCOUNT), BLOB);

        assertNull(access.identityEndpoint());
        assertThrows(UsageBlobClient.BlobUnavailableException.class, access::get);
    }

    @Test
    @DisplayName("E4: a token answer without access_token, not parseable or failing is 'unavailable'")
    void mapsEveryTokenFailureToUnavailable() {
        for (String answer : new String[] {"{\"token_type\":\"Bearer\"}", "not json", null}) {
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            BlobAccess access = access(properties(ACCOUNT), builder);
            if (answer == null) {
                server.expect(request -> assertTrue(request.getURI().toString().startsWith(IDENTITY_ENDPOINT)))
                    .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
            } else {
                expectToken(server, answer);
            }

            assertThrows(UsageBlobClient.BlobUnavailableException.class, access::get, String.valueOf(answer));
            server.verify();
        }
    }

    @Test
    @DisplayName("E4: a read failure other than 404 (e.g. 403, role missing) is 'unavailable'; 404 is empty")
    void mapsReadFailuresAndTheFirstRun() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BlobAccess access = access(properties(ACCOUNT), builder);
        expectToken(server, "{\"access_token\":\"token\"}");
        server.expect(requestTo(BLOB_URL)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        expectToken(server, "{\"access_token\":\"token\"}");
        server.expect(requestTo(BLOB_URL)).andExpect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(UsageBlobClient.BlobUnavailableException.class, access::get);
        BlobAccess.Loaded missing = access.get();

        server.verify();
        assertFalse(missing.exists());
        assertNull(missing.body());
        assertNull(missing.etag());
    }

    @Test
    @DisplayName("E5: 412 and 409 are write conflicts, 500 is 'unavailable', 201 hands back the new ETag")
    void mapsWriteResults() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BlobAccess access = access(properties(ACCOUNT), builder);
        expectPut(server, "If-None-Match", "*", withStatus(HttpStatus.CREATED).header("ETag", "\"0x2\""));
        expectPut(server, "If-Match", "\"0x2\"", withStatus(HttpStatus.PRECONDITION_FAILED));
        expectPut(server, "If-None-Match", "*", withStatus(HttpStatus.CONFLICT));
        expectPut(server, "If-Match", "\"0x2\"", withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertEquals("\"0x2\"", access.put("{}", null), "first write creates the blob");
        assertThrows(UsageBlobClient.BlobPreconditionFailedException.class, () -> access.put("{}", "\"0x2\""));
        assertThrows(UsageBlobClient.BlobPreconditionFailedException.class, () -> access.put("{}", null));
        assertThrows(UsageBlobClient.BlobUnavailableException.class, () -> access.put("{}", "\"0x2\""));

        server.verify();
    }

    // ---- helpers / Hilfsmethoden ----

    private static void expectPut(MockRestServiceServer server, String condition, String value,
                                  org.springframework.test.web.client.ResponseCreator response) {
        expectToken(server, "{\"access_token\":\"token\"}");
        server.expect(requestTo(BLOB_URL)).andExpect(method(HttpMethod.PUT))
            .andExpect(header(condition, value))
            .andExpect(header("x-ms-blob-type", "BlockBlob"))
            .andRespond(response);
    }

    private static void expectToken(MockRestServiceServer server, String answer) {
        server.expect(request -> {
                String uri = request.getURI().toString();
                assertTrue(uri.startsWith(IDENTITY_ENDPOINT) && uri.contains("storage.azure.com"), uri);
            })
            .andExpect(header("X-IDENTITY-HEADER", IDENTITY_SECRET))
            .andRespond(withSuccess(answer, MediaType.APPLICATION_JSON));
    }

    private static BlobAccess access(UsageStatsProperties properties, RestClient.Builder builder) {
        return new BlobAccess(properties, BLOB, builder) {
            @Override
            protected String identityEndpoint() {
                return IDENTITY_ENDPOINT;
            }

            @Override
            protected String identityHeader() {
                return IDENTITY_SECRET;
            }
        };
    }

    private static UsageStatsProperties properties(String account) {
        return new UsageStatsProperties(true, "0 */15 * * * *", "0 30 3 * * *", "Europe/Berlin", null, 24, 60,
            new UsageStatsProperties.Storage(account, "usage-stats", "usage-daily.json"));
    }
}
