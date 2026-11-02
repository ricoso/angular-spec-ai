package com.autohaus.backend.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {

    private static ErrorReportingProperties reportingProps() {
        return new ErrorReportingProperties(
            "ws-id", "0 0 3 * * *", "Europe/Berlin",
            new ErrorReportingProperties.Admin("admin", "secret"));
    }

    @Test
    void securityConfig_canBeInstantiated() {
        SecurityConfig config = new SecurityConfig("'self' https://www.gottfried-schultz.de", reportingProps());
        assertNotNull(config);
    }

    @Test
    void securityConfig_acceptsCustomFrameAncestors() {
        SecurityConfig config = new SecurityConfig("'self' http://localhost:8080", reportingProps());
        assertNotNull(config);
    }
}
