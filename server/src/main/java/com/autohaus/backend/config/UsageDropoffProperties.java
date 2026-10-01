package com.autohaus.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;

/**
 * Kill switch of the drop-off scenarios (REQ-038 N1, F-4, §10.7) / Kill-Switch der
 * Absprungszenarien.
 *
 * <p>Subordinate to the funnel: only effective while {@code usage.enabled} <b>and</b>
 * {@code usage.funnel.enabled} are true (§5.1). It jointly gates the access-line field
 * {@code hits} (BR-29), the drop-off variant of the nightly query (KQL 3b), {@code dropoff} in the
 * blob and the S6 subsection {@code .fn-dropoff} (AC-35). Gated by G-DSB, delivered {@code false}.
 * / Wirkt nur zusammen mit dem Trichter; Default aus (Gate G-DSB).
 *
 * @param enabled {@code USAGE_DROPOFF_ENABLED}
 * @param collectingSince {@code USAGE_DROPOFF_COLLECTING_SINCE} — {@code null} = activation day + 1
 *     (BR-19)
 */
@ConfigurationProperties(prefix = "usage.dropoff")
public record UsageDropoffProperties(boolean enabled, LocalDate collectingSince) {}
