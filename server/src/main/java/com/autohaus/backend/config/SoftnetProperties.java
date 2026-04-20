package com.autohaus.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "softnet")
public record SoftnetProperties(
    String baseUrl,
    String authUrl,
    String fingerprintPath,
    int timeout,
    String groupId,
    String branchId
) {
    public SoftnetProperties {
        if (timeout <= 0) timeout = 10000;
    }
}
