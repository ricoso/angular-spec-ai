package com.autohaus.backend.reporting;

import com.autohaus.backend.capacity.CapacityLevel;
import com.autohaus.backend.capacity.CapacityRow;
import com.autohaus.backend.capacity.CapacityTrend;
import com.autohaus.backend.capacity.CapacityViewModel;
import com.autohaus.backend.capacity.ScanState;
import com.autohaus.backend.usage.insights.BranchRankingRow;
import com.autohaus.backend.usage.insights.Cell;
import com.autohaus.backend.usage.insights.InsightsViewModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The S0 impulses I-1..I-4 (REQ-038 BR-21, BR-25) / die Handlungsimpulse.
 *
 * <p>Covers TC-30 (thresholds, lower third, fallback scan, empty state) and the briefing part of
 * TC-54 (title relative to the day of retrieval). The view models are built by hand — the
 * calculator is a pure function over already suppressed data. / Handgebaute View-Models.
 */
class BriefingCalculatorTest {

    private final BriefingCalculator calculator = new BriefingCalculator();

    /** Thursday 19.11.2026 — scan day of the TC-54 fixture / Scan-Tag. */
    private static final LocalDate THU_19 = LocalDate.of(2026, 11, 19);
    /** Friday 20.11.2026 — its target day / Zieltag. */
    private static final LocalDate FRI_20 = LocalDate.of(2026, 11, 20);
    /** Saturday 21.11.2026 — scan day of the weekend case / Samstags-Scan. */
    private static final LocalDate SAT_21 = LocalDate.of(2026, 11, 21);
    /** Monday 23.11.2026 — target day of the Saturday scan / Zieltag Montag. */
    private static final LocalDate MON_23 = LocalDate.of(2026, 11, 23);

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    /** The 8 ranked branches of TC-29, rank 1..8 / die 8 gerankten Standorte. */
    private static final List<String> RANKED = List.of(
        "Audi Zentrum Rheinfeld", "VW Bergheim-Süd", "ŠKODA Centrum Talstadt", "VW Nordtor",
        "Audi Waldkirchen", "VW Zentrum Rheinfeld", "Audi Hafenstadt", "VW Altstadt");

    // ===========================================================================================
    // Sources / Quellen
    // ===========================================================================================

    @Test
    @DisplayName("BR-4: both sources off -> no briefing at all (S0 omitted)")
    void returnsNullWhenBothSourcesAreOff() {
        assertThat(calculator.compute(null, null, THU_19)).isNull();
    }

    @Test
    @DisplayName("§5.1 row 5: a funnel-only insights view counts as off")
    void treatsAFunnelOnlyViewAsSwitchedOff() {
        InsightsViewModel funnelOnly = new InsightsViewModel(THU_19.minusDays(1), false, true,
            null, THU_19.minusDays(30), null, null, false, 0, null, null, null, null, null, null,
            null, List.of());

        assertThat(calculator.compute(funnelOnly, null, THU_19)).isNull();
    }

    @Test
    @DisplayName("TC-30: without any data basis the briefing is empty (renders the empty text)")
    void yieldsAnEmptyBriefingWithoutAnyDataBasis() {
        InsightsViewModel noClosedMonth = insights(null, List.of(), List.of(), Cell.tooFew());
        CapacityViewModel firstScanRunning = new CapacityViewModel(null, null, ScanState.RUNNING,
            false, null, THU_19.atTime(21, 31).toInstant(java.time.ZoneOffset.UTC), null,
            List.of(), List.of(), List.of());

        Briefing briefing = calculator.compute(noClosedMonth, firstScanRunning, THU_19);

        assertThat(briefing).isNotNull();
        assertThat(briefing.impulses()).isEmpty();
    }

    // ===========================================================================================
    // I-1 free target day / Luft am Zieltag
    // ===========================================================================================

    @Nested
    class FreeTargetDay {

        @Test
        @DisplayName("TC-30: at most 5 names, alphabetical while K-2 is off")
        void listsAtMostFiveNamesAlphabetically() {
            List<CapacityRow> rows = new ArrayList<>();
            for (String name : List.of("VW Nordtor", "Audi Waldkirchen", "VW Neuhaus", "VW Altstadt",
                "ŠKODA Centrum Talstadt", "Audi Hafenstadt", "VW Bergheim-Süd")) {
                rows.add(row(name, CapacityLevel.FREE_NEXT_DAY, CapacityTrend.MIXED, null));
            }
            rows.add(row("Audi Zentrum Rheinfeld", CapacityLevel.TIGHT, CapacityTrend.MOSTLY_BOOKED, null));

            Impulse free = impulse(calculator.compute(null, capacity(THU_19, FRI_20, rows), THU_19),
                BriefingCalculator.KEY_FREE_TARGET_DAY).orElseThrow();

            assertThat(free.branchNames()).containsExactly(
                "Audi Hafenstadt", "Audi Waldkirchen", "ŠKODA Centrum Talstadt", "VW Altstadt",
                "VW Bergheim-Süd");
            assertThat(free.percent()).isNull();
            assertThat(free.source()).isEqualTo("Scan 19.11.");
        }

        @Test
        @DisplayName("BR-21: with K-2 shown the names are sorted by free slots descending")
        void sortsByFreeSlotsWhileTheSlotCountIsShown() {
            List<CapacityRow> rows = List.of(
                row("VW Nordtor", CapacityLevel.FREE_NEXT_DAY, CapacityTrend.MOSTLY_FREE, 14),
                row("Audi Waldkirchen", CapacityLevel.FREE_NEXT_DAY, CapacityTrend.MOSTLY_FREE, 11),
                row("VW Altstadt", CapacityLevel.FREE_NEXT_DAY, CapacityTrend.MOSTLY_FREE, 14),
                row("VW Neuhaus", CapacityLevel.FREE_NEXT_DAY, CapacityTrend.MOSTLY_FREE, null),
                row("Audi Hafenstadt", CapacityLevel.WITHIN_4_WORKDAYS, CapacityTrend.MIXED, null));

            Impulse free = impulse(calculator.compute(null, capacity(THU_19, FRI_20, rows), THU_19),
                BriefingCalculator.KEY_FREE_TARGET_DAY).orElseThrow();

            assertThat(free.branchNames())
                .containsExactly("VW Altstadt", "VW Nordtor", "Audi Waldkirchen", "VW Neuhaus");
            assertThat(free.values()).as("§17 IMPULSE_FREE_TEXT: K-2 per named branch, aligned")
                .containsExactly(14, 14, 11, null);
        }

        @Test
        @DisplayName("§17 IMPULSE_FREE_TEXT: without K-2 the impulse carries no per-branch values")
        void carriesNoValuesWhileTheSlotCountIsOff() {
            List<CapacityRow> rows = List.of(
                row("VW Nordtor", CapacityLevel.FREE_NEXT_DAY, CapacityTrend.MOSTLY_FREE, null));

            Impulse free = impulse(calculator.compute(null, capacity(THU_19, FRI_20, rows), THU_19),
                BriefingCalculator.KEY_FREE_TARGET_DAY).orElseThrow();

            assertThat(free.values()).isNull();
        }

        @Test
        @DisplayName("BR-21: a scan older than yesterday feeds no I-1")
        void dropsTheImpulseForAnOldScan() {
            List<CapacityRow> rows = List.of(
                row("VW Nordtor", CapacityLevel.FREE_NEXT_DAY, CapacityTrend.MOSTLY_FREE, null));

            Briefing briefing = calculator.compute(null, capacity(THU_19, FRI_20, rows), THU_19.plusDays(2));

            assertThat(impulse(briefing, BriefingCalculator.KEY_FREE_TARGET_DAY)).isEmpty();
        }

        @Test
        @DisplayName("BR-21: no branch free on the target day -> no I-1")
        void dropsTheImpulseWithoutAFreeBranch() {
            List<CapacityRow> rows = List.of(
                row("VW Nordtor", CapacityLevel.WITHIN_4_WORKDAYS, CapacityTrend.MIXED, null),
                row("VW Altstadt", CapacityLevel.NOT_MEASURABLE, CapacityTrend.TOO_FEW_DAYS, null));

            Briefing briefing = calculator.compute(null, capacity(THU_19, FRI_20, rows), THU_19);

            assertThat(briefing.impulses()).isEmpty();
        }

        /** TC-54: Thursday scan, target Friday — „morgen" in the evening, „heute" next morning. */
        @Test
        @DisplayName("TC-54: title relative to the day of retrieval (tomorrow / today)")
        void titlesTheTileRelativeToTheDayOfRetrieval() {
            CapacityViewModel scan = capacity(THU_19, FRI_20,
                List.of(row("VW Nordtor", CapacityLevel.FREE_NEXT_DAY, CapacityTrend.MOSTLY_FREE, null)));

            assertThat(title(calculator.compute(null, scan, THU_19))).isEqualTo("Luft morgen (Fr 20.11.)");
            assertThat(title(calculator.compute(null, scan, FRI_20))).isEqualTo("Luft heute (Fr 20.11.)");
        }

        /** TC-54: Saturday scan, target Monday — „am Mo 23.11." on Saturday, „morgen" on Sunday. */
        @Test
        @DisplayName("TC-54: Saturday scan -> 'Luft am Mo 23.11.', Sunday -> 'Luft morgen (Mo 23.11.)'")
        void titlesTheWeekendCaseWithWeekdayAndDate() {
            CapacityViewModel scan = capacity(SAT_21, MON_23,
                List.of(row("VW Nordtor", CapacityLevel.FREE_NEXT_DAY, CapacityTrend.MOSTLY_FREE, null)));

            assertThat(title(calculator.compute(null, scan, SAT_21))).isEqualTo("Luft am Mo 23.11.");
            assertThat(title(calculator.compute(null, scan, SAT_21.plusDays(1))))
                .isEqualTo("Luft morgen (Mo 23.11.)");
            // Monday: the Saturday scan is two days old — I-1 needs a scan of today or yesterday.
            assertThat(impulse(calculator.compute(null, scan, MON_23), BriefingCalculator.KEY_FREE_TARGET_DAY))
                .isEmpty();
        }

        @Test
        @DisplayName("TC-54: the key stays 'free-target-day' whatever the title says")
        void keepsTheKeyStable() {
            CapacityViewModel scan = capacity(SAT_21, MON_23,
                List.of(row("VW Nordtor", CapacityLevel.FREE_NEXT_DAY, CapacityTrend.MOSTLY_FREE, null)));

            assertThat(calculator.compute(null, scan, SAT_21).impulses())
                .extracting(Impulse::key).contains("free-target-day");
        }
    }

    // ===========================================================================================
    // I-2 promo, I-3 bottleneck / Werbe-Kandidat, Engpass
    // ===========================================================================================

    @Nested
    class PromoAndBottleneck {

        /**
         * TC-30: n = 10 → ⌈20/3⌉ = 7, so positions 8–10 (rank 8 plus both rk-low branches) are
         * the lower third; rank 7 is not. / Positionen 8–10, nicht Rang 7.
         */
        @Test
        @DisplayName("TC-30: I-2 exactly for position 8 and the two rk-low branches, not rank 7")
        void namesThePromoCandidatesOfTheLowerThird() {
            Briefing briefing = calculator.compute(tc29Insights(), allMostlyFree(), THU_19);

            Impulse promo = impulse(briefing, BriefingCalculator.KEY_PROMO).orElseThrow();
            assertThat(promo.branchNames()).containsExactly("VW Altstadt", "VW Neuhaus", "VW Oststadt");
            assertThat(promo.branchNames()).doesNotContain("Audi Hafenstadt"); // rank 7
            assertThat(promo.source()).isEqualTo("Oktober 2026");
            assertThat(promo.title()).isEqualTo("Werbe-Kandidat");
        }

        @Test
        @DisplayName("TC-30: a 'mixed' branch (6 of 10 free) gets no I-2")
        void skipsAMixedBranch() {
            List<CapacityRow> rows = new ArrayList<>(allMostlyFree().rows());
            rows.replaceAll(r -> r.branchName().equals("VW Altstadt")
                ? row("VW Altstadt", CapacityLevel.FREE_NEXT_DAY, CapacityTrend.MIXED, null) : r);

            Impulse promo = impulse(calculator.compute(tc29Insights(), capacity(THU_19, FRI_20, rows), THU_19),
                BriefingCalculator.KEY_PROMO).orElseThrow();

            assertThat(promo.branchNames()).containsExactly("VW Neuhaus", "VW Oststadt");
        }

        @Test
        @DisplayName("TC-30: I-3 exactly for 'mostly-booked', 'too-few-days' gets neither I-2 nor I-3")
        void namesTheBottlenecksOnly() {
            List<CapacityRow> rows = new ArrayList<>(allMostlyFree().rows());
            rows.replaceAll(r -> switch (r.branchName()) {
                case "Audi Zentrum Rheinfeld" -> row(r.branchName(), CapacityLevel.TIGHT,
                    CapacityTrend.MOSTLY_BOOKED, null);
                case "VW Neuhaus" -> row(r.branchName(), CapacityLevel.TIGHT,
                    CapacityTrend.TOO_FEW_DAYS, null);
                case "VW Oststadt" -> row(r.branchName(), CapacityLevel.NONE_FOUND,
                    CapacityTrend.MOSTLY_BOOKED, null);
                default -> r;
            });

            Briefing briefing = calculator.compute(tc29Insights(), capacity(THU_19, FRI_20, rows), THU_19);

            Impulse bottleneck = impulse(briefing, BriefingCalculator.KEY_BOTTLENECK).orElseThrow();
            assertThat(bottleneck.branchNames()).containsExactly("Audi Zentrum Rheinfeld", "VW Oststadt");
            assertThat(bottleneck.source()).isEqualTo("Scan 19.11.");
            Impulse promo = impulse(briefing, BriefingCalculator.KEY_PROMO).orElseThrow();
            assertThat(promo.branchNames()).containsExactly("VW Altstadt"); // Neuhaus too-few, Oststadt booked
        }

        @Test
        @DisplayName("BR-21: without a closed month there is no I-2 (and no I-4)")
        void dropsPromoAndTopWithoutAClosedMonth() {
            InsightsViewModel noMonth = insights(null, List.of(), List.of(), Cell.tooFew());

            Briefing briefing = calculator.compute(noMonth, allMostlyFree(), THU_19);

            assertThat(briefing.impulses()).extracting(Impulse::key)
                .containsExactly("free-target-day");
        }

        @Test
        @DisplayName("TC-30: latest scan failed (fallback shown) -> I-1 to I-3 are omitted, I-4 stays")
        void dropsTheCapacityImpulsesWhileAFallbackScanIsShown() {
            CapacityViewModel base = allMostlyFree();
            CapacityViewModel fallback = new CapacityViewModel(base.scanDay(), base.targetDay(),
                ScanState.FAILED, true, ScanState.COMPLETE, null, base.collectingSince(),
                base.last10ScanDays(), base.rows(), List.of("Letzter Scan fehlgeschlagen"));

            Briefing briefing = calculator.compute(tc29Insights(), fallback, THU_19);

            assertThat(briefing.impulses()).extracting(Impulse::key).containsExactly("top");
        }

        @Test
        @DisplayName("BR-21: a failed latest scan without fallback feeds no capacity impulse either")
        void dropsTheCapacityImpulsesForAFailedScan() {
            CapacityViewModel base = allMostlyFree();
            CapacityViewModel failed = new CapacityViewModel(base.scanDay(), base.targetDay(),
                ScanState.FAILED, false, null, null, base.collectingSince(), base.last10ScanDays(),
                base.rows(), List.of());

            assertThat(calculator.compute(null, failed, THU_19).impulses()).isEmpty();
        }

        @Test
        @DisplayName("BR-21: a closed month without any branch row yields no I-2")
        void dropsPromoWithoutAnyBranchOfTheMonth() {
            InsightsViewModel empty = insights(OCTOBER, List.of(), List.of(), Cell.tooFew());

            Briefing briefing = calculator.compute(empty, allMostlyFree(), THU_19);

            assertThat(impulse(briefing, BriefingCalculator.KEY_PROMO)).isEmpty();
            assertThat(impulse(briefing, BriefingCalculator.KEY_FREE_TARGET_DAY)).isPresent();
        }

        @Test
        @DisplayName("BR-21 I-2: rk-low branches always count, positions > ceil(2n/3) otherwise")
        void computesTheLowerThirdForOtherSizes() {
            // n = 27 → ⌈54/3⌉ = 18 → positions 19–27.
            List<BranchRankingRow> ranked = new ArrayList<>();
            for (int i = 1; i <= 27; i++) {
                ranked.add(ranked("B" + i, i, 100 - i));
            }
            Set<String> lower = BriefingCalculator.lowerThird(ranked, List.of());
            assertThat(lower).hasSize(9).contains("B19", "B27").doesNotContain("B18");

            // Few ranked, many rk-low: every rk-low branch belongs to the lower third.
            Set<String> mostlyLow = BriefingCalculator.lowerThird(
                List.of(ranked("A", 1, 9)), List.of(below("X", Cell.of(0)), below("Y", Cell.suppressed())));
            assertThat(mostlyLow).containsExactlyInAnyOrder("X", "Y");

            assertThat(BriefingCalculator.lowerThird(null, null)).isEmpty();
        }
    }

    // ===========================================================================================
    // I-4 top / Renner
    // ===========================================================================================

    @Nested
    class Top {

        @Test
        @DisplayName("TC-30: I-4 top 3 without suppressed rows plus the T-2 share of the month")
        void namesTheTopThreeAndTheOffHoursShare() {
            List<BranchRankingRow> ranking = new ArrayList<>(tc29Insights().ranking());
            // A ranked row whose count is withheld must never be named (visible values only).
            ranking.set(1, new BranchRankingRow("VW Bergheim-Süd", 2, "same", 2, Cell.withheld(),
                Cell.withheld(), Cell.withheld(), Cell.of(10), Cell.withheld(), Cell.withheld(),
                Cell.withheld(), 0.0));
            InsightsViewModel insights = insights(OCTOBER, ranking, tc29Insights().belowThreshold(),
                Cell.percentOf(38.0));

            Impulse top = impulse(calculator.compute(insights, null, THU_19), BriefingCalculator.KEY_TOP)
                .orElseThrow();

            assertThat(top.branchNames())
                .containsExactly("Audi Zentrum Rheinfeld", "ŠKODA Centrum Talstadt", "VW Nordtor");
            assertThat(top.percent()).isEqualTo(38);
            assertThat(top.source()).isEqualTo("Oktober 2026");
            assertThat(top.title()).isEqualTo("Renner & Abendbuchungen");
        }

        @Test
        @DisplayName("TC-30: T-2 only with n >= 20 — a withheld share yields no percent")
        void omitsTheShareBelowTwentyBookings() {
            InsightsViewModel insights = insights(OCTOBER, tc29Insights().ranking(), List.of(), Cell.tooFew());

            Impulse top = impulse(calculator.compute(insights, null, THU_19), BriefingCalculator.KEY_TOP)
                .orElseThrow();

            assertThat(top.percent()).isNull();
            assertThat(top.branchNames()).hasSize(3);
        }

        @Test
        @DisplayName("BR-21: no visible ranking and no share -> no I-4")
        void dropsTheTopImpulseWithoutData() {
            InsightsViewModel insights = insights(OCTOBER, List.of(),
                List.of(below("VW Neuhaus", Cell.suppressed())), Cell.tooFew());

            assertThat(calculator.compute(insights, null, THU_19).impulses()).isEmpty();
        }

        @Test
        @DisplayName("BR-21: the share alone keeps I-4 with an empty name list")
        void keepsTheShareWithoutRankedBranches() {
            InsightsViewModel insights = insights(OCTOBER, List.of(), List.of(), Cell.percentOf(41.4));

            Impulse top = impulse(calculator.compute(insights, null, THU_19), BriefingCalculator.KEY_TOP)
                .orElseThrow();

            assertThat(top.branchNames()).isEmpty();
            assertThat(top.percent()).isEqualTo(41);
        }
    }

    @Test
    @DisplayName("TC-30: full fixture yields I-1, I-2, I-3, I-4 in this order")
    void ordersTheImpulsesFromOneToFour() {
        List<CapacityRow> rows = new ArrayList<>(allMostlyFree().rows());
        rows.replaceAll(r -> r.branchName().equals("Audi Zentrum Rheinfeld")
            ? row(r.branchName(), CapacityLevel.TIGHT, CapacityTrend.MOSTLY_BOOKED, null) : r);

        Briefing briefing = calculator.compute(tc29Insights(), capacity(THU_19, FRI_20, rows), THU_19);

        assertThat(briefing.impulses()).extracting(Impulse::key)
            .containsExactly("free-target-day", "promo", "bottleneck", "top");
    }

    @Test
    @DisplayName("§17 WEEKDAY_*: Mo..So and the German month label")
    void formatsWeekdaysAndMonths() {
        assertThat(BriefingCalculator.weekday(DayOfWeek.MONDAY)).isEqualTo("Mo");
        assertThat(BriefingCalculator.weekday(DayOfWeek.SUNDAY)).isEqualTo("So");
        assertThat(BriefingCalculator.monthLabel(YearMonth.of(2026, 3))).isEqualTo("März 2026");
        assertThat(BriefingCalculator.freeTitle(MON_23, SAT_21)).isEqualTo("Luft am Mo 23.11.");
    }

    // ===========================================================================================
    // Fixtures / Testdaten
    // ===========================================================================================

    /** TC-29: 8 ranked branches, „VW Neuhaus" with 3 and „VW Oststadt" with 0 bookings. */
    private static InsightsViewModel tc29Insights() {
        List<BranchRankingRow> ranking = new ArrayList<>();
        for (int i = 0; i < RANKED.size(); i++) {
            ranking.add(ranked(RANKED.get(i), i + 1, 60 - 6 * i));
        }
        List<BranchRankingRow> below = List.of(below("VW Neuhaus", Cell.suppressed()),
            below("VW Oststadt", Cell.of(0)));
        return insights(OCTOBER, ranking, below, Cell.percentOf(38.0));
    }

    /** All 10 TC-29 branches free on the target day and „meist frei" / alle meist frei. */
    private static CapacityViewModel allMostlyFree() {
        List<CapacityRow> rows = new ArrayList<>();
        for (String name : RANKED) {
            rows.add(row(name, CapacityLevel.FREE_NEXT_DAY, CapacityTrend.MOSTLY_FREE, null));
        }
        rows.add(row("VW Neuhaus", CapacityLevel.FREE_NEXT_DAY, CapacityTrend.MOSTLY_FREE, null));
        rows.add(row("VW Oststadt", CapacityLevel.FREE_NEXT_DAY, CapacityTrend.MOSTLY_FREE, null));
        return capacity(THU_19, FRI_20, rows);
    }

    private static InsightsViewModel insights(YearMonth closedMonth, List<BranchRankingRow> ranking,
                                              List<BranchRankingRow> below, Cell offHours) {
        return new InsightsViewModel(THU_19.minusDays(1), true, false, LocalDate.of(2026, 9, 1), null,
            closedMonth, null, false, 0, ranking, below, null, null, null, offHours, null, List.of());
    }

    private static CapacityViewModel capacity(LocalDate scanDay, LocalDate targetDay, List<CapacityRow> rows) {
        return new CapacityViewModel(scanDay, targetDay, ScanState.COMPLETE, false, null, null,
            LocalDate.of(2026, 11, 2), List.of(), rows, List.of());
    }

    private static CapacityRow row(String name, CapacityLevel level, CapacityTrend trend, Integer slots) {
        return new CapacityRow(name, level, level.key(), 0, null, slots, List.of(), List.of(),
            7, 10, trend);
    }

    private static BranchRankingRow ranked(String name, int rank, long bookings) {
        return new BranchRankingRow(name, rank, "same", rank, Cell.of(bookings), Cell.percentOf(10.0),
            Cell.of(bookings), Cell.of(200), Cell.ratioOf(5.0), Cell.tooFew(), Cell.tooFew(), 50.0);
    }

    private static BranchRankingRow below(String name, Cell bookings) {
        return new BranchRankingRow(name, null, null, null, bookings, Cell.withheld(), Cell.withheld(),
            Cell.of(40), Cell.withheld(), Cell.withheld(), Cell.withheld(), 0.0);
    }

    private static Optional<Impulse> impulse(Briefing briefing, String key) {
        return briefing.impulses().stream().filter(i -> key.equals(i.key())).findFirst();
    }

    private static String title(Briefing briefing) {
        return impulse(briefing, BriefingCalculator.KEY_FREE_TARGET_DAY).orElseThrow().title();
    }
}
