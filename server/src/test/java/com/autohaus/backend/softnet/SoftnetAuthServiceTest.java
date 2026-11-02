package com.autohaus.backend.softnet;

import com.autohaus.backend.config.SoftnetProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoftnetAuthServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void init_withEmptyFingerprint_doesNotThrow() {
        SoftnetProperties props = new SoftnetProperties("http://test", "http://auth", "", null, 10000);
        SoftnetAuthService service = new SoftnetAuthService(props, objectMapper);
        assertDoesNotThrow(service::init);
    }

    @Test
    void init_withNullFingerprint_doesNotThrow() {
        SoftnetProperties props = new SoftnetProperties("http://test", "http://auth", null, null, 10000);
        SoftnetAuthService service = new SoftnetAuthService(props, objectMapper);
        assertDoesNotThrow(service::init);
    }

    @Test
    void init_withBlankFingerprint_doesNotThrow() {
        SoftnetProperties props = new SoftnetProperties("http://test", "http://auth", "   ", null, 10000);
        SoftnetAuthService service = new SoftnetAuthService(props, objectMapper);
        assertDoesNotThrow(service::init);
    }

    @Test
    void init_withInvalidJsonString_doesNotThrow() {
        SoftnetProperties props = new SoftnetProperties("http://test", "http://auth", "not valid json {{{", null, 10000);
        SoftnetAuthService service = new SoftnetAuthService(props, objectMapper);
        assertDoesNotThrow(service::init);
    }

    @Test
    void init_withIncompleteJsonString_doesNotThrow() {
        String incompleteJson = "{\"client_email\": \"test@test.com\"}";
        SoftnetProperties props = new SoftnetProperties("http://test", "http://auth", incompleteJson, null, 10000);
        SoftnetAuthService service = new SoftnetAuthService(props, objectMapper);
        assertDoesNotThrow(service::init);
    }

    @Test
    void init_withValidJsonStructure_parsesFieldsWithoutThrowing() {
        // JSON with all fields but an invalid private key — init() should catch the RSA parse error
        // and not throw, so the app keeps starting even if auth fails later.
        String validStructure = "{"
            + "\"client_email\": \"svc@test.com\","
            + "\"client_id\": \"cid-123\","
            + "\"private_key_id\": \"pkid-123\","
            + "\"private_key\": \"-----BEGIN PRIVATE KEY-----\\nTk9UQVZBTElES0VZ\\n-----END PRIVATE KEY-----\""
            + "}";
        SoftnetProperties props = new SoftnetProperties("http://test", "http://auth", validStructure, null, 10000);
        SoftnetAuthService service = new SoftnetAuthService(props, objectMapper);
        assertDoesNotThrow(service::init);
    }

    @Test
    void getAccessToken_withoutFingerprint_throwsRuntimeException() {
        SoftnetProperties props = new SoftnetProperties("http://test", "http://auth", "", null, 10000);
        SoftnetAuthService service = new SoftnetAuthService(props, objectMapper);
        service.init();
        RuntimeException ex = assertThrows(RuntimeException.class, service::getAccessToken);
        assertTrue(ex.getMessage().contains("Failed to authenticate"));
    }

    @Test
    void getAccessToken_withNullFingerprint_throwsRuntimeException() {
        SoftnetProperties props = new SoftnetProperties("http://test", "http://auth", null, null, 10000);
        SoftnetAuthService service = new SoftnetAuthService(props, objectMapper);
        service.init();
        assertThrows(RuntimeException.class, service::getAccessToken);
    }

    @Test
    void getAccessToken_withBadJsonString_throwsRuntimeException() {
        SoftnetProperties props = new SoftnetProperties("http://test", "http://auth", "not json", null, 10000);
        SoftnetAuthService service = new SoftnetAuthService(props, objectMapper);
        service.init();
        assertThrows(RuntimeException.class, service::getAccessToken);
    }

    // REQ-041: the recall group falls back to the DEFAULT credential when no recall fingerprint is
    // configured (e.g. INT before the dedicated recall client is provisioned).
    @Test
    void getAccessToken_recallGroupWithoutRecallCredential_fallsBackToDefault() {
        SoftnetProperties props = new SoftnetProperties(
            "http://test", "http://auth", "", null, 10000, "", null, "recall-grp");
        SoftnetAuthService service = new SoftnetAuthService(props, objectMapper);
        service.init();
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.getAccessToken("recall-grp"));
        assertTrue(ex.getMessage().contains("'default'"),
            "recall group without a recall credential must fall back to the default credential");
    }
}
