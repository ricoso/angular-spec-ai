package com.autohaus.backend.softnet;

import com.autohaus.backend.config.SoftnetProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SoftnetAuthServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void init_withEmptyFingerprintPath_doesNotThrow() {
        SoftnetProperties props = new SoftnetProperties("http://test", "http://auth", "", 10000, "g", "b");
        SoftnetAuthService service = new SoftnetAuthService(props, objectMapper);
        assertDoesNotThrow(service::init);
    }

    @Test
    void init_withNullFingerprintPath_doesNotThrow() {
        SoftnetProperties props = new SoftnetProperties("http://test", "http://auth", null, 10000, "g", "b");
        SoftnetAuthService service = new SoftnetAuthService(props, objectMapper);
        assertDoesNotThrow(service::init);
    }

    @Test
    void init_withBlankFingerprintPath_doesNotThrow() {
        SoftnetProperties props = new SoftnetProperties("http://test", "http://auth", "   ", 10000, "g", "b");
        SoftnetAuthService service = new SoftnetAuthService(props, objectMapper);
        assertDoesNotThrow(service::init);
    }

    @Test
    void init_withNonexistentFile_doesNotThrow() {
        SoftnetProperties props = new SoftnetProperties("http://test", "http://auth", "/tmp/nonexistent-fp-file.json", 10000, "g", "b");
        SoftnetAuthService service = new SoftnetAuthService(props, objectMapper);
        assertDoesNotThrow(service::init);
    }

    @Test
    void init_withInvalidJsonFile_doesNotThrow(@TempDir Path tempDir) throws Exception {
        Path fpFile = tempDir.resolve("fingerprint.json");
        Files.writeString(fpFile, "not valid json {{{");

        SoftnetProperties props = new SoftnetProperties("http://test", "http://auth", fpFile.toString(), 10000, "g", "b");
        SoftnetAuthService service = new SoftnetAuthService(props, objectMapper);
        assertDoesNotThrow(service::init);
    }

    @Test
    void init_withIncompleteJsonFile_doesNotThrow(@TempDir Path tempDir) throws Exception {
        Path fpFile = tempDir.resolve("fingerprint.json");
        Files.writeString(fpFile, "{\"client_email\": \"test@test.com\"}");

        SoftnetProperties props = new SoftnetProperties("http://test", "http://auth", fpFile.toString(), 10000, "g", "b");
        SoftnetAuthService service = new SoftnetAuthService(props, objectMapper);
        assertDoesNotThrow(service::init);
    }

    @Test
    void getAccessToken_withoutFingerprint_throwsRuntimeException() {
        SoftnetProperties props = new SoftnetProperties("http://test", "http://auth", "", 10000, "g", "b");
        SoftnetAuthService service = new SoftnetAuthService(props, objectMapper);
        service.init();
        RuntimeException ex = assertThrows(RuntimeException.class, service::getAccessToken);
        assertTrue(ex.getMessage().contains("Failed to authenticate"));
    }

    @Test
    void getAccessToken_withNullFingerprint_throwsRuntimeException() {
        SoftnetProperties props = new SoftnetProperties("http://test", "http://auth", null, 10000, "g", "b");
        SoftnetAuthService service = new SoftnetAuthService(props, objectMapper);
        service.init();
        assertThrows(RuntimeException.class, service::getAccessToken);
    }

    @Test
    void getAccessToken_withBadFile_throwsRuntimeException(@TempDir Path tempDir) throws Exception {
        Path fpFile = tempDir.resolve("fingerprint.json");
        Files.writeString(fpFile, "not json");

        SoftnetProperties props = new SoftnetProperties("http://test", "http://auth", fpFile.toString(), 10000, "g", "b");
        SoftnetAuthService service = new SoftnetAuthService(props, objectMapper);
        service.init();
        assertThrows(RuntimeException.class, service::getAccessToken);
    }
}
