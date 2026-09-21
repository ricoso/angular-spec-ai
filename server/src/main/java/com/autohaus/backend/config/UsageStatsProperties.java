package com.autohaus.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the anonymous usage statistics on the status page (REQ-037, §10.4).
 *
 * <p>Everything is env-driven so the feature can be switched on/off from the outside.
 * {@code enabled} is a real kill switch (BR-13): it gates collection, aggregation, rendering and
 * the endpoint together — with {@code false} the {@code LoggingFilter} does not even read the two
 * headers and nothing is collected "just in case" (purpose limitation, Art. 5 (1) (b) GDPR). /
 * {@code enabled} schaltet Erhebung, Aggregation, Rendering und Endpoint gemeinsam ab.
 *
 * <p>Unlike {@code reporting}, this feature is deliberately independent of the LLM toggle — the
 * statistics must be visible without {@code LLM_API_KEY}/{@code LLM_MODEL} (§18). Blob access uses
 * the system-managed identity and therefore needs no secret. / Bewusst LLM-unabhängig.
 *
 * @param enabled {@code USAGE_STATS_ENABLED} — kill switch, also gates the log field (BR-13)
 * @param refreshCron {@code USAGE_REFRESH_CRON} — live refresh from Log Analytics
 * @param rollupCron {@code USAGE_ROLLUP_CRON} — nightly rollup into the blob
 * @param zone {@code USAGE_ZONE} — time zone of all day/month/year boundaries (BR-7)
 * @param retentionMonths {@code USAGE_RETENTION_MONTHS} — months of day rows kept in the blob
 *     (NFR-Datenschutz-7)
 * @param refreshMinIntervalSeconds {@code USAGE_REFRESH_MIN_INTERVAL_SECONDS} — throttle for the
 *     manual refresh endpoint (AC-21)
 * @param storage blob storage coordinates / Koordinaten der Blob-Ablage
 */
@ConfigurationProperties(prefix = "usage")
public record UsageStatsProperties(
    boolean enabled,
    String refreshCron,
    String rollupCron,
    String zone,
    int retentionMonths,
    int refreshMinIntervalSeconds,
    Storage storage
) {

    /**
     * Blob storage coordinates / Koordinaten der Blob-Ablage — accessed via REST + managed
     * identity, no SDK and no secret (§14.2).
     *
     * @param account storage account name, empty locally / Name des Storage Accounts
     * @param container container name / Container-Name
     * @param blob blob name of the daily history / Blob-Name der Tageshistorie
     */
    public record Storage(String account, String container, String blob) {}
}
