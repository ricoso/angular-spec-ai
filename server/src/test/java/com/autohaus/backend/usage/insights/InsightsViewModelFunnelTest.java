package com.autohaus.backend.usage.insights;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.assertNoHiddenValues;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.devices;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.fc;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.funnelDay;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.funnelHistory;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.stages;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.sum;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S6 in the view model — funnel, device columns and drop-off scenarios (REQ-038 TC-52, TC-57,
 * TC-63 build part, TC-64 view-model part; BR-8, BR-9 Nr. 5/7, BR-27, BR-31, BR-32, AC-33,
 * AC-35, AC-36) / Buchungsstrecke im View-Model: Trichter, Geräteklassen, Absprungszenarien.
 *
 * <p>Clock: today 19.11.2026 → „aktueller Monat bis gestern" = 01.–18.11.2026; the funnel and the
 * drop-off scenarios are collected from 01.11. unless stated otherwise. / Zeitraum 01.–18.11.2026.
 */
class InsightsViewModelFunnelTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);
    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    private static FunnelView funnel(SortedMap<LocalDate, InsightsDay> days, boolean dropoff) {
        InsightsViewModel vm = InsightsViewModel.build(funnelHistory(START, START, days), Map.of(), TODAY,
            false, true, dropoff);
        assertTrue(vm.funnelEnabled());
        return vm.funnel();
    }

    private static SortedMap<LocalDate, InsightsDay> oneDay(InsightsDay day) {
        SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
        days.put(DAY, day);
        return days;
    }

    // -----------------------------------------------------------------------------------------
    // Funnel stages / Trichterstufen
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("F-1/F-2: reached, % of start, % of previous (one decimal), dropped per stage")
    void stagesWithPercentages() {
        FunnelView f = funnel(oneDay(funnelDay(fc(200, 150, 90, 60, 21), null, null)), false);

        assertEquals(List.of("visits", "services", "notes", "search", "booking"),
            f.stages().stream().map(FunnelStage::key).toList());
        FunnelStage notes = f.stages().get(2);
        assertEquals(Cell.of(90), notes.reached());
        assertEquals(Cell.percentOf(45.0), notes.percentOfStart());
        assertEquals(Cell.percentOf(60.0), notes.percentOfPrevious());
        assertEquals(Cell.of(30), notes.droppedHere(), "90 − 60");
        assertEquals(Cell.percentOf(100.0), f.stages().getFirst().percentOfStart());
        assertNull(f.stages().getFirst().percentOfPrevious());
        assertNull(f.stages().getLast().droppedHere(), "stage 5 has no drop-out");
        assertEquals(Cell.percentOf(35.0), f.stages().getLast().percentOfPrevious(), "21 / 60");
        assertEquals("aktueller Monat bis gestern", f.period().label());
        assertEquals("01.11.–18.11.2026", f.period().rangeLabel());
        assertNull(f.dropoff(), "dropoff switched off (AC-35)");
    }

    @Test
    @DisplayName("TC-57 stage 4 = 3: % of start (4) and % of previous (4, 5) are k. A. without value")
    void hiddenStageWithholdsItsPercentages() throws Exception {
        InsightsViewModel vm = InsightsViewModel.build(funnelHistory(START, null,
            oneDay(funnelDay(fc(100, 60, 30, 3, 0), null, null))), Map.of(), TODAY, false, true, false);
        List<FunnelStage> stages = vm.funnel().stages();

        assertEquals(Cell.suppressed(), stages.get(3).reached());
        assertEquals(Cell.withheld(), stages.get(3).percentOfStart());
        assertEquals(Cell.withheld(), stages.get(3).percentOfPrevious());
        assertEquals(Cell.withheld(), stages.get(4).percentOfPrevious());
        assertEquals(Cell.percentOf(0.0), stages.get(4).percentOfStart(), "0 bookings stay 0");
        // erreicht(3) − Ausstieg(3) would reveal erreicht(4): the drop-out is withheld as well
        assertEquals(Cell.withheld(), stages.get(2).droppedHere());
        assertEquals(Cell.suppressed(), stages.get(3).droppedHere());
        assertTrue(assertNoHiddenValues(vm) >= 5);
    }

    @Test
    @DisplayName("below 20 sessions the funnel percentages are n < 20")
    void fewSessions() {
        FunnelView f = funnel(oneDay(funnelDay(fc(18, 12, 6, 0, 0), null, null)), false);

        assertEquals(Cell.tooFew(), f.stages().get(2).percentOfStart());
        assertEquals(Cell.of(18), f.stages().getFirst().reached());
    }

    @Test
    @DisplayName("days before the funnel start and inconsistent days are never counted (BR-19)")
    void onlyValidDaysCount() {
        SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
        days.put(DAY, funnelDay(fc(100, 60, 30, 20, 10), null, null));
        days.put(DAY.plusDays(1), funnelDay(fc(10, 20, 5, 5, 1), null, null)); // not monotone
        days.put(DAY.plusDays(2), funnelDay(new FunnelCounts(10L, null, 5L, 5L, 1L), null, null));
        days.put(LocalDate.of(2026, 10, 31), funnelDay(fc(999, 999, 999, 999, 999), null, null));

        InsightsViewModel vm = InsightsViewModel.build(funnelHistory(START, null, days), Map.of(), TODAY,
            false, true, false);

        assertEquals(Cell.of(100), vm.funnel().stages().getFirst().reached());
    }

    @Test
    @DisplayName("before the first funnel day: every stage „—“, never 0 (AC-23)")
    void noFunnelData() {
        InsightsViewModel vm = InsightsViewModel.build(funnelHistory(null, null, new TreeMap<>()), Map.of(),
            TODAY, false, true, true);

        FunnelView f = vm.funnel();
        assertTrue(f.stages().stream().allMatch(s -> s.reached().equals(Cell.noData())));
        assertEquals(List.of(), f.deviceColumns());
        assertNotNull(f.dropoff());
        assertEquals(List.of(), f.dropoff().rows());
        assertEquals(Cell.noData(), f.dropoff().total().total());
        assertNull(vm.funnelCollectingSince());
    }

    // -----------------------------------------------------------------------------------------
    // TC-52 device columns / Geräteklassen
    // -----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("TC-52 device classes F-3 / Geräteklassen")
    class DeviceColumns {

        private FunnelView withDevices(long mobile, long tablet, long desktop, long unknown) {
            Map<String, FunnelCounts> byDevice = devices(mobile, tablet, desktop, unknown);
            return funnel(oneDay(funnelDay(sum(byDevice), byDevice, null)), false);
        }

        private void assertRowsAddUp(FunnelView f) {
            for (int k = 0; k < 4; k++) {
                Cell all = f.stages().get(k).droppedHere();
                long visible = 0;
                boolean allVisible = all.state() == CellState.VALUE;
                for (String column : f.deviceColumns()) {
                    Cell cell = f.byDevice().get(column).get(k);
                    allVisible &= cell.state() == CellState.VALUE;
                    visible += cell.state() == CellState.VALUE ? cell.value() : 0;
                }
                if (allVisible) {
                    assertEquals(all.value(), visible, "row " + (k + 1) + ": Alle = Σ Spalten");
                }
            }
        }

        @Test
        @DisplayName("TC-52 (1) Mobil 60, Desktop 40, Tablet 19, Unbekannt 12 → Sonstige (Tablet, Unbekannt)")
        void smallClassesMergeIntoOther() {
            FunnelView f = withDevices(60, 19, 40, 12);

            assertEquals(List.of("mobile", "desktop", "other"), f.deviceColumns());
            assertEquals(List.of("tablet", "unknown"), f.otherDevices());
            assertEquals(Cell.of(13), f.byDevice().get("other").getFirst(), "tablet 8 + unknown 5");
            assertFalse(f.byDevice().containsKey("tablet"), "no hidden class as its own key");
            assertRowsAddUp(f);
        }

        @Test
        @DisplayName("TC-52 (2) all classes ≥ 20 → four own columns; row rule 4 a withholds a partner")
        void allClassesVisible() {
            FunnelView f = withDevices(60, 20, 40, 25);

            assertEquals(List.of("mobile", "tablet", "desktop", "unknown"), f.deviceColumns());
            assertEquals(List.of(), f.otherDevices());
            // stage 2: mobile 12, tablet 4 (< 5), desktop 8, unknown 5 → the smallest visible (5) k. A.
            assertEquals(Cell.suppressed(), f.byDevice().get("tablet").get(1));
            assertEquals(Cell.withheld(), f.byDevice().get("unknown").get(1));
            assertEquals(Cell.of(12), f.byDevice().get("mobile").get(1));
            assertRowsAddUp(f);
        }

        @Test
        @DisplayName("TC-52 (3) Sonstige 17 < 20 → the smallest visible class (Desktop) joins it")
        void otherBelowTwentyTakesSmallestClass() {
            FunnelView f = withDevices(60, 12, 40, 5);

            assertEquals(List.of("mobile", "other"), f.deviceColumns());
            assertEquals(List.of("tablet", "desktop", "unknown"), f.otherDevices());
            assertRowsAddUp(f);
        }

        @Test
        @DisplayName("TC-52 (4) Mobil 25, Desktop 18 → no own class left, only „Alle“")
        void noOwnClassLeft() {
            FunnelView f = withDevices(25, 0, 18, 0);

            assertEquals(List.of(), f.deviceColumns());
            assertEquals(List.of(), f.otherDevices());
            assertEquals(Map.of(), f.byDevice());
        }

        @Test
        @DisplayName("device counters that do not add up to „Alle“ drop the breakdown entirely")
        void inconsistentDevicesDropTheBreakdown() {
            Map<String, FunnelCounts> byDevice = devices(60, 20, 40, 25);
            FunnelView f = funnel(oneDay(funnelDay(fc(999, 500, 300, 200, 100), byDevice, null)), false);
            Map<String, FunnelCounts> foreign = new LinkedHashMap<>(devices(60, 20, 40, 25));
            foreign.put("watch", fc(1, 1, 1, 1, 1));
            FunnelView g = funnel(oneDay(funnelDay(sum(devices(60, 20, 40, 25)), foreign, null)), false);

            assertEquals(List.of(), f.deviceColumns());
            assertEquals(List.of(), g.deviceColumns());
        }
    }

    // -----------------------------------------------------------------------------------------
    // TC-64 / TC-63 drop-off scenarios / Absprungszenarien
    // -----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("TC-64/TC-63 drop-off scenarios / Absprungszenarien")
    class Dropoff {

        /** scenario → stage values 1..4 (catalogue order is irrelevant here) / Stufenwerte. */
        private final Map<String, long[]> steps = new LinkedHashMap<>();
        /** scenario → device values mobile, tablet, desktop, unknown / Gerätewerte. */
        private final Map<String, long[]> devicesOf = new LinkedHashMap<>();

        Dropoff() {
            put("no-slots", new long[] {0, 0, 0, 146}, new long[] {94, 3, 47, 2});
            put("server-error", new long[] {38, 21, 12, 18}, new long[] {58, 0, 31, 0});
            put("access-denied", new long[] {11, 0, 0, 0}, new long[] {7, 0, 4, 0});
            put("slot-conflict", new long[] {0, 0, 0, 11}, new long[] {6, 0, 5, 0});
            put("booking-rejected", new long[] {0, 0, 0, 7}, new long[] {5, 0, 2, 0});
            put("search-rejected", new long[] {0, 0, 3, 0}, new long[] {2, 0, 1, 0});
            put("requests-lost", new long[] {6, 4, 3, 5}, new long[] {12, 0, 6, 0});
            put("quiet-1", new long[] {542, 0, 0, 0}, new long[] {330, 6, 200, 6});
            put("quiet-2", new long[] {0, 283, 0, 0}, new long[] {170, 3, 110, 0});
            put("quiet-3", new long[] {0, 0, 129, 0}, new long[] {80, 2, 47, 0});
            put("quiet-4", new long[] {0, 0, 0, 342}, new long[] {195, 0, 143, 4});
        }

        private void put(String scenario, long[] stageValues, long[] deviceValues) {
            steps.put(scenario, stageValues);
            devicesOf.put(scenario, deviceValues);
        }

        private DropoffDay dropoffDay() {
            Map<String, Map<String, Long>> byStep = new LinkedHashMap<>();
            Map<String, Map<String, Long>> byDevice = new LinkedHashMap<>();
            String[] classes = {"mobile", "tablet", "desktop", "unknown"};
            steps.forEach((scenario, s) -> byStep.put(scenario, stages(s[0], s[1], s[2], s[3])));
            devicesOf.forEach((scenario, d) -> {
                Map<String, Long> perDevice = new LinkedHashMap<>();
                for (int c = 0; c < classes.length; c++) {
                    perDevice.put(classes[c], d[c]);
                }
                byDevice.put(scenario, perDevice);
            });
            return new DropoffDay(byStep, byDevice);
        }

        /** Funnel devices whose drop-offs match the scenario margins (sum row = Ausstieg). */
        private Map<String, FunnelCounts> funnelDevices() {
            Map<String, FunnelCounts> byDevice = new LinkedHashMap<>();
            byDevice.put("mobile", fc(1109, 749, 559, 469, 150));
            byDevice.put("tablet", fc(20, 14, 10, 9, 6));
            byDevice.put("desktop", fc(736, 511, 401, 346, 140));
            byDevice.put("unknown", fc(16, 10, 6, 5, 4));
            return byDevice;
        }

        private FunnelView view() {
            Map<String, FunnelCounts> byDevice = funnelDevices();
            return funnel(oneDay(funnelDay(sum(byDevice), byDevice, dropoffDay())), true);
        }

        private DropoffRow row(DropoffView view, String key) {
            return view.rows().stream().filter(r -> r.scenarioKey().equals(key)).findFirst().orElseThrow();
        }

        @Test
        @DisplayName("TC-64 rows by total descending, ties in catalogue order, hidden totals after, A-0 last")
        void rowOrder() {
            DropoffView d = view().dropoff();

            assertEquals(List.of("no-slots", "server-error", "requests-lost", "access-denied", "slot-conflict",
                "booking-rejected", "search-rejected", "quiet"),
                d.rows().stream().map(DropoffRow::scenarioKey).toList());
            assertEquals(START, d.collectingSince());
        }

        @Test
        @DisplayName("TC-64 3 → < 5, secondary suppression → k. A.; the sum row equals the funnel difference")
        void suppressionAndSumRow() throws Exception {
            FunnelView f = view();
            DropoffView d = f.dropoff();

            assertEquals(Cell.suppressed(), row(d, "search-rejected").byStep().get(2));
            assertEquals(Cell.suppressed(), row(d, "search-rejected").total());
            assertEquals(Cell.withheld(), row(d, "search-rejected").share());
            assertEquals(Cell.suppressed(), row(d, "requests-lost").byStep().get(1));
            assertEquals(Cell.withheld(), row(d, "server-error").byStep().get(1), "column 2 had one < 5");
            assertEquals(Cell.withheld(), row(d, "booking-rejected").total(), "Gesamt column had one < 5");
            assertEquals(Cell.withheld(), row(d, "booking-rejected").byStep().get(3), "row total hidden");
            assertEquals(Cell.of(146), row(d, "no-slots").total());
            assertEquals(Cell.percentOf(9.2), row(d, "no-slots").share(), "146 / 1581");
            // AC-33: Alle Abbrüche = visits − bookings, the sum row is „Ausstieg nach Stufe"
            assertEquals(Cell.of(1581), d.total().total());
            assertEquals(Cell.percentOf(100.0), d.total().share());
            assertEquals(1881L - 300L, d.total().total().value());
            for (int k = 0; k < 4; k++) {
                assertEquals(f.stages().get(k).droppedHere(), d.total().byStep().get(k));
            }
            assertNoHiddenValues(f);
        }

        @Test
        @DisplayName("TC-64 A-0 is one aggregated row; A-0 and A-8 carry „Messartefakt möglich“ (BR-32)")
        void quietRowAndHints() {
            DropoffView d = view().dropoff();
            DropoffRow quiet = d.rows().getLast();

            assertEquals("quiet", quiet.scenarioKey());
            assertEquals(List.of(Cell.of(542), Cell.of(283), Cell.of(129), Cell.of(342)), quiet.byStep());
            assertEquals(Cell.of(1296), quiet.total());
            assertTrue(quiet.hint().endsWith("– Messartefakt möglich"));
            assertEquals("Stufe 4: Formular bzw. Übersicht; genauer nur mit dem Step-Beacon (Folge-REQ 1)"
                + " – Messartefakt möglich", quiet.hint(), "§8 A-0 with „Folge-REQ 1“");
            assertEquals(InsightsViewModelFunnel.SC_QUIET_HINT + " – " + InsightsViewModelFunnel.HINT_ARTIFACT,
                quiet.hint(), "the constants are the single source (BR-32)");
            assertEquals("Netz bzw. Circuit Breaker – Messartefakt möglich", row(d, "requests-lost").hint());
            assertEquals("Kapazität bzw. Terminangebot, zusammen mit S1 lesen", row(d, "no-slots").hint());
            assertNull(d.total().hint());
        }

        @Test
        @DisplayName("TC-64 devices: Tablet 14 and Unbekannt 12 merge into „Sonstige“ = 26 (BR-31)")
        void deviceColumns() {
            FunnelView f = view();
            DropoffView d = f.dropoff();

            assertEquals(List.of("mobile", "desktop", "other"), d.deviceColumns());
            assertEquals(List.of("tablet", "unknown"), d.otherDevices());
            assertEquals(Cell.of(26), d.total().byDevice().get("other"));
            assertEquals(Cell.of(959), d.total().byDevice().get("mobile"));
            assertEquals(Cell.suppressed(), row(d, "access-denied").byDevice().get("desktop"));
            assertEquals(Cell.withheld(), row(d, "access-denied").byDevice().get("mobile"), "row rule 4 a");
            // the funnel keeps its own grouping by sessions (BR-9 Nr. 7)
            assertEquals(List.of("mobile", "desktop", "other"), f.deviceColumns());
            assertEquals(Cell.suppressed(), f.byDevice().get("other").get(2));
            assertEquals(Cell.withheld(), f.byDevice().get("desktop").get(2));
        }

        @Test
        @DisplayName("TC-64 every row with a visible total and every column with a visible sum is protected")
        void noRecomputableCell() {
            DropoffView d = view().dropoff();

            for (DropoffRow r : d.rows()) {
                if (r.total().state() == CellState.VALUE) {
                    assertNotEquals1Hidden(r.byStep());
                    assertNotEquals1Hidden(List.copyOf(r.byDevice().values()));
                }
            }
        }

        private void assertNotEquals1Hidden(List<Cell> cells) {
            long hidden = cells.stream().filter(c -> c.state() != CellState.VALUE).count();
            boolean visiblePositive = cells.stream()
                .anyMatch(c -> c.state() == CellState.VALUE && c.value() != null && c.value() > 0);
            assertFalse(hidden == 1 && visiblePositive, "exactly one hidden cell in " + cells);
        }

        @Test
        @DisplayName("TC-63 an unknown scenario key is discarded; the sum row keeps the valid values")
        void unknownScenarioDiscarded() {
            steps.put("foo", new long[] {50, 0, 0, 0});
            devicesOf.put("foo", new long[] {50, 0, 0, 0});
            FunnelView f = view();
            DropoffView d = f.dropoff();

            assertTrue(d.rows().stream().noneMatch(r -> r.scenarioKey().equals("foo")));
            assertEquals(Cell.of(1581), d.total().total());
        }

        @Test
        @DisplayName("TC-63 a day without drop-off data and days before the drop-off start give „—“")
        void missingDropoffIsNoData() {
            SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
            days.put(DAY, funnelDay(fc(100, 60, 30, 20, 10), null, null));
            days.put(DAY.plusDays(1), funnelDay(fc(100, 60, 30, 20, 10), null,
                new DropoffDay(Map.of("no-slots", stages(0, 0, 0, 10)), Map.of())));
            LocalDate dropoffSince = DAY.plusDays(2);

            InsightsViewModel vm = InsightsViewModel.build(funnelHistory(START, dropoffSince, days), Map.of(),
                TODAY, false, true, true);
            DropoffView d = vm.funnel().dropoff();

            assertEquals(dropoffSince, d.collectingSince());
            assertEquals(List.of(), d.rows());
            assertEquals(Cell.noData(), d.total().total());
            assertEquals(List.of(Cell.noData(), Cell.noData(), Cell.noData(), Cell.noData()), d.total().byStep());
        }

        @Test
        @DisplayName("drop-offs below 20 give no shares; an unreadable device margin gives no device table")
        void smallDropoffsWithoutDevices() {
            SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
            days.put(DAY, funnelDay(fc(40, 30, 25, 20, 25 - 12), null,
                new DropoffDay(Map.of("no-slots", stages(0, 0, 0, 7), "quiet-1", stages(10, 0, 0, 0)),
                    Map.of("no-slots", Map.of("fridge", 7L)))));

            FunnelView f = funnel(days, true);

            assertEquals(Cell.tooFew(), f.dropoff().rows().getFirst().share());
            assertEquals(Cell.tooFew(), f.dropoff().total().share());
            assertEquals(List.of(), f.dropoff().deviceColumns());
            assertEquals(Map.of(), f.dropoff().rows().getFirst().byDevice());
        }

        @Test
        @DisplayName("AC-35 dropoff switched off → null; switched on without a start → empty view")
        void switchedOffOrNeverStarted() {
            SortedMap<LocalDate, InsightsDay> days = oneDay(funnelDay(fc(100, 60, 30, 20, 10), null, dropoffDay()));
            InsightsViewModel off = InsightsViewModel.build(funnelHistory(START, START, days), Map.of(), TODAY,
                false, true, false);
            InsightsViewModel neverStarted = InsightsViewModel.build(funnelHistory(START, null,
                oneDay(funnelDay(fc(100, 60, 30, 20, 10), null, null))), Map.of(), TODAY, false, true, true);

            assertNull(off.funnel().dropoff());
            assertNotNull(neverStarted.funnel().dropoff());
            assertNull(neverStarted.funnel().dropoff().collectingSince());
            assertEquals(List.of(), neverStarted.funnel().dropoff().rows());
        }
    }

    @Test
    @DisplayName("device grouping helper: zero classes are left out, on a tie the later class joins „Sonstige“")
    void groupingHelper() {
        InsightsViewModelFunnel.Grouping g = InsightsViewModelFunnel.Grouping.of(new long[] {30, 0, 30, 5}, 20);

        assertEquals(List.of("mobile", "other"), g.columns());
        assertEquals(List.of("desktop", "unknown"), g.otherKeys());
    }
}
