package com.autohaus.backend.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesTest {

    // === SoftnetProperties ===

    @Test
    void softnetProperties_defaultTimeout_whenZero() {
        SoftnetProperties props = new SoftnetProperties("url", "auth", "/fp", 0, "g", "b");
        assertEquals(10000, props.timeout());
    }

    @Test
    void softnetProperties_defaultTimeout_whenNegative() {
        SoftnetProperties props = new SoftnetProperties("url", "auth", "/fp", -1, "g", "b");
        assertEquals(10000, props.timeout());
    }

    @Test
    void softnetProperties_customTimeout() {
        SoftnetProperties props = new SoftnetProperties("url", "auth", "/fp", 5000, "g", "b");
        assertEquals(5000, props.timeout());
    }

    @Test
    void softnetProperties_allAccessors() {
        SoftnetProperties props = new SoftnetProperties("base", "auth-url", "/path/fp", 3000, "grp", "br");
        assertEquals("base", props.baseUrl());
        assertEquals("auth-url", props.authUrl());
        assertEquals("/path/fp", props.fingerprintJson());
        assertEquals(3000, props.timeout());
        assertEquals("grp", props.groupId());
        assertEquals("br", props.branchId());
    }

    @Test
    void softnetProperties_equality() {
        SoftnetProperties a = new SoftnetProperties("url", "auth", "/fp", 5000, "g", "b");
        SoftnetProperties b = new SoftnetProperties("url", "auth", "/fp", 5000, "g", "b");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void softnetProperties_nullValues() {
        SoftnetProperties props = new SoftnetProperties(null, null, null, 0, null, null);
        assertNull(props.baseUrl());
        assertNull(props.authUrl());
        assertNull(props.fingerprintJson());
        assertEquals(10000, props.timeout());
        assertNull(props.groupId());
        assertNull(props.branchId());
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
}
