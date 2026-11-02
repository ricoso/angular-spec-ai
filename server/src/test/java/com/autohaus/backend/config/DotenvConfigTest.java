package com.autohaus.backend.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DotenvConfigTest {

    @Test
    void postProcessEnvironment_noEnvFile_doesNotThrow() {
        DotenvConfig config = new DotenvConfig();
        MockEnvironment environment = new MockEnvironment();
        SpringApplication app = mock(SpringApplication.class);

        String originalDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", "/tmp/nonexistent-dir-dotenv-test");
            assertDoesNotThrow(() -> config.postProcessEnvironment(environment, app));
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void postProcessEnvironment_withEnvFile_loadsProperties(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve(".env"), """
            PORT=3000
            SESSION_SECRET=test-secret
            # Comment line
            CORS_ALLOWED_ORIGINS="http://localhost:3000"
            EMPTY_LINE_BEFORE=value
            """);

        DotenvConfig config = new DotenvConfig();
        MockEnvironment environment = new MockEnvironment();
        SpringApplication app = mock(SpringApplication.class);

        String originalDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            config.postProcessEnvironment(environment, app);
            assertEquals("3000", environment.getProperty("PORT"));
            assertEquals("test-secret", environment.getProperty("SESSION_SECRET"));
            assertEquals("http://localhost:3000", environment.getProperty("CORS_ALLOWED_ORIGINS"));
            assertEquals("value", environment.getProperty("EMPTY_LINE_BEFORE"));
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void postProcessEnvironment_singleQuotedValues(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve(".env"), "MY_VAR='single-quoted'\n");

        DotenvConfig config = new DotenvConfig();
        MockEnvironment environment = new MockEnvironment();
        SpringApplication app = mock(SpringApplication.class);

        String originalDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            config.postProcessEnvironment(environment, app);
            assertEquals("single-quoted", environment.getProperty("MY_VAR"));
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void postProcessEnvironment_emptyFile(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve(".env"), "");

        DotenvConfig config = new DotenvConfig();
        MockEnvironment environment = new MockEnvironment();
        SpringApplication app = mock(SpringApplication.class);

        String originalDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            assertDoesNotThrow(() -> config.postProcessEnvironment(environment, app));
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void postProcessEnvironment_commentsAndBlankLines(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve(".env"), """
            # Full line comment

            KEY1=value1
            # Another comment
            KEY2=value2
            """);

        DotenvConfig config = new DotenvConfig();
        MockEnvironment environment = new MockEnvironment();
        SpringApplication app = mock(SpringApplication.class);

        String originalDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            config.postProcessEnvironment(environment, app);
            assertEquals("value1", environment.getProperty("KEY1"));
            assertEquals("value2", environment.getProperty("KEY2"));
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void postProcessEnvironment_valueWithEquals(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve(".env"), "DB_URL=jdbc:mysql://host:3306/db?user=root&pass=pw\n");

        DotenvConfig config = new DotenvConfig();
        MockEnvironment environment = new MockEnvironment();
        SpringApplication app = mock(SpringApplication.class);

        String originalDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            config.postProcessEnvironment(environment, app);
            assertEquals("jdbc:mysql://host:3306/db?user=root&pass=pw", environment.getProperty("DB_URL"));
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }

    @Test
    void postProcessEnvironment_unquotedValue(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve(".env"), "SIMPLE=hello\n");

        DotenvConfig config = new DotenvConfig();
        MockEnvironment environment = new MockEnvironment();
        SpringApplication app = mock(SpringApplication.class);

        String originalDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            config.postProcessEnvironment(environment, app);
            assertEquals("hello", environment.getProperty("SIMPLE"));
        } finally {
            System.setProperty("user.dir", originalDir);
        }
    }
}
