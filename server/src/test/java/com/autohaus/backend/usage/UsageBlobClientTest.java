package com.autohaus.backend.usage;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.autohaus.backend.config.UsageStatsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Blob persistence of the daily history (REQ-037 TC-9, TC-10, TC-11) / Blob-Persistenz der
 * Tageshistorie.
 *
 * <p>Covers the three failure and conflict paths of §12.5 plus the Jackson round trip of §14.1:
 * {@link UsageHistory#updatedAt()} is an {@link Instant} and the day keys are ISO
 * {@link LocalDate}s, which the injectable bare {@code ObjectMapper} cannot bind — the client's own
 * mapper with {@code JavaTimeModule} can. That pitfall only ever shows up in Azure, inside the
 * nightly rollup, so it is pinned by a test here. / Der Round-Trip-Fallstrick wird hier
 * festgenagelt.
 *
 * <p>The conditional-write logic itself lives in {@code UsageStatsScheduler.persistDailySnapshot},
 * which is why TC-10 and TC-11 drive the scheduler against a mocked blob client — that is the pair
 * the contract describes. / Der bedingte Schreibvorgang wird über den Scheduler getrieben.
 *
 * <p>Its HTTP half — {@code If-Match}, the {@code 412}/{@code 409} answer, the managed-identity
 * {@code Bearer} token — is covered separately against {@link MockRestServiceServer} in the last
 * section, because a mocked client can never prove which headers Azure Blob actually receives. /
 * Die HTTP-Seite von AC-10 wird gegen einen Mock-Server geprüft.
 */
@ExtendWith(MockitoExtension.class)
class UsageBlobClientTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Instant NOW = Instant.parse("2026-09-21T01:30:12Z");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 20);

    @Mock private UsageBlobClient blobClient;
    @Mock private UsageStatsAggregator aggregator;

    private UsageStatsStore store;
    private UsageStatsScheduler scheduler;

    @BeforeEach
    void setUp() {
        store = new UsageStatsStore();
        UsageStatsProperties properties = properties("gsterminbuchungstats");
        Clock clock = Clock.fixed(NOW, BERLIN);
        scheduler = new UsageStatsScheduler(aggregator, blobClient,
            new UsageStatsService(properties, clock), store, properties, clock, 0);
    }

    // ---- TC-9: blob not reachable / Blob nicht erreichbar (E2) ----

    /**
     * TC-9/E2: without {@code USAGE_STORAGE_ACCOUNT} the client is simply not configured — the
     * normal local case. Reading and writing then fail with {@link
     * UsageBlobClient.BlobUnavailableException} instead of returning a fake empty history, so the
     * page can name the gap („Historie derzeit nicht verfügbar") rather than hide it behind zeros.
     * / Nicht konfiguriert ist ein benannter Ausfall, keine stille Null.
     */
    @Test
    void unconfiguredStorageIsReportedAsUnavailable() {
        UsageBlobClient client = new UsageBlobClient(properties(""));

        assertFalse(client.isConfigured());
        assertThrows(UsageBlobClient.BlobUnavailableException.class, client::loadUsageHistory);
        assertThrows(UsageBlobClient.BlobUnavailableException.class,
            () -> client.saveUsageHistory(UsageHistory.empty(), null));
    }

    /**
     * TC-9/E2: configured, but no managed identity — the second unavailability path. The message
     * names the missing environment, never a credential. / Zweiter E2-Pfad: keine Managed Identity.
     */
    @Test
    void missingManagedIdentityIsReportedAsUnavailable() {
        UsageBlobClient client = new UsageBlobClient(properties("gsterminbuchungstats"));
        assertTrue(client.isConfigured());

        UsageBlobClient.BlobUnavailableException thrown = assertThrows(
            UsageBlobClient.BlobUnavailableException.class, client::loadUsageHistory);

        assertTrue(thrown.getMessage().contains("Managed identity"), thrown.getMessage());
    }

    /**
     * E3 is a special case of E2 so a single {@code catch} in the refresh path covers both, while
     * the rollup can still distinguish the conflict. / Der Schreibkonflikt ist ein Spezialfall.
     */
    @Test
    void writeConflictIsASpecialCaseOfUnavailability() {
        assertTrue(UsageBlobClient.BlobUnavailableException.class.isAssignableFrom(
            UsageBlobClient.BlobPreconditionFailedException.class));
    }

    /**
     * TC-9: with the blob unavailable the page still renders — month and year fall back to the
     * running day and {@code historyAvailable == false} drives the hint „Historie derzeit nicht
     * verfügbar". No 5xx, no empty page (AC-11). / Die Seite zeigt den laufenden Tag plus Hinweis.
     */
    @Test
    void pageFallsBackToTheRunningDayWhenTheBlobIsUnavailable() {
        when(blobClient.isConfigured()).thenReturn(true);
        when(blobClient.loadUsageHistory())
            .thenThrow(new UsageBlobClient.BlobUnavailableException("403 AuthorizationPermissionMismatch"));
        when(aggregator.aggregateDailyUsage(LocalDate.of(2026, 9, 21)))
            .thenReturn(Optional.of(dayUsage(41)));

        assertDoesNotThrow(() -> scheduler.refreshCurrentUsage());

        UsageStats stats = store.current();
        assertFalse(stats.historyAvailable());
        assertTrue(stats.liveAvailable());
        // 41 + 3 + 12 + 1 — month and year hold exactly the running day, nothing else.
        assertEquals(57L, stats.month().visitCount());
        assertEquals(57L, stats.year().visitCount());
        assertEquals(41L, stats.month().visits().mobile());
        assertNull(stats.lastRollupDay());
        // Only the running day is queried when there is no history to close the gap against (E2).
        verify(aggregator, never()).aggregateDailyUsage(DAY);
    }

    // ---- TC-10: the rollup is idempotent / Rollup ist idempotent (BR-8) ----

    /**
     * TC-10/BR-8: the rollup replaces absolute day values, it never increments. Running
     * {@code persistDailySnapshot} twice with the same input writes the same file — one row for
     * 20.09. with {@code mobile == 41}, an unchanged {@code days.size()}. / Zweimal ausgeführt,
     * identisches Ergebnis.
     *
     * <p>The written JSON is asserted as well: the day objects carry <b>no</b> {@code day} field
     * (the calendar day is exclusively the map key, §10.3) and {@code updatedAt} is an ISO-8601
     * <b>string</b>, not a number. / Kein Feld „day", {@code updatedAt} als ISO-String.
     */
    @Test
    void rollupReplacesTheDayRowInsteadOfIncrementingIt() throws Exception {
        doNothing().when(blobClient).saveUsageHistory(any(), any());
        SortedMap<LocalDate, DailyUsage> days = new TreeMap<>();
        days.put(DAY, dayUsage(41));

        assertTrue(scheduler.persistDailySnapshot(days, "\"0x8DC1\""));
        assertTrue(scheduler.persistDailySnapshot(days, "\"0x8DC1\""));

        ArgumentCaptor<UsageHistory> written = ArgumentCaptor.forClass(UsageHistory.class);
        verify(blobClient, times(2)).saveUsageHistory(written.capture(), eq("\"0x8DC1\""));
        UsageHistory first = written.getAllValues().get(0);
        UsageHistory second = written.getAllValues().get(1);

        assertEquals(1, second.days().size());
        assertEquals(41L, second.days().get("2026-09-20").visits().mobile());
        assertEquals(first.days(), second.days(), "a repeated run must produce the same file");

        String json = blobMapper().writeValueAsString(second);
        assertTrue(json.contains("\"2026-09-20\""), json);
        assertFalse(json.contains("\"day\""), "the calendar day is the map key only (§10.3): " + json);
        assertTrue(json.contains("\"updatedAt\":\"2026-09-21T01:30:12Z\""),
            "updatedAt must be an ISO-8601 string, not a number: " + json);
        assertTrue(json.contains("\"schemaVersion\":1"), json);
    }

    /**
     * §14.1: the round trip over {@link Instant} and ISO {@link LocalDate} keys must work with the
     * client's own mapper. With the injectable bare {@code ObjectMapper} (no registered modules)
     * this would blow up as {@code InvalidDefinitionException: Java 8 date/time type not supported
     * by default} — in Azure, inside the nightly rollup, not locally. / Der Fallstrick aus §14.1.
     */
    @Test
    void historyRoundTripsThroughTheClientsOwnMapper() throws Exception {
        SortedMap<LocalDate, DailyUsage> days = new TreeMap<>();
        days.put(LocalDate.of(2026, 9, 19),
            new DailyUsage(new DeviceBreakdown(38, 2, 15, 1), 61, 58, 11, 10, 27, 9));
        days.put(DAY, new DailyUsage(new DeviceBreakdown(41, 3, 12, 1), 55, 53, 9, 9, 22, 7));
        UsageHistory history = UsageBlobClient.toHistory(days, NOW);
        ObjectMapper mapper = blobMapper();

        String json = mapper.writeValueAsString(history);
        UsageHistory back = mapper.readValue(json, UsageHistory.class);

        assertEquals(history, back);
        assertEquals(NOW, back.updatedAt());
        assertEquals(2, back.days().size());
        assertEquals(38L, back.days().get("2026-09-19").visits().mobile());
        assertEquals(9L, back.days().get("2026-09-19").sessionsWithBooking());
        // Keys stay ISO dates in ascending order, so a human can read the file (§10.3).
        assertEquals(List.of("2026-09-19", "2026-09-20"), List.copyOf(history.days().keySet()));
        assertTrue(json.indexOf("2026-09-19") < json.indexOf("2026-09-20"), json);
    }

    // ---- TC-11: write conflict between two replicas / Schreibkonflikt zweier Replicas (E3) ----

    /**
     * TC-11/AC-10/E3: the first {@code PUT} answers {@code 412} because another replica wrote in
     * the meantime, so the client re-reads with a fresh {@code ETag}, merges and writes again — the
     * second attempt succeeds. Days only the other replica knows survive the merge; our absolute
     * values win for the days we recomputed (BR-8 makes every outcome identical). / Neu lesen,
     * mergen, erneut schreiben.
     */
    @Test
    void reReadsAndMergesAfterAWriteConflict() {
        SortedMap<LocalDate, DailyUsage> otherReplica = new TreeMap<>();
        otherReplica.put(LocalDate.of(2026, 9, 18), dayUsage(17));
        otherReplica.put(DAY, dayUsage(4));
        when(blobClient.loadUsageHistory()).thenReturn(new UsageBlobClient.LoadedHistory(
            UsageBlobClient.toHistory(otherReplica, NOW), otherReplica, "\"0x8DC2\"", true));
        doThrow(new UsageBlobClient.BlobPreconditionFailedException("412"))
            .doNothing()
            .when(blobClient).saveUsageHistory(any(), any());

        SortedMap<LocalDate, DailyUsage> days = new TreeMap<>();
        days.put(DAY, dayUsage(41));

        assertTrue(scheduler.persistDailySnapshot(days, "\"0x8DC1\""));

        verify(blobClient, times(1)).loadUsageHistory();
        ArgumentCaptor<UsageHistory> written = ArgumentCaptor.forClass(UsageHistory.class);
        ArgumentCaptor<String> etags = ArgumentCaptor.forClass(String.class);
        verify(blobClient, times(2)).saveUsageHistory(written.capture(), etags.capture());

        assertEquals(List.of("\"0x8DC1\"", "\"0x8DC2\""), etags.getAllValues(),
            "the retry must use the ETag of the re-read, not the stale one");
        Map<String, DailyUsage> merged = written.getAllValues().get(1).days();
        assertEquals(2, merged.size());
        assertEquals(17L, merged.get("2026-09-18").visits().mobile(),
            "a day only the other replica has must survive the merge");
        assertEquals(41L, merged.get("2026-09-20").visits().mobile(),
            "our freshly computed absolute value wins (BR-8)");
    }

    /**
     * TC-11/E3: after three failed attempts the run gives up with a {@code WARN} and <b>no</b>
     * exception leaves the method — the next night's catch-up picks the day up again (AC-9). /
     * Nach drei Versuchen: WARN, keine Exception.
     */
    @Test
    void givesUpAfterThreeConflictsWithoutThrowing() {
        SortedMap<LocalDate, DailyUsage> present = new TreeMap<>();
        present.put(DAY, dayUsage(4));
        when(blobClient.loadUsageHistory()).thenReturn(new UsageBlobClient.LoadedHistory(
            UsageBlobClient.toHistory(present, NOW), present, "\"0x8DC2\"", true));
        doThrow(new UsageBlobClient.BlobPreconditionFailedException("412"))
            .when(blobClient).saveUsageHistory(any(), any());

        ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(UsageStatsScheduler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        SortedMap<LocalDate, DailyUsage> days = new TreeMap<>();
        days.put(DAY, dayUsage(41));
        try {
            assertFalse(scheduler.persistDailySnapshot(days, "\"0x8DC1\""));
        } finally {
            logger.detachAppender(appender);
        }

        verify(blobClient, times(UsageStatsScheduler.MAX_WRITE_ATTEMPTS))
            .saveUsageHistory(any(), any());
        assertTrue(appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .anyMatch(event -> event.getFormattedMessage().contains("gave up after")),
            "giving up must be visible as WARN, never silent");
    }

    /**
     * E4 during the retry: a file that became unusable is <b>not</b> overwritten — it stays frozen
     * for manual analysis. / Eine unbrauchbare Datei wird nicht überschrieben.
     */
    @Test
    void doesNotOverwriteAFileThatBecameUnusableDuringTheRetry() {
        when(blobClient.loadUsageHistory()).thenReturn(new UsageBlobClient.LoadedHistory(
            UsageHistory.empty(), new TreeMap<>(), "\"0x8DC2\"", false));
        doThrow(new UsageBlobClient.BlobPreconditionFailedException("412"))
            .when(blobClient).saveUsageHistory(any(), any());

        SortedMap<LocalDate, DailyUsage> days = new TreeMap<>();
        days.put(DAY, dayUsage(41));

        assertFalse(scheduler.persistDailySnapshot(days, "\"0x8DC1\""));

        verify(blobClient, times(1)).saveUsageHistory(any(), any());
    }

    // ---- TC-11/AC-10 on the real HTTP layer / auf echter HTTP-Ebene ----

    /**
     * AC-10 over the wire / AC-10 über die Leitung: the tests above drive the retry loop against a
     * <b>mocked</b> {@link UsageBlobClient}, so the HTTP half of the conditional write —
     * {@code If-Match}, the {@code 412} answer, the {@code Bearer} token — never ran. Exactly that
     * half prevents the lost update between two replicas in production, which is why the four
     * tests below execute {@link UsageBlobClient#saveUsageHistory} and
     * {@link UsageBlobClient#loadUsageHistory} for real against
     * {@link MockRestServiceServer}. / Hier läuft der echte HTTP-Pfad.
     *
     * <p><b>Covered:</b> the request line and headers Azure Blob sees ({@code If-Match} /
     * {@code If-None-Match: *}, {@code Authorization}, {@code x-ms-version},
     * {@code x-ms-blob-type}), the mapping of {@code 412} and {@code 409} to
     * {@link UsageBlobClient.BlobPreconditionFailedException}, the {@code 404} first-run path and
     * the JSON round trip over {@link Instant} plus ISO {@link LocalDate} keys through the real
     * request and response bodies (§14.1).
     *
     * <p><b>Not covered:</b> the managed-identity <b>environment</b> itself — the test pins
     * {@code IDENTITY_ENDPOINT}/{@code IDENTITY_HEADER} to the mock server by overriding the two
     * lookups, because environment variables cannot be set inside a running JVM. That those
     * variables exist in Azure Container Apps stays a deployment concern; the missing-environment
     * path is covered by {@link #missingManagedIdentityIsReportedAsUnavailable()}. Real Azure
     * behaviour (SAS, RBAC, actual ETag values) is out of scope of a unit test. / Die Umgebung
     * selbst wird nicht geprüft, wohl aber jeder Header, den der Client daraus baut.
     */
    @Test
    void sendsTheIfMatchHeaderAndTheManagedIdentityTokenOnAConditionalWrite() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UsageBlobClient client = new IdentityPinnedBlobClient(properties(ACCOUNT), builder);
        expectTokenRequest(server);
        AtomicReference<String> body = new AtomicReference<>();
        server.expect(requestTo(BLOB_URL))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(header("Authorization", "Bearer " + TOKEN))
            .andExpect(header("x-ms-version", "2021-08-06"))
            .andExpect(header("x-ms-blob-type", "BlockBlob"))
            .andExpect(header("If-Match", ETAG))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(captureBody(body))
            .andRespond(withStatus(HttpStatus.CREATED));

        client.saveUsageHistory(history(), ETAG);

        server.verify();
        // The file that would land in the container — ISO day keys, no "day" field (§10.3).
        assertTrue(body.get().contains("\"2026-09-20\""), body.get());
        assertTrue(body.get().contains("\"updatedAt\":\"2026-09-21T01:30:12Z\""), body.get());
        assertFalse(body.get().contains("If-None-Match"), "a known ETag must not create blindly");
    }

    /**
     * TC-11/AC-10/E3: a real {@code 412} answer — the status Azure returns when another replica
     * wrote since our read — must surface as {@link
     * UsageBlobClient.BlobPreconditionFailedException} and <b>not</b> as the generic
     * {@link UsageBlobClient.BlobUnavailableException}. Only that distinction makes
     * {@code UsageStatsScheduler.persistDailySnapshot} re-read, merge and retry instead of
     * dropping the day. / Nur so wird aus dem Konflikt ein Retry statt eines verlorenen Tages.
     */
    @Test
    void mapsAReal412ToAWriteConflictSoTheSchedulerRetries() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UsageBlobClient client = new IdentityPinnedBlobClient(properties(ACCOUNT), builder);
        expectTokenRequest(server);
        server.expect(requestTo(BLOB_URL))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(header("If-Match", ETAG))
            .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED));

        UsageBlobClient.BlobPreconditionFailedException thrown = assertThrows(
            UsageBlobClient.BlobPreconditionFailedException.class,
            () -> client.saveUsageHistory(history(), ETAG));

        server.verify();
        assertTrue(thrown.getMessage().contains("412"), thrown.getMessage());
    }

    /**
     * AC-10, initial creation: without an ETag the client must not overwrite a file another
     * replica created in the same second — it sends {@code If-None-Match: *}. Azure answers a lost
     * race there with {@code 409}, not {@code 412}, and both have to reach the caller as the same
     * conflict. / Erstanlage mit {@code If-None-Match: *}, 409 ist derselbe Konflikt.
     */
    @Test
    void createsWithIfNoneMatchAndTreats409AsTheSameConflict() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UsageBlobClient client = new IdentityPinnedBlobClient(properties(ACCOUNT), builder);
        expectTokenRequest(server);
        server.expect(requestTo(BLOB_URL))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(header("If-None-Match", "*"))
            .andExpect(headerDoesNotExist("If-Match"))
            .andRespond(withStatus(HttpStatus.CONFLICT));

        UsageBlobClient.BlobPreconditionFailedException thrown = assertThrows(
            UsageBlobClient.BlobPreconditionFailedException.class,
            () -> client.saveUsageHistory(history(), null));

        server.verify();
        assertTrue(thrown.getMessage().contains("409"), thrown.getMessage());
    }

    /**
     * §14.1 end to end: what {@code saveUsageHistory} puts on the wire is what
     * {@code loadUsageHistory} reads back — {@link Instant} as an ISO-8601 string, the calendar
     * days as ISO map keys, the {@code ETag} of the response carried into
     * {@link UsageBlobClient.LoadedHistory} for the next conditional write. The bare
     * {@code ObjectMapper} bean would already fail on the write half of this. / Der Round-Trip
     * einmal komplett über HTTP.
     *
     * <p>The {@code 404} of the very first run is asserted in the same flow: it is not an error but
     * an empty, <b>usable</b> history without an ETag, which is what makes the following write use
     * {@code If-None-Match: *} (Alt Flow A). / Der Erstlauf liefert eine leere, brauchbare
     * Historie.
     */
    @Test
    void roundTripsTheWrittenFileBackOverHttpIncludingTheFirstRun404() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UsageBlobClient client = new IdentityPinnedBlobClient(properties(ACCOUNT), builder);
        AtomicReference<String> written = new AtomicReference<>();
        expectTokenRequest(server);
        server.expect(requestTo(BLOB_URL)).andExpect(method(HttpMethod.GET))
            .andRespond(withResourceNotFound());
        expectTokenRequest(server);
        server.expect(requestTo(BLOB_URL)).andExpect(method(HttpMethod.PUT))
            .andExpect(captureBody(written))
            .andRespond(withStatus(HttpStatus.CREATED));
        expectTokenRequest(server);
        server.expect(requestTo(BLOB_URL)).andExpect(method(HttpMethod.GET))
            // The container hands back exactly the bytes of the preceding PUT, plus its new ETag.
            .andRespond(request -> withSuccess(written.get(), MediaType.APPLICATION_JSON)
                .header("ETag", ETAG)
                .createResponse(request));

        UsageBlobClient.LoadedHistory firstRun = client.loadUsageHistory();
        client.saveUsageHistory(history(), firstRun.etag());
        UsageBlobClient.LoadedHistory reloaded = client.loadUsageHistory();

        server.verify();
        assertTrue(firstRun.usable(), "a missing blob is the normal first run, not a failure");
        assertTrue(firstRun.days().isEmpty());
        assertNull(firstRun.etag(), "no ETag yet — the write has to use If-None-Match: *");

        assertTrue(reloaded.usable());
        assertEquals(ETAG, reloaded.etag(), "the ETag must reach the next conditional write");
        assertEquals(NOW, reloaded.history().updatedAt(), "Instant survives the round trip (§14.1)");
        assertEquals(List.of(DAY), List.copyOf(reloaded.days().keySet()),
            "the ISO map key is parsed back into a LocalDate (§10.3)");
        assertEquals(41L, reloaded.days().get(DAY).visits().mobile());
        assertEquals(7L, reloaded.days().get(DAY).sessionsWithBooking());
    }

    // ---- HTTP fixtures / HTTP-Testdaten ----

    private static final String ACCOUNT = "gsterminbuchungstats";
    private static final String BLOB_URL =
        "https://" + ACCOUNT + ".blob.core.windows.net/usage-stats/usage-daily.json";
    private static final String IDENTITY_ENDPOINT = "http://169.254.169.254/metadata/identity/oauth2/token";
    private static final String IDENTITY_SECRET = "identity-header-secret";
    private static final String TOKEN = "managed-identity-access-token";
    private static final String ETAG = "\"0x8DC1\"";

    /**
     * The production client with the two managed-identity lookups pinned to the mock server / der
     * Produktionsclient mit fixierter Managed-Identity-Umgebung. Everything else — URL building,
     * headers, status handling, mapper — is the production code under test. / Nur die Umgebung ist
     * ersetzt.
     */
    private static final class IdentityPinnedBlobClient extends UsageBlobClient {

        private IdentityPinnedBlobClient(UsageStatsProperties properties, RestClient.Builder builder) {
            super(properties, builder);
        }

        @Override
        protected String identityEndpoint() {
            return IDENTITY_ENDPOINT;
        }

        @Override
        protected String identityHeader() {
            return IDENTITY_SECRET;
        }
    }

    /**
     * The managed-identity call that precedes every blob request / der Token-Abruf vor jeder
     * Blob-Anfrage. Asserts the storage data plane is requested with the shared secret in
     * {@code X-IDENTITY-HEADER} — the header is never logged and never leaves this call.
     */
    private static void expectTokenRequest(MockRestServiceServer server) {
        server.expect(UsageBlobClientTest::assertIsTokenRequest)
            .andExpect(header("X-IDENTITY-HEADER", IDENTITY_SECRET))
            .andRespond(withSuccess("{\"access_token\":\"" + TOKEN + "\",\"expires_in\":\"3599\"}",
                MediaType.APPLICATION_JSON));
    }

    /** Query-string assertions that survive either URI encoding / unabhängig von der Kodierung. */
    private static void assertIsTokenRequest(ClientHttpRequest request) {
        String uri = request.getURI().toString();
        assertTrue(uri.startsWith(IDENTITY_ENDPOINT), uri);
        assertTrue(uri.contains("storage.azure.com"), "the storage data plane is the resource: " + uri);
        assertTrue(uri.contains("api-version=2019-08-01"), uri);
    }

    /** Keeps the request body for assertions after the call / hält den Request-Body fest. */
    private static RequestMatcher captureBody(AtomicReference<String> target) {
        return request -> target.set(((MockClientHttpRequest) request).getBodyAsString());
    }

    /** No such header at all — {@code header(...)} can only assert a value. / Header fehlt ganz. */
    private static RequestMatcher headerDoesNotExist(String name) {
        return request -> assertFalse(request.getHeaders().containsHeader(name),
            "the request must not carry " + name);
    }

    private static UsageHistory history() {
        SortedMap<LocalDate, DailyUsage> days = new TreeMap<>();
        days.put(DAY, dayUsage(41));
        return UsageBlobClient.toHistory(days, NOW);
    }

    // ---- helpers ----

    /**
     * The client's own mapper / der eigene Mapper des Clients. It is deliberately private and
     * static (§14.1) — reading it reflectively is what makes the round-trip assertion test the
     * production instance instead of a look-alike built in the test. / Nur so prüft der Test den
     * echten Mapper.
     */
    private static ObjectMapper blobMapper() throws Exception {
        Field field = UsageBlobClient.class.getDeclaredField("BLOB_MAPPER");
        field.setAccessible(true);
        return (ObjectMapper) field.get(null);
    }

    private static DailyUsage dayUsage(long mobileVisits) {
        return new DailyUsage(new DeviceBreakdown(mobileVisits, 3, 12, 1), 55, 53, 9, 9, 22, 7);
    }

    private static UsageStatsProperties properties(String account) {
        return new UsageStatsProperties(true, "0 */15 * * * *", "0 30 3 * * *", "Europe/Berlin",
            24, 60, new UsageStatsProperties.Storage(account, "usage-stats", "usage-daily.json"));
    }
}
