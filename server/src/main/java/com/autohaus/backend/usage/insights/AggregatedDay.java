package com.autohaus.backend.usage.insights;

import java.util.Map;

/**
 * Result of {@code InsightsAggregator.aggregateDay(day)} (REQ-038 §10.3) — never persisted as
 * such; the scheduler folds it into {@link InsightsHistory}. / Aggregat eines Tages, wird nie
 * direkt persistiert.
 *
 * @param global the day's global counters / globale Tageszähler
 * @param branches per-branch counters keyed by branchId / Standortzähler je branchId
 */
public record AggregatedDay(InsightsDay global, Map<String, BranchCounts> branches) {}
