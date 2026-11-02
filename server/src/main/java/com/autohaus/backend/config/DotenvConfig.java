package com.autohaus.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads .env file from server/ or backend/ directory into Spring Environment.
 * This ensures compatibility with the existing NestJS .env configuration.
 */
public class DotenvConfig implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DotenvConfig.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String basePath = System.getProperty("user.dir");

        // Try multiple locations for .env file
        Path[] candidates = {
            Path.of(basePath, ".env"),
            Path.of(basePath, "..", "backend", ".env"),
            Path.of(basePath, "..", ".env"),
        };

        for (Path envPath : candidates) {
            Path resolved = envPath.toAbsolutePath().normalize();
            if (Files.exists(resolved)) {
                Map<String, Object> props = loadEnvFile(resolved);
                if (!props.isEmpty()) {
                    environment.getPropertySources().addLast(new MapPropertySource("dotenv", props));
                    System.out.println("[DotenvConfig] Loaded " + props.size() + " properties from " + resolved);
                }
                return;
            }
        }

        System.out.println("[DotenvConfig] No .env file found — using environment variables only");
    }

    private Map<String, Object> loadEnvFile(Path path) {
        Map<String, Object> props = new HashMap<>();
        try {
            for (String line : Files.readAllLines(path)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

                int eqIdx = trimmed.indexOf('=');
                if (eqIdx > 0) {
                    String key = trimmed.substring(0, eqIdx).trim();
                    String value = trimmed.substring(eqIdx + 1).trim();
                    // Remove surrounding quotes
                    if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    props.put(key, value);
                }
            }
        } catch (IOException e) {
            System.err.println("[DotenvConfig] Failed to read " + path + ": " + e.getMessage());
        }
        return props;
    }
}
