package com.autohaus.backend.usage.insights;

import java.util.Map;

/**
 * Funnel counters of one day (REQ-038 §10.3) / Trichter-Zähler eines Tages.
 *
 * @param all cumulative stage counters over all sessions / kumulierte Stufenzähler gesamt
 * @param byDevice the same per device class; keys {@code mobile|tablet|desktop|unknown}
 * @param dropoff drop-off margins, {@code null} while {@code usage.dropoff.enabled=false} (N1)
 */
public record FunnelDay(FunnelCounts all, Map<String, FunnelCounts> byDevice, DropoffDay dropoff) {}
