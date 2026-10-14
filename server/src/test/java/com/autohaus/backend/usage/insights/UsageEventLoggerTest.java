package com.autohaus.backend.usage.insights;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.autohaus.backend.common.filter.RequestIdFilter;
import com.autohaus.backend.config.FeatureProperties;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.config.setup.BranchNotFoundException;
import com.autohaus.backend.config.setup.BranchSetup;
import com.autohaus.backend.config.setup.GroupEntry;
import com.autohaus.backend.config.setup.SetupService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests of the anonymous {@code USAGE_BOOKING} events (REQ-038 §10.2, BR-6, BR-24, E12) / Tests der
 * anonymen Buchungsereignisse: TC-13 (Zeileninhalt), TC-15 (Variante), TC-16 (Sommerzeit-Bucketing)
 * and the decoupling from the booking request — buffer, daily shuffled flush under MDC
 * {@code job.usagebooking}, {@code @PreDestroy}, buffer limit, {@code day} field. / Entkopplung vom
 * Buchungs-Request: Puffer, gemischter Tagesflush, Shutdown-Flush, Obergrenze, Buchungstag.
 */
class UsageEventLoggerTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /** TC-13 clock: 18:30 MESZ on Thursday 22.10.2026 / Buchung am Donnerstagabend. */
    private static final Instant BOOKED_AT = Instant.parse("2026-10-22T16:30:00Z");

    private static final String BRANCH = "68f0a1b2c3d4e5f6a7b8c9d0";
    private static final String OTHER_BRANCH = "68f0a1b2c3d4e5f6a7b8c9ff";
    private static final String GROUP_DEFAULT = "6908a1b2c3d4e5f6a7b8c9d1";
    private static final String GROUP_TIRE = "6908a1b2c3d4e5f6a7b8c9d2";
    private static final String GROUP_DEFAULT_OTHER = "6908a1b2c3d4e5f6a7b8c9d5";
    private static final String OP_1 = "6908a1b2c3d4e5f6a7b8c9e2";
    private static final String OP_2 = "6908a1b2c3d4e5f6a7b8c9e3";
    private static final String SNAP_ID = "snap-4711-internal";

    /** Trace id of the booking request (RequestIdFilter UUID) / Trace-ID des Buchungs-Requests. */
    private static final String REQUEST_TRACE_ID = "3f2a9c1e-7b44-4d0e-9a51-0c6d2e8f1b77";

    /** Thread name of the booking request / Thread des Buchungs-Requests. */
    private static final String REQUEST_THREAD = "http-nio-3000-exec-7";

    /** The eleven keys of BR-6 in their fixed order / die elf Schlüssel in fester Reihenfolge. */
    private static final List<String> KEYS = List.of("v", "day", "branch", "group", "variant", "ops",
        "bookingDaypart", "slotWeekday", "slotDaypart", "leadTime", "mobility");

    private final ObjectMapper om = new ObjectMapper();
    private SetupService setupService;
    private UsageEventLogger usageLogger;

    private Logger logbackLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        setupService = mock(SetupService.class);
        BranchSetup branch = new BranchSetup(BRANCH, "Autohaus Nord", null, List.of(
            new GroupEntry("default", GROUP_DEFAULT, "Autohaus Nord Werkstatt"),
            new GroupEntry("tireChange", GROUP_TIRE, "Autohaus Nord Boxenstopp")));
        when(setupService.findByBranchId(BRANCH)).thenReturn(branch);
        usageLogger = loggerAt(BOOKED_AT);

        logbackLogger = (Logger) LoggerFactory.getLogger(UsageEventLogger.class);
        appender = new CapturingAppender();
        appender.start();
        logbackLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logbackLogger.detachAppender(appender);
        appender.stop();
        MDC.remove(RequestIdFilter.TRACE_ID_MDC_KEY);
    }

    // ---- TC-13 line content / Zeileninhalt ----

    @Test
    @DisplayName("TC-13: writes exactly the whitelisted keys in fixed order and nothing identifying")
    void writesExactlyTheWhitelistedFieldsInFixedOrder() throws Exception {
        ObjectNode snap = snapAt("2026-10-24T07:30:00Z");
        ArrayNode ops = snap.withObject("wishlist").putArray("operations");
        ops.add(OP_1);
        ops.addObject().put("id", OP_2);
        ops.add("not-an-operation");
        ops.addObject().put("id", "XYZ");
        ops.add(42);
        snap.withObject("wishlist").put("rentCategoryid", "682ad3dc6969db66dd27f6c3");

        usageLogger.logBooking(BRANCH, GROUP_DEFAULT, snap, true);

        String line = singleFlushedLine(usageLogger);
        assertEquals("USAGE_BOOKING {\"v\":2,\"day\":\"2026-10-22\",\"branch\":\"" + BRANCH + "\",\"group\":\""
            + GROUP_DEFAULT + "\",\"variant\":\"default\",\"ops\":[\"" + OP_1 + "\",\"" + OP_2 + "\"],"
            + "\"bookingDaypart\":\"evening\",\"slotWeekday\":\"SA\",\"slotDaypart\":\"morning\","
            + "\"leadTime\":\"2-3\",\"mobility\":true}", line);

        JsonNode json = payload(line);
        assertEquals(KEYS, fieldNames(json), "exactly the keys of BR-6, in fixed order");
        assertEquals(UsageBookingEvent.FORMAT_VERSION, json.path("v").asInt());
        assertEquals("2026-10-22", json.path("day").asText(), "booking day, ISO, no time");
        assertEquals("evening", json.path("bookingDaypart").asText());   // 18:30 MESZ
        assertEquals("SA", json.path("slotWeekday").asText());
        assertEquals("morning", json.path("slotDaypart").asText());      // 09:30 MESZ
        assertEquals("2-3", json.path("leadTime").asText());
        assertTrue(json.path("mobility").asBoolean());

        // BR-24: nothing identifying leaks into the line — no slot date, no time of day.
        for (String forbidden : List.of("@", SNAP_ID, "internal", "checkInResource", "Berater",
                "brand", "volkswagen", "model", "Golf", "sessionId", "Musterfrau", "2026-10-24",
                "07:30", "09:30", "16:30", "18:30", "not-an-operation", "XYZ", "Autohaus Nord")) {
            assertFalse(line.contains(forbidden), "line must not contain '" + forbidden + "': " + line);
        }
        assertFalse(line.matches(".*\\d{2}:\\d{2}.*"), "no time of day at all: " + line);
    }

    @Test
    @DisplayName("TC-13: mobility false, night booking and a same-day slot")
    void writesMobilityFalseAndNightDaypart() throws Exception {
        usageLogger = loggerAt(Instant.parse("2026-10-22T21:00:00Z"));   // 23:00 MESZ

        usageLogger.logBooking(BRANCH, GROUP_DEFAULT, snapAt("2026-10-23T14:00:00Z"), false);

        JsonNode json = payload(singleFlushedLine(usageLogger));
        assertEquals("2026-10-22", json.path("day").asText());
        assertEquals("night", json.path("bookingDaypart").asText());
        assertEquals("FR", json.path("slotWeekday").asText());
        assertEquals("late", json.path("slotDaypart").asText());          // 16:00 MESZ
        assertEquals("0-1", json.path("leadTime").asText());
        assertFalse(json.path("mobility").asBoolean());
        assertTrue(json.path("mobility").isBoolean());
    }

    @Test
    @DisplayName("TC-13: operations are capped at MAX_OPS, deduplicated and strictly lowercase hex")
    void capsAndFiltersOperations() throws Exception {
        ObjectNode snap = snapAt("2026-10-24T07:30:00Z");
        ArrayNode ops = snap.withObject("wishlist").putArray("operations");
        ops.add(OP_1);
        ops.add(OP_1);                                        // duplicate / Duplikat
        ops.add(OP_2.toUpperCase());                           // uppercase is not whitelisted
        ops.addNull();
        ops.addObject().put("name", "Inspektion");             // object without id
        List<String> expected = new ArrayList<>(List.of(OP_1));
        for (int i = 0; i < 25; i++) {
            String id = String.format("6908a1b2c3d4e5f6a7b8%04x", i);
            ops.add(id);
            if (expected.size() < UsageBookingEvent.MAX_OPS) {
                expected.add(id);
            }
        }

        usageLogger.logBooking(BRANCH, GROUP_DEFAULT, snap, false);

        JsonNode json = payload(singleFlushedLine(usageLogger));
        List<String> logged = new ArrayList<>();
        json.path("ops").forEach(node -> logged.add(node.asText()));
        assertEquals(UsageBookingEvent.MAX_OPS, logged.size());
        assertEquals(expected, logged);
    }

    @Test
    @DisplayName("TC-13: missing or malformed wishlist yields an empty ops array, never null")
    void writesEmptyOpsWithoutUsableWishlist() throws Exception {
        ObjectNode withoutWishlist = snapAt("2026-10-24T07:30:00Z");
        ObjectNode opsNotArray = snapAt("2026-10-24T07:30:00Z");
        opsNotArray.withObject("wishlist").put("operations", OP_1);

        usageLogger.logBooking(BRANCH, GROUP_DEFAULT, withoutWishlist, false);
        usageLogger.logBooking(BRANCH, GROUP_DEFAULT, opsNotArray, false);

        List<String> lines = flushedLines(usageLogger);
        assertEquals(2, lines.size());
        for (String line : lines) {
            JsonNode ops = payload(line).path("ops");
            assertTrue(ops.isArray(), line);
            assertEquals(0, ops.size(), line);
        }
    }

    // ---- E12 slot without usable time / Snap ohne verwertbare Zeit ----

    @Test
    @DisplayName("E12: missing, blank, non-textual or unparsable checkInTime becomes unknown")
    void bucketsUnusableSlotTimeAsUnknown() throws Exception {
        ObjectNode missing = snapAt(null);
        ObjectNode blank = snapAt(" ");
        ObjectNode garbage = snapAt("morgen frueh");
        ObjectNode numeric = snapAt(null);
        numeric.put("checkInTime", 1_793_000_000L);

        for (JsonNode snap : List.of(missing, blank, garbage, numeric)) {
            usageLogger.logBooking(BRANCH, GROUP_DEFAULT, snap, false);
        }
        usageLogger.logBooking(BRANCH, GROUP_DEFAULT, null, false);

        List<String> lines = flushedLines(usageLogger);
        assertEquals(5, lines.size(), "the booking still counts (R-1, G-1)");
        for (String line : lines) {
            JsonNode json = payload(line);
            assertEquals("unknown", json.path("slotWeekday").asText(), line);
            assertEquals("unknown", json.path("slotDaypart").asText(), line);
            assertEquals("unknown", json.path("leadTime").asText(), line);
            assertEquals("evening", json.path("bookingDaypart").asText(), line);
            assertEquals("2026-10-22", json.path("day").asText(), "the booking day never depends on the slot");
        }
    }

    @Test
    @DisplayName("E12: an ISO time with explicit offset is still understood")
    void acceptsIsoTimeWithOffset() throws Exception {
        usageLogger.logBooking(BRANCH, GROUP_DEFAULT, snapAt("2026-10-24T09:30:00+02:00"), false);

        JsonNode json = payload(singleFlushedLine(usageLogger));
        assertEquals("SA", json.path("slotWeekday").asText());
        assertEquals("morning", json.path("slotDaypart").asText());
        assertEquals("2-3", json.path("leadTime").asText());
    }

    @Test
    @DisplayName("E12: a slot before the booking day is an implausible lead time -> unknown")
    void bucketsSlotInThePastAsUnknownLeadTime() throws Exception {
        usageLogger.logBooking(BRANCH, GROUP_DEFAULT, snapAt("2026-10-20T07:30:00Z"), false);

        assertEquals("unknown", payload(singleFlushedLine(usageLogger)).path("leadTime").asText());
    }

    // ---- whitelist of branch and group / Whitelist Standort und Group ----

    @Test
    @DisplayName("BR-6: a malformed branch id buffers nothing and writes a WARN without values")
    void skipsMalformedBranchIdWithoutLoggingIt() {
        for (String branch : new String[] {"branch-1", BRANCH.toUpperCase(), BRANCH + "0", "", null}) {
            usageLogger.logBooking(branch, GROUP_DEFAULT, snapAt("2026-10-24T07:30:00Z"), false);
        }

        assertEquals(0, usageLogger.flush(), "nothing was buffered");
        assertTrue(infoLines().isEmpty(), "no USAGE_BOOKING line for a foreign branch id");
        List<String> warnings = warnLines();
        assertEquals(5, warnings.size());
        for (String warning : warnings) {
            assertEquals("USAGE_BOOKING skipped: branch id not whitelisted", warning);
        }
        verify(setupService, never()).findByBranchId(anyString());
    }

    @Test
    @DisplayName("BR-6: a well-formed branch that is not in setup.json buffers nothing")
    void skipsBranchMissingFromSetup() {
        when(setupService.findByBranchId(OTHER_BRANCH)).thenThrow(new BranchNotFoundException(OTHER_BRANCH));

        usageLogger.logBooking(OTHER_BRANCH, GROUP_DEFAULT, snapAt("2026-10-24T07:30:00Z"), false);

        assertEquals(0, usageLogger.flush());
        assertTrue(infoLines().isEmpty());
        assertEquals(List.of("USAGE_BOOKING skipped: branch not in setup"), warnLines());
        assertFalse(warnLines().getFirst().contains(OTHER_BRANCH), "the warning carries no values");
    }

    @Test
    @DisplayName("BR-6: a malformed group id buffers nothing and writes a WARN without values")
    void skipsMalformedGroupId() {
        usageLogger.logBooking(BRANCH, "mock-group-id", snapAt("2026-10-24T07:30:00Z"), false);
        usageLogger.logBooking(BRANCH, null, snapAt("2026-10-24T07:30:00Z"), false);

        assertEquals(0, usageLogger.flush());
        assertTrue(infoLines().isEmpty());
        assertEquals(List.of("USAGE_BOOKING skipped: group id not whitelisted",
            "USAGE_BOOKING skipped: group id not whitelisted"), warnLines());
    }

    // ---- TC-15 variant / Variante ----

    @Test
    @DisplayName("TC-15: tireChange booking at a branch without tireChange group logs variant default")
    void logsDefaultWhenResolveGroupIdFellBackToDefault() throws Exception {
        SetupService realSetup = realSetup(true);
        UsageEventLogger logger = new UsageEventLogger(realSetup, Clock.fixed(BOOKED_AT, BERLIN));
        String resolved = realSetup.resolveGroupId(OTHER_BRANCH, "tireChange");   // fallback, §5.5
        assertEquals(GROUP_DEFAULT_OTHER, resolved);

        logger.logBooking(OTHER_BRANCH, resolved, snapAt("2026-10-24T07:30:00Z"), false);

        JsonNode json = payload(singleFlushedLine(logger));
        assertEquals("default", json.path("variant").asText());
        assertEquals(GROUP_DEFAULT_OTHER, json.path("group").asText());
    }

    @Test
    @DisplayName("TC-15: tireChange booking at a branch with tireChange group logs variant tireChange")
    void logsTireChangeWhenTheTireChangeGroupWasResolved() throws Exception {
        SetupService realSetup = realSetup(true);
        UsageEventLogger logger = new UsageEventLogger(realSetup, Clock.fixed(BOOKED_AT, BERLIN));
        String resolved = realSetup.resolveGroupId(BRANCH, "tireChange");

        logger.logBooking(BRANCH, resolved, snapAt("2026-10-24T07:30:00Z"), false);

        JsonNode json = payload(singleFlushedLine(logger));
        assertEquals("tireChange", json.path("variant").asText());
        assertEquals(GROUP_TIRE, json.path("group").asText());
    }

    @Test
    @DisplayName("TC-15/REQ-042: a recall-group booking logs variant recall, not default")
    void logsRecallVariant() throws Exception {
        String recallGroup = "6908a1b2c3d4e5f6a7b8c9e9";
        when(setupService.findByBranchId(BRANCH)).thenReturn(new BranchSetup(BRANCH, "Autohaus Nord", null,
            List.of(new GroupEntry("default", GROUP_DEFAULT, "Werkstatt"),
                new GroupEntry("recall", recallGroup, "Rückrufaktion"))));

        usageLogger.logBooking(BRANCH, recallGroup, snapAt("2026-10-24T07:30:00Z"), false);

        JsonNode json = payload(singleFlushedLine(usageLogger));
        assertEquals("recall", json.path("variant").asText());
        assertEquals(recallGroup, json.path("group").asText());
    }

    @Test
    @DisplayName("TC-15: with the tire-change season switched off the default group is logged")
    void logsDefaultWhenTireChangeSeasonIsOff() throws Exception {
        SetupService realSetup = realSetup(false);
        UsageEventLogger logger = new UsageEventLogger(realSetup, Clock.fixed(BOOKED_AT, BERLIN));
        String resolved = realSetup.resolveGroupId(BRANCH, "tireChange");

        logger.logBooking(BRANCH, resolved, snapAt("2026-10-24T07:30:00Z"), false);

        assertEquals("default", payload(singleFlushedLine(logger)).path("variant").asText());
    }

    @Test
    @DisplayName("TC-15: a group that matches no entry, or an ambiguous one, falls back to default")
    void fallsBackToDefaultForUnmatchedOrAmbiguousGroups() throws Exception {
        String shared = "6908a1b2c3d4e5f6a7b8c9aa";
        when(setupService.findByBranchId(OTHER_BRANCH)).thenReturn(new BranchSetup(OTHER_BRANCH, "Autohaus Süd",
            null, List.of(new GroupEntry("tireChange", shared, "a"), new GroupEntry("default", shared, "b"))));
        String nullGroupsBranch = "68f0a1b2c3d4e5f6a7b8c9ee";
        when(setupService.findByBranchId(nullGroupsBranch))
            .thenReturn(new BranchSetup(nullGroupsBranch, "Autohaus Ost", null, null));
        String nullEntryBranch = "68f0a1b2c3d4e5f6a7b8c9dd";
        when(setupService.findByBranchId(nullEntryBranch)).thenReturn(new BranchSetup(nullEntryBranch,
            "Autohaus West", null, Arrays.asList(null, new GroupEntry("default", GROUP_DEFAULT, "c"))));

        usageLogger.logBooking(BRANCH, "6908a1b2c3d4e5f6a7b8c9bb", snapAt(null), false);   // no entry
        usageLogger.logBooking(OTHER_BRANCH, shared, snapAt(null), false);                  // ambiguous
        usageLogger.logBooking(nullGroupsBranch, GROUP_DEFAULT, snapAt(null), false);       // no groups
        usageLogger.logBooking(nullEntryBranch, GROUP_DEFAULT, snapAt(null), false);        // null entry

        List<String> lines = flushedLines(usageLogger);
        assertEquals(4, lines.size());
        for (String line : lines) {
            assertEquals("default", payload(line).path("variant").asText(), line);
        }
    }

    // ---- TC-16 daylight saving time / Sommerzeit ----

    @ParameterizedTest(name = "TC-16: booking at {0} is {1}")
    @CsvSource({
        "2026-10-23T16:30:00Z, evening",   // 18:30 MESZ (UTC+2)
        "2026-10-26T16:30:00Z, day",       // 17:30 MEZ (UTC+1) after the switch on 25.10.
        "2026-03-28T16:30:00Z, day",       // 17:30 MEZ before the switch on 29.03.
        "2026-03-30T16:30:00Z, evening",   // 18:30 MESZ after the switch
        "2026-10-24T04:30:00Z, night",     // 06:30 MESZ
        "2026-10-24T05:30:00Z, day",       // 07:30 MESZ — same UTC time as the next row ...
        "2026-10-26T05:30:00Z, night",     // 06:30 MEZ  — ... but night after the switch
        "2026-10-26T06:30:00Z, day",       // 07:30 MEZ
        "2026-10-22T20:00:00Z, night"      // 22:00 MESZ — upper bound of evening is exclusive
    })
    @DisplayName("TC-16: booking daypart follows Europe/Berlin across the DST switch")
    void bucketsBookingDaypartInBerlinTime(String bookedAt, String expected) throws Exception {
        UsageEventLogger logger = loggerAt(Instant.parse(bookedAt));
        logger.logBooking(BRANCH, GROUP_DEFAULT, snapAt(null), false);

        assertEquals(expected, payload(singleFlushedLine(logger)).path("bookingDaypart").asText());
    }

    @ParameterizedTest(name = "TC-16: booking at {0} counts for day {1}")
    @CsvSource({
        "2026-10-24T22:30:00Z, 2026-10-25",   // 00:30 MESZ on 25.10. — the UTC date is still the 24th
        "2026-10-25T22:30:00Z, 2026-10-25",   // 23:30 MEZ on 25.10. (the 25-hour day)
        "2026-10-25T23:30:00Z, 2026-10-26",   // 00:30 MEZ on 26.10. — the UTC date is still the 25th
        "2026-03-28T23:30:00Z, 2026-03-29",   // 00:30 MEZ on 29.03. (the 23-hour day)
        "2026-03-29T21:59:59Z, 2026-03-29",   // 23:59:59 MESZ — last second of the 23-hour day
        "2026-03-29T22:00:00Z, 2026-03-30"    // 00:00 MESZ on 30.03.
    })
    @DisplayName("§10.2: day is the Berlin calendar day of the booking across the DST switch")
    void writesTheBerlinBookingDay(String bookedAt, String expectedDay) throws Exception {
        UsageEventLogger logger = loggerAt(Instant.parse(bookedAt));
        logger.logBooking(BRANCH, GROUP_DEFAULT, snapAt(null), false);

        assertEquals(expectedDay, payload(singleFlushedLine(logger)).path("day").asText());
    }

    @ParameterizedTest(name = "TC-16: slot at {0} is {1} {2}")
    @CsvSource({
        "2026-03-27T07:30:00Z, FR, early",     // 08:30 MEZ
        "2026-03-30T07:30:00Z, MO, morning",   // 09:30 MESZ
        "2026-10-23T09:59:00Z, FR, morning",   // 11:59 MESZ — last minute of morning
        "2026-10-26T11:00:00Z, MO, midday",    // 12:00 MEZ
        "2026-10-26T13:00:00Z, MO, afternoon", // 14:00 MEZ
        "2026-10-24T22:30:00Z, SU, early"      // 00:30 MESZ on Sunday 25.10.
    })
    @DisplayName("TC-16: slot weekday and daypart follow Europe/Berlin across the DST switch")
    void bucketsSlotInBerlinTime(String slotAt, String weekday, String daypart) throws Exception {
        usageLogger.logBooking(BRANCH, GROUP_DEFAULT, snapAt(slotAt), false);

        JsonNode json = payload(singleFlushedLine(usageLogger));
        assertEquals(weekday, json.path("slotWeekday").asText());
        assertEquals(daypart, json.path("slotDaypart").asText());
    }

    @ParameterizedTest(name = "TC-16: booked {0}, slot {1} -> lead time {2}")
    @CsvSource({
        // 25.10. 00:30 MESZ -> 26.10. 08:30 MEZ: one Berlin day (two UTC days)
        "2026-10-24T22:30:00Z, 2026-10-26T07:30:00Z, 0-1",
        // 30.03. 00:30 MESZ -> 31.03. 09:00 MESZ: one Berlin day (two UTC days)
        "2026-03-29T22:30:00Z, 2026-03-31T07:00:00Z, 0-1",
        "2026-10-22T16:30:00Z, 2026-10-26T07:30:00Z, 4-7",
        "2026-10-22T16:30:00Z, 2026-11-05T07:30:00Z, 8-14",
        "2026-10-22T16:30:00Z, 2026-11-20T07:30:00Z, 15-30",
        "2026-10-22T16:30:00Z, 2026-12-01T07:30:00Z, 31+"
    })
    @DisplayName("TC-16: lead time counts Berlin calendar days, not UTC days")
    void countsLeadTimeInBerlinCalendarDays(String bookedAt, String slotAt, String expected) throws Exception {
        UsageEventLogger logger = loggerAt(Instant.parse(bookedAt));
        logger.logBooking(BRANCH, GROUP_DEFAULT, snapAt(slotAt), false);

        assertEquals(expected, payload(singleFlushedLine(logger)).path("leadTime").asText());
    }

    // ---- decoupling from the booking request / Entkopplung vom Buchungs-Request ----

    @Test
    @DisplayName("Decoupling: logBooking in the request writes no log line at all — the event is only buffered")
    void logBookingWritesNothingInTheRequest() {
        MDC.put(RequestIdFilter.TRACE_ID_MDC_KEY, REQUEST_TRACE_ID);

        usageLogger.logBooking(BRANCH, GROUP_DEFAULT, snapAt("2026-10-24T07:30:00Z"), true);
        usageLogger.logBooking(BRANCH, GROUP_TIRE, snapAt("2026-10-24T07:30:00Z"), false);

        assertTrue(appender.list.isEmpty(), "no line of any level in the request: " + messages());
        assertEquals(2, usageLogger.bufferedEvents());
        assertEquals(REQUEST_TRACE_ID, MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY), "request MDC untouched");
    }

    @Test
    @DisplayName("Decoupling: the flush writes under MDC job.usagebooking, never with the trace id or thread of the booking")
    void flushWritesUnderTheJobMarkerWithoutTheRequestTraceId() throws Exception {
        Thread request = new Thread(() -> {
            MDC.put(RequestIdFilter.TRACE_ID_MDC_KEY, REQUEST_TRACE_ID);
            try {
                usageLogger.logBooking(BRANCH, GROUP_DEFAULT, snapAt("2026-10-24T07:30:00Z"), true);
            } finally {
                MDC.remove(RequestIdFilter.TRACE_ID_MDC_KEY);
            }
        }, REQUEST_THREAD);
        request.start();
        request.join();
        assertTrue(appender.list.isEmpty(), "nothing written on the request thread: " + messages());

        assertEquals(1, usageLogger.flush());

        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.getFirst();
        assertEquals(Level.INFO, event.getLevel());
        assertTrue(event.getFormattedMessage().startsWith("USAGE_BOOKING {\"v\":2,\"day\":\"2026-10-22\","),
            event.getFormattedMessage());
        assertEquals(UsageEventLogger.TRACE_ID, event.getMDCPropertyMap().get(RequestIdFilter.TRACE_ID_MDC_KEY));
        assertEquals("job.usagebooking", UsageEventLogger.TRACE_ID);
        assertNotEquals(REQUEST_THREAD, event.getThreadName(), "written on the flushing thread");
        assertFalse(event.getFormattedMessage().contains(REQUEST_TRACE_ID));
        assertFalse(event.getMDCPropertyMap().containsValue(REQUEST_TRACE_ID), "no request trace id");
        assertNull(MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY), "MDC removed after the flush");
    }

    @Test
    @DisplayName("Decoupling: the marker is outside the X-Request-Id whitelist — no client can forge the prefix")
    void theJobMarkerCannotBeSentAsRequestId() {
        assertTrue(UsageEventLogger.TRACE_ID.startsWith("job."), "BR-15 family of job markers");
        assertFalse(UsageEventLogger.TRACE_ID.matches("^[A-Za-z0-9_-]{1,64}$"),
            "the dot lies outside the RequestIdFilter whitelist");
    }

    @Test
    @DisplayName("Decoupling: a flush on a thread with its own trace id restores it afterwards")
    void flushRestoresAPreviousTraceId() {
        usageLogger.logBooking(BRANCH, GROUP_DEFAULT, snapAt(null), false);
        MDC.put(RequestIdFilter.TRACE_ID_MDC_KEY, "system-shutdown");

        usageLogger.flush();

        assertEquals(UsageEventLogger.TRACE_ID,
            appender.list.getFirst().getMDCPropertyMap().get(RequestIdFilter.TRACE_ID_MDC_KEY));
        assertEquals("system-shutdown", MDC.get(RequestIdFilter.TRACE_ID_MDC_KEY));
    }

    @Test
    @DisplayName("Decoupling: the flush shuffles the batch — the log order is not the booking order")
    void flushShufflesTheBatch() throws Exception {
        UsageEventLogger seeded = new UsageEventLogger(setupService, Clock.fixed(BOOKED_AT, BERLIN), new Random(4711));
        List<String> bookingOrder = bookDistinct(seeded, 40);

        List<String> logged = opsOf(flushedLines(seeded));

        List<String> expected = new ArrayList<>(bookingOrder);
        Collections.shuffle(expected, new Random(4711));
        assertEquals(expected, logged, "exactly Collections.shuffle with the injected random");
        assertNotEquals(bookingOrder, logged, "the order differs from the booking order");
    }

    @Test
    @DisplayName("Decoupling: the production shuffle (SecureRandom) writes every event once, in a new order")
    void productionFlushWritesEveryEventOnceInANewOrder() throws Exception {
        List<String> bookingOrder = bookDistinct(usageLogger, 40);

        List<String> logged = opsOf(flushedLines(usageLogger));

        assertEquals(new HashSet<>(bookingOrder), new HashSet<>(logged), "every event exactly once");
        assertEquals(40, logged.size());
        // 1 / 40! chance of the identical order / verschwindend kleine Wahrscheinlichkeit.
        assertNotEquals(bookingOrder, logged);
    }

    @Test
    @DisplayName("Decoupling: the flush empties the buffer — a second flush writes nothing")
    void flushEmptiesTheBuffer() {
        assertEquals(0, usageLogger.flush(), "empty buffer: no line");
        assertTrue(appender.list.isEmpty());

        usageLogger.logBooking(BRANCH, GROUP_DEFAULT, snapAt(null), false);
        assertEquals(1, usageLogger.flush());
        assertEquals(0, usageLogger.flush());
        assertEquals(0, usageLogger.bufferedEvents());
        assertEquals(1, infoLines().size());
    }

    @Test
    @DisplayName("Decoupling: the scheduled flush runs daily at 23:59:59 in usage.zone and writes the buffer")
    void scheduledFlushRunsAtTheEndOfTheDay() throws Exception {
        Scheduled scheduled = UsageEventLogger.class.getMethod("scheduledFlush").getAnnotation(Scheduled.class);
        assertNotNull(scheduled, "@Scheduled on scheduledFlush()");
        assertEquals("59 59 23 * * *", scheduled.cron());
        assertEquals(UsageEventLogger.FLUSH_CRON, scheduled.cron());
        assertEquals("${usage.zone:Europe/Berlin}", scheduled.zone());

        usageLogger.logBooking(BRANCH, GROUP_DEFAULT, snapAt(null), false);
        usageLogger.scheduledFlush();

        assertEquals(1, infoLines().size());
    }

    @Test
    @DisplayName("Decoupling: @PreDestroy flushes the buffer when the context closes (restart, deploy)")
    void shutdownFlushesTheBuffer() throws Exception {
        assertNotNull(UsageEventLogger.class.getMethod("flushOnShutdown").getAnnotation(PreDestroy.class));
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(UsageEventLogger.class, () -> usageLogger);
            context.refresh();
            context.getBean(UsageEventLogger.class).logBooking(BRANCH, GROUP_DEFAULT, snapAt(null), true);
            assertTrue(infoLines().isEmpty(), "nothing before the shutdown");
        }

        List<String> lines = infoLines();
        assertEquals(1, lines.size(), "the running day is not lost on a graceful shutdown");
        assertTrue(payload(lines.getFirst()).path("mobility").asBoolean());
        assertEquals(UsageEventLogger.TRACE_ID,
            appender.list.getFirst().getMDCPropertyMap().get(RequestIdFilter.TRACE_ID_MDC_KEY));
    }

    @Test
    @DisplayName("Decoupling: above MAX_BUFFERED_EVENTS further events are dropped with one WARN without values")
    void dropsEventsAboveTheBufferLimit() {
        // 10 000 lines stay in the list appender instead of flooding the console / nicht auf die Konsole.
        logbackLogger.setAdditive(false);
        try {
            assertBufferLimit();
        } finally {
            logbackLogger.setAdditive(true);
        }
    }

    private void assertBufferLimit() {
        ObjectNode snap = snapAt("2026-10-24T07:30:00Z");
        for (int i = 0; i < UsageEventLogger.MAX_BUFFERED_EVENTS + 5; i++) {
            usageLogger.logBooking(BRANCH, GROUP_DEFAULT, snap, false);
        }

        assertEquals(10_000, UsageEventLogger.MAX_BUFFERED_EVENTS);
        assertEquals(UsageEventLogger.MAX_BUFFERED_EVENTS, usageLogger.bufferedEvents());
        assertTrue(infoLines().isEmpty());
        assertEquals(List.of("USAGE_BOOKING buffer full (10000 events) — further bookings are not recorded "
            + "until the next flush"), warnLines(), "exactly one WARN for the period");

        assertEquals(UsageEventLogger.MAX_BUFFERED_EVENTS, usageLogger.flush());

        assertEquals(UsageEventLogger.MAX_BUFFERED_EVENTS, infoLines().size());
        List<String> warnings = warnLines();
        assertEquals("USAGE_BOOKING flush: 5 event(s) dropped at the buffer limit of 10000", warnings.getLast());
        for (String warning : warnings) {
            assertFalse(warning.contains(BRANCH) || warning.contains(GROUP_DEFAULT), "no values: " + warning);
        }

        // The next period starts with an empty buffer and a fresh WARN budget.
        usageLogger.logBooking(BRANCH, GROUP_DEFAULT, snap, false);
        assertEquals(1, usageLogger.bufferedEvents());
    }

    // ---- wiring / Verdrahtung ----

    @Test
    @DisplayName("Spring constructor resolves the zone from usage.zone and falls back to Berlin")
    void springConstructorWritesALineWithTheConfiguredZone() throws Exception {
        UsageStatsProperties utc = new UsageStatsProperties(true, "-", "-", "UTC", null, 24, 60, null);
        UsageStatsProperties blank = new UsageStatsProperties(true, "-", "-", " ", null, 24, 60, null);
        UsageStatsProperties unset = new UsageStatsProperties(true, "-", "-", null, null, 24, 60, null);

        List<UsageEventLogger> loggers = List.of(new UsageEventLogger(setupService, utc),
            new UsageEventLogger(setupService, blank), new UsageEventLogger(setupService, unset),
            new UsageEventLogger(setupService, (UsageStatsProperties) null));
        for (UsageEventLogger logger : loggers) {
            logger.logBooking(BRANCH, GROUP_DEFAULT, snapAt(null), false);
            logger.flush();
        }

        List<String> lines = infoLines();
        assertEquals(4, lines.size());
        for (String line : lines) {
            JsonNode json = payload(line);
            assertEquals(KEYS, fieldNames(json));
            assertTrue(json.path("day").asText().matches("^\\d{4}-\\d{2}-\\d{2}$"), line);
        }
    }

    @Test
    @DisplayName("toJson keeps the fixed field order for a hand-built event")
    void serialisesTheRecordWithFixedFieldOrder() throws Exception {
        UsageBookingEvent event = new UsageBookingEvent(2, "2026-10-25", BRANCH, GROUP_TIRE, "tireChange",
            List.of(), BookingDaypart.DAY, SlotWeekday.UNKNOWN, SlotDaypart.UNKNOWN, LeadTimeBucket.UNKNOWN, false);

        String json = UsageEventLogger.toJson(event);

        assertEquals("{\"v\":2,\"day\":\"2026-10-25\",\"branch\":\"" + BRANCH + "\",\"group\":\"" + GROUP_TIRE
            + "\",\"variant\":\"tireChange\",\"ops\":[],\"bookingDaypart\":\"day\",\"slotWeekday\":\"unknown\","
            + "\"slotDaypart\":\"unknown\",\"leadTime\":\"unknown\",\"mobility\":false}", json);
        assertEquals(2, UsageBookingEvent.FORMAT_VERSION, "v2 since the batch flush (§10.2)");
        assertEquals(1, UsageBookingEvent.LEGACY_FORMAT_VERSION);
    }

    // ---- helpers / Hilfen ----

    private UsageEventLogger loggerAt(Instant now) {
        return new UsageEventLogger(setupService, Clock.fixed(now, BERLIN));
    }

    /** Books {@code count} events told apart by their single operation id; returns the ids in order. */
    private List<String> bookDistinct(UsageEventLogger logger, int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = String.format("6908a1b2c3d4e5f6a7b8%04x", i);
            ObjectNode snap = snapAt("2026-10-24T07:30:00Z");
            snap.withObject("wishlist").putArray("operations").add(id);
            logger.logBooking(BRANCH, GROUP_DEFAULT, snap, false);
            ids.add(id);
        }
        return ids;
    }

    private List<String> opsOf(List<String> lines) throws Exception {
        List<String> ids = new ArrayList<>();
        for (String line : lines) {
            ids.add(payload(line).path("ops").path(0).asText());
        }
        return ids;
    }

    /**
     * Real {@link SetupService}: {@link #BRANCH} with default + tireChange group, {@link #OTHER_BRANCH}
     * with a default group only / echter SetupService für die Fallback-Logik.
     */
    private SetupService realSetup(boolean tireChangeSeason) {
        String json = """
            {"branches":[
              {"branchId":"%s","name":"Autohaus Nord","groups":[
                {"variant":"default","groupId":"%s","fullName":"Nord Werkstatt"},
                {"variant":"tireChange","groupId":"%s","fullName":"Nord Boxenstopp"}]},
              {"branchId":"%s","name":"Autohaus Süd","groups":[
                {"variant":"default","groupId":"%s","fullName":"Süd Werkstatt"}]}
            ]}""".formatted(BRANCH, GROUP_DEFAULT, GROUP_TIRE, OTHER_BRANCH, GROUP_DEFAULT_OTHER);
        SetupService real = new SetupService(om, new FeatureProperties(tireChangeSeason), json, "");
        real.init();
        return real;
    }

    /**
     * A SoftNet-like snap carrying everything that must never be logged / ein Snap mit allem, was nie
     * geloggt werden darf.
     */
    private ObjectNode snapAt(String checkInTime) {
        ObjectNode snap = om.createObjectNode();
        snap.put("internal", SNAP_ID);
        if (checkInTime != null) {
            snap.put("checkInTime", checkInTime);
        }
        snap.put("checkInResource", "Berater Meier");
        snap.put("sessionId", "0123456789abcdef");
        snap.withObject("branch").put("branchLabel", "Autohaus Nord");
        ObjectNode wishlistCar = snap.withObject("wishlist").withObject("car");
        wishlistCar.put("brand", "volkswagen");
        wishlistCar.put("model", "Golf");
        snap.withObject("wishlist").withObject("client").put("email", "erika@example.com")
            .put("lastName", "Musterfrau");
        return snap;
    }

    /**
     * Flushes {@code logger} and returns the INFO lines — asserting that {@code logBooking} itself
     * wrote none / flusht und prüft, dass vorher keine Zeile entstand.
     */
    private List<String> flushedLines(UsageEventLogger logger) {
        assertTrue(infoLines().isEmpty(), "logBooking writes no line before the flush: " + infoLines());
        logger.flush();
        return infoLines();
    }

    private String singleFlushedLine(UsageEventLogger logger) {
        List<String> lines = flushedLines(logger);
        assertEquals(1, lines.size(), "exactly one USAGE_BOOKING line expected: " + lines);
        return lines.getFirst();
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private List<String> infoLines() {
        return appender.list.stream()
            .filter(event -> event.getLevel() == Level.INFO)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }

    private List<String> warnLines() {
        return appender.list.stream()
            .filter(event -> event.getLevel() == Level.WARN)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }

    private JsonNode payload(String line) throws Exception {
        assertTrue(line.startsWith(UsageEventLogger.MARKER + " {"), line);
        return om.readTree(line.substring(UsageEventLogger.MARKER.length() + 1));
    }

    private static List<String> fieldNames(JsonNode json) {
        List<String> names = new ArrayList<>();
        for (Iterator<String> it = json.fieldNames(); it.hasNext(); ) {
            names.add(it.next());
        }
        return names;
    }

    /**
     * Captures MDC and thread name at logging time — {@code LoggingEvent} reads both lazily, i.e.
     * too late for these assertions. / Hält MDC und Thread im Moment des Loggens fest.
     */
    private static final class CapturingAppender extends ListAppender<ILoggingEvent> {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    }
}
