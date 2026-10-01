package com.autohaus.backend.usage.insights;

import com.autohaus.backend.capacity.CapacityDayState;
import com.autohaus.backend.capacity.CapacityLevel;
import com.autohaus.backend.capacity.CapacityTrend;
import com.autohaus.backend.capacity.ProbeReason;
import com.autohaus.backend.capacity.ProbeStatus;
import com.autohaus.backend.capacity.ScanState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The binding JSON convention of every REQ-038 enum (§10.2 „JSON-Konvention für Enums") / die
 * verbindliche JSON-Konvention aller REQ-038-Enums.
 *
 * <p>Log line, blob and JSON endpoints always use the key ({@code @JsonValue key()}), never the
 * Java name, and {@code fromKey(key())} returns the constant. A foreign key becomes
 * {@code UNKNOWN} for the usage dimensions; for the capacity enums it is {@code null} (E6 — the
 * claim state and the level must stay unambiguous), and for {@link DropoffScenario},
 * {@link SearchHits} and {@link BookingDaypart} it is {@code null} as well: the caller discards
 * the row, a catch-all value would falsify Σ scenarios = drop-offs resp. Σ T-1 = bookings (E11). /
 * Fremde Schlüssel: {@code UNKNOWN} bzw. {@code null}.
 */
class EnumKeyConventionTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    static Stream<Arguments> enums() {
        return Stream.of(
            Arguments.of(SlotWeekday.class, SlotWeekday.UNKNOWN),
            Arguments.of(SlotDaypart.class, SlotDaypart.UNKNOWN),
            Arguments.of(LeadTimeBucket.class, LeadTimeBucket.UNKNOWN),
            Arguments.of(ServiceGroup.class, ServiceGroup.UNKNOWN),
            Arguments.of(BookingDaypart.class, null),
            Arguments.of(SearchHits.class, null),
            Arguments.of(DropoffScenario.class, null),
            Arguments.of(ProbeStatus.class, null),
            Arguments.of(ProbeReason.class, null),
            Arguments.of(ScanState.class, null),
            Arguments.of(CapacityLevel.class, null),
            Arguments.of(CapacityDayState.class, null),
            Arguments.of(CapacityTrend.class, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("enums")
    @DisplayName("§10.2: key() round-trips through fromKey and Jackson; a foreign key maps as specified")
    <E extends Enum<E>> void followsTheKeyConvention(Class<E> type, E foreign) throws Exception {
        Method key = type.getMethod("key");
        Method fromKey = type.getMethod("fromKey", String.class);
        for (E constant : type.getEnumConstants()) {
            String k = (String) key.invoke(constant);
            assertSame(constant, fromKey.invoke(null, k), type.getSimpleName() + "." + constant);
            assertEquals('"' + k + '"', JSON.writeValueAsString(constant), "the key, never the Java name");
        }
        assertSame(foreign, fromKey.invoke(null, "definitely-not-a-key"));
        assertSame(foreign, fromKey.invoke(null, (Object) null));
    }

    @Test
    @DisplayName("BR-11: tag rank 0–6 maps to the seven tag groups, every other rank to unknown")
    void mapsTagRanksToGroups() {
        ServiceGroup[] expected = {ServiceGroup.INSPECTION, ServiceGroup.TUV, ServiceGroup.BRAKE_FLUID,
            ServiceGroup.TIRE_CHANGE, ServiceGroup.ACTIONS_CHECKS, ServiceGroup.REPAIR, ServiceGroup.BODYWORK};
        for (int rank = 0; rank < expected.length; rank++) {
            assertSame(expected[rank], ServiceGroup.ofRank(rank), "rank " + rank);
        }
        assertSame(ServiceGroup.UNKNOWN, ServiceGroup.ofRank(-1));
        assertSame(ServiceGroup.UNKNOWN, ServiceGroup.ofRank(7));
    }

    @Test
    @DisplayName("§10.8: 0 days → none, 1–3 → few, ≥ 4 → many")
    void bucketsTheDayCountOfASearch() {
        assertSame(SearchHits.NONE, SearchHits.ofDayCount(-1));
        assertSame(SearchHits.NONE, SearchHits.ofDayCount(0));
        assertSame(SearchHits.FEW, SearchHits.ofDayCount(1));
        assertSame(SearchHits.FEW, SearchHits.ofDayCount(SearchHits.FEW_MAX_DAYS));
        assertSame(SearchHits.MANY, SearchHits.ofDayCount(SearchHits.FEW_MAX_DAYS + 1));
        assertNull(SearchHits.fromKey("some"), "foreign hits values are discarded (E11)");
    }
}
