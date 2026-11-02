package com.autohaus.backend.usage.insights;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.JSON;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.assertNoHiddenValues;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.branch;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.compactedMonth;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.data;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.day;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.history;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.id;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.matrixBranch;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.names;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.simpleDays;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.zeroDays;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The suppressed insights view — closed month, ranking, matrix, periods and warnings (REQ-038
 * TC-25, TC-26, TC-28, TC-29, TC-49, TC-50, TC-51, TC-56, TC-57; BR-9, BR-10, BR-19) / die
 * unterdrückte Insights-Sicht: Vormonat, Rangliste, Matrix, Zeiträume und Warnhinweise.
 *
 * <p>Every case calls {@link InsightsViewModel#build} with a fixed {@code today} (the Berlin
 * calendar day the scheduler passes in); the suppression engine itself and the random property
 * test live in {@link InsightsViewModelSuppressionTest}, T-1…T-5 in
 * {@link InsightsViewModelPeriodsTest}, S6 in {@link InsightsViewModelFunnelTest}. / Engine und
 * Eigenschaftstest, Zeiträume und Trichter in eigenen Klassen.
 */
class InsightsViewModelTest {

    /** Sunday 15.11.2026 — closed month October 2026, asOf 14.11.2026. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final LocalDate SINCE = LocalDate.of(2026, 9, 1);

    private static InsightsViewModel build(InsightsHistory history, Map<String, String> names, LocalDate today) {
        return InsightsViewModel.build(history, names, today, true, false, false);
    }

    /** October compacted, global days until yesterday → no pending/incomplete warnings. */
    private static InsightsViewModel october(Map<String, String> names, Map<String, BranchCounts> data) {
        return build(compactedMonth(SINCE, OCTOBER, data, TODAY.minusDays(1)), names, TODAY);
    }

    private static BranchRankingRow row(InsightsViewModel vm, String name) {
        return java.util.stream.Stream.concat(vm.ranking().stream(), vm.belowThreshold().stream())
            .filter(r -> r.branchName().equals(name)).findFirst().orElseThrow();
    }

    // -----------------------------------------------------------------------------------------
    // TC-25 Primär, Anteile, Komplement
    // -----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("TC-25 primary suppression, shares and complement / Primär, Anteile, Komplement")
    class PrimaryAndShares {

        @Test
        @DisplayName("TC-25 counters 0, 1, 4, 5 render as 0, < 5, < 5, 5")
        void countersZeroOneFourFive() {
            InsightsViewModel vm = october(names("A Null", "B Eins", "C Vier", "D Fünf", "E Groß"),
                data(branch(0), branch(1), branch(4), branch(5), branch(40)));

            assertEquals(Cell.of(0), row(vm, "A Null").bookings());
            assertEquals(Cell.suppressed(), row(vm, "B Eins").bookings());
            assertEquals(Cell.suppressed(), row(vm, "C Vier").bookings());
            assertEquals(Cell.of(5), row(vm, "D Fünf").bookings());
        }

        @Test
        @DisplayName("TC-25 a share needs a denominator of at least 20 (19 → n < 20, 20 → value)")
        void shareNeedsTwentyCases() {
            InsightsViewModel nineteen = october(names("A", "B"), data(branch(10), branch(9)));
            InsightsViewModel twenty = october(names("A", "B"), data(branch(10), branch(10)));

            assertEquals(Cell.tooFew(), row(nineteen, "A").share());
            assertEquals(Cell.percentOf(50.0), row(twenty, "A").share());
        }

        @Test
        @DisplayName("TC-25 R-4 is n < 20 below 20 views and k. A. when R-1 is suppressed")
        void bookingsPer100Views() {
            InsightsViewModel vm = october(names("A Wenig Aufrufe", "B Drei", "C Groß"),
                data(branch(8, 19, 0, 0), branch(3, 50, 0, 0), branch(30, 200, 0, 0)));

            assertEquals(Cell.tooFew(), row(vm, "A Wenig Aufrufe").bookingsPer100Views());
            assertEquals(Cell.withheld(), row(vm, "B Drei").bookingsPer100Views());
            assertEquals(Cell.ratioOf(15.0), row(vm, "C Groß").bookingsPer100Views());
        }

        @Test
        @DisplayName("TC-25 T-4 with 4 of 24 and T-2 with complement 2 of 30 are k. A.; 0 of 24 is 0 %")
        void numeratorAndComplementOneToFourAreWithheld() {
            InsightsViewModel vm = october(names("A Spät Vier", "B Komplement", "C Spät Null"),
                data(branch(24, 100, 10, 4), branch(30, 100, 28, 10), branch(24, 100, 12, 0)));

            assertEquals(Cell.withheld(), row(vm, "A Spät Vier").lateShare(), "4/24 would read 17 %");
            assertEquals(Cell.withheld(), row(vm, "B Komplement").offHoursShare());
            assertEquals(Cell.percentOf(0.0), row(vm, "C Spät Null").lateShare());
        }

        @Test
        @DisplayName("TC-25 share of the online volume is withheld for 1–4 bookings and rounds HALF_UP")
        void onlineShareRules() {
            InsightsViewModel vm = october(names("A", "B", "C"), data(branch(3), branch(67), branch(130)));

            assertEquals(Cell.withheld(), row(vm, "A").share());
            // 67 / 200 = 33.5 % → 34 (HALF_UP)
            assertEquals(Cell.percentOf(34.0), row(vm, "B").share());
            assertEquals(Cell.percentOf(65.0), row(vm, "C").share());
        }
    }

    // -----------------------------------------------------------------------------------------
    // TC-29 Rangliste
    // -----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("TC-29 ranking / Rangliste")
    class Ranking {

        private final Map<String, String> names = names("Audi Zentrum Rheinfeld", "VW Bergheim-Süd",
            "ŠKODA Centrum Talstadt", "VW Nordtor", "VW Altstadt", "Seat Hafen", "VW Linden", "VW Wall",
            "VW Oststadt", "VW Neuhaus");

        private InsightsViewModel vm() {
            Map<String, BranchCounts> october = data(branch(58, 410, 22, 7), branch(44), branch(44),
                branch(24), branch(20), branch(12), branch(7), branch(5), branch(0, 40, 0, 0), branch(3));
            Map<String, BranchCounts> september = data(branch(50), branch(30), branch(20), branch(40),
                branch(15), branch(10), branch(3), branch(8), branch(0), branch(6));
            SortedMap<YearMonth, Map<String, BranchCounts>> months = new TreeMap<>();
            months.put(OCTOBER, october);
            months.put(OCTOBER.minusMonths(1), september);
            return build(history(SINCE, zeroDays(SINCE, TODAY.minusDays(1)), new TreeMap<>(), months), names, TODAY);
        }

        @Test
        @DisplayName("TC-29 ranks 1–8 descending, ties by name (Collator DE)")
        void ranksDescendingWithTiesByName() {
            List<BranchRankingRow> ranking = vm().ranking();

            assertEquals(List.of("Audi Zentrum Rheinfeld", "ŠKODA Centrum Talstadt", "VW Bergheim-Süd",
                "VW Nordtor", "VW Altstadt", "Seat Hafen", "VW Linden", "VW Wall"),
                ranking.stream().map(BranchRankingRow::branchName).toList());
            for (int i = 0; i < ranking.size(); i++) {
                assertEquals(i + 1, ranking.get(i).rank());
            }
        }

        @Test
        @DisplayName("TC-29 rank change against the month before: up 2, down 1, same, new")
        void rankMoves() {
            InsightsViewModel vm = vm();

            assertEquals("up", row(vm, "ŠKODA Centrum Talstadt").rankMove());
            assertEquals(4, row(vm, "ŠKODA Centrum Talstadt").previousRank());
            assertEquals("down", row(vm, "VW Wall").rankMove());
            assertEquals(7, row(vm, "VW Wall").previousRank());
            assertEquals("same", row(vm, "Audi Zentrum Rheinfeld").rankMove());
            assertEquals("new", row(vm, "VW Linden").rankMove());
            assertNull(row(vm, "VW Linden").previousRank());
        }

        @Test
        @DisplayName("TC-29 below 5 bookings: alphabetical, without rank, no hidden value leaks")
        void belowThreshold() {
            InsightsViewModel vm = vm();

            assertEquals(List.of("VW Neuhaus", "VW Oststadt"),
                vm.belowThreshold().stream().map(BranchRankingRow::branchName).toList());
            BranchRankingRow neuhaus = row(vm, "VW Neuhaus");
            assertNull(neuhaus.rank());
            assertNull(neuhaus.rankMove());
            assertEquals(Cell.suppressed(), neuhaus.bookings());
            assertEquals(Cell.withheld(), neuhaus.share());
            assertEquals(Cell.withheld(), neuhaus.bookingsPer100Views());
            assertEquals(0.0, neuhaus.barWidth());
            BranchRankingRow oststadt = row(vm, "VW Oststadt");
            assertEquals(Cell.of(0), oststadt.bookings());
            assertEquals(Cell.percentOf(0.0), oststadt.share());
            assertEquals(Cell.ratioOf(0.0), oststadt.bookingsPer100Views());
            assertEquals(Cell.of(40), oststadt.serviceViews());
        }

        @Test
        @DisplayName("TC-29 the ranking is monotone and bar widths are relative to the largest value")
        void monotoneWithRelativeBars() {
            InsightsViewModel vm = vm();
            long lowestRanked = vm.ranking().getLast().bookings().value();

            assertTrue(vm.belowThreshold().stream()
                .allMatch(r -> r.bookings().state() != CellState.VALUE || r.bookings().value() < lowestRanked));
            assertEquals(100.0, vm.ranking().getFirst().barWidth());
            assertEquals(41.4, row(vm, "VW Nordtor").barWidth(), "24 / 58");
            assertEquals(Cell.of(108), row(vm, "Audi Zentrum Rheinfeld").bookingsYear(), "Sep 50 + Oct 58");
        }

        @Test
        @DisplayName("TC-29/TC-57 JSON: no hidden cell carries a value, barWidth 0.0 for hidden rows")
        void jsonCarriesNoHiddenValue() throws Exception {
            InsightsViewModel vm = vm();

            assertTrue(assertNoHiddenValues(vm) > 0);
            var json = JSON.valueToTree(vm);
            var neuhaus = json.get("belowThreshold").get(0);
            assertEquals("VW Neuhaus", neuhaus.get("branchName").asText());
            assertEquals(0.0, neuhaus.get("barWidth").asDouble());
            assertEquals("SUPPRESSED", neuhaus.get("bookings").get("state").asText());
        }

        @Test
        @DisplayName("TC-29 without data for the month before there is no rank change at all")
        void noPreviousMonthNoRankMove() {
            LocalDate since = LocalDate.of(2026, 10, 1);
            InsightsViewModel vm = build(compactedMonth(since, OCTOBER, data(branch(10), branch(6)),
                TODAY.minusDays(1)), names("A", "B"), TODAY);

            assertTrue(vm.ranking().stream().allMatch(r -> r.rankMove() == null && r.previousRank() == null));
        }
    }

    // -----------------------------------------------------------------------------------------
    // TC-26 Matrix
    // -----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("TC-26 matrix G-2/G-3 / Matrix")
    class Matrix {

        private final Map<String, String> names =
            names("VW Nordtor", "Audi Zentrum", "VW Altstadt", "VW Linden", "VW Wall");

        private InsightsViewModel vm(BranchCounts wall) {
            return october(names, data(
                matrixBranch(24, Map.of("inspection", 3L, "tire-change", 22L, "repair", 10L), 5),
                matrixBranch(40, Map.of("inspection", 12L, "tire-change", 20L, "repair", 8L), 6),
                matrixBranch(8, Map.of("inspection", 2L, "tire-change", 3L, "repair", 1L), 0),
                matrixBranch(7, Map.of("inspection", 2L, "tire-change", 3L, "repair", 1L), 0),
                wall));
        }

        private MatrixRow matrixRow(InsightsViewModel vm, String name) {
            return vm.matrix().rows().stream().filter(r -> r.branchName().equals(name)).findFirst().orElseThrow();
        }

        @Test
        @DisplayName("TC-26 columns are the ten groups plus mobility, including „nicht zuordenbar“")
        void columns() {
            GroupMatrix matrix = vm(matrixBranch(6, Map.of(), 0)).matrix();

            assertEquals(List.of("inspection", "tuv", "brake-fluid", "tire-change", "actions-checks", "repair",
                "bodywork", "extras", "pickup", "unknown", "mobility"), matrix.columns());
        }

        @Test
        @DisplayName("TC-26 below 20 bookings → „weitere Standorte“; 3 → < 5, 22 of 24 → k. A., no row rule")
        void ownRowsAndCells() {
            InsightsViewModel vm = vm(matrixBranch(6, Map.of("inspection", 1L, "tire-change", 3L, "repair", 1L), 0));

            assertEquals(List.of("Audi Zentrum", "VW Nordtor", InsightsViewModelBranches.MATRIX_OTHER_BRANCHES),
                vm.matrix().rows().stream().map(MatrixRow::branchName).toList());
            MatrixRow nordtor = matrixRow(vm, "VW Nordtor");
            assertEquals(Cell.suppressed(), nordtor.cells().get("inspection"));
            assertEquals(Cell.withheld(), nordtor.cells().get("tire-change"));
            // no row-wise rule: the row keeps visible cells despite two hidden ones (G-2 not additive)
            assertEquals(Cell.percentOf(42.0, 3), nordtor.cells().get("repair"));
            assertEquals(Cell.percentOf(21.0, 2), nordtor.cells().get("mobility"));
            assertEquals(Cell.percentOf(0.0, 0), nordtor.cells().get("tuv"));
            assertTrue(vm.matrix().rows().stream().noneMatch(r -> r.branchName().equals("VW Altstadt")));
        }

        @Test
        @DisplayName("TC-26 collective row of 3 contributing branches (≥ 20 bookings) carries values")
        void collectiveRowWithThreeBranches() {
            InsightsViewModel vm = vm(matrixBranch(6, Map.of("inspection", 1L, "tire-change", 3L, "repair", 1L), 0));
            MatrixRow other = vm.matrix().rows().getLast();

            assertTrue(other.otherBranches());
            assertEquals(Cell.of(21), other.bookings());
            // tire-change 9 of 21 = 43 % — the smallest visible cell of its column after Nordtor's k. A.
            assertEquals(Cell.withheld(), other.cells().get("tire-change"));
            assertEquals(Cell.suppressed(), other.cells().get("repair"), "1 + 1 + 1 = 3");
        }

        @Test
        @DisplayName("TC-26 collective row of only 2 contributing branches is k. A. including the bookings")
        void collectiveRowWithTwoBranches() {
            InsightsViewModel vm = vm(matrixBranch(0, Map.of(), 0));
            MatrixRow other = vm.matrix().rows().getLast();

            assertTrue(other.otherBranches());
            assertEquals(Cell.withheld(), other.bookings());
            assertTrue(other.cells().values().stream().allMatch(c -> c.equals(Cell.withheld())));
        }

        @Test
        @DisplayName("TC-26 hidden cells carry heat 0 and integer percentages only")
        void hiddenCellsHaveNoHeat() {
            InsightsViewModel vm = vm(matrixBranch(6, Map.of("inspection", 1L), 0));

            for (MatrixRow r : vm.matrix().rows()) {
                for (Cell c : r.cells().values()) {
                    if (c.state() != CellState.VALUE) {
                        assertEquals(0, c.heat());
                        assertNull(c.percent());
                    } else {
                        assertEquals(Math.rint(c.percent()), c.percent());
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // TC-56 Spaltenweise Sekundärunterdrückung (fixed example; the property test is separate)
    // -----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("TC-56 column-wise secondary suppression / spaltenweise Sekundärunterdrückung")
    class ColumnRule {

        @Test
        @DisplayName("TC-56 „Reparatur“ 8, 7, 5, 5, 0, 0, 0 plus one < 5 → one of the two 5 (by name) k. A.")
        void repairColumn() {
            Map<String, String> names = names("VW A", "VW B", "VW C", "VW D", "VW E", "VW F", "VW G", "VW H");
            long[] repair = {8, 7, 5, 5, 0, 0, 0, 3};
            long[] tuv = {6, 9, 0, 0, 18, 0, 0, 0};
            BranchCounts[] counts = new BranchCounts[8];
            for (int i = 0; i < 8; i++) {
                counts[i] = matrixBranch(i == 4 ? 20 : 30, Map.of("repair", repair[i], "tuv", tuv[i]), 0);
            }
            InsightsViewModel vm = october(names, data(counts));
            Function<String, Map<String, Cell>> cells = name -> vm.matrix().rows().stream()
                .filter(r -> r.branchName().equals(name)).findFirst().orElseThrow().cells();

            assertEquals(Cell.suppressed(), cells.apply("VW H").get("repair"));
            assertEquals(Cell.withheld(), cells.apply("VW C").get("repair"), "tie 5/5 → „VW C“ before „VW D“");
            assertEquals(CellState.VALUE, cells.apply("VW D").get("repair").state());
            assertEquals(CellState.VALUE, cells.apply("VW A").get("repair").state());
            // „TÜV": exactly one k. A. (18 of 20 → complement 2) → smallest visible > 0 (6) k. A.
            assertEquals(Cell.withheld(), cells.apply("VW E").get("tuv"));
            assertEquals(Cell.withheld(), cells.apply("VW A").get("tuv"));
            assertEquals(CellState.VALUE, cells.apply("VW B").get("tuv").state());
        }

        @Test
        @DisplayName("TC-56 S2 T-2 with exactly one n < 20 branch → the smallest visible value k. A.")
        void offHoursColumn() {
            InsightsViewModel vm = october(names("VW A", "VW B", "VW C", "VW D"),
                data(branch(40, 100, 10, 0), branch(30, 100, 6, 0), branch(25, 100, 9, 0), branch(12, 100, 5, 0)));

            assertEquals(Cell.tooFew(), row(vm, "VW D").offHoursShare());
            assertEquals(Cell.withheld(), row(vm, "VW B").offHoursShare(), "6 of 30 is the smallest > 0");
            assertEquals(Cell.percentOf(25.0), row(vm, "VW A").offHoursShare());
            assertEquals(Cell.percentOf(36.0), row(vm, "VW C").offHoursShare());
        }

        @Test
        @DisplayName("TC-56 rk-low rows count as hidden; a derivable hidden sum of 1–4 triggers as well")
        void hiddenSumOneToFour() {
            // two rk-low branches with 1 off-hours booking each → hidden sum 2 → k. A. for the smallest
            InsightsViewModel vm = october(names("VW A", "VW B", "VW C", "VW D"),
                data(branch(40, 100, 10, 0), branch(30, 100, 6, 0), branch(3, 100, 1, 0), branch(4, 100, 1, 0)));

            assertEquals(Cell.withheld(), row(vm, "VW B").offHoursShare());
            assertEquals(CellState.VALUE, row(vm, "VW A").offHoursShare().state());
        }

        @Test
        @DisplayName("TC-56 a branch with 0 bookings hides nothing and does not trigger the column rule")
        void zeroBookingBranchIsKnown() {
            InsightsViewModel vm = october(names("VW A", "VW B", "VW Null"),
                data(branch(40, 100, 10, 0), branch(30, 100, 6, 0), branch(0, 100, 0, 0)));

            assertEquals(Cell.tooFew(), row(vm, "VW Null").offHoursShare());
            assertEquals(Cell.percentOf(20.0), row(vm, "VW B").offHoursShare());
        }

        @Test
        @DisplayName("TC-56 branches unknown to setup.json stay hidden in every total but are never shown")
        void unknownBranchIsHiddenButCounted() {
            Map<String, BranchCounts> data = data(branch(40, 100, 10, 0), branch(30, 100, 6, 0));
            data.put(id(99), branch(25, 100, 8, 0));
            InsightsViewModel vm = october(names("VW A", "VW B"), data);

            assertEquals(2, vm.ranking().size());
            assertEquals(Cell.percentOf(42.0), row(vm, "VW A").share(), "40 of 95");
            assertEquals(Cell.withheld(), row(vm, "VW B").offHoursShare(), "the unknown branch is one hidden cell");
        }
    }

    // -----------------------------------------------------------------------------------------
    // TC-28 Zeiträume
    // -----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("TC-28 periods and collection start / Zeiträume und Erfassungsbeginn")
    class Periods {

        @Test
        @DisplayName("TC-28 15.10.2026 with collection from 01.10.: no closed month, G-1 month 01.–14.10.")
        void noClosedMonthYet() {
            LocalDate today = LocalDate.of(2026, 10, 15);
            LocalDate since = LocalDate.of(2026, 10, 1);
            SortedMap<LocalDate, InsightsDay> days = simpleDays(since, today.minusDays(1), 2);
            days.put(LocalDate.of(2026, 9, 30), day(50).build()); // before the start: never counted
            InsightsViewModel vm = build(history(since, days, new TreeMap<>(), new TreeMap<>()), names("A"), today);

            assertNull(vm.closedMonth());
            assertEquals(List.of(), vm.ranking());
            assertEquals(List.of(), vm.belowThreshold());
            assertNull(vm.matrix());
            assertNull(vm.closedMonthOffHoursShare());
            assertEquals("aktueller Monat bis gestern", vm.month().label());
            assertEquals("01.10.–14.10.2026", vm.month().rangeLabel());
            assertEquals(Cell.of(28), vm.month().bookings());
            assertEquals("01.10.–14.10.2026", vm.year().rangeLabel());
            assertEquals(Cell.of(28), vm.year().bookings(), "the 50 bookings of 30.09. are never counted");
            assertEquals(since, vm.collectingSince());
            assertEquals(LocalDate.of(2026, 10, 14), vm.asOf());
        }

        @Test
        @DisplayName("TC-28 10.01.2027: ranking December 2026, „Jahr bis Vormonat“ —, G-1 month 01.–09.01.")
        void januaryHasNoYearToPreviousMonth() {
            LocalDate today = LocalDate.of(2027, 1, 10);
            LocalDate since = LocalDate.of(2026, 10, 1);
            SortedMap<YearMonth, Map<String, BranchCounts>> months = new TreeMap<>();
            months.put(YearMonth.of(2026, 10), data(branch(10)));
            months.put(YearMonth.of(2026, 11), data(branch(12)));
            months.put(YearMonth.of(2026, 12), data(branch(14)));
            SortedMap<LocalDate, InsightsDay> days = simpleDays(since, today.minusDays(1), 1);
            InsightsViewModel vm = build(history(since, days, new TreeMap<>(), months), names("A"), today);

            assertEquals(YearMonth.of(2026, 12), vm.closedMonth());
            assertEquals(Cell.of(14), vm.ranking().getFirst().bookings());
            assertEquals(Cell.noData(), vm.ranking().getFirst().bookingsYear());
            assertEquals("01.01.–09.01.2027", vm.month().rangeLabel());
            assertEquals(Cell.of(9), vm.month().bookings());
            assertEquals("01.01.–09.01.2027", vm.year().rangeLabel());
        }

        @Test
        @DisplayName("TC-28 collection from 18.11.2026, 05.12.2026: November partial with warning")
        void partialClosedMonth() {
            LocalDate today = LocalDate.of(2026, 12, 5);
            LocalDate since = LocalDate.of(2026, 11, 18);
            SortedMap<LocalDate, Map<String, BranchCounts>> branchDays = new TreeMap<>();
            for (LocalDate d = since; d.isBefore(LocalDate.of(2026, 12, 1)); d = d.plusDays(1)) {
                branchDays.put(d, data(branch(1)));
            }
            branchDays.put(LocalDate.of(2026, 11, 10), data(branch(99))); // before the start: ignored
            SortedMap<LocalDate, InsightsDay> days = simpleDays(since, today.minusDays(1), 1);
            InsightsViewModel vm = build(history(since, days, branchDays, new TreeMap<>()), names("A"), today);

            assertEquals(YearMonth.of(2026, 11), vm.closedMonth());
            assertEquals(since, vm.closedMonthPartialSince());
            assertEquals(Cell.of(13), vm.ranking().getFirst().bookings());
            assertTrue(vm.warnings().contains("Monat unvollständig – Erfassung seit 18.11.2026"));
            assertFalse(vm.closedMonthIncomplete());
        }

        @Test
        @DisplayName("TC-28 before any collection everything is „—“, never 0 (BR-19)")
        void neverCollected() {
            InsightsViewModel vm = build(InsightsHistory.empty(), names("A"), TODAY);

            assertNull(vm.collectingSince());
            assertNull(vm.closedMonth());
            assertEquals(Cell.noData(), vm.month().bookings());
            assertEquals(Cell.noData(), vm.year().offHoursShare());
            assertTrue(vm.month().bookedWhen().values().stream()
                .flatMap(m -> m.values().stream()).allMatch(c -> c.equals(Cell.noData())));
            assertEquals(List.of(), vm.warnings());
        }

        @Test
        @DisplayName("TC-28 a closed month without any counted day shows „—“ instead of rows")
        void closedMonthWithoutData() {
            LocalDate since = LocalDate.of(2026, 10, 31);
            InsightsViewModel vm = build(history(since, new TreeMap<>(), new TreeMap<>(), new TreeMap<>()),
                names("A"), TODAY);

            assertEquals(OCTOBER, vm.closedMonth());
            assertEquals(List.of(), vm.ranking());
            assertEquals(List.of(), vm.belowThreshold());
            assertEquals(List.of(), vm.matrix().rows());
            assertEquals(Cell.noData(), vm.closedMonthOffHoursShare());
        }
    }

    // -----------------------------------------------------------------------------------------
    // TC-49 / TC-50 / TC-51 Warnungen
    // -----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("TC-49–TC-51 warnings / Warnhinweise")
    class Warnings {

        private final LocalDate since = LocalDate.of(2026, 10, 1);

        @Test
        @DisplayName("TC-49 01.11. before the rollup: October is the closed month, 31.10. pending")
        void monthChangeWithPendingDay() {
            LocalDate today = LocalDate.of(2026, 11, 1);
            SortedMap<LocalDate, Map<String, BranchCounts>> branchDays = new TreeMap<>();
            branchDays.put(LocalDate.of(2026, 10, 12), data(branch(9)));
            SortedMap<LocalDate, InsightsDay> days = simpleDays(since, LocalDate.of(2026, 10, 30), 1);
            InsightsViewModel before = build(history(since, days, branchDays, new TreeMap<>()), names("A"), today);

            assertEquals(OCTOBER, before.closedMonth());
            assertEquals(List.of("Auswertung für 31.10.2026 steht noch aus"), before.warnings());

            days.putAll(simpleDays(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 31), 1));
            InsightsViewModel after = build(history(since, days, branchDays, new TreeMap<>()), names("A"), today);
            assertEquals(List.of(), after.warnings());
        }

        @Test
        @DisplayName("TC-49 Log Analytics failed on 23.10.: 22.10. is pending")
        void failedRollupLeavesDayPending() {
            LocalDate today = LocalDate.of(2026, 10, 23);
            SortedMap<LocalDate, InsightsDay> days = simpleDays(since, LocalDate.of(2026, 10, 21), 1);
            InsightsViewModel vm = build(history(since, days, new TreeMap<>(), new TreeMap<>()), names("A"), today);

            assertEquals(List.of("Auswertung für 22.10.2026 steht noch aus"), vm.warnings());
        }

        @Test
        @DisplayName("TC-49 yesterday before the collection start is never pending")
        void notPendingBeforeStart() {
            InsightsViewModel vm = build(history(TODAY, new TreeMap<>(), new TreeMap<>(), new TreeMap<>()),
                names("A"), TODAY);

            assertEquals(List.of(), vm.warnings());
        }

        @Test
        @DisplayName("TC-50 latest day 4 days old → stale; 3 days old → not stale")
        void staleAfterMoreThanThreeDays() {
            InsightsViewModel four = build(history(since, simpleDays(since, TODAY.minusDays(4), 1),
                new TreeMap<>(), new TreeMap<>()), names("A"), TODAY);
            InsightsViewModel three = build(history(since, simpleDays(since, TODAY.minusDays(3), 1),
                new TreeMap<>(), new TreeMap<>()), names("A"), TODAY);

            assertTrue(four.warnings().contains("Letzte Auswertung: 11.11.2026 – Daten möglicherweise veraltet"));
            assertTrue(three.warnings().stream().noneMatch(w -> w.startsWith("Letzte Auswertung")));
            assertTrue(three.warnings().contains("Auswertung für 14.11.2026 steht noch aus"));
        }

        @Test
        @DisplayName("TC-51 compacted October with 2 missing days → „unvollständig: 2 Tage fehlen“")
        void incompleteCompactedMonth() {
            InsightsHistory full = compactedMonth(since, OCTOBER, data(branch(30)), TODAY.minusDays(1));
            SortedMap<LocalDate, InsightsDay> days = new TreeMap<>(full.days());
            days.remove(LocalDate.of(2026, 10, 3));
            days.put(LocalDate.of(2026, 10, 4), new InsightsDay(null, null, null, null, null, null, null, null, null));
            InsightsViewModel vm = build(history(since, days, new TreeMap<>(), full.branchMonths()), names("A"), TODAY);

            assertTrue(vm.closedMonthIncomplete());
            assertEquals(2, vm.missingDays());
            assertTrue(vm.warnings().contains("unvollständig: 2 Tage fehlen"));
        }

        @Test
        @DisplayName("TC-51 one missing day uses the singular „unvollständig: 1 Tag fehlt“ (§17)")
        void incompleteCompactedMonthSingular() {
            InsightsHistory full = compactedMonth(since, OCTOBER, data(branch(30)), TODAY.minusDays(1));
            SortedMap<LocalDate, InsightsDay> days = new TreeMap<>(full.days());
            days.remove(LocalDate.of(2026, 10, 3));
            InsightsViewModel vm = build(history(since, days, new TreeMap<>(), full.branchMonths()), names("A"), TODAY);

            assertTrue(vm.closedMonthIncomplete());
            assertEquals(1, vm.missingDays());
            assertTrue(vm.warnings().contains("unvollständig: 1 Tag fehlt"), vm.warnings().toString());
            assertTrue(vm.warnings().stream().noneMatch(w -> w.contains("Tage fehlen")));
            assertTrue(InsightsViewModelBuilder.isClosedMonthWarning(InsightsViewModelBuilder.WARN_MONTH_INCOMPLETE_ONE));
            assertEquals("unvollständig: 1 Tag fehlt", InsightsViewModelBuilder.monthIncomplete(1));
            assertEquals("unvollständig: 2 Tage fehlen", InsightsViewModelBuilder.monthIncomplete(2));
            assertEquals("unvollständig: 31 Tage fehlen", InsightsViewModelBuilder.monthIncomplete(31));
        }

        @Test
        @DisplayName("TC-51 a not yet compacted month is never „unvollständig“ (the day can be caught up)")
        void uncompactedMonthIsNotIncomplete() {
            SortedMap<LocalDate, Map<String, BranchCounts>> branchDays = new TreeMap<>();
            branchDays.put(LocalDate.of(2026, 10, 5), data(branch(30)));
            SortedMap<LocalDate, InsightsDay> days = new TreeMap<>(simpleDays(since, TODAY.minusDays(1), 0)
                .headMap(LocalDate.of(2026, 10, 20)));
            InsightsViewModel vm = build(history(since, days, branchDays, new TreeMap<>()), names("A"), TODAY);
            InsightsViewModel view = build(history(since, simpleDays(since, TODAY.minusDays(1), 0)
                .headMap(LocalDate.of(2026, 10, 20)), branchDays, new TreeMap<>()), names("A"), TODAY);

            assertFalse(vm.closedMonthIncomplete());
            assertEquals(0, vm.missingDays());
            assertEquals(vm, view, "a range view of the history is read like a plain map");
        }

        @Test
        @DisplayName("withAdditionalWarnings appends the history hint once / Hinweis wird einmal angehängt")
        void additionalWarnings() {
            InsightsViewModel vm = build(InsightsHistory.empty(), names("A"), TODAY)
                .withAdditionalWarnings(List.of(InsightsViewModel.WARN_HISTORY_UNAVAILABLE))
                .withAdditionalWarnings(List.of(InsightsViewModel.WARN_HISTORY_UNAVAILABLE));

            assertEquals(List.of("Historie derzeit nicht verfügbar"), vm.warnings());
        }
    }

    // -----------------------------------------------------------------------------------------
    // Switches / Schalter (§5.1, §12.5)
    // -----------------------------------------------------------------------------------------

    @Nested
    @DisplayName("switches and robustness / Schalter und Robustheit")
    class Switches {

        @Test
        @DisplayName("§12.5 only the funnel active: bookings part null, funnel present")
        void funnelOnly() {
            InsightsViewModel vm = InsightsViewModel.build(
                compactedMonth(SINCE, OCTOBER, data(branch(30)), TODAY.minusDays(1)), names("A"), TODAY,
                false, true, false);

            assertFalse(vm.bookingsEnabled());
            assertTrue(vm.funnelEnabled());
            assertNull(vm.closedMonth());
            assertNull(vm.ranking());
            assertNull(vm.belowThreshold());
            assertNull(vm.month());
            assertNull(vm.year());
            assertNull(vm.matrix());
            assertNull(vm.closedMonthOffHoursShare());
            assertNotNull(vm.funnel());
            assertNull(vm.funnel().dropoff(), "dropoff off → null (AC-35)");
        }

        @Test
        @DisplayName("§5.1 funnel off → funnel null; dropoff without funnel has no effect")
        void funnelOff() {
            InsightsViewModel vm = InsightsViewModel.build(InsightsHistory.empty(), names("A"), TODAY,
                true, false, true);

            assertNull(vm.funnel());
            assertNotNull(vm.month());
        }

        @Test
        @DisplayName("null history and null names are tolerated; today is mandatory")
        void robustInputs() {
            InsightsViewModel vm = InsightsViewModel.build(null, null, TODAY, true, true, true);

            assertEquals(TODAY.minusDays(1), vm.asOf());
            assertEquals(List.of(), vm.ranking());
            assertThrows(NullPointerException.class,
                () -> InsightsViewModel.build(null, null, null, true, true, true));
        }

        @Test
        @DisplayName("a history with missing maps is read as empty (tolerant reader, BR-18)")
        void missingMaps() {
            InsightsHistory broken = new InsightsHistory(1, null, SINCE, null, null, null, null, null);

            InsightsViewModel vm = InsightsViewModel.build(broken, names("A"), TODAY, true, true, false);

            assertEquals(OCTOBER, vm.closedMonth());
            assertEquals(Cell.noData(), vm.month().bookings());
        }

        @Test
        @DisplayName("a history without collectingSince falls back to its first evaluated day (BR-19)")
        void collectionStartFallback() {
            LocalDate first = LocalDate.of(2026, 11, 3);
            InsightsViewModel vm = build(history(null, simpleDays(first, TODAY.minusDays(1), 1),
                new TreeMap<>(), new TreeMap<>()), names("A"), TODAY);

            assertEquals(first, vm.collectingSince());
            assertEquals("03.11.–14.11.2026", vm.month().rangeLabel());
        }

        @Test
        @DisplayName("missing branch counters are „—“, never 0 (BR-19)")
        void missingBranchCounters() {
            BranchCounts partial = new BranchCounts(30L, null, null, null, null, null);
            InsightsViewModel vm = october(names("A"), data(partial));
            BranchRankingRow a = vm.ranking().getFirst();

            assertEquals(Cell.noData(), a.serviceViews());
            assertEquals(Cell.noData(), a.bookingsPer100Views());
            assertEquals(Cell.noData(), a.offHoursShare());
            assertEquals(Cell.noData(), a.lateShare());
            assertEquals(Cell.noData(), vm.closedMonthOffHoursShare());
            assertTrue(vm.matrix().rows().getFirst().cells().values().stream().allMatch(c -> c.equals(Cell.noData())));
        }

        @Test
        @DisplayName("BR-5: a realistic 14-month history with 27 branches builds in well under a second")
        void largeHistoryBuildsQuickly() throws Exception {
            LocalDate since = LocalDate.of(2025, 10, 1);
            java.util.Random random = new java.util.Random(27L);
            Map<String, String> names = new java.util.LinkedHashMap<>();
            for (int b = 1; b <= 27; b++) {
                names.put(id(b), "Standort " + b);
            }
            SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
            SortedMap<YearMonth, Map<String, BranchCounts>> months = new TreeMap<>();
            SortedMap<LocalDate, Map<String, BranchCounts>> branchDays = new TreeMap<>();
            for (LocalDate d = since; d.isBefore(TODAY); d = d.plusDays(1)) {
                long bookings = 5 + random.nextInt(15);
                Map<String, Map<String, Long>> scenarioDevices = new java.util.LinkedHashMap<>();
                scenarioDevices.put("no-slots", Map.of("mobile", 20L));
                scenarioDevices.put("quiet-1", Map.of("desktop", 40L));
                DropoffDay dropoff = new DropoffDay(Map.of("no-slots", InsightsViewModelFixtures.stages(0, 0, 0, 20),
                    "quiet-1", InsightsViewModelFixtures.stages(40, 0, 0, 0)), scenarioDevices);
                FunnelDay funnel = new FunnelDay(InsightsViewModelFixtures.fc(60 + bookings, 30, 25, 20 + bookings,
                    bookings), null, dropoff);
                days.put(d, day(bookings).daypart("day", bookings).slot("TU", "late", bookings)
                    .lead("2-3", bookings).group("repair", bookings).funnel(funnel).build());
                Map<String, BranchCounts> perBranch = new java.util.LinkedHashMap<>();
                for (String branch : names.keySet()) {
                    perBranch.put(branch, new BranchCounts((long) random.nextInt(3), 5L,
                        Map.of("repair", 1L), 0L, 0L, 1L));
                }
                YearMonth month = YearMonth.from(d);
                if (month.isBefore(YearMonth.from(TODAY))) {
                    Map<String, BranchCounts> sum = months.computeIfAbsent(month, m -> new java.util.LinkedHashMap<>());
                    perBranch.forEach((branch, c) -> sum.merge(branch, c, (x, y) -> new BranchCounts(
                        x.bookings() + y.bookings(), x.serviceViews() + y.serviceViews(), Map.of("repair",
                        x.groups().get("repair") + y.groups().get("repair")), 0L, 0L, x.slotLate() + y.slotLate())));
                } else {
                    branchDays.put(d, perBranch);
                }
            }
            InsightsHistory history = new InsightsHistory(1, null, since, since, since, days, branchDays, months);

            long start = System.nanoTime();
            InsightsViewModel vm = InsightsViewModel.build(history, names, TODAY, true, true, true);
            long millis = (System.nanoTime() - start) / 1_000_000;

            assertTrue(millis < 1_000, "build took " + millis + " ms");
            assertEquals(OCTOBER, vm.closedMonth());
            assertEquals(27, vm.ranking().size() + vm.belowThreshold().size());
            assertNotNull(vm.funnel().dropoff());
            assertNoHiddenValues(vm);
        }

        @Test
        @DisplayName("I-4: the global off-hours share of the closed month follows the share rule")
        void closedMonthOffHoursShare() {
            InsightsViewModel vm = october(names("A", "B"), data(branch(40, 100, 10, 0), branch(10, 100, 5, 0)));
            InsightsViewModel small = october(names("A"), data(branch(15, 100, 5, 0)));

            assertEquals(Cell.percentOf(30.0), vm.closedMonthOffHoursShare());
            assertEquals(Cell.tooFew(), small.closedMonthOffHoursShare());
        }
    }
}
