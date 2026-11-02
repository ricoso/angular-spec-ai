package com.autohaus.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "softnet")
public record SoftnetProperties(
    String baseUrl,
    String authUrl,
    String fingerprintJson,
    String fingerprintPath,
    int timeout,
    // REQ-041: dedicated recall-campaign credential + its group (separate SoftNet billing).
    // Empty recall fingerprint → the recall group falls back to the default credential.
    String recallFingerprintJson,
    String recallFingerprintPath,
    String recallGroupId
) {
    // Two constructors → tell Spring which one binds the properties (the canonical one).
    @ConstructorBinding
    public SoftnetProperties {
        if (timeout <= 0) timeout = 10000;
    }

    /** Backwards-compatible constructor (pre-REQ-041, no recall credential). */
    public SoftnetProperties(String baseUrl, String authUrl, String fingerprintJson, String fingerprintPath, int timeout) {
        this(baseUrl, authUrl, fingerprintJson, fingerprintPath, timeout, null, null, null);
    }
}
