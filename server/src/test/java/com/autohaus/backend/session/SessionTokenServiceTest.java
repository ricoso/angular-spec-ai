package com.autohaus.backend.session;

import com.autohaus.backend.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionTokenServiceTest {

    private SessionTokenService service;

    @BeforeEach
    void setUp() {
        AppProperties.SessionProperties session = new AppProperties.SessionProperties(
            "test-secret-that-is-at-least-32-characters-long", 20);
        AppProperties.CorsProperties cors = new AppProperties.CorsProperties("http://localhost:3000");
        AppProperties appProperties = new AppProperties(session, cors);
        service = new SessionTokenService(appProperties);
    }

    @Test
    void generateToken_returnsValidToken() {
        SessionTokenService.TokenResult result = service.generateToken();
        assertNotNull(result.token());
        assertNotNull(result.expiresAt());
        assertTrue(result.token().contains("."));
    }

    @Test
    void validateToken_validToken_returnsTrue() {
        SessionTokenService.TokenResult result = service.generateToken();
        assertTrue(service.validateToken(result.token()));
    }

    @Test
    void validateToken_invalidToken_returnsFalse() {
        assertFalse(service.validateToken("invalid.token"));
    }

    @Test
    void validateToken_emptyToken_returnsFalse() {
        assertFalse(service.validateToken(""));
    }

    @Test
    void validateToken_nullParts_returnsFalse() {
        assertFalse(service.validateToken("onlyone"));
    }

    @Test
    void validateToken_tamperedToken_returnsFalse() {
        SessionTokenService.TokenResult result = service.generateToken();
        String tampered = result.token() + "x";
        assertFalse(service.validateToken(tampered));
    }

    @Test
    void generateToken_expiresAtIsFuture() {
        SessionTokenService.TokenResult result = service.generateToken();
        java.time.Instant expiry = java.time.Instant.parse(result.expiresAt());
        assertTrue(expiry.isAfter(java.time.Instant.now()));
    }

    @Test
    void generateToken_tokenFormatIsBase64Url() {
        SessionTokenService.TokenResult result = service.generateToken();
        String[] parts = result.token().split("\\.");
        assertEquals(2, parts.length);
        // Both parts should be valid base64url
        assertDoesNotThrow(() -> java.util.Base64.getUrlDecoder().decode(parts[0]));
        assertDoesNotThrow(() -> java.util.Base64.getUrlDecoder().decode(parts[1]));
    }
}
