package com.autohaus.backend.reporting;

import com.autohaus.backend.capacity.CapacityViewModel;
import com.autohaus.backend.usage.UsageStats;
import com.autohaus.backend.usage.insights.InsightsViewModel;

/**
 * Render input of the statistics tab (REQ-038 §10.1) / Render-Eingabe des Statistik-Tabs. A
 * {@code null} member means the source is switched off — its section, its jump link and any
 * {@code .st-empty} are omitted without replacement (BR-4, §5.1). / {@code null} = Quelle aus,
 * Sektion entfällt ersatzlos.
 *
 * <p>{@code StatisticsPageRenderer} decides exclusively on these fields: S0 (I-2/I-4), S2, S3, S4
 * only with {@code insights != null && insights.bookingsEnabled()}; S6 only with
 * {@code insights != null && insights.funnelEnabled()}; S1 and I-1/I-3 only with
 * {@code capacity != null}; S5 only with {@code usage != null}; S7 always. / Entscheidung allein
 * über diese Felder.
 *
 * @param usage REQ-037 snapshot, {@code null} when {@code usage.enabled=false}
 * @param insights K2/K3 view, {@code null} when neither insights nor (usage and funnel) is active
 * @param capacity K1 view, {@code null} when {@code capacity.enabled=false}
 * @param briefing S0, {@code null} when insights and capacity are both off
 */
public record StatisticsPage(
    UsageStats usage,
    InsightsViewModel insights,
    CapacityViewModel capacity,
    Briefing briefing
) {}
