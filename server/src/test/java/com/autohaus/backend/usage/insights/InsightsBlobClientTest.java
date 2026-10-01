package com.autohaus.backend.usage.insights;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.autohaus.backend.config.UsageInsightsProperties;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.usage.BlobAccess;
import com.autohaus.backend.usage.UsageBlobClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
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
 * Tolerant reading and ordered writing of {@code usage-insights.json} (REQ-038 TC-24 blob part,
 * TC-63, BR-18, E6) / tolerantes Lesen und geordnetes Schreiben der Insights-Historie.
 *
 * <p>The round trip runs against the literal JSON example of §10.6; the HTTP half (404 first
 * run, {@code If-Match}, {@code If-None-Match: *}, 412) runs against {@link MockRestServiceServer}
 * with the managed-identity lookups pinned, pattern {@code UsageBlobClientTest}. Only the
 * {@code usage-insights.json} URL is expected — a request for {@code usage-daily.json} would fail
 * the mock server. / Nur die Insights-Datei wird angefragt.
 */
class InsightsBlobClientTest {

    private static final ObjectMapper OM = new ObjectMapper();

    /** §10.6, verbatim. */
    static final String EXAMPLE = """
        {
          "schemaVersion": 1,
          "updatedAt": "2026-10-23T01:52:10Z",
          "collectingSince": "2026-09-01",
          "funnelCollectingSince": null,
          "dropoffCollectingSince": null,
          "days": {
            "2026-10-22": {
              "bookings": 11,
              "bookingDaypart": { "day": 6, "evening": 4, "night": 1 },
              "slotGrid": { "MO": { "early": 0, "morning": 1, "midday": 0, "afternoon": 1, "late": 0 } },
              "slotUnknown": 0,
              "leadTime": { "0-1": 1, "2-3": 4, "4-7": 3, "8-14": 2, "15-30": 1, "31+": 0, "unknown": 0 },
              "groups": { "inspection": 5, "tire-change": 3, "extras": 4, "pickup": 1, "unknown": 0 },
              "mobility": 2,
              "serviceViews": 96,
              "funnel": null
            }
          },
          "branchDays": {
            "2026-10-22": {
              "68f0a1b2c3d4e5f6a7b8c9d0": { "bookings": 2, "serviceViews": 19, "groups": { "inspection": 1, "extras": 1 },
                                            "mobility": 0, "offHours": 1, "slotLate": 0 }
            }
          },
          "branchMonths": {
            "2026-09": {
              "68f0a1b2c3d4e5f6a7b8c9d0": { "bookings": 58, "serviceViews": 410, "groups": { "inspection": 31, "extras": 12 },
                                            "mobility": 9, "offHours": 22, "slotLate": 7 }
            }
          }
        }
        """;

    /** §10.6 funnel with drop-off (N1, BR-30). */
    static final String FUNNEL_DAY = """
        {
          "schemaVersion": 1,
          "updatedAt": "2026-10-23T01:52:10Z",
          "collectingSince": null,
          "funnelCollectingSince": "2026-10-01",
          "dropoffCollectingSince": "2026-10-10",
          "days": {
            "2026-10-22": {
              "bookings": null, "bookingDaypart": null, "slotGrid": null, "slotUnknown": null,
              "leadTime": null, "groups": null, "mobility": null, "serviceViews": null,
              "funnel": {
                "all": { "visits": 96, "services": 70, "notes": 55, "search": 47, "booking": 11 },
                "byDevice": {
                  "mobile": { "visits": 60, "services": 40, "notes": 30, "search": 25, "booking": 6 },
                  "tablet": { "visits": 6, "services": 5, "notes": 5, "search": 4, "booking": 1 },
                  "desktop": { "visits": 28, "services": 24, "notes": 19, "search": 17, "booking": 4 },
                  "unknown": { "visits": 2, "services": 1, "notes": 1, "search": 1, "booking": 0 }
                },
                "dropoff": {
                  "byScenarioStep": { "server-error": { "1": 2, "2": 1, "3": 0, "4": 1 },
                                      "no-slots": { "1": 0, "2": 0, "3": 0, "4": 7 },
                                      "quiet-1": { "1": 22, "2": 0, "3": 0, "4": 0 } },
                  "byScenarioDevice": { "server-error": { "mobile": 3, "tablet": 0, "desktop": 1, "unknown": 0 } }
                }
              }
            }
          },
          "branchDays": {},
          "branchMonths": {}
        }
        """;

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void attachLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(InsightsBlobClient.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detachLog() {
        ((Logger) LoggerFactory.getLogger(InsightsBlobClient.class)).detachAppender(logs);
    }

    // ---- round trip / Round-Trip ----

    @Test
    @DisplayName("TC-24: the §10.6 example parses into the records")
    void parsesTheDocumentedExample() {
        InsightsBlobClient.Loaded loaded = InsightsBlobClient.parse(EXAMPLE, "\"0x1\"");

        assertTrue(loaded.usable());
        assertEquals("\"0x1\"", loaded.etag());
        InsightsHistory history = loaded.history();
        assertEquals(1, history.schemaVersion());
        assertEquals(Instant.parse("2026-10-23T01:52:10Z"), history.updatedAt());
        assertEquals(LocalDate.of(2026, 9, 1), history.collectingSince());
        assertNull(history.funnelCollectingSince());
        assertNull(history.dropoffCollectingSince());
        InsightsDay day = history.days().get(LocalDate.of(2026, 10, 22));
        assertEquals(11L, day.bookings());
        assertEquals(4L, day.bookingDaypart().get("evening"));
        assertEquals(1L, day.slotGrid().get("MO").get("afternoon"));
        assertEquals(3L, day.groups().get("tire-change"));
        assertEquals(96L, day.serviceViews());
        assertNull(day.funnel(), "funnel: null → not collected (§5.4)");
        BranchCounts branchDay = history.branchDays().get(LocalDate.of(2026, 10, 22)).get("68f0a1b2c3d4e5f6a7b8c9d0");
        assertEquals(new BranchCounts(2L, 19L, Map.of("inspection", 1L, "extras", 1L), 0L, 1L, 0L), branchDay);
        assertEquals(58L, history.branchMonths().get(YearMonth.of(2026, 9)).get("68f0a1b2c3d4e5f6a7b8c9d0").bookings());
    }

    @Test
    @DisplayName("TC-24: parse → serialize reproduces the §10.6 example exactly (Include.ALWAYS)")
    void roundTripsTheDocumentedExample() throws IOException {
        String written = InsightsBlobClient.serialize(InsightsBlobClient.parse(EXAMPLE, null).history());

        assertEquals(OM.readTree(EXAMPLE), OM.readTree(written));
        assertTrue(written.contains("\"funnelCollectingSince\":null"), "nulls are written explicitly");
        assertTrue(written.contains("\"updatedAt\":\"2026-10-23T01:52:10Z\""), "ISO timestamp, no epoch");
    }

    @Test
    @DisplayName("TC-24/TC-63: the funnel with drop-off margins round-trips nested (§10.6, BR-30)")
    void roundTripsTheFunnelWithTheDropoffMargins() throws IOException {
        InsightsBlobClient.Loaded loaded = InsightsBlobClient.parse(FUNNEL_DAY, null);

        assertTrue(loaded.usable());
        InsightsDay day = loaded.history().days().get(LocalDate.of(2026, 10, 22));
        assertNull(day.bookings(), "bookings off → null, never 0");
        FunnelDay funnel = day.funnel();
        assertEquals(new FunnelCounts(96L, 70L, 55L, 47L, 11L), funnel.all());
        assertEquals(6L, funnel.byDevice().get("mobile").booking());
        assertEquals(7L, funnel.dropoff().byScenarioStep().get("no-slots").get("4"));
        assertEquals(3L, funnel.dropoff().byScenarioDevice().get("server-error").get("mobile"),
            "nested like the blob: scenario → device class → sessions");
        assertEquals(LocalDate.of(2026, 10, 10), loaded.history().dropoffCollectingSince());

        String written = InsightsBlobClient.serialize(loaded.history());
        assertEquals(OM.readTree(FUNNEL_DAY), OM.readTree(written));
    }

    @Test
    @DisplayName("TC-24: serialize writes the calendar keys in ascending order")
    void writesCalendarKeysInAscendingOrder() {
        SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
        days.put(LocalDate.of(2026, 10, 22), day(3L));
        days.put(LocalDate.of(2026, 10, 1), day(1L));
        days.put(LocalDate.of(2026, 9, 30), day(2L));
        SortedMap<YearMonth, Map<String, BranchCounts>> months = new TreeMap<>();
        months.put(YearMonth.of(2026, 9), Map.of("b", counts(5L)));
        months.put(YearMonth.of(2025, 12), Map.of("b", counts(4L)));
        InsightsHistory history = new InsightsHistory(1, Instant.parse("2026-10-23T01:52:10Z"),
            null, null, null, days, new TreeMap<>(), months);

        String json = InsightsBlobClient.serialize(history);

        assertTrue(json.indexOf("\"2026-09-30\"") < json.indexOf("\"2026-10-01\""), json);
        assertTrue(json.indexOf("\"2026-10-01\"") < json.indexOf("\"2026-10-22\""), json);
        assertTrue(json.indexOf("\"2025-12\"") < json.indexOf("\"2026-09\""), json);
        assertTrue(json.contains("\"branchDays\":{}"), json);
    }

    @Test
    @DisplayName("§10.6: an empty history serializes with empty maps and explicit nulls")
    void serializesAnEmptyHistory() throws IOException {
        JsonNode json = OM.readTree(InsightsBlobClient.serialize(InsightsHistory.empty()));

        assertEquals(1, json.get("schemaVersion").asInt());
        assertTrue(json.get("updatedAt").isNull());
        assertTrue(json.get("collectingSince").isNull());
        assertTrue(json.get("days").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> InsightsBlobClient.serialize(null));
    }

    @Test
    @DisplayName("robustness: null maps inside records are written as null, not skipped")
    void writesNullMapsAsNull() throws IOException {
        SortedMap<LocalDate, Map<String, BranchCounts>> branchDays = new TreeMap<>();
        java.util.HashMap<String, BranchCounts> withNull = new java.util.HashMap<>();
        withNull.put("b", null);
        withNull.put("c", new BranchCounts(null, null, null, null, null, null));
        branchDays.put(LocalDate.of(2026, 10, 1), withNull);
        SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
        days.put(LocalDate.of(2026, 10, 1), new InsightsDay(null, null, null, null, null, null, null, null,
            new FunnelDay(null, null, new DropoffDay(null, null))));
        days.put(LocalDate.of(2026, 10, 2), null);

        JsonNode json = OM.readTree(InsightsBlobClient.serialize(new InsightsHistory(1, null, null, null,
            null, days, branchDays, null)));

        assertTrue(json.at("/days/2026-10-02").isNull());
        assertTrue(json.at("/days/2026-10-01/funnel/all").isNull());
        assertTrue(json.at("/days/2026-10-01/funnel/byDevice").isNull());
        assertTrue(json.at("/days/2026-10-01/funnel/dropoff/byScenarioStep").isNull());
        assertTrue(json.at("/days/2026-10-01/funnel/dropoff/byScenarioDevice").isNull());
        assertTrue(json.at("/branchDays/2026-10-01/b").isNull());
        assertTrue(json.at("/branchDays/2026-10-01/c/groups").isNull());
        assertTrue(json.get("branchMonths").isEmpty());
    }

    @Test
    @DisplayName("§10.6: the scenario × device margin is written nested, a null inner map as null")
    void writesTheDeviceMarginNested() throws IOException {
        Map<String, Map<String, Long>> byDevice = new java.util.LinkedHashMap<>();
        byDevice.put("no-slots", Map.of("mobile", 2L));
        byDevice.put("quiet-1", null);
        SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
        days.put(LocalDate.of(2026, 10, 1), new InsightsDay(null, null, null, null, null, null, null, null,
            new FunnelDay(null, null, new DropoffDay(Map.of(), byDevice))));

        JsonNode json = OM.readTree(InsightsBlobClient.serialize(new InsightsHistory(1, null, null, null,
            null, days, null, null)));

        JsonNode devices = json.at("/days/2026-10-01/funnel/dropoff/byScenarioDevice");
        assertEquals(2, devices.size());
        assertEquals(2, devices.at("/no-slots/mobile").asInt());
        assertTrue(devices.get("quiet-1").isNull());
    }

    // ---- tolerant reading / tolerantes Lesen (BR-18) ----

    @Test
    @DisplayName("TC-24: unknown fields are ignored on every level")
    void ignoresUnknownFields() {
        String json = EXAMPLE
            .replace("\"schemaVersion\": 1,", "\"schemaVersion\": 1, \"futureField\": {\"x\": [1, 2]},")
            .replace("\"bookings\": 11,", "\"bookings\": 11, \"weather\": \"sunny\",");

        InsightsBlobClient.Loaded loaded = InsightsBlobClient.parse(json, null);

        assertTrue(loaded.usable());
        assertEquals(11L, loaded.history().days().get(LocalDate.of(2026, 10, 22)).bookings());
    }

    @Test
    @DisplayName("TC-24: a missing counter or map stays null („—\"), never 0")
    void missingCountersStayNull() {
        String json = EXAMPLE.replace("\"mobility\": 2,", "").replace("\"slotUnknown\": 0,", "\"slotUnknown\": null,")
            .replace("\"groups\": { \"inspection\": 5, \"tire-change\": 3, \"extras\": 4, \"pickup\": 1, \"unknown\": 0 },", "");

        InsightsDay day = InsightsBlobClient.parse(json, null).history().days().get(LocalDate.of(2026, 10, 22));

        assertNull(day.mobility());
        assertNull(day.slotUnknown());
        assertNull(day.groups());
        assertEquals(11L, day.bookings());
    }

    @Test
    @DisplayName("BR-18: a lower schema version is readable; null day and branch entries are skipped")
    void acceptsOlderVersionsAndSkipsNullEntries() {
        InsightsBlobClient.Loaded loaded = InsightsBlobClient.parse("""
            {"schemaVersion": 0, "days": {"2026-10-01": null}, "branchDays": {"2026-10-01": {"b": null}},
             "branchMonths": {"2026-09": null}, "updatedAt": null}""", "e");

        assertTrue(loaded.usable());
        assertEquals(0, loaded.history().schemaVersion());
        assertTrue(loaded.history().days().isEmpty());
        assertTrue(loaded.history().branchDays().get(LocalDate.of(2026, 10, 1)).isEmpty());
        assertTrue(loaded.history().branchMonths().isEmpty());
    }

    // ---- E6 / unbrauchbarer Inhalt ----

    @Test
    @DisplayName("TC-24/E6: schemaVersion 2 → unusable, ERROR with blob name and schemaVersion")
    void aNewerSchemaVersionIsUnusable() {
        InsightsBlobClient.Loaded loaded = InsightsBlobClient.parse(
            EXAMPLE.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"), "\"0x9\"");

        assertFalse(loaded.usable());
        assertEquals("\"0x9\"", loaded.etag());
        assertTrue(loaded.history().days().isEmpty());
        List<String> errors = messages(Level.ERROR);
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("usage-insights.json"), errors.get(0));
        assertTrue(errors.get(0).contains("schemaVersion 2"), errors.get(0));
    }

    @Test
    @DisplayName("E6: every kind of unusable content is reported, never thrown")
    void reportsEveryKindOfUnusableContent() {
        List<String> broken = List.of(
            "",
            "   ",
            "not json {",
            "[1, 2]",
            "{\"days\": {}}",
            "{\"schemaVersion\": \"1\"}",
            "{\"schemaVersion\": 1.5}",
            EXAMPLE.replace("\"bookings\": 11", "\"bookings\": -1"),
            EXAMPLE.replace("\"bookings\": 11", "\"bookings\": \"11\""),
            EXAMPLE.replace("\"bookings\": 11", "\"bookings\": 11.5"),
            EXAMPLE.replace("\"evening\": 4", "\"evening\": true"),
            EXAMPLE.replace("\"days\": {\n    \"2026-10-22\"", "\"days\": {\n    \"22.10.2026\""),
            EXAMPLE.replace("\"branchDays\": {\n    \"2026-10-22\"", "\"branchDays\": {\n    \"yesterday\""),
            EXAMPLE.replace("\"2026-09\": {", "\"September\": {"),
            EXAMPLE.replace("\"collectingSince\": \"2026-09-01\"", "\"collectingSince\": \"01.09.2026\""),
            EXAMPLE.replace("\"collectingSince\": \"2026-09-01\"", "\"collectingSince\": 20260901"),
            EXAMPLE.replace("\"updatedAt\": \"2026-10-23T01:52:10Z\"", "\"updatedAt\": \"gestern\""),
            EXAMPLE.replace("\"updatedAt\": \"2026-10-23T01:52:10Z\"", "\"updatedAt\": 1761184330"),
            EXAMPLE.replace("\"days\": {", "\"days\": [").replace("\"funnel\": null\n    }\n  },", "\"funnel\": null\n    }\n  ],"),
            EXAMPLE.replace("\"slotGrid\": { \"MO\": {", "\"slotGrid\": { \"MO\": [").replace("\"late\": 0 } },", "\"late\": 0 ] },"),
            FUNNEL_DAY.replace("\"visits\": 96", "\"visits\": -96"),
            FUNNEL_DAY.replace("\"byDevice\": {", "\"byDevice\": [").replace("\"booking\": 0 }\n        },", "\"booking\": 0 }\n        ],"),
            FUNNEL_DAY.replace("\"4\": 7", "\"4\": \"seven\""),
            FUNNEL_DAY.replace("\"dropoff\": {", "\"dropoff\": 5, \"x\": {"));

        for (String json : broken) {
            InsightsBlobClient.Loaded loaded = assertDoesNotThrow(() -> InsightsBlobClient.parse(json, "e"));
            assertFalse(loaded.usable(), "must be E6: " + json);
            assertEquals(InsightsHistory.empty(), loaded.history());
        }
        assertEquals(broken.size(), messages(Level.ERROR).size());
        assertFalse(InsightsBlobClient.parse(null, null).usable());
    }

    // ---- TC-63: drop-off tolerant / Dropoff tolerant ----

    @Test
    @DisplayName("TC-63: an unknown scenario key is discarded with one WARN without values; the sums stay consistent")
    void discardsUnknownDropoffKeys() {
        String json = FUNNEL_DAY
            .replace("\"quiet-1\": { \"1\": 22,", "\"quiet-1\": { \"5\": 3, \"1\": 22,")
            .replace("\"no-slots\": { \"1\": 0,", "\"lost-in-space\": { \"1\": 9 }, \"no-slots\": { \"1\": 0,")
            .replace("\"unknown\": 0 } }", "\"unknown\": 0, \"fridge\": 4 }, \"teleport\": { \"mobile\": 1 } }");

        InsightsBlobClient.Loaded loaded = InsightsBlobClient.parse(json, null);

        assertTrue(loaded.usable(), "unknown keys are not E6");
        DropoffDay dropoff = loaded.history().days().get(LocalDate.of(2026, 10, 22)).funnel().dropoff();
        assertEquals(List.of("server-error", "no-slots", "quiet-1"), List.copyOf(dropoff.byScenarioStep().keySet()));
        assertEquals(Map.of("1", 22L, "2", 0L, "3", 0L, "4", 0L), dropoff.byScenarioStep().get("quiet-1"));
        assertEquals(Map.of("server-error", Map.of("mobile", 3L, "tablet", 0L, "desktop", 1L, "unknown", 0L)),
            dropoff.byScenarioDevice());
        long sum = dropoff.byScenarioStep().values().stream().flatMap(m -> m.values().stream())
            .mapToLong(Long::longValue).sum();
        assertEquals(33L, sum, "the sum row is built from the valid values only");

        List<String> warnings = messages(Level.WARN);
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("4 unknown"), warnings.get(0));
        assertTrue(warnings.stream().noneMatch(m -> m.contains("lost-in-space") || m.contains("fridge")
            || m.contains("teleport")), "WARN without values (E11)");
    }

    @Test
    @DisplayName("TC-63: a foreign device class in the funnel breakdown is discarded as well")
    void discardsForeignFunnelDevices() {
        String json = FUNNEL_DAY.replace("\"unknown\": { \"visits\": 2,",
            "\"toaster\": { \"visits\": 1 }, \"unknown\": { \"visits\": 2,");

        FunnelDay funnel = InsightsBlobClient.parse(json, null).history().days()
            .get(LocalDate.of(2026, 10, 22)).funnel();

        assertEquals(List.of("mobile", "tablet", "desktop", "unknown"), List.copyOf(funnel.byDevice().keySet()));
    }

    @Test
    @DisplayName("TC-63: a missing dropoff is null („—\" with „Erfassung seit …\"), not an empty object")
    void aMissingDropoffIsNull() {
        InsightsBlobClient.Loaded loaded = InsightsBlobClient.parse("""
            {"schemaVersion": 1, "dropoffCollectingSince": "2026-10-10",
             "days": {"2026-10-09": {"funnel": {"all": {"visits": 5, "booking": 1}}}}}""", null);

        assertTrue(loaded.usable());
        FunnelDay funnel = loaded.history().days().get(LocalDate.of(2026, 10, 9)).funnel();
        assertNotNull(funnel);
        assertNull(funnel.dropoff());
        assertNull(funnel.byDevice());
        assertNull(funnel.all().services(), "missing counter → null");
        assertEquals(LocalDate.of(2026, 10, 10), loaded.history().dropoffCollectingSince());
    }

    @Test
    @DisplayName("TC-63: empty or partial drop-off objects are read as they are")
    void readsPartialDropoffObjects() {
        InsightsBlobClient.Loaded loaded = InsightsBlobClient.parse("""
            {"schemaVersion": 1, "days": {"2026-10-22": {"funnel": {"dropoff": {
               "byScenarioStep": {"quiet-2": null}, "byScenarioDevice": {"quiet-2": null}}}},
             "2026-10-23": {"funnel": {"dropoff": {}}}}}""", null);

        assertTrue(loaded.usable());
        DropoffDay partial = loaded.history().days().get(LocalDate.of(2026, 10, 22)).funnel().dropoff();
        assertTrue(partial.byScenarioStep().isEmpty());
        assertTrue(partial.byScenarioDevice().isEmpty());
        DropoffDay empty = loaded.history().days().get(LocalDate.of(2026, 10, 23)).funnel().dropoff();
        assertNull(empty.byScenarioStep());
        assertNull(empty.byScenarioDevice());
    }

    // ---- HTTP / Blob-Zugriff ----

    @Test
    @DisplayName("§5.2: 404 → empty, usable history without ETag (first run)")
    void aMissingBlobIsAnEmptyUsableHistory() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        InsightsBlobClient client = client(builder);
        expectToken(server);
        server.expect(requestTo(BLOB_URL)).andExpect(method(HttpMethod.GET)).andRespond(withResourceNotFound());

        InsightsBlobClient.Loaded loaded = client.load();

        server.verify();
        assertTrue(loaded.usable());
        assertNull(loaded.etag());
        assertEquals(InsightsHistory.empty(), loaded.history());
    }

    @Test
    @DisplayName("TC-24: load parses the body and carries the ETag; E6 content is reported unusable")
    void loadsParsesAndCarriesTheEtag() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        InsightsBlobClient client = client(builder);
        expectToken(server);
        server.expect(requestTo(BLOB_URL)).andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(EXAMPLE, MediaType.APPLICATION_JSON).header("ETag", ETAG));
        expectToken(server);
        server.expect(requestTo(BLOB_URL)).andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"schemaVersion\": 7}", MediaType.APPLICATION_JSON).header("ETag", ETAG));

        InsightsBlobClient.Loaded loaded = client.load();
        InsightsBlobClient.Loaded broken = client.load();

        server.verify();
        assertTrue(loaded.usable());
        assertEquals(ETAG, loaded.etag());
        assertEquals(11L, loaded.history().days().get(LocalDate.of(2026, 10, 22)).bookings());
        assertFalse(broken.usable());
        assertTrue(messages(Level.ERROR).get(0).contains(BLOB), "blob name in the ERROR (E6)");
    }

    @Test
    @DisplayName("TC-24/E5: save sends If-Match with the ETag and the serialized file; 412 is a write conflict")
    void savesWithIfMatchAndMapsA412ToAConflict() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        InsightsBlobClient client = client(builder);
        AtomicReference<String> body = new AtomicReference<>();
        expectToken(server);
        server.expect(requestTo(BLOB_URL)).andExpect(method(HttpMethod.PUT))
            .andExpect(header("If-Match", ETAG))
            .andExpect(header("x-ms-blob-type", "BlockBlob"))
            .andExpect(captureBody(body))
            .andRespond(withStatus(HttpStatus.CREATED));
        expectToken(server);
        server.expect(requestTo(BLOB_URL)).andExpect(method(HttpMethod.PUT))
            .andExpect(header("If-Match", ETAG))
            .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED));

        InsightsHistory history = InsightsBlobClient.parse(EXAMPLE, null).history();
        client.save(history, ETAG);
        assertThrows(UsageBlobClient.BlobPreconditionFailedException.class, () -> client.save(history, ETAG));

        server.verify();
        assertEquals(OM.readTree(EXAMPLE), OM.readTree(body.get()));
    }

    @Test
    @DisplayName("§5.2: the first write uses If-None-Match: *")
    void theFirstWriteUsesIfNoneMatch() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        InsightsBlobClient client = client(builder);
        expectToken(server);
        server.expect(requestTo(BLOB_URL)).andExpect(method(HttpMethod.PUT))
            .andExpect(header("If-None-Match", "*"))
            .andRespond(withStatus(HttpStatus.CREATED));

        client.save(InsightsHistory.empty(), null);

        server.verify();
    }

    @Test
    @DisplayName("E4: without a storage account the client is not configured and load fails loudly")
    void withoutAStorageAccountTheClientIsNotConfigured() {
        InsightsBlobClient client = new InsightsBlobClient(properties(""), null);

        assertFalse(client.isConfigured());
        assertThrows(UsageBlobClient.BlobUnavailableException.class, client::load);
        assertTrue(new InsightsBlobClient(properties(ACCOUNT),
            new UsageInsightsProperties(true, null, "-", " ")).isConfigured(), "blank blob name → default");
    }

    // ---- fixtures / Testdaten ----

    private static final String ACCOUNT = "gsterminbuchungstats";
    private static final String BLOB = "usage-insights.json";
    private static final String BLOB_URL = "https://" + ACCOUNT + ".blob.core.windows.net/usage-stats/" + BLOB;
    private static final String IDENTITY_ENDPOINT = "http://169.254.169.254/metadata/identity/oauth2/token";
    private static final String IDENTITY_SECRET = "identity-header-secret";
    private static final String ETAG = "\"0x8DC2\"";

    private static InsightsBlobClient client(RestClient.Builder builder) {
        BlobAccess access = new BlobAccess(properties(ACCOUNT), BLOB, builder) {
            @Override
            protected String identityEndpoint() {
                return IDENTITY_ENDPOINT;
            }

            @Override
            protected String identityHeader() {
                return IDENTITY_SECRET;
            }
        };
        return new InsightsBlobClient(access);
    }

    private static void expectToken(MockRestServiceServer server) {
        server.expect(InsightsBlobClientTest::assertIsTokenRequest)
            .andExpect(header("X-IDENTITY-HEADER", IDENTITY_SECRET))
            .andRespond(withSuccess("{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
    }

    private static void assertIsTokenRequest(ClientHttpRequest request) {
        String uri = request.getURI().toString();
        assertTrue(uri.startsWith(IDENTITY_ENDPOINT), uri);
        assertTrue(uri.contains("storage.azure.com"), uri);
    }

    private static RequestMatcher captureBody(AtomicReference<String> target) {
        return request -> target.set(((MockClientHttpRequest) request).getBodyAsString());
    }

    static UsageStatsProperties properties(String account) {
        return new UsageStatsProperties(true, "0 */15 * * * *", "0 30 3 * * *", "Europe/Berlin", null, 24, 60,
            new UsageStatsProperties.Storage(account, "usage-stats", "usage-daily.json"));
    }

    private static InsightsDay day(Long bookings) {
        return new InsightsDay(bookings, Map.of(), Map.of(), 0L, Map.of(), Map.of(), 0L, 0L, null);
    }

    private static BranchCounts counts(Long bookings) {
        return new BranchCounts(bookings, 0L, Map.of(), 0L, 0L, 0L);
    }

    private List<String> messages(Level level) {
        return logs.list.stream()
            .filter(event -> event.getLevel() == level)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }
}
