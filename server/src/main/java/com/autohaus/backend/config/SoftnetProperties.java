package com.autohaus.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "softnet")
public record SoftnetProperties(
    String baseUrl,
    String authUrl,
    String fingerprintJson,
    int timeout
) {
    public SoftnetProperties {
        if (timeout <= 0) timeout = 10000;
    }
}
