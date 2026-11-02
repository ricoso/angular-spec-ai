package com.autohaus.backend.capacity;

import com.autohaus.backend.usage.insights.InsightsSchedulerCondition;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Activates {@link CapacityScanScheduler} exactly when {@code capacity.enabled} is on (REQ-038 K1,
 * §5.1, §14.1) / aktiviert den Kapazitäts-Scan genau bei eingeschaltetem K1.
 *
 * <p>Replaces {@code @ConditionalOnProperty(havingValue = "true")}, which compares the raw text and
 * therefore disagreed with the {@code CapacityProperties} binding: {@code CAPACITY_SCAN_ENABLED=1}
 * showed S1 and {@code capacity.json} without any scanner. The value is now read through
 * {@link InsightsSchedulerCondition#isSwitchOn} — the conversion of the properties binding
 * ({@code true|1|yes|on} on, {@code false|0|no|off}, empty and unknown values off; missing = off,
 * AC-31). / Gleiche Semantik wie das Properties-Binding; Default aus.
 */
public class CapacityScanCondition implements Condition {

    /** Kill switch K1 / Kill-Switch des Kapazitäts-Scans. */
    public static final String CAPACITY_ENABLED = "capacity.enabled";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return isActive(context.getEnvironment());
    }

    /**
     * The activation rule / die Aktivierungsregel.
     *
     * @param environment the application environment, {@code null} = delivery default / Umgebung
     * @return whether the capacity scan scheduler must exist / ob der Scheduler existieren muss
     */
    public static boolean isActive(Environment environment) {
        return InsightsSchedulerCondition.isSwitchOn(environment, CAPACITY_ENABLED, false);
    }
}
