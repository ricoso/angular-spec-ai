package com.autohaus.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;

/**
 * Kill switch and coordinates of the booking insights (REQ-038 K2, §10.7) / Kill-Switch und
 * Koordinaten der Buchungs-Insights.
 *
 * <p>{@code enabled == false} (the delivery default, AC-31) means: no {@code USAGE_BOOKING} line is
 * written, KQL 1 and 2 never run, S0 (I-2/I-4) and S2–S4 are omitted and the bookings part of
 * {@code insights.json} answers 404. / Standard ist aus; nichts wird „auf Vorrat" erhoben.
 *
 * @param enabled {@code USAGE_INSIGHTS_ENABLED} — kill switch K2 (BR-6, AC-31)
 * @param collectingSince {@code USAGE_INSIGHTS_COLLECTING_SINCE} — first counted day; {@code null}
 *     = first full Berlin calendar day after activation (BR-19)
 * @param rollupCron {@code USAGE_INSIGHTS_ROLLUP_CRON} — nightly rollup, after the REQ-037 rollup
 * @param blob {@code USAGE_INSIGHTS_BLOB} — blob name inside {@code USAGE_STORAGE_CONTAINER}
 */
@ConfigurationProperties(prefix = "usage.insights")
public record UsageInsightsProperties(
    boolean enabled,
    LocalDate collectingSince,
    String rollupCron,
    String blob
) {}
