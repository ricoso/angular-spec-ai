package com.autohaus.backend.usage.insights;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.text.Collator;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.assertNoHiddenValues;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.compactedMonth;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.data;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.day;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.matrixBranch;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.names;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Global period metrics G-1, T-1…T-5 with primary and secondary suppression and heat levels
 * (REQ-038 TC-27, TC-51 b/d, TC-53; BR-9 Nr. 1, 2, 4 a; §10.5) / globale Zeitraum-Kennzahlen mit
 * Unterdrückung und Heat-Stufen.
 *
 * <p>Days of November 2026: 02.11. is a Monday. Most cases call the package-private
 * {@link InsightsViewModelPeriods#build} directly; the result is exactly what
 * {@code InsightsViewModel.month()} carries. / Die meisten Fälle rufen den Perioden-Teil direkt.
 */
class InsightsViewModelPeriodsTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 14);

    private static Period period(SortedMap<LocalDate, InsightsDay> days) {
        return InsightsViewModelPeriods.build(InsightsViewModelPeriods.PERIOD_MONTH, FROM, TO, FROM, days,
            Collator.getInstance(Locale.GERMAN));
    }

    private static SortedMap<LocalDate, InsightsDay> days(InsightsDay... perWeekdayFromMonday) {
        assertEquals(DayOfWeek.MONDAY, MONDAY.getDayOfWeek());
        SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
        for (int i = 0; i < perWeekdayFromMonday.length; i++) {
            days.put(MONDAY.plusDays(i), perWeekdayFromMonday[i]);
        }
        return days;
    }

    @Test
    @DisplayName("TC-27 T-1 with exactly one cell = 2 → the smallest visible cell > 0 is k. A.")
    void exactlyOneHiddenCellInT1() throws Exception {
        Period p = period(days(
            day(32).daypart("day", 30).daypart("evening", 2).build(),
            day(19).daypart("day", 12).daypart("evening", 7).build()));

        assertEquals(Cell.suppressed(), p.bookedWhen().get("MO").get("evening"));
        assertEquals(Cell.withheld(), p.bookedWhen().get("TU").get("evening"));
        assertEquals(CellState.VALUE, p.bookedWhen().get("TU").get("day").state());
        assertEquals(CellState.VALUE, p.bookedWhen().get("MO").get("day").state());
        assertEquals(Cell.noData(), p.bookedWhen().get("WE").get("day"), "no counted Wednesday → „—“");
        assertTrue(assertNoHiddenValues(p) >= 2, "hidden cells carry no value in JSON");
    }

    @Test
    @DisplayName("TC-27 a visible T-2 tile makes both T-1 regions derivable — each is protected too")
    void offHoursTileProtectsBothRegions() {
        Period p = period(days(
            day(9).daypart("day", 6).daypart("evening", 3).build(),
            day(60).daypart("day", 40).daypart("evening", 20).build(),
            day(30).daypart("day", 30).build()));

        assertEquals(Cell.percentOf(23.0), p.offHoursShare(), "23 of 99");
        assertEquals(Cell.suppressed(), p.bookedWhen().get("MO").get("evening"));
        assertEquals(Cell.withheld(), p.bookedWhen().get("MO").get("day"), "table rule");
        assertEquals(Cell.withheld(), p.bookedWhen().get("TU").get("evening"), "off-hours region");
        assertEquals(Cell.withheld(), p.bookedWhen().get("WE").get("day"), "business-hours region");
        assertEquals(Cell.of(40, 4), p.bookedWhen().get("TU").get("day"), "the only visible cell > 0");
    }

    @Test
    @DisplayName("TC-53 relative heat levels of T-1: max 44 → 14 → 2, 9 → 1, 33 → 3, 34 → 4")
    void relativeHeatLevelsT1() {
        Period p = period(days(
            day(44).daypart("day", 44).build(),
            day(14).daypart("day", 14).build(),
            day(9).daypart("day", 9).build(),
            day(33).daypart("day", 33).build(),
            day(34).daypart("day", 34).build()));

        assertEquals(new HeatLegend(44, 11, 22, 33), p.bookedWhenLegend());
        assertEquals(4, p.bookedWhen().get("MO").get("day").heat());
        assertEquals(2, p.bookedWhen().get("TU").get("day").heat());
        assertEquals(1, p.bookedWhen().get("WE").get("day").heat());
        assertEquals(3, p.bookedWhen().get("TH").get("day").heat());
        assertEquals(4, p.bookedWhen().get("FR").get("day").heat());
        assertEquals(0, p.bookedWhen().get("FR").get("evening").heat(), "0 has no colour");
        assertEquals(Cell.percentOf(0.0), p.offHoursShare());
    }

    @Test
    @DisplayName("TC-53 relative heat levels of T-3: max 24 → 9 → 2, 13 → 3")
    void relativeHeatLevelsT3() {
        Period p = period(days(day(46).slot("MO", "morning", 24).slot("TU", "morning", 9)
            .slot("WE", "morning", 13).build()));

        assertEquals(new HeatLegend(24, 6, 12, 18), p.slotLegend());
        assertEquals(2, p.slotGrid().get("TU").get("morning").heat());
        assertEquals(3, p.slotGrid().get("WE").get("morning").heat());
        assertEquals(4, p.slotGrid().get("MO").get("morning").heat());
    }

    @Test
    @DisplayName("TC-53 a single visible cell gets level 4; hidden cells stay 0 and are not in max")
    void singleVisibleCell() {
        Period p = period(days(day(13).slot("MO", "early", 8).slot("TU", "early", 3)
            .slot("WE", "early", 2).build()));

        assertEquals(Cell.of(8, 4), p.slotGrid().get("MO").get("early"));
        assertEquals(Cell.suppressed(), p.slotGrid().get("TU").get("early"));
        assertEquals(0, p.slotGrid().get("WE").get("early").heat());
        assertEquals(8, p.slotLegend().max());
    }

    @Test
    @DisplayName("TC-53 G-2 fixed heat scale: 14 % → 1, 15 % → 2, 45 % → 4")
    void fixedMatrixHeatScale() {
        InsightsViewModel vm = InsightsViewModel.build(compactedMonth(LocalDate.of(2026, 9, 1),
                YearMonth.of(2026, 10), data(matrixBranch(100, Map.of("inspection", 14L, "tuv", 15L,
                    "repair", 45L, "bodywork", 30L), 0)), TO),
            names("VW Nordtor"), TO.plusDays(1), true, false, false);
        Map<String, Cell> cells = vm.matrix().rows().getFirst().cells();

        assertEquals(Cell.percentOf(14.0, 1), cells.get("inspection"));
        assertEquals(Cell.percentOf(15.0, 2), cells.get("tuv"));
        assertEquals(Cell.percentOf(30.0, 3), cells.get("bodywork"));
        assertEquals(Cell.percentOf(45.0, 4), cells.get("repair"));
        assertEquals(1, InsightsViewModelBranches.shareHeat(0.4), "a visible share > 0 is at least level 1");
    }

    @Test
    @DisplayName("TC-51 (b) slotUnknown = 2 is „< 5“ and still counted in the bookings and G-1")
    void slotUnknownSuppressedButCounted() {
        Period p = period(days(day(32).slot("MO", "morning", 20).slot("TU", "morning", 10).slotUnknown(2)
            .group("inspection", 32).build()));

        assertEquals(Cell.suppressed(), p.slotUnknown());
        assertEquals(Cell.withheld(), p.slotGrid().get("TU").get("morning"), "exactly one hidden part in T-3");
        assertEquals(Cell.of(32), p.bookings());
        assertEquals(32L, p.groups().get("inspection").value());
    }

    @Test
    @DisplayName("TC-51 (b/d) slotUnknown ≥ 5 is shown, 0 is omitted")
    void slotUnknownShownOrOmitted() {
        Period seven = period(days(day(27).slot("MO", "morning", 20).slotUnknown(7).build()));
        Period zero = period(days(day(20).slot("MO", "morning", 20).build()));

        assertEquals(Cell.of(7), seven.slotUnknown());
        assertNull(zero.slotUnknown());
    }

    @Test
    @DisplayName("T-3 shows Monday–Saturday; Sunday only when a Sunday slot was booked")
    void sundayOnlyWhenNeeded() {
        Period withoutSunday = period(days(day(20).slot("MO", "morning", 20).build()));
        Period withSunday = period(days(day(26).slot("MO", "morning", 20).slot("SU", "late", 6).build()));

        assertEquals(6, withoutSunday.slotGrid().size());
        assertEquals(7, withSunday.slotGrid().size());
        assertEquals(Cell.of(6, 2), withSunday.slotGrid().get("SU").get("late"), "max 20 → bounds 5/10/15");
    }

    @Test
    @DisplayName("T-5 carries counts plus one-decimal shares from 20 bookings (HALF_UP)")
    void leadTimeShares() {
        Period p = period(days(day(22).lead("0-1", 5).lead("2-3", 10).lead("4-7", 7).build()));

        assertEquals(new Cell(5L, null, 22.7, 0, CellState.VALUE), p.leadTime().get("0-1"));
        assertEquals(new Cell(10L, null, 45.5, 0, CellState.VALUE), p.leadTime().get("2-3"));
        assertEquals(new Cell(7L, null, 31.8, 0, CellState.VALUE), p.leadTime().get("4-7"));
        assertEquals(new Cell(0L, null, 0.0, 0, CellState.VALUE), p.leadTime().get("31+"));
        assertTrue(p.leadTime().containsKey("unknown"));
    }

    @Test
    @DisplayName("G-1 below 20 bookings: mentions only, 1–4 → < 5; mobility is part of the groups")
    void thinServiceGroups() {
        Period p = period(days(day(12).group("tire-change", 6).group("inspection", 3).mobility(2).build()));

        assertEquals(new Cell(6L, null, null, 0, CellState.VALUE), p.groups().get("tire-change"));
        assertEquals(Cell.suppressed(), p.groups().get("inspection"));
        assertEquals(Cell.suppressed(), p.groups().get(InsightsViewModelPeriods.MOBILITY_KEY));
        assertEquals(Cell.of(0), p.groups().get("pickup"));
        assertEquals(Cell.tooFew(), p.offHoursShare(), "12 < 20");
    }

    @Test
    @DisplayName("G-1 from 20 bookings: mentions plus one-decimal share of the bookings")
    void serviceGroupShares() {
        Period p = period(days(day(286).group("tire-change", 131).mobility(20).build()));

        assertEquals(new Cell(131L, null, 45.8, 0, CellState.VALUE), p.groups().get("tire-change"));
        assertEquals(new Cell(20L, null, 7.0, 0, CellState.VALUE), p.groups().get("mobility"));
    }

    @Test
    @DisplayName("missing source maps are „—“, never 0; the period bookings stay visible (BR-19)")
    void missingSourceMaps() {
        Period p = period(days(new InsightsDay(25L, null, null, null, null, null, null, null, null)));

        assertEquals(Cell.of(25), p.bookings());
        assertEquals(Cell.noData(), p.bookedWhen().get("MO").get("day"));
        assertEquals(Cell.noData(), p.slotGrid().get("MO").get("early"));
        assertEquals(Cell.noData(), p.leadTime().get("0-1"));
        assertEquals(Cell.noData(), p.groups().get("inspection"));
        assertEquals(Cell.noData(), p.groups().get("mobility"));
        assertEquals(Cell.noData(), p.offHoursShare());
        assertNull(p.bookedWhenLegend());
        assertNull(p.slotLegend());
    }

    @Test
    @DisplayName("a period entirely before the collection start is „—“ with its nominal range")
    void periodBeforeCollectionStart() {
        Period p = InsightsViewModelPeriods.build(InsightsViewModelPeriods.PERIOD_YEAR, FROM, TO,
            TO.plusDays(3), days(day(10).build()), Collator.getInstance(Locale.GERMAN));

        assertEquals(Cell.noData(), p.bookings());
        assertEquals("01.11.–14.11.2026", p.rangeLabel());
        assertEquals("laufendes Jahr bis gestern", p.label());
    }

    @Test
    @DisplayName("range labels: single day, same year, across years")
    void rangeLabels() {
        assertEquals("18.11.2026", InsightsViewModelBuilder.rangeLabel(LocalDate.of(2026, 11, 18),
            LocalDate.of(2026, 11, 18)));
        assertEquals("01.11.–18.11.2026", InsightsViewModelBuilder.rangeLabel(FROM, LocalDate.of(2026, 11, 18)));
        assertEquals("30.12.2026–02.01.2027", InsightsViewModelBuilder.rangeLabel(LocalDate.of(2026, 12, 30),
            LocalDate.of(2027, 1, 2)));
    }

    @Test
    @DisplayName("Saturday and Sunday count completely as off-hours (T-2)")
    void weekendIsOffHours() {
        SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
        days.put(MONDAY.plusDays(5), day(10).daypart("day", 10).build()); // Saturday
        days.put(MONDAY.plusDays(2), day(30).daypart("day", 30).build()); // Wednesday
        Period p = period(days);

        assertEquals(Cell.percentOf(25.0), p.offHoursShare(), "10 of 40");
    }
}
