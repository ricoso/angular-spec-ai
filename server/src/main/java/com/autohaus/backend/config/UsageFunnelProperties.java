package com.autohaus.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;

/**
 * Kill switch of the global booking funnel (REQ-038 K3, F-1..F-3, §10.7) / Kill-Switch des
 * globalen Buchungs-Trichters.
 *
 * <p>Only effective while {@code usage.enabled=true}; gated by G-DSB and therefore delivered as
 * {@code false} (AC-31). With {@code false} KQL 3 never runs and S6 is omitted without any
 * replacement (BR-8). / Wirkt nur mit aktivem {@code usage.enabled}; Default aus (Gate G-DSB).
 *
 * @param enabled {@code USAGE_FUNNEL_ENABLED}
 * @param collectingSince {@code USAGE_FUNNEL_COLLECTING_SINCE} — {@code null} = activation day + 1
 *     (BR-19)
 */
@ConfigurationProperties(prefix = "usage.funnel")
public record UsageFunnelProperties(boolean enabled, LocalDate collectingSince) {}
