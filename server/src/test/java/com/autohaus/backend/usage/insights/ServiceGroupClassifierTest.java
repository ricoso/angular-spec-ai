package com.autohaus.backend.usage.insights;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.autohaus.backend.common.filter.RequestIdFilter;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-group classification (REQ-038 TC-20, TC-21, BR-11, E9) / Servicegruppen-Klassifikation.
 *
 * <p>SoftNet is mocked at the {@link SoftnetApiClient} boundary with hand-built tag and operation
 * lists in the shape {@code JobTypesService} reads them ({@code id}, {@code name}, {@code rank},
 * {@code tags}); the cache runs against a mutable clock and the 30-second retry wait is recorded
 * instead of slept. / SoftNet wird gemockt, der Cache läuft gegen eine verstellbare Uhr.
 */
@ExtendWith(MockitoExtension.class)
class ServiceGroupClassifierTest {

    private static final String GROUP = "6908a1b2c3d4e5f6a7b8c9d1";
    private static final String OTHER_GROUP = "6908a1b2c3d4e5f6a7b8c9d2";

    private static final String OP_PICKUP = "6908a1b2c3d4e5f6a7b8c9e1";
    private static final String OP_TIRE = "6908a1b2c3d4e5f6a7b8c9e2";
    private static final String OP_TIRE_2 = "6908a1b2c3d4e5f6a7b8c9e3";
    private static final String OP_TWO_TAGS = "6908a1b2c3d4e5f6a7b8c9e4";
    private static final String OP_NO_TAGS = "6908a1b2c3d4e5f6a7b8c9e5";
    private static final String OP_INSPECTION = "6908a1b2c3d4e5f6a7b8c9e6";
    private static final String OP_ODD_RANK = "6908a1b2c3d4e5f6a7b8c9e7";
    private static final String OP_FOREIGN_TAG = "6908a1b2c3d4e5f6a7b8c9e8";
    private static final String OP_UNKNOWN = "6908a1b2c3d4e5f6a7b8c9ff";

    private static final ObjectMapper OM = new ObjectMapper();

    @Mock private SoftnetApiClient softnet;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-23T01:45:00Z"));
    private final List<Duration> sleeps = new ArrayList<>();
    private final List<String> traceIdsDuringCalls = new ArrayList<>();
    private ListAppender<ILoggingEvent> logs;
    private ServiceGroupClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ServiceGroupClassifier(softnet, clock, sleeps::add);
        Logger logger = (Logger) LoggerFactory.getLogger(ServiceGroupClassifier.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(ServiceGroupClassifier.class)).detachAppender(logs);
        MDC.clear();
    }

    // ---- TC-20: BR-11 rules / Regeln ----

    @Test
    @DisplayName("TC-20: pickup, tire-change, extras, unknown, unknown (BR-11)")
    void classifiesEachOperationByTheBr11Rules() {
        givenCatalogue(GROUP);

        assertEquals(Set.of(ServiceGroup.PICKUP), classify(OP_PICKUP), "Hol & Bringservice (7 tags)");
        assertEquals(Set.of(ServiceGroup.TIRE_CHANGE), classify(OP_TIRE), "one tag with rank 3");
        assertEquals(Set.of(ServiceGroup.EXTRAS), classify(OP_TWO_TAGS), "two tags");
        assertEquals(Set.of(ServiceGroup.UNKNOWN), classify(OP_NO_TAGS), "no tag");
        assertEquals(Set.of(ServiceGroup.UNKNOWN), classify(OP_UNKNOWN), "unknown operation id");
        assertEquals(Set.of(ServiceGroup.INSPECTION), classify(OP_INSPECTION), "one tag with rank 0");
    }

    @Test
    @DisplayName("TC-20: tag with an unknown rank or a tag missing from the tag list → unknown")
    void aTagWithAnUnknownRankOrAForeignTagIsUnknown() {
        givenCatalogue(GROUP);

        assertEquals(Set.of(ServiceGroup.UNKNOWN), classify(OP_ODD_RANK));
        assertEquals(Set.of(ServiceGroup.UNKNOWN), classify(OP_FOREIGN_TAG));
    }

    @Test
    @DisplayName("TC-20: a tireChange booking with two tire operations counts tire-change exactly once")
    void aTireChangeBookingCountsTireChangeExactlyOnce() {
        givenCatalogue(GROUP);

        Set<ServiceGroup> groups = classifier.classify(GROUP, List.of(OP_TIRE, OP_TIRE_2), "tireChange");

        assertEquals(Set.of(ServiceGroup.TIRE_CHANGE), groups);
        assertEquals(1, groups.size());
    }

    @Test
    @DisplayName("TC-20: variant tireChange always adds tire-change; each group once per booking")
    void theTireChangeVariantAlwaysAddsTireChange() {
        givenCatalogue(GROUP);

        Set<ServiceGroup> groups = classifier.classify(GROUP,
            List.of(OP_INSPECTION, OP_PICKUP, OP_PICKUP, OP_TWO_TAGS, OP_UNKNOWN), "tireChange");

        assertEquals(Set.of(ServiceGroup.INSPECTION, ServiceGroup.PICKUP, ServiceGroup.EXTRAS,
            ServiceGroup.UNKNOWN, ServiceGroup.TIRE_CHANGE), groups);
    }

    @Test
    @DisplayName("TC-20: the pickup rule matches case-insensitively and wins over the tag rule")
    void thePickupRuleIsCaseInsensitiveAndPrecedesTheTagRule() {
        when(softnet.getList(ServiceGroupClassifier.TAGS_ENDPOINT, GROUP)).thenReturn(tags());
        when(softnet.getOperations(GROUP)).thenReturn(json("""
            [{"id":"%s","name":"HOL- UND BRINGSERVICE","tags":["t3"]}]""".formatted(OP_PICKUP)));

        assertEquals(Set.of(ServiceGroup.PICKUP), classify(OP_PICKUP));
    }

    @Test
    @DisplayName("TC-20: a booking without operations is unknown (tire-change only for tireChange)")
    void aBookingWithoutOperationsIsUnknownWithoutAnySoftnetCall() {
        assertEquals(Set.of(ServiceGroup.UNKNOWN), classifier.classify(GROUP, List.of(), "default"));
        assertEquals(Set.of(ServiceGroup.UNKNOWN), classifier.classify(GROUP, null, "default"));
        assertEquals(Set.of(ServiceGroup.TIRE_CHANGE), classifier.classify(GROUP, List.of(), "tireChange"));
        assertEquals(Set.of(ServiceGroup.UNKNOWN), classifier.classify(null, List.of(OP_TIRE), "default"));

        verify(softnet, never()).getOperations(anyString());
        verify(softnet, never()).getList(anyString(), anyString());
    }

    @Test
    @DisplayName("TC-20: operations given as {id} objects inside tags are read like plain ids")
    void readsTagReferencesGivenAsObjects() {
        when(softnet.getList(ServiceGroupClassifier.TAGS_ENDPOINT, GROUP)).thenReturn(tags());
        when(softnet.getOperations(GROUP)).thenReturn(json("""
            [{"id":"%s","name":"Bremsflüssigkeit wechseln","tags":[{"id":"t2"}]}]""".formatted(OP_TIRE)));

        assertEquals(Set.of(ServiceGroup.BRAKE_FLUID), classify(OP_TIRE));
    }

    // ---- TC-21: cache and load failures / Cache und Ladefehler ----

    @Test
    @DisplayName("TC-21: the second day with the same groupId causes no SoftNet call (6 h cache)")
    void cachesTheCatalogueForSixHours() {
        givenCatalogue(GROUP);

        classify(OP_TIRE);
        clock.advance(Duration.ofHours(5).plusMinutes(59));
        classify(OP_TWO_TAGS);

        verify(softnet, times(1)).getOperations(GROUP);
        verify(softnet, times(1)).getList(ServiceGroupClassifier.TAGS_ENDPOINT, GROUP);

        clock.advance(Duration.ofMinutes(2));
        classify(OP_TIRE);

        verify(softnet, times(2)).getOperations(GROUP);
        assertEquals(Duration.ofHours(6), ServiceGroupClassifier.CACHE_TTL);
    }

    @Test
    @DisplayName("TC-21: 500 twice → one retry after 30 s, then the day is aborted (E9)")
    void aTransientFailureIsRetriedOnceAfterThirtySecondsThenAbortsTheDay() {
        when(softnet.getList(ServiceGroupClassifier.TAGS_ENDPOINT, GROUP))
            .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
            .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(ServiceGroupClassifier.CatalogUnavailableException.class, () -> classify(OP_TIRE));

        assertEquals(List.of(Duration.ofSeconds(30)), sleeps, "exactly one wait of 30 s");
        verify(softnet, times(2)).getList(ServiceGroupClassifier.TAGS_ENDPOINT, GROUP);
        assertTrue(messages(Level.WARN).stream().anyMatch(m -> m.contains(GROUP)));
    }

    @Test
    @DisplayName("TC-21: a transient failure that heals in the retry classifies normally")
    void aTransientFailureThatHealsInTheRetryClassifiesNormally() {
        when(softnet.getList(ServiceGroupClassifier.TAGS_ENDPOINT, GROUP))
            .thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY))
            .thenReturn(tags());
        when(softnet.getOperations(GROUP)).thenReturn(operations());

        assertEquals(Set.of(ServiceGroup.TIRE_CHANGE), classify(OP_TIRE));
        assertEquals(1, sleeps.size());
    }

    @Test
    @DisplayName("TC-21: timeout and 429 are transient as well (E9)")
    void timeoutsAndRateLimitsAreTransient() {
        when(softnet.getList(ServiceGroupClassifier.TAGS_ENDPOINT, GROUP))
            .thenThrow(new ResourceAccessException("Read timed out", new SocketTimeoutException()))
            .thenThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        assertThrows(ServiceGroupClassifier.CatalogUnavailableException.class, () -> classify(OP_TIRE));
        assertEquals(1, sleeps.size());
    }

    @Test
    @DisplayName("TC-21: a permanently refused group (403) counts unknown, the other group correctly")
    void aPermanentlyRefusedGroupCountsUnknownWhileTheOtherStaysCorrect() {
        when(softnet.getList(ServiceGroupClassifier.TAGS_ENDPOINT, OTHER_GROUP))
            .thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden",
                HttpHeaders.EMPTY,
                "{\"name\":\"AccessContextError\",\"message\":\"Client does not have access to group\"}"
                    .getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        givenCatalogue(GROUP);

        assertEquals(Set.of(ServiceGroup.UNKNOWN),
            classifier.classify(OTHER_GROUP, List.of(OP_TIRE, OP_INSPECTION), "default"));
        assertEquals(Set.of(ServiceGroup.TIRE_CHANGE), classify(OP_TIRE));

        assertTrue(sleeps.isEmpty(), "a permanent refusal is not retried");
        assertTrue(messages(Level.WARN).stream().anyMatch(m ->
                m.contains("group " + OTHER_GROUP + " not readable (403)")),
            "WARN with the groupId (E9): " + messages(Level.WARN));

        // The refusal is cached like a catalogue — no hammering within 6 h.
        classifier.classify(OTHER_GROUP, List.of(OP_TIRE), "default");
        verify(softnet, times(1)).getList(ServiceGroupClassifier.TAGS_ENDPOINT, OTHER_GROUP);
    }

    @Test
    @DisplayName("TC-21: 401 and an AccessContextError body are permanent refusals")
    void unauthorizedAndAccessContextErrorsArePermanent() {
        assertEquals(401, ServiceGroupClassifier.permanentStatus(
            HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY,
                new byte[0], StandardCharsets.UTF_8)));
        assertEquals(400, ServiceGroupClassifier.permanentStatus(
            HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
                "AccessContextError".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));
        assertEquals(404, ServiceGroupClassifier.permanentStatus(new RuntimeException("wrapped",
            HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY,
                new byte[0], StandardCharsets.UTF_8))));
        assertNull(ServiceGroupClassifier.permanentStatus(
            new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE)));
        assertNull(ServiceGroupClassifier.permanentStatus(
            HttpClientErrorException.create(HttpStatus.REQUEST_TIMEOUT, "Timeout", HttpHeaders.EMPTY,
                new byte[0], StandardCharsets.UTF_8)));
        assertNull(ServiceGroupClassifier.permanentStatus(new ResourceAccessException("down")));
        assertNull(ServiceGroupClassifier.permanentStatus(new IllegalStateException("token")));
    }

    @Test
    @DisplayName("TC-21: a refusal in the retry is still handled as permanent")
    void aRefusalInTheRetryIsHandledAsPermanent() {
        when(softnet.getList(ServiceGroupClassifier.TAGS_ENDPOINT, GROUP))
            .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
            .thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        assertEquals(Set.of(ServiceGroup.UNKNOWN), classify(OP_TIRE));
        assertEquals(1, sleeps.size());
    }

    @Test
    @DisplayName("TC-21: an interrupted retry wait aborts the day and keeps the interrupt flag")
    void anInterruptedRetryWaitAbortsTheDay() {
        classifier = new ServiceGroupClassifier(softnet, clock, duration -> {
            throw new InterruptedException("shutdown");
        });
        when(softnet.getList(ServiceGroupClassifier.TAGS_ENDPOINT, GROUP))
            .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        try {
            assertThrows(ServiceGroupClassifier.CatalogUnavailableException.class, () -> classify(OP_TIRE));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("TC-21/BR-15: during the SoftNet calls MDC traceId = job.insightsrollup")
    void setsTheJobTraceIdDuringTheSoftnetCallsWhenNoneIsSet() {
        when(softnet.getList(ServiceGroupClassifier.TAGS_ENDPOINT, GROUP)).thenAnswer(invocation -> {
            traceIdsDuringCalls.add(MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY));
            return tags();
        });
        when(softnet.getOperations(GROUP)).thenAnswer(invocation -> {
            traceIdsDuringCalls.add(MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY));
            return operations();
        });

        classify(OP_TIRE);

        assertEquals(List.of("job.insightsrollup", "job.insightsrollup"), traceIdsDuringCalls);
        assertNull(MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY), "restored after the calls");
    }

    @Test
    @DisplayName("BR-15: an existing trace id (the rollup's) is left untouched")
    void keepsAnExistingTraceId() {
        MDC.put(RequestIdFilter.TRACE_ID_MDC_KEY, InsightsScheduler.TRACE_ID);
        when(softnet.getList(eq(ServiceGroupClassifier.TAGS_ENDPOINT), eq(GROUP))).thenReturn(tags());
        when(softnet.getOperations(GROUP)).thenReturn(operations());

        classify(OP_TIRE);

        assertEquals(InsightsScheduler.TRACE_ID, MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY));
    }

    @Test
    @DisplayName("TC-21: clearing the cache forces a reload")
    void clearingTheCacheForcesAReload() {
        givenCatalogue(GROUP);

        classify(OP_TIRE);
        classifier.clearCache();
        classify(OP_TIRE);

        verify(softnet, times(2)).getOperations(GROUP);
    }

    @Test
    @DisplayName("production constructor wires the real clock and sleeper")
    void productionConstructorIsUsable() {
        ServiceGroupClassifier production = new ServiceGroupClassifier(softnet);
        assertEquals(Set.of(ServiceGroup.UNKNOWN), production.classify(GROUP, List.of(), "default"));
    }

    // ---- fixtures / Testdaten ----

    private Set<ServiceGroup> classify(String operationId) {
        return classifier.classify(GROUP, List.of(operationId), "default");
    }

    private void givenCatalogue(String groupId) {
        when(softnet.getList(ServiceGroupClassifier.TAGS_ENDPOINT, groupId)).thenReturn(tags());
        when(softnet.getOperations(groupId)).thenReturn(operations());
    }

    /** The seven SoftNet tags with ranks 0–6 plus one with a rank outside the catalogue. */
    private static List<JsonNode> tags() {
        return json("""
            [{"id":"t0","name":"Inspektion","rank":0},{"id":"t1","name":"TÜV","rank":1},
             {"id":"t2","name":"Bremsflüssigkeit","rank":2},{"id":"t3","name":"Räderwechsel","rank":3},
             {"id":"t4","name":"Aktionen","rank":4},{"id":"t5","name":"Reparatur","rank":5},
             {"id":"t6","name":"Karosserie","rank":6},{"id":"t9","name":"Sonstiges","rank":9}]""");
    }

    private static List<JsonNode> operations() {
        return json("""
            [{"id":"%s","name":"Hol & Bringservice","tags":["t0","t1","t2","t3","t4","t5","t6"]},
             {"id":"%s","name":"Radwechsel ohne Einlagerung","tags":["t3"]},
             {"id":"%s","name":"Räderwechsel - ich bringe meine Räder mit","tags":["t3"]},
             {"id":"%s","name":"Klimaanlagenreinigung","tags":["t4","t5"]},
             {"id":"%s","name":"Wischerblätter erneuern","tags":[]},
             {"id":"%s","name":"Inspektion nach Herstellervorgabe","tags":["t0"]},
             {"id":"%s","name":"Sonderleistung","tags":["t9"]},
             {"id":"%s","name":"Fremder Tag","tags":["tx"]},
             {"name":"ohne ID","tags":["t0"]}]""".formatted(OP_PICKUP, OP_TIRE, OP_TIRE_2, OP_TWO_TAGS,
            OP_NO_TAGS, OP_INSPECTION, OP_ODD_RANK, OP_FOREIGN_TAG));
    }

    private static List<JsonNode> json(String array) {
        try {
            List<JsonNode> nodes = new ArrayList<>();
            OM.readTree(array).forEach(nodes::add);
            return nodes;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> messages(Level level) {
        return logs.list.stream()
            .filter(event -> event.getLevel() == level)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }

    /** A clock the test can move forward / eine vorstellbare Uhr. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
