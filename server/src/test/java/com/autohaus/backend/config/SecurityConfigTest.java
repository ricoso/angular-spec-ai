package com.autohaus.backend.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {

    @Test
    void securityConfig_canBeInstantiated() {
        SecurityConfig config = new SecurityConfig();
        assertNotNull(config);
    }
}
