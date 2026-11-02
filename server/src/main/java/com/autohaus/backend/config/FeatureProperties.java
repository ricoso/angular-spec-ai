package com.autohaus.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Feature toggles. Controlled via env variables, evaluated at startup only
 * (switching requires redeploy - prevents mid-session inconsistencies).
 */
@ConfigurationProperties(prefix = "features")
public record FeatureProperties(
    boolean radwechselEnabled
) {}
