package com.autohaus.backend.usage.insights;

import com.autohaus.backend.capacity.CapacityScanCondition;
import com.autohaus.backend.capacity.CapacityScanScheduler;
import com.autohaus.backend.config.CapacityProperties;
import com.autohaus.backend.config.UsageFunnelProperties;
import com.autohaus.backend.config.UsageInsightsProperties;
import com.autohaus.backend.config.UsageStatsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * One switch semantics for every REQ-038 switch (§5.1, §14; security finding „Schalter-Semantik")
 * / eine einheitliche Schalter-Semantik.
 *
 * <p>The page and {@code insights.json}/{@code capacity.json} follow the {@code @ConfigurationProperties}
 * records, the job beans follow {@link InsightsSchedulerCondition} and {@link CapacityScanCondition}.
 * Both must read an environment variable identically — otherwise {@code USAGE_INSIGHTS_ENABLED=1}
 * would show the section without its rollup, {@code CAPACITY_SCAN_ENABLED=1} S1 without its scan.
 * Every case runs through the real {@code application.yml} placeholders ({@code ${VAR:default}}),
 * so the comparison covers exactly the production path. / Jeder Fall über die echten Platzhalter
 * des {@code application.yml}.
 */
class SwitchSemanticsTest {

    /** Values the binding reads as on / Werte, die das Binding als „an" liest. */
    private static final String[] ON = {"true", "1", "yes", "on", "TRUE", " On "};

    /** Values the binding reads as off / Werte, die das Binding als „aus" liest. */
    private static final String[] OFF = {"false", "0", "no", "off", "FALSE", " Off "};

    /**
     * Values the binding refuses for a {@code boolean} component — including the empty value of a
     * variable that is set but blank. / Werte, die das Binding ablehnt, auch der leere Wert.
     */
    private static final String[] UNKNOWN = {"", " ", "maybe", "2", "ja", "enabled"};

    /** One switch: its variable, the record reading and the condition reading. */
    private record Switch(String variable, Map<String, Object> companions, Predicate<Binder> record,
                          Predicate<MockEnvironment> condition) {

        @Override
        public String toString() {
            return variable;
        }
    }

    static Stream<Switch> switches() {
        return Stream.of(
            new Switch("USAGE_INSIGHTS_ENABLED", Map.of(),
                binder -> bind(binder, "usage.insights", UsageInsightsProperties.class).enabled(),
                InsightsSchedulerCondition::isActive),
            new Switch("USAGE_FUNNEL_ENABLED", Map.of(),
                binder -> bind(binder, "usage.funnel", UsageFunnelProperties.class).enabled(),
                InsightsSchedulerCondition::isActive),
            // usage.enabled only matters together with the funnel (§5.1 rows 5–8).
            new Switch("USAGE_STATS_ENABLED", Map.of("USAGE_FUNNEL_ENABLED", "true"),
                binder -> bind(binder, "usage", UsageStatsProperties.class).enabled(),
                InsightsSchedulerCondition::isActive),
            new Switch("CAPACITY_SCAN_ENABLED", Map.of(),
                binder -> bind(binder, "capacity", CapacityProperties.class).enabled(),
                CapacityScanCondition::isActive));
    }

    static Stream<Arguments> switchesWithValues(String[] values) {
        return switches().flatMap(s -> Stream.of(values).map(value -> Arguments.of(s, value)));
    }

    static Stream<Arguments> onValues() {
        return switchesWithValues(ON);
    }

    static Stream<Arguments> offValues() {
        return switchesWithValues(OFF);
    }

    static Stream<Arguments> unknownValues() {
        return switchesWithValues(UNKNOWN);
    }

    @ParameterizedTest(name = "{0}=''{1}''")
    @MethodSource("onValues")
    @DisplayName("§5.1: true/1/yes/on (any case, trimmed) switch the record AND the job bean on")
    void readsOnValuesLikeTheBinding(Switch s, String value) throws IOException {
        MockEnvironment environment = environment(s, value);

        assertTrue(s.record().test(Binder.get(environment)), "record binding");
        assertTrue(s.condition().test(environment), "condition");
    }

    @ParameterizedTest(name = "{0}=''{1}''")
    @MethodSource("offValues")
    @DisplayName("§5.1: false/0/no/off (any case, trimmed) switch the record AND the job bean off")
    void readsOffValuesLikeTheBinding(Switch s, String value) throws IOException {
        MockEnvironment environment = environment(s, value);

        assertFalse(s.record().test(Binder.get(environment)), "record binding");
        assertFalse(s.condition().test(environment), "condition");
    }

    @ParameterizedTest(name = "{0}=''{1}''")
    @MethodSource("unknownValues")
    @DisplayName("§5.1: an empty or unknown value makes the binding refuse the start; the condition stays off")
    void keepsTheJobOffForAnUnknownValue(Switch s, String value) throws IOException {
        MockEnvironment environment = environment(s, value);

        assertThrows(BindException.class, () -> s.record().test(Binder.get(environment)),
            "the record binding refuses the value — the application does not start");
        assertFalse(s.condition().test(environment), "never a job without its switch");
    }

    @Test
    @DisplayName("AC-31: without any variable the shipped defaults keep every job off")
    void shipsEveryJobOff() throws IOException {
        MockEnvironment environment = applicationYml(Map.of());

        assertFalse(InsightsSchedulerCondition.isActive(environment));
        assertFalse(CapacityScanCondition.isActive(environment));
        assertTrue(bind(Binder.get(environment), "usage", UsageStatsProperties.class).enabled(),
            "REQ-037 stays on by default");
    }

    @Test
    @DisplayName("missing property (no application.yml): delivery defaults — usage.enabled true, the REQ-038 switches false")
    void aMissingPropertyFallsBackToTheDeliveryDefault() {
        assertFalse(InsightsSchedulerCondition.isActive(new MockEnvironment()));
        assertFalse(CapacityScanCondition.isActive(new MockEnvironment()));
        assertTrue(InsightsSchedulerCondition.isActive(
            new MockEnvironment().withProperty("usage.funnel.enabled", "on")), "usage.enabled missing → true");
        assertFalse(InsightsSchedulerCondition.isActive(new MockEnvironment()
            .withProperty("usage.funnel.enabled", "on").withProperty("usage.enabled", "")),
            "an EMPTY usage.enabled is off (the record binding refuses it), not the missing-default");
        assertFalse(InsightsSchedulerCondition.isActive(null));
        assertFalse(CapacityScanCondition.isActive(null));
        assertTrue(InsightsSchedulerCondition.isSwitchOn(null, "usage.enabled", true));
    }

    @Test
    @DisplayName("relaxed names: the condition resolves the property like the binding does")
    void resolvesRelaxedNames() {
        MockEnvironment environment = new MockEnvironment().withProperty("capacity.ENABLED", "yes");

        assertTrue(CapacityScanCondition.isActive(environment));
        assertTrue(bind(Binder.get(environment), "capacity", CapacityProperties.class).enabled());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "on", "YES"})
    @DisplayName("both conditions answer through Spring's Condition contract")
    void matchesThroughTheConditionContext(String value) {
        ConditionContext capacity = mock(ConditionContext.class);
        when(capacity.getEnvironment()).thenReturn(new MockEnvironment().withProperty("capacity.enabled", value));
        ConditionContext insights = mock(ConditionContext.class);
        when(insights.getEnvironment()).thenReturn(
            new MockEnvironment().withProperty("usage.insights.enabled", value));

        assertTrue(new CapacityScanCondition().matches(capacity, null));
        assertTrue(new InsightsSchedulerCondition().matches(insights, null));
    }

    @Test
    @DisplayName("§14.1: both job schedulers are gated by these conditions — no raw-text @ConditionalOnProperty")
    void theJobSchedulersUseTheConditions() {
        assertArrayEquals(new Object[] {CapacityScanCondition.class},
            CapacityScanScheduler.class.getAnnotation(Conditional.class).value());
        assertArrayEquals(new Object[] {InsightsSchedulerCondition.class},
            InsightsScheduler.class.getAnnotation(Conditional.class).value());
        for (Class<?> scheduler : new Class<?>[] {CapacityScanScheduler.class, InsightsScheduler.class}) {
            assertNull(scheduler.getAnnotation(
                org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class), scheduler.getSimpleName());
        }
    }

    // ---- fixtures / Testdaten ----

    private static MockEnvironment environment(Switch s, String value) throws IOException {
        Map<String, Object> variables = new java.util.LinkedHashMap<>(s.companions());
        variables.put(s.variable(), value);
        return applicationYml(variables);
    }

    /**
     * An environment over the real {@code application.yml}; its placeholders resolve against the
     * given variables only — never the process environment. / Umgebung über das echte
     * {@code application.yml}, Platzhalter nur gegen die übergebenen Variablen.
     */
    private static MockEnvironment applicationYml(Map<String, Object> variables) throws IOException {
        MockEnvironment environment = new MockEnvironment();
        variables.forEach((key, value) -> environment.setProperty(key, String.valueOf(value)));
        for (PropertySource<?> yml : new YamlPropertySourceLoader().load("application.yml",
            new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(yml);
        }
        return environment;
    }

    private static <T> T bind(Binder binder, String prefix, Class<T> type) {
        return binder.bind(prefix, type).orElseThrow(() -> new AssertionError(prefix + " did not bind"));
    }
}
