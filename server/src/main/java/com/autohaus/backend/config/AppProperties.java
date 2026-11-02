package com.autohaus.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
    SessionProperties session,
    CorsProperties cors
) {
    public record SessionProperties(String secret, int ttlMinutes) {
        public SessionProperties {
            if (ttlMinutes <= 0) ttlMinutes = 20;
        }
    }
    public record CorsProperties(String allowedOrigins) {}
}
