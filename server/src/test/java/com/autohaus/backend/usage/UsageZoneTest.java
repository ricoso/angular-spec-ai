package com.autohaus.backend.usage;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.autohaus.backend.config.UsageStatsProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one {@code usage.zone} resolution of REQ-038 (§14.1, BR-7) / die eine Auflösung von
 * {@code usage.zone}: missing → Berlin silently, blank or unknown → Berlin with one WARN per value.
 */
class UsageZoneTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private Logger logbackLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        UsageZone.forgetWarnings();
        logbackLogger = (Logger) LoggerFactory.getLogger(UsageZone.class);
        appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logbackLogger.detachAppender(appender);
        appender.stop();
        UsageZone.forgetWarnings();
    }

    @Test
    @DisplayName("a configured zone is used, trimmed")
    void usesTheConfiguredZoneTrimmed() {
        assertEquals(ZoneId.of("UTC"), UsageZone.resolve(properties("UTC")));
        assertEquals(ZoneId.of("Europe/Vienna"), UsageZone.resolve(" Europe/Vienna "));
        assertTrue(warnings().isEmpty());
    }

    @Test
    @DisplayName("missing properties or zone → Europe/Berlin without a log line")
    void fallsBackSilentlyWhenNothingIsConfigured() {
        assertSame(UsageZone.DEFAULT_ZONE, UsageZone.resolve((UsageStatsProperties) null));
        assertSame(UsageZone.DEFAULT_ZONE, UsageZone.resolve(properties(null)));
        assertSame(UsageZone.DEFAULT_ZONE, UsageZone.resolve((String) null));
        assertEquals(BERLIN, UsageZone.DEFAULT_ZONE);
        assertEquals("Europe/Berlin", UsageZone.DEFAULT_ZONE_ID);
        assertTrue(appender.list.isEmpty());
    }

    @Test
    @DisplayName("a blank zone → Europe/Berlin with exactly one WARN, however often it is resolved")
    void warnsOnceForABlankZone() {
        assertSame(UsageZone.DEFAULT_ZONE, UsageZone.resolve(properties(" ")));
        assertSame(UsageZone.DEFAULT_ZONE, UsageZone.resolve(""));
        assertSame(UsageZone.DEFAULT_ZONE, UsageZone.resolve("\t"));

        assertEquals(1, warnings().size());
        assertTrue(warnings().getFirst().contains("falling back to Europe/Berlin"), warnings().toString());
    }

    @Test
    @DisplayName("an unknown or malformed zone → Europe/Berlin with one WARN per distinct value")
    void warnsOncePerUnknownZone() {
        for (int i = 0; i < 3; i++) {
            assertSame(UsageZone.DEFAULT_ZONE, UsageZone.resolve(properties("Mars/Olympus")));
            assertSame(UsageZone.DEFAULT_ZONE, UsageZone.resolve(" Mars/Olympus "));
            assertSame(UsageZone.DEFAULT_ZONE, UsageZone.resolve("+99:99"));
        }

        List<String> warnings = warnings();
        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.getFirst().contains("'Mars/Olympus'"), warnings.toString());
        assertTrue(warnings.get(1).contains("'+99:99'"), warnings.toString());
    }

    private List<String> warnings() {
        return appender.list.stream()
            .filter(e -> e.getLevel() == Level.WARN)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }

    private static UsageStatsProperties properties(String zone) {
        return new UsageStatsProperties(true, "-", "-", zone, null, 24, 60, null);
    }
}
