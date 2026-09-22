package com.autohaus.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.convert.ApplicationConversionService;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesTest {

    // === SoftnetProperties ===

    @Test
    void softnetProperties_defaultTimeout_whenZero() {
        SoftnetProperties props = new SoftnetProperties("url", "auth", "/fp", null, 0);
        assertEquals(10000, props.timeout());
    }

    @Test
    void softnetProperties_defaultTimeout_whenNegative() {
        SoftnetProperties props = new SoftnetProperties("url", "auth", "/fp", null, -1);
        assertEquals(10000, props.timeout());
    }

    @Test
    void softnetProperties_customTimeout() {
        SoftnetProperties props = new SoftnetProperties("url", "auth", "/fp", null, 5000);
        assertEquals(5000, props.timeout());
    }

    @Test
    void softnetProperties_allAccessors() {
        SoftnetProperties props = new SoftnetProperties("base", "auth-url", "/path/fp", "/path/file", 3000);
        assertEquals("base", props.baseUrl());
        assertEquals("auth-url", props.authUrl());
        assertEquals("/path/fp", props.fingerprintJson());
        assertEquals("/path/file", props.fingerprintPath());
        assertEquals(3000, props.timeout());
    }

    @Test
    void softnetProperties_equality() {
        SoftnetProperties a = new SoftnetProperties("url", "auth", "/fp", null, 5000);
        SoftnetProperties b = new SoftnetProperties("url", "auth", "/fp", null, 5000);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void softnetProperties_nullValues() {
        SoftnetProperties props = new SoftnetProperties(null, null, null, null, 0);
        assertNull(props.baseUrl());
        assertNull(props.authUrl());
        assertNull(props.fingerprintJson());
        assertNull(props.fingerprintPath());
        assertEquals(10000, props.timeout());
    }

    // === AppProperties ===

    @Test
    void appProperties_sessionDefaultTtl_whenZero() {
        AppProperties.SessionProperties session = new AppProperties.SessionProperties("secret", 0);
        assertEquals(20, session.ttlMinutes());
    }

    @Test
    void appProperties_sessionDefaultTtl_whenNegative() {
        AppProperties.SessionProperties session = new AppProperties.SessionProperties("secret", -5);
        assertEquals(20, session.ttlMinutes());
    }

    @Test
    void appProperties_sessionCustomTtl() {
        AppProperties.SessionProperties session = new AppProperties.SessionProperties("secret", 30);
        assertEquals(30, session.ttlMinutes());
    }

    @Test
    void appProperties_sessionAccessors() {
        AppProperties.SessionProperties session = new AppProperties.SessionProperties("my-secret", 45);
        assertEquals("my-secret", session.secret());
        assertEquals(45, session.ttlMinutes());
    }

    @Test
    void appProperties_corsAccessors() {
        AppProperties.CorsProperties cors = new AppProperties.CorsProperties("http://localhost:3000");
        assertEquals("http://localhost:3000", cors.allowedOrigins());
    }

    @Test
    void appProperties_corsNull() {
        AppProperties.CorsProperties cors = new AppProperties.CorsProperties(null);
        assertNull(cors.allowedOrigins());
    }

    @Test
    void appProperties_recordAccessors() {
        AppProperties.CorsProperties cors = new AppProperties.CorsProperties("http://localhost:3000");
        AppProperties.SessionProperties session = new AppProperties.SessionProperties("secret", 20);
        AppProperties props = new AppProperties(session, cors);

        assertSame(session, props.session());
        assertSame(cors, props.cors());
    }

    @Test
    void appProperties_equality() {
        AppProperties.SessionProperties s1 = new AppProperties.SessionProperties("secret", 20);
        AppProperties.SessionProperties s2 = new AppProperties.SessionProperties("secret", 20);
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());

        AppProperties.CorsProperties c1 = new AppProperties.CorsProperties("http://localhost:3000");
        AppProperties.CorsProperties c2 = new AppProperties.CorsProperties("http://localhost:3000");
        assertEquals(c1, c2);

        AppProperties a1 = new AppProperties(s1, c1);
        AppProperties a2 = new AppProperties(s2, c2);
        assertEquals(a1, a2);
    }

    // === UsageStatsProperties: BR-15 collection start / Erfassungsbeginn ===

    /**
     * REQ-037 BR-15: {@code collecting-since: ${USAGE_COLLECTING_SINCE:}} must bind to {@code null}
     * when the environment variable is unset. Without this the backend would fail to start the
     * moment the variable is left empty — and no unit test would notice, because nothing else here
     * loads the Spring context. / Leerer Wert muss auf {@code null} binden, sonst startet das
     * Backend nicht.
     */
    @Test
    void usageStatsProperties_collectingSince_bindsEmptyValueToNull() {
        UsageStatsProperties props = bindUsage("");

        assertNull(props.collectingSince(), "an unset USAGE_COLLECTING_SINCE means: no lower bound");
        assertTrue(props.enabled());
        assertEquals("Europe/Berlin", props.zone());
    }

    /** BR-15: an ISO date binds to {@link LocalDate} without any custom converter. */
    @Test
    void usageStatsProperties_collectingSince_bindsIsoDate() {
        assertEquals(LocalDate.of(2026, 9, 22), bindUsage("2026-09-22").collectingSince());
    }

    /**
     * Binds the {@code usage} block exactly the way Spring Boot does at startup — same relaxed
     * binder, same conversion service — so a binding break shows up here instead of in production.
     */
    private static UsageStatsProperties bindUsage(String collectingSince) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("usage.enabled", "true");
        properties.put("usage.refresh-cron", "0 */15 * * * *");
        properties.put("usage.rollup-cron", "0 30 3 * * *");
        properties.put("usage.zone", "Europe/Berlin");
        properties.put("usage.collecting-since", collectingSince);
        properties.put("usage.retention-months", "24");
        properties.put("usage.refresh-min-interval-seconds", "60");
        properties.put("usage.storage.account", "");
        properties.put("usage.storage.container", "usage-stats");
        properties.put("usage.storage.blob", "usage-daily.json");

        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(List.of(source), null,
            ApplicationConversionService.getSharedInstance());
        return binder.bind("usage", UsageStatsProperties.class)
            .orElseThrow(() -> new AssertionError("usage block did not bind"));
    }
}
