package com.autohaus.backend.usage.insights;

import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Activates {@link InsightsScheduler} exactly when {@code usage.insights.enabled} <b>or</b>
 * ({@code usage.enabled} <b>and</b> {@code usage.funnel.enabled}) is true (REQ-038 §5.1, §14.1) /
 * aktiviert den Insights-Scheduler genau bei aktivem K2 oder aktivem Trichter.
 *
 * <p>The same rule decides whether {@code insights.json} answers 200. {@code usage.dropoff.enabled}
 * is subordinate to the funnel and never activates the bean on its own (N1). / Dieselbe Regel
 * entscheidet über {@code insights.json}.
 *
 * <p><b>One switch semantics everywhere (§5.1, §14):</b> every switch is read through
 * {@link #isSwitchOn} — Spring Boot's {@link Binder} with {@code Boolean} as target, i.e. exactly
 * the conversion the {@code @ConfigurationProperties} records ({@code UsageInsightsProperties},
 * {@code CapacityProperties}, …) and the page apply: {@code true}/{@code 1}/{@code yes}/{@code on}
 * are on, {@code false}/{@code 0}/{@code no}/{@code off} are off, case-insensitive and trimmed. An
 * empty or unknown value is off here — the record binding refuses such a value for its
 * {@code boolean} component, so the application does not start at all. Only a <b>missing</b>
 * property falls back to its delivery default: {@code usage.enabled} true (REQ-037
 * {@code matchIfMissing}), the REQ-038 switches false (AC-31). So {@code USAGE_INSIGHTS_ENABLED=1}
 * activates section, JSON <b>and</b> scheduler together — never a section without its job. /
 * Einheitliche Schalter-Semantik wie beim Properties-Binding; leere oder unbekannte Werte sind aus,
 * fehlende folgen den Auslieferungs-Defaults.
 */
public class InsightsSchedulerCondition implements Condition {

    /** Kill switch K2 / Kill-Switch der Buchungs-Insights. */
    public static final String INSIGHTS_ENABLED = "usage.insights.enabled";

    /** REQ-037 kill switch, required by the funnel / REQ-037-Schalter, Voraussetzung des Trichters. */
    public static final String USAGE_ENABLED = "usage.enabled";

    /** Kill switch K3 / Kill-Switch des Trichters. */
    public static final String FUNNEL_ENABLED = "usage.funnel.enabled";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return isActive(context.getEnvironment());
    }

    /**
     * The activation rule / die Aktivierungsregel.
     *
     * @param environment the application environment, {@code null} = delivery defaults / Umgebung
     * @return whether the insights scheduler must exist / ob der Scheduler existieren muss
     */
    public static boolean isActive(Environment environment) {
        boolean insights = isSwitchOn(environment, INSIGHTS_ENABLED, false);
        boolean usage = isSwitchOn(environment, USAGE_ENABLED, true);
        boolean funnel = isSwitchOn(environment, FUNNEL_ENABLED, false);
        return insights || (usage && funnel);
    }

    /**
     * Reads one boolean switch with the conversion of the properties binding (§5.1, §14) / liest
     * einen Schalter mit der Konvertierung des Properties-Bindings. Shared by every REQ-038
     * condition (also {@code CapacityScanCondition}). / Gemeinsam für alle REQ-038-Conditions.
     *
     * @param environment the application environment, {@code null} = property missing / Umgebung
     * @param key the relaxed property name, e.g. {@code capacity.enabled} / Property-Name
     * @param fallbackIfMissing the delivery default while the property does not exist at all /
     *     Default, solange die Property gar nicht existiert
     * @return {@code true} only for a value the binding reads as {@code true} / nur bei „an"
     */
    public static boolean isSwitchOn(Environment environment, String key, boolean fallbackIfMissing) {
        if (!(environment instanceof ConfigurableEnvironment configurable)) {
            return fallbackIfMissing;
        }
        Binder binder = Binder.get(configurable);
        try {
            BindResult<Boolean> bound = binder.bind(key, Boolean.class);
            if (bound.isBound()) {
                return bound.get();
            }
        } catch (BindException e) {
            // Unknown value (e.g. "maybe"): off — the record binding refuses the start anyway.
            return false;
        }
        // Unbound: either missing (delivery default) or empty (off — the record binding refuses
        // it); the raw value tells them apart with the same relaxed name resolution.
        return !binder.bind(key, String.class).isBound() && fallbackIfMissing;
    }
}
