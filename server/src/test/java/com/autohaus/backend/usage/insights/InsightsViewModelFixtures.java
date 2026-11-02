package com.autohaus.backend.usage.insights;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hand-built histories for the {@code InsightsViewModel*} tests (REQ-038 §13.3, §13.6) /
 * handgebaute Historien für die View-Model-Tests. Fictional branch names as in the mockup. /
 * Fiktive Standortnamen wie im Mockup.
 */
final class InsightsViewModelFixtures {

    /** Same configuration as the controller's JSON mapper (§12.5) / wie der Controller-Mapper. */
    static final ObjectMapper JSON = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.ALWAYS,
            JsonInclude.Include.ALWAYS))
        .build();

    private InsightsViewModelFixtures() {
    }

    /** A 24-hex branch id / eine 24-stellige Hex-ID. */
    static String id(int n) {
        return String.format("%024x", n);
    }

    /** Display names keyed by {@link #id(int)} 1..n in argument order / Anzeigenamen. */
    static Map<String, String> names(String... names) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < names.length; i++) {
            result.put(id(i + 1), names[i]);
        }
        return result;
    }

    // ---- histories / Historien ----

    static InsightsHistory history(LocalDate since, SortedMap<LocalDate, InsightsDay> days,
                                   SortedMap<LocalDate, Map<String, BranchCounts>> branchDays,
                                   SortedMap<YearMonth, Map<String, BranchCounts>> branchMonths) {
        return new InsightsHistory(InsightsHistory.CURRENT_SCHEMA_VERSION, null, since, null, null,
            days, branchDays, branchMonths);
    }

    static InsightsHistory funnelHistory(LocalDate funnelSince, LocalDate dropoffSince,
                                         SortedMap<LocalDate, InsightsDay> days) {
        return new InsightsHistory(InsightsHistory.CURRENT_SCHEMA_VERSION, null, null, funnelSince,
            dropoffSince, days, new TreeMap<>(), new TreeMap<>());
    }

    /** One compacted month plus complete global day rows (no „Tage fehlen") / verdichteter Monat. */
    static InsightsHistory compactedMonth(LocalDate since, YearMonth month, Map<String, BranchCounts> data,
                                          LocalDate daysUntil) {
        SortedMap<YearMonth, Map<String, BranchCounts>> months = new TreeMap<>();
        months.put(month, data);
        return history(since, zeroDays(since, daysUntil), new TreeMap<>(), months);
    }

    /** Global day rows with 0 bookings — marks the days as evaluated / ausgewertete Nulltage. */
    static SortedMap<LocalDate, InsightsDay> zeroDays(LocalDate from, LocalDate to) {
        SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            days.put(d, day(0).build());
        }
        return days;
    }

    /** Days with {@code bookings} each, all in daypart „day", slot MO/morning, lead 4-7. */
    static SortedMap<LocalDate, InsightsDay> simpleDays(LocalDate from, LocalDate to, long bookings) {
        SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            days.put(d, day(bookings).daypart("day", bookings).slot("MO", "morning", bookings)
                .lead("4-7", bookings).group("inspection", bookings).build());
        }
        return days;
    }

    static DayBuilder day(long bookings) {
        return new DayBuilder(bookings);
    }

    /** Fluent builder of one global day / Builder für einen globalen Tag. */
    static final class DayBuilder {
        private final Long bookings;
        private final Map<String, Long> daypart = new LinkedHashMap<>();
        private final Map<String, Map<String, Long>> slots = new LinkedHashMap<>();
        private final Map<String, Long> lead = new LinkedHashMap<>();
        private final Map<String, Long> groups = new LinkedHashMap<>();
        private Long slotUnknown = 0L;
        private Long mobility = 0L;
        private FunnelDay funnel;
        private boolean noDaypart;

        private DayBuilder(long bookings) {
            this.bookings = bookings;
        }

        DayBuilder daypart(String key, long value) {
            daypart.put(key, value);
            return this;
        }

        DayBuilder noDaypart() {
            noDaypart = true;
            return this;
        }

        DayBuilder slot(String weekday, String part, long value) {
            slots.computeIfAbsent(weekday, k -> new LinkedHashMap<>()).put(part, value);
            return this;
        }

        DayBuilder slotUnknown(long value) {
            slotUnknown = value;
            return this;
        }

        DayBuilder lead(String key, long value) {
            lead.put(key, value);
            return this;
        }

        DayBuilder group(String key, long value) {
            groups.put(key, value);
            return this;
        }

        DayBuilder mobility(long value) {
            mobility = value;
            return this;
        }

        DayBuilder funnel(FunnelDay value) {
            funnel = value;
            return this;
        }

        InsightsDay build() {
            return new InsightsDay(bookings, noDaypart ? null : daypart, slots, slotUnknown, lead, groups,
                mobility, 0L, funnel);
        }
    }

    // ---- branch counters / Standortzähler ----

    static BranchCounts branch(long bookings, long views, long offHours, long late) {
        return new BranchCounts(bookings, views, Map.of(), 0L, offHours, late);
    }

    static BranchCounts branch(long bookings) {
        return branch(bookings, 100, 0, 0);
    }

    static BranchCounts matrixBranch(long bookings, Map<String, Long> groups, long mobility) {
        return new BranchCounts(bookings, 100L, groups, mobility, 0L, 0L);
    }

    /** Branch data keyed by {@link #id(int)} 1..n / Standortdaten nach ID. */
    static Map<String, BranchCounts> data(BranchCounts... counts) {
        Map<String, BranchCounts> result = new LinkedHashMap<>();
        for (int i = 0; i < counts.length; i++) {
            result.put(id(i + 1), counts[i]);
        }
        return result;
    }

    // ---- funnel / Trichter ----

    static FunnelCounts fc(long visits, long services, long notes, long search, long booking) {
        return new FunnelCounts(visits, services, notes, search, booking);
    }

    /** A funnel-only day row / eine reine Trichter-Tageszeile. */
    static InsightsDay funnelDay(FunnelCounts all, Map<String, FunnelCounts> byDevice, DropoffDay dropoff) {
        return new InsightsDay(null, null, null, null, null, null, null, null,
            new FunnelDay(all, byDevice, dropoff));
    }

    /**
     * Device counters from stage-1 sessions per class; every class drops evenly so that the sum
     * equals „Alle" / Geräte-Zähler, deren Summe „Alle" ergibt.
     */
    static Map<String, FunnelCounts> devices(long mobile, long tablet, long desktop, long unknown) {
        Map<String, FunnelCounts> result = new LinkedHashMap<>();
        result.put("mobile", shape(mobile));
        result.put("tablet", shape(tablet));
        result.put("desktop", shape(desktop));
        result.put("unknown", shape(unknown));
        return result;
    }

    /** Fixed shape: 100 % · 60 % · 40 % · 20 % · 10 % (integer division) / feste Form. */
    static FunnelCounts shape(long visits) {
        return fc(visits, visits * 6 / 10, visits * 4 / 10, visits * 2 / 10, visits / 10);
    }

    static FunnelCounts sum(Map<String, FunnelCounts> byDevice) {
        long[] s = new long[5];
        for (FunnelCounts c : byDevice.values()) {
            s[0] += c.visits();
            s[1] += c.services();
            s[2] += c.notes();
            s[3] += c.search();
            s[4] += c.booking();
        }
        return fc(s[0], s[1], s[2], s[3], s[4]);
    }

    static Map<String, Long> stages(long s1, long s2, long s3, long s4) {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("1", s1);
        result.put("2", s2);
        result.put("3", s3);
        result.put("4", s4);
        return result;
    }

    // ---- JSON checks / JSON-Prüfungen ----

    /**
     * Walks the serialised model: every object with a non-{@code VALUE} {@code state} carries no
     * number and heat 0 (AC-20, TC-57) / jede ausgeblendete Zelle ohne Zahl.
     *
     * @return the number of hidden cells found / Anzahl gefundener ausgeblendeter Zellen
     */
    static int assertNoHiddenValues(Object viewModel) throws Exception {
        JsonNode root = JSON.valueToTree(viewModel);
        List<JsonNode> hidden = new ArrayList<>();
        collectHidden(root, hidden);
        for (JsonNode cell : hidden) {
            assertTrue(cell.get("value").isNull(), "value of " + cell);
            assertTrue(cell.get("ratio").isNull(), "ratio of " + cell);
            assertTrue(cell.get("percent").isNull(), "percent of " + cell);
            assertEquals(0, cell.get("heat").asInt(), "heat of " + cell);
        }
        return hidden.size();
    }

    private static void collectHidden(JsonNode node, List<JsonNode> hidden) {
        if (node.isObject()) {
            JsonNode state = node.get("state");
            if (state != null && state.isTextual() && node.has("heat") && !"VALUE".equals(state.asText())) {
                hidden.add(node);
            }
            node.properties().forEach(e -> collectHidden(e.getValue(), hidden));
        } else if (node.isArray()) {
            node.forEach(child -> collectHidden(child, hidden));
        }
    }
}
