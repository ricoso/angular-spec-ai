package com.autohaus.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the daily AI error report (REQ-030).
 *
 * <p>The LLM connection itself is configured via Spring AI's {@code spring.ai.openai.*}
 * properties (fed from {@code LLM_API_KEY} / {@code LLM_BASE_URL} / {@code LLM_MODEL}).
 * The scheduled job and LLM summary bean only activate when an api-key and model are
 * actually set — see {@code ErrorReportingConfiguration} {@code @ConditionalOnExpression}.
 * Everything is env-driven so the feature can be switched on/off from the outside.
 */
@ConfigurationProperties(prefix = "reporting")
public record ErrorReportingProperties(
    String workspaceId,
    String cron,
    String zone,
    Admin admin
) {

    public record Admin(String user, String password) {}
}
