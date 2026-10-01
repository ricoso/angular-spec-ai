package com.autohaus.backend.capacity;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.autohaus.backend.config.CapacityProperties;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.usage.BlobAccess;
import com.autohaus.backend.usage.UsageBlobClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Persistence of {@code capacity-daily.json} (REQ-038 TC-24 capacity part, §10.6, §12.10, BR-18,
 * E4–E6) / Persistenz der Kapazitäts-Historie.
 *
 * <p>The first half pins the tolerant reader with strict capacity enums and the round trip of the
 * §10.6 example; the second half drives the real HTTP layer ({@code BlobAccess}) against
 * {@link MockRestServiceServer} — with the managed-identity lookups pinned — to prove the
 * conditional headers, the 404 first run and that only {@code capacity-daily.json} is ever
 * requested ({@code usage-daily.json} never). / Zweite Hälfte über den echten HTTP-Pfad.
 */
class CapacityBlobClientTest {

    /** The §10.6 example verbatim / das Beispiel aus §10.6 wörtlich. */
    private static final String EXAMPLE = """
        {
          "schemaVersion": 1,
          "referenceService": "wiper-blades",
          "scans": {
            "2026-10-22": {
              "state": "partial",
              "startedAt": "2026-10-22T20:31:12Z",
              "finishedAt": "2026-10-22T20:35:40Z",
              "targetDay": "2026-10-23",
              "branches": {
                "68f0a1b2c3d4e5f6a7b8c9d0": { "status": "ok", "firstFreeDate": "2026-10-23", "workdaysToFirstFree": 0,
                                              "freeSlotsTargetDay": 13 },
                "68f0a1b2c3d4e5f6a7b8c9d4": { "status": "none" },
                "68f0a1b2c3d4e5f6a7b8c9d5": { "status": "error", "reason": "softnet-unavailable" }
              }
            }
          }
        }""";

    private static final LocalDate DAY = LocalDate.of(2026, 10, 22);
    private static final String ETAG = "\"0x8DC5\"";

    private final ObjectMapper json = new ObjectMapper();

    // ---- TC-24: tolerant reader, strict enums / toleranter Leser, strenge Enums ----

    /**
     * TC-24: the §10.6 example parses to {@code ScanState.PARTIAL}, {@code ProbeStatus.OK/NONE/
     * ERROR} and {@code ProbeReason.SOFTNET_UNAVAILABLE}, and serializes back to the identical
     * document (same keys, same values). / Round-trip des Beispiels.
     */
    @Test
    @DisplayName("TC-24: §10.6 example round-trips with identical keys")
    void roundTripsTheSpecExample() throws Exception {
        CapacityBlobClient.Loaded loaded = CapacityBlobClient.parse(EXAMPLE, ETAG);

        assertTrue(loaded.usable());
        assertEquals(ETAG, loaded.etag());
        CapacityHistory history = loaded.history();
        assertEquals(1, history.schemaVersion());
        assertEquals("wiper-blades", history.referenceService());
        CapacityScan scan = history.scans().get(DAY);
        assertEquals(ScanState.PARTIAL, scan.state());
        assertEquals(Instant.parse("2026-10-22T20:31:12Z"), scan.startedAt());
        assertEquals(Instant.parse("2026-10-22T20:35:40Z"), scan.finishedAt());
        assertEquals(LocalDate.of(2026, 10, 23), scan.targetDay());
        assertEquals(new ProbeResult(ProbeStatus.OK, null, LocalDate.of(2026, 10, 23), 0, 13),
            scan.branches().get("68f0a1b2c3d4e5f6a7b8c9d0"));
        assertEquals(new ProbeResult(ProbeStatus.NONE, null, null, null, null),
            scan.branches().get("68f0a1b2c3d4e5f6a7b8c9d4"));
        assertEquals(new ProbeResult(ProbeStatus.ERROR, ProbeReason.SOFTNET_UNAVAILABLE, null,
            null, null), scan.branches().get("68f0a1b2c3d4e5f6a7b8c9d5"));

        String written = CapacityBlobClient.serialize(history);

        assertEquals(json.readTree(EXAMPLE), json.readTree(written), "identical document");
        assertFalse(written.contains("\n"), "compact JSON");
        assertEquals(history, CapacityBlobClient.parse(written, null).history());
    }

    /**
     * TC-24: {@code softnet-forbidden} and every other enum key survive the round trip as the same
     * lower-case key; a running claim without {@code finishedAt} is written without it. / Alle
     * Schlüssel identisch zurück.
     */
    @Test
    @DisplayName("TC-24: every enum key round-trips identically")
    void roundTripsEveryEnumKey() {
        SortedMap<LocalDate, CapacityScan> scans = new TreeMap<>();
        Map<String, ProbeResult> branches = new TreeMap<>();
        int i = 0;
        for (ProbeReason reason : ProbeReason.values()) {
            branches.put(String.format("68f0a1b2c3d4e5f6a7b8c9%02d", i++),
                new ProbeResult(ProbeStatus.ERROR, reason, null, null, null));
        }
        for (ScanState state : ScanState.values()) {
            scans.put(DAY.minusDays(state.ordinal()), new CapacityScan(state,
                Instant.parse("2026-10-22T20:31:12Z"), null, DAY.plusDays(1), branches));
        }
        CapacityHistory history = new CapacityHistory(1, "wiper-blades", scans);

        String written = CapacityBlobClient.serialize(history);
        CapacityHistory reread = CapacityBlobClient.parse(written, null).history();

        assertTrue(written.contains("\"reason\":\"softnet-forbidden\""), written);
        assertTrue(written.contains("\"state\":\"running\""), written);
        assertFalse(written.contains("finishedAt"), "null values are omitted");
        assertEquals(history, reread);
        assertEquals(ProbeReason.SOFTNET_FORBIDDEN,
            reread.scans().get(DAY).branches().get("68f0a1b2c3d4e5f6a7b8c901").reason());
    }

    /**
     * BR-18: unknown fields at every level are ignored; missing counters and optional values
     * become {@code null} („—"); missing {@code scans}/{@code branches} are empty. / Unbekannte
     * Felder ignoriert, fehlende Zähler null.
     */
    @Test
    @DisplayName("TC-24: unknown fields ignored, missing counters null")
    void readsTolerantly() {
        CapacityBlobClient.Loaded loaded = CapacityBlobClient.parse("""
            {"schemaVersion":1,"future":{"x":1},"scans":{
              "2026-10-22":{"state":"complete","note":"x","branches":{
                "68f0a1b2c3d4e5f6a7b8c9d0":{"status":"ok","extra":true}}},
              "2026-10-21":{"state":"failed"}}}""", ETAG);

        assertTrue(loaded.usable());
        assertNull(loaded.history().referenceService());
        ProbeResult result = loaded.history().scans().get(DAY).branches()
            .get("68f0a1b2c3d4e5f6a7b8c9d0");
        assertEquals(new ProbeResult(ProbeStatus.OK, null, null, null, null), result);
        assertTrue(loaded.history().scans().get(DAY.minusDays(1)).branches().isEmpty());
        assertNull(loaded.history().scans().get(DAY).startedAt());
        assertTrue(CapacityBlobClient.parse("{\"schemaVersion\":1}", null).history().scans().isEmpty());
        assertTrue(CapacityBlobClient.parse("{\"schemaVersion\":1,\"scans\":null}", null).usable());
    }

    /**
     * TC-24/E6: {@code schemaVersion} 2, a missing or textual version, an unknown status, reason or
     * state, a non-ISO day key, unparseable timestamps, negative or non-numeric counters and
     * invalid JSON all make the content unusable — {@code usable == false}, never an exception, so
     * the scheduler leaves the file untouched. / Alles E6, nie eine Exception.
     */
    @Test
    @DisplayName("TC-24/E6: foreign versions, unknown capacity enums and bad values are unusable")
    void rejectsUnusableContent() {
        List<String> unusable = List.of(
            "{\"schemaVersion\":2,\"scans\":{}}",
            "{\"scans\":{}}",
            "{\"schemaVersion\":\"1\",\"scans\":{}}",
            "{\"schemaVersion\":0}",
            scanWith("\"state\":\"paused\""),
            scanWith("\"branches\":{}"),
            branchWith("\"status\":\"maybe\""),
            branchWith("\"status\":\"error\",\"reason\":\"cosmic-rays\""),
            branchWith("\"reason\":\"timeout\""),
            branchWith("\"status\":\"ok\",\"workdaysToFirstFree\":-1"),
            branchWith("\"status\":\"ok\",\"workdaysToFirstFree\":\"3\""),
            branchWith("\"status\":\"ok\",\"freeSlotsTargetDay\":1.5"),
            branchWith("\"status\":\"ok\",\"firstFreeDate\":\"23.10.2026\""),
            branchWith("\"status\":\"ok\",\"firstFreeDate\":20261023"),
            scanWith("\"state\":\"complete\",\"startedAt\":\"yesterday\""),
            scanWith("\"state\":\"complete\",\"finishedAt\":1700000000"),
            scanWith("\"state\":\"complete\",\"targetDay\":\"2026-13-01\""),
            scanWith("\"state\":\"complete\",\"branches\":[]"),
            "{\"schemaVersion\":1,\"scans\":{\"22.10.2026\":{\"state\":\"complete\"}}}",
            "{\"schemaVersion\":1,\"scans\":{\"2026-10-22\":\"complete\"}}",
            "{\"schemaVersion\":1,\"scans\":{\"2026-10-22\":{\"state\":\"complete\","
                + "\"branches\":{\"68f0a1b2c3d4e5f6a7b8c9d0\":\"ok\"}}}}",
            "{\"schemaVersion\":1,\"scans\":[]}",
            "[1,2,3]",
            "{not json",
            "");
        for (String content : unusable) {
            CapacityBlobClient.Loaded loaded = CapacityBlobClient.parse(content, ETAG);
            assertFalse(loaded.usable(), content);
            assertTrue(loaded.history().scans().isEmpty(), content);
            assertEquals(ETAG, loaded.etag(), content);
        }
        assertFalse(CapacityBlobClient.parse(null, null).usable());
    }

    /** E6 is logged as {@code ERROR} with blob name and {@code schemaVersion}. / ERROR-Log. */
    @Test
    void logsUnusableContentWithBlobNameAndVersion() {
        Logger logger = (Logger) LoggerFactory.getLogger(CapacityBlobClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            CapacityBlobClient.parse("{\"schemaVersion\":2}", null);
        } finally {
            logger.detachAppender(appender);
        }

        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.ERROR, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("capacity-daily.json"), event.getFormattedMessage());
        assertTrue(event.getFormattedMessage().contains("schemaVersion 2"), event.getFormattedMessage());
    }

    /**
     * §10.6: keys ascending, compact, {@code null} values omitted, always the current schema
     * version; an empty or {@code null} history is a valid empty file. / Leere Historie.
     */
    @Test
    void serializesKeysAscendingAndOmitsNulls() throws Exception {
        SortedMap<LocalDate, CapacityScan> scans = new TreeMap<>();
        scans.put(DAY, new CapacityScan(ScanState.COMPLETE, null, null, null,
            Map.of("68f0a1b2c3d4e5f6a7b8c9d9", new ProbeResult(ProbeStatus.NONE, null, null, null, null),
                "68f0a1b2c3d4e5f6a7b8c9d1", new ProbeResult(ProbeStatus.OK, null, DAY, 0, 2))));
        scans.put(DAY.minusDays(1), null);

        String written = CapacityBlobClient.serialize(new CapacityHistory(0, null, scans));

        assertEquals("{\"schemaVersion\":1,\"scans\":{\"2026-10-22\":{\"state\":\"complete\","
            + "\"branches\":{\"68f0a1b2c3d4e5f6a7b8c9d1\":{\"status\":\"ok\",\"firstFreeDate\":"
            + "\"2026-10-22\",\"workdaysToFirstFree\":0,\"freeSlotsTargetDay\":2},"
            + "\"68f0a1b2c3d4e5f6a7b8c9d9\":{\"status\":\"none\"}}}}}", written);
        assertEquals("{\"schemaVersion\":1,\"scans\":{}}", CapacityBlobClient.serialize(null));
        assertEquals("{\"schemaVersion\":1,\"scans\":{}}",
            CapacityBlobClient.serialize(CapacityHistory.empty()));
        JsonNode tree = json.readTree(written);
        assertFalse(tree.toString().contains("checkInResource"));
    }

    // ---- HTTP layer / HTTP-Schicht (§12.10, E4, E5) ----

    /**
     * §5.2/§12.10: the first run gets 404 → empty, usable history without ETag, and the following
     * write creates with {@code If-None-Match: *}; the stored bytes read back with their ETag. Only
     * {@code capacity-daily.json} is ever requested. / Erstlauf und Round-trip über HTTP.
     */
    @Test
    @DisplayName("§12.10: 404 first run, If-None-Match create, read back with ETag")
    void roundTripsOverHttpIncludingTheFirstRun() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CapacityBlobClient client = new CapacityBlobClient(pinnedAccess(builder));
        AtomicReference<String> written = new AtomicReference<>();
        expectToken(server);
        server.expect(requestTo(BLOB_URL)).andExpect(method(HttpMethod.GET))
            .andRespond(withResourceNotFound());
        expectToken(server);
        server.expect(requestTo(BLOB_URL)).andExpect(method(HttpMethod.PUT))
            .andExpect(header("If-None-Match", "*"))
            .andExpect(header("x-ms-blob-type", "BlockBlob"))
            .andExpect(header("Authorization", "Bearer " + TOKEN))
            .andExpect(captureBody(written))
            .andRespond(withStatus(HttpStatus.CREATED).header("ETag", ETAG));
        expectToken(server);
        server.expect(requestTo(BLOB_URL)).andExpect(method(HttpMethod.GET))
            .andRespond(request -> withSuccess(written.get(), MediaType.APPLICATION_JSON)
                .header("ETag", ETAG).createResponse(request));

        CapacityBlobClient.Loaded first = client.load();
        String newEtag = client.save(CapacityBlobClient.parse(EXAMPLE, null).history(), first.etag());
        CapacityBlobClient.Loaded reread = client.load();

        server.verify();
        assertTrue(client.isConfigured());
        assertTrue(first.usable());
        assertNull(first.etag());
        assertTrue(first.history().scans().isEmpty());
        assertEquals(ETAG, newEtag, "the claim write hands its ETag to the result write");
        assertTrue(reread.usable());
        assertEquals(ETAG, reread.etag());
        assertEquals(ScanState.PARTIAL, reread.history().scans().get(DAY).state());
    }

    /** E5: a real 412 on {@code If-Match} surfaces as the write conflict. / 412 → Konflikt. */
    @Test
    void mapsA412ToTheWriteConflict() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CapacityBlobClient client = new CapacityBlobClient(pinnedAccess(builder));
        expectToken(server);
        server.expect(requestTo(BLOB_URL)).andExpect(method(HttpMethod.PUT))
            .andExpect(header("If-Match", ETAG))
            .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED));

        assertThrows(UsageBlobClient.BlobPreconditionFailedException.class,
            () -> client.save(CapacityHistory.empty(), ETAG));
        server.verify();
    }

    /** E6 through HTTP: an unusable body is reported, never thrown. / E6 über HTTP. */
    @Test
    void reportsAnUnusableBodyFromTheBlob() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CapacityBlobClient client = new CapacityBlobClient(pinnedAccess(builder));
        expectToken(server);
        server.expect(requestTo(BLOB_URL)).andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"schemaVersion\":9}", MediaType.APPLICATION_JSON)
                .header("ETag", ETAG));

        CapacityBlobClient.Loaded loaded = client.load();

        server.verify();
        assertFalse(loaded.usable());
        assertEquals(ETAG, loaded.etag());
    }

    /**
     * E4/AC-32: without {@code USAGE_STORAGE_ACCOUNT} the client is not configured and reading or
     * writing fails as unavailable; the production constructor takes the blob name from
     * {@code CAPACITY_BLOB} with the default as fallback. / Ohne Account nicht konfiguriert.
     */
    @Test
    void isUnconfiguredWithoutAStorageAccount() {
        CapacityBlobClient unconfigured = new CapacityBlobClient(usage(""),
            new CapacityProperties(true, "-", "wiper-blades", false, null));
        CapacityBlobClient configured = new CapacityBlobClient(usage(ACCOUNT),
            new CapacityProperties(true, "-", "wiper-blades", false, " "));
        CapacityBlobClient nullProperties = new CapacityBlobClient(usage(ACCOUNT), null);

        assertFalse(unconfigured.isConfigured());
        assertThrows(UsageBlobClient.BlobUnavailableException.class, unconfigured::load);
        assertThrows(UsageBlobClient.BlobUnavailableException.class,
            () -> unconfigured.save(CapacityHistory.empty(), null));
        assertTrue(configured.isConfigured());
        assertTrue(nullProperties.isConfigured());
        assertEquals("capacity-daily.json", CapacityBlobClient.DEFAULT_BLOB);
    }

    // ---- fixtures / Testdaten ----

    private static final String ACCOUNT = "gsterminbuchungstats";
    private static final String BLOB_URL =
        "https://" + ACCOUNT + ".blob.core.windows.net/usage-stats/capacity-daily.json";
    private static final String IDENTITY_ENDPOINT = "http://169.254.169.254/metadata/identity/oauth2/token";
    private static final String IDENTITY_SECRET = "identity-header-secret";
    private static final String TOKEN = "managed-identity-access-token";

    private static String scanWith(String scanFields) {
        return "{\"schemaVersion\":1,\"scans\":{\"2026-10-22\":{" + scanFields + "}}}";
    }

    private static String branchWith(String branchFields) {
        return "{\"schemaVersion\":1,\"scans\":{\"2026-10-22\":{\"state\":\"complete\","
            + "\"branches\":{\"68f0a1b2c3d4e5f6a7b8c9d0\":{" + branchFields + "}}}}}";
    }

    /** The production blob access with the managed-identity environment pinned to the mock. */
    private static BlobAccess pinnedAccess(RestClient.Builder builder) {
        return new BlobAccess(usage(ACCOUNT), CapacityBlobClient.DEFAULT_BLOB, builder) {
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

    private static void expectToken(MockRestServiceServer server) {
        server.expect(CapacityBlobClientTest::assertIsTokenRequest)
            .andExpect(header("X-IDENTITY-HEADER", IDENTITY_SECRET))
            .andRespond(withSuccess("{\"access_token\":\"" + TOKEN + "\"}", MediaType.APPLICATION_JSON));
    }

    private static void assertIsTokenRequest(ClientHttpRequest request) {
        String uri = request.getURI().toString();
        assertTrue(uri.startsWith(IDENTITY_ENDPOINT), uri);
        assertTrue(uri.contains("storage.azure.com"), uri);
        assertFalse(uri.contains("usage-daily.json"), "REQ-037's blob is never touched");
    }

    private static RequestMatcher captureBody(AtomicReference<String> target) {
        return request -> target.set(((MockClientHttpRequest) request).getBodyAsString());
    }

    private static UsageStatsProperties usage(String account) {
        return new UsageStatsProperties(true, "-", "-", "Europe/Berlin", null, 24, 60,
            new UsageStatsProperties.Storage(account, "usage-stats", "usage-daily.json"));
    }
}
