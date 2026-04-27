package com.autohaus.backend.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {

    @Test
    void securityConfig_canBeInstantiated() {
        SecurityConfig config = new SecurityConfig("'self' https://www.gottfried-schultz.de");
        assertNotNull(config);
    }

    @Test
    void securityConfig_acceptsCustomFrameAncestors() {
        SecurityConfig config = new SecurityConfig("'self' http://localhost:8080");
        assertNotNull(config);
    }
}
