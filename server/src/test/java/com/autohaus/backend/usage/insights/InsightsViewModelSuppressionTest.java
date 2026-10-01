package com.autohaus.backend.usage.insights;

import com.autohaus.backend.usage.insights.InsightsViewModelSuppression.Node;
import com.autohaus.backend.usage.insights.InsightsViewModelSuppression.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.text.Collator;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;

import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.assertNoHiddenValues;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.compactedMonth;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.day;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.fc;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.funnelDay;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.funnelHistory;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.id;
import static com.autohaus.backend.usage.insights.InsightsViewModelFixtures.stages;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The suppression engine and the property tests over random tables (REQ-038 TC-56 property part,
 * BR-9 Nr. 1–5, BR-31) / die Unterdrückungs-Engine und Eigenschaftstests über Zufallstabellen.
 *
 * <p><b>Reader / Leser:</b> knows the rules and, per hidden cell, only the interval its display
 * allows — „&lt; 5" is 1–4; „k. A." in a counter table (T-1, T-3, T-5, F-3, drop-off) is ≥ 5,
 * because only a visible value &gt; 0 is withheld secondarily; a matrix „k. A." is 5…n (complement
 * 1–4 or secondary), a collective row „k. A." ≥ 0; an S2 share „n &lt; 20" is 0…n and „k. A."
 * 1…n. / Der Leser kennt je ausgeblendeter Zelle nur das Intervall ihrer Anzeige.
 *
 * <p><b>Property / Eigenschaft (BR-9 Nr. 4, extended):</b> for every table, row or column with a
 * visible or derivable total, the total minus the visible cells never pins a single hidden cell
 * — witnessed by a second hidden cell with which one unit can be swapped without leaving either
 * interval (an indistinguishable alternative table) — and never yields a hidden sum of 1–4, unless
 * no visible cell &gt; 0 is left, where the rule deliberately stops. Fixed seeds keep the runs
 * reproducible. / Zeuge ist eine zweite ausgeblendete Zelle, mit der sich eine Einheit tauschen
 * lässt; feste Seeds.
 */
class InsightsViewModelSuppressionTest {

    private static final Collator COLLATOR = Collator.getInstance(Locale.GERMAN);

    /** Open upper bound of a reader interval / offene Obergrenze. */
    private static final long OPEN = Long.MAX_VALUE;

    // -----------------------------------------------------------------------------------------
    // Engine rules / Regeln der Engine
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("primary and share states: 1–4 < 5, n < 20, numerator or complement 1–4 k. A.")
    void states() {
        assertEquals(CellState.VALUE, InsightsViewModelSuppression.countState(0));
        assertEquals(CellState.SUPPRESSED, InsightsViewModelSuppression.countState(1));
        assertEquals(CellState.SUPPRESSED, InsightsViewModelSuppression.countState(4));
        assertEquals(CellState.VALUE, InsightsViewModelSuppression.countState(5));
        assertEquals(CellState.TOO_FEW, InsightsViewModelSuppression.shareState(10, 19));
        assertEquals(CellState.WITHHELD, InsightsViewModelSuppression.shareState(4, 24));
        assertEquals(CellState.WITHHELD, InsightsViewModelSuppression.shareState(28, 30));
        assertEquals(CellState.VALUE, InsightsViewModelSuppression.shareState(0, 24));
        assertEquals(CellState.VALUE, InsightsViewModelSuppression.shareState(24, 24), "complement 0");
        assertEquals(CellState.SUPPRESSED, InsightsViewModelSuppression.matrixState(3, 24));
        assertEquals(CellState.WITHHELD, InsightsViewModelSuppression.matrixState(22, 24));
        assertEquals(33.5, InsightsViewModelSuppression.percent(67, 200, 1));
        assertEquals(34.0, InsightsViewModelSuppression.percent(67, 200, 0), "HALF_UP");
        assertEquals(Cell.noData(), InsightsViewModelSuppression.hiddenCell(CellState.VALUE));
        assertEquals(Cell.noData(), InsightsViewModelSuppression.countCell(null));
        assertEquals(Cell.noData(), InsightsViewModelSuppression.shareCell(null, 20));
    }

    @Test
    @DisplayName("reader intervals: < 5 is 1–4, n < 20 is 0…n, share k. A. 1…n − 1, complement n − 4…n − 1")
    void readerIntervals() {
        InsightsViewModelSuppression e = new InsightsViewModelSuppression(COLLATOR);
        assertInterval(e.count("a", 3), 1, 4);
        assertInterval(e.share("b", 6, 12), 0, 12);
        assertInterval(e.share("c", 3, 30), 1, 29);
        assertInterval(e.matrixShare("d", 2, 30), 1, 4);
        assertInterval(e.matrixShare("e", 27, 30), 26, 29);
        assertInterval(e.hidden("f", 9), 0, OPEN);
        // secondary: a visible value > 0 is ≥ 5, a share at most its denominator
        Node count = e.count("g", 12);
        Node share = e.share("h", 9, 40);
        e.constrain(Rule.SUM, null, List.of(e.count("x", 2), count));
        e.constrain(Rule.MARGIN, null, List.of(e.share("y", 2, 10), share));
        e.solve();
        assertEquals(CellState.WITHHELD, count.state());
        assertInterval(count, 5, OPEN);
        assertEquals(CellState.WITHHELD, share.state());
        assertInterval(share, 5, 40);
    }

    private static void assertInterval(Node node, long low, long high) {
        assertEquals(low, node.low(), "low of " + node.name());
        assertEquals(high, node.high(), "high of " + node.name());
    }

    @Test
    @DisplayName("(a) exactly one hidden part → the smallest visible part > 0 is withheld")
    void exactlyOneHiddenPart() {
        InsightsViewModelSuppression e = new InsightsViewModelSuppression(COLLATOR);
        Node hidden = e.count("a", 3);
        Node zero = e.count("b", 0);
        Node small = e.count("c", 7);
        Node big = e.count("d", 20);
        e.constrain(Rule.SUM, null, List.of(hidden, zero, small, big));

        assertEquals(1, e.solve());
        assertEquals(CellState.WITHHELD, small.state());
        assertEquals(CellState.VALUE, big.state());
        assertEquals(CellState.VALUE, zero.state(), "0 is never chosen");
    }

    @Test
    @DisplayName("(b) a derivable hidden sum of 1–4 triggers for SUM and MARGIN alike (two < 5 = 1 + 2)")
    void hiddenSumOneToFourForBothRules() {
        for (Rule rule : Rule.values()) {
            InsightsViewModelSuppression e = new InsightsViewModelSuppression(COLLATOR);
            List<Node> parts = List.of(e.count("a", 1), e.count("b", 2), e.count("c", 9), e.count("d", 6));
            e.constrain(rule, null, parts);

            assertEquals(1, e.solve(), rule.name());
            assertEquals(CellState.WITHHELD, parts.get(3).state(), rule + ": 6 is the smallest visible > 0");
            assertEquals(CellState.VALUE, parts.get(2).state(), rule.name());
        }
    }

    @Test
    @DisplayName("(c) all < 5 at their upper bound: 4 + 4 = 8 resolves uniquely → one more k. A.")
    void twoFoursResolveUniquely() {
        InsightsViewModelSuppression e = new InsightsViewModelSuppression(COLLATOR);
        List<Node> parts = List.of(e.count("a", 4), e.count("b", 4), e.count("c", 12), e.count("d", 30));
        e.constrain(Rule.SUM, null, parts);

        assertEquals(1, e.solve());
        assertEquals(CellState.WITHHELD, parts.get(2).state());
        assertEquals(CellState.VALUE, parts.get(3).state());
    }

    @Test
    @DisplayName("(c) all < 5 at their lower bound: five times 1 = 5 resolves uniquely → one more k. A.")
    void fiveOnesResolveUniquely() {
        InsightsViewModelSuppression e = new InsightsViewModelSuppression(COLLATOR);
        List<Node> parts = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            parts.add(e.count("s" + i, 1));
        }
        Node twelve = e.count("t", 12);
        parts.add(twelve);
        parts.add(e.count("u", 30));
        e.constrain(Rule.MARGIN, null, parts);

        assertEquals(1, e.solve());
        assertEquals(CellState.WITHHELD, twelve.state());
    }

    @Test
    @DisplayName("(c) no unique resolution: 2 + 3 = 5 could be 1 + 4, 4 + 1 … → nothing more is withheld")
    void twoAndThreeStayAmbiguous() {
        InsightsViewModelSuppression e = new InsightsViewModelSuppression(COLLATOR);
        List<Node> parts = List.of(e.count("a", 2), e.count("b", 3), e.count("c", 12), e.count("d", 30));
        e.constrain(Rule.SUM, null, parts);

        assertEquals(0, e.solve());
        assertEquals(CellState.VALUE, parts.get(2).state());
    }

    @Test
    @DisplayName("(c) mixed: < 5 plus k. A. with sum 6 = 1 + 5 is unique → iterates to the fixpoint")
    void suppressedPlusWithheldAtLowerBound() {
        InsightsViewModelSuppression e = new InsightsViewModelSuppression(COLLATOR);
        Node one = e.count("a", 1);
        Node five = e.count("b", 5);
        Node nine = e.count("c", 9);
        Node big = e.count("d", 30);
        e.constrain(Rule.SUM, null, List.of(one, five, nine, big));

        assertEquals(2, e.solve(), "5 for the single hidden cell, then 9 for 6 = 1 + 5");
        assertEquals(CellState.WITHHELD, five.state());
        assertEquals(CellState.WITHHELD, nine.state());
        assertEquals(CellState.VALUE, big.state());

        InsightsViewModelSuppression other = new InsightsViewModelSuppression(COLLATOR);
        Node two = other.count("a", 2);
        Node otherFive = other.count("b", 5);
        Node otherNine = other.count("c", 9);
        other.constrain(Rule.SUM, null, List.of(two, otherFive, otherNine, other.count("d", 30)));
        assertEquals(1, other.solve(), "7 = 2 + 5 could also be 1 + 6");
        assertEquals(CellState.VALUE, otherNine.state());
    }

    @Test
    @DisplayName("(c) matrix: complement k. A. (16 of 20) plus < 5 (1) with sum 17 is unique")
    void matrixComplementAtLowerBound() {
        InsightsViewModelSuppression e = new InsightsViewModelSuppression(COLLATOR);
        Node complement = e.matrixShare("A", 16, 20);
        Node suppressed = e.matrixShare("B", 1, 30);
        Node nine = e.matrixShare("C", 9, 30);
        Node twelve = e.matrixShare("D", 12, 30);
        e.constrain(Rule.MARGIN, null, List.of(complement, suppressed, nine, twelve));

        assertEquals(1, e.solve());
        assertEquals(CellState.WITHHELD, nine.state());
        assertEquals(CellState.VALUE, twelve.state());
    }

    @Test
    @DisplayName("a hidden total must not follow from visible parts; a visible total with one hidden part")
    void hiddenAndVisibleTotals() {
        InsightsViewModelSuppression e = new InsightsViewModelSuppression(COLLATOR);
        Node total = e.hidden("total", 12);
        Node a = e.count("a", 5);
        Node b = e.count("b", 7);
        e.constrain(Rule.SUM, total, List.of(a, b));
        Node visibleTotal = e.count("t2", 30);
        Node c = e.count("c", 3);
        Node d = e.count("d", 27);
        e.constrain(Rule.SUM, visibleTotal, List.of(c, d));

        e.solve();

        assertEquals(CellState.WITHHELD, a.state(), "12 = 5 + 7 would reveal the hidden total");
        assertEquals(CellState.VALUE, b.state());
        assertEquals(CellState.WITHHELD, d.state(), "30 − 27 would reveal the 3");
    }

    @Test
    @DisplayName("a hidden total next to hidden parts is only an extra unknown: < 5 = < 5 + 0 stays open")
    void hiddenTotalWithHiddenParts() {
        InsightsViewModelSuppression e = new InsightsViewModelSuppression(COLLATOR);
        Node total = e.count("total", 4);
        Node part = e.count("a", 4);
        Node zero = e.count("b", 0);
        e.constrain(Rule.SUM, total, List.of(part, zero));

        assertEquals(0, e.solve());
        assertEquals(CellState.SUPPRESSED, total.state());
        assertEquals(CellState.SUPPRESSED, part.state());
    }

    @Test
    @DisplayName("determinable: single unknown, both bounds, open intervals and a priori known cells")
    void determinable() {
        InsightsViewModelSuppression e = new InsightsViewModelSuppression(COLLATOR);
        assertTrue(InsightsViewModelSuppression.determinable(List.of(e.count("a", 1), e.count("b", 1)), List.of(), 2));
        assertTrue(InsightsViewModelSuppression.determinable(List.of(e.count("a", 4), e.count("b", 4)), List.of(), 8));
        assertFalse(InsightsViewModelSuppression.determinable(List.of(e.count("a", 2), e.count("b", 3)), List.of(), 5));
        assertTrue(InsightsViewModelSuppression.determinable(List.of(e.hidden("a", 0)), List.of(), 0));
        assertFalse(InsightsViewModelSuppression.determinable(List.of(e.hidden("a", 0), e.hidden("b", 3)), List.of(), 3));
        assertTrue(InsightsViewModelSuppression.determinable(List.of(e.hidden("a", 0), e.count("b", 1)), List.of(), 1),
            "0 + 1 is the minimum of [0, ∞) + [1, 4]");
        // x − y = 0 with x, y ∈ [1, 4] is open; x − y = 3 pins x = 4, y = 1
        assertFalse(InsightsViewModelSuppression.determinable(List.of(e.count("x", 2)), List.of(e.count("y", 2)), 0));
        assertTrue(InsightsViewModelSuppression.determinable(List.of(e.count("x", 4)), List.of(e.count("y", 1)), 3));
        // a share „n < 20" with n = 0 is known a priori (0…0) and never counts as revealed
        Node known = e.share("k", 0, 0);
        assertFalse(InsightsViewModelSuppression.determinable(List.of(known), List.of(), 0));
        assertTrue(InsightsViewModelSuppression.determinable(List.of(known, e.count("s", 2)), List.of(), 2));
        assertFalse(InsightsViewModelSuppression.determinable(List.of(), List.of(), 0));
    }

    @Test
    @DisplayName("ties: by name (Collator DE), then creation order; last-resort nodes come last")
    void tieBreaks() {
        InsightsViewModelSuppression e = new InsightsViewModelSuppression(COLLATOR);
        Node hidden = e.count("x", 2);
        Node second = e.count("Öhringen", 5);
        Node first = e.count("Ober", 5);
        Node resort = e.countLastResort("ohne Terminzeit", 5);
        e.constrain(Rule.SUM, null, List.of(hidden, second, first, resort));

        e.solve();

        assertEquals(CellState.WITHHELD, first.state(), "„Ober“ < „Öhringen“ in German collation");
        assertEquals(CellState.VALUE, second.state());
        assertEquals(CellState.VALUE, resort.state());
    }

    @Test
    @DisplayName("without a visible cell > 0 the rule ends; hidden nodes are never candidates")
    void noCandidateLeft() {
        InsightsViewModelSuppression e = new InsightsViewModelSuppression(COLLATOR);
        Node suppressed = e.count("a", 3);
        Node fixed = e.hidden("b", 9);
        Node zero = e.count("c", 0);
        e.constrain(Rule.MARGIN, null, List.of(suppressed, zero));
        e.constrain(Rule.SUM, null, new ArrayList<>(Arrays.asList(fixed, null, zero)));
        e.constrain(Rule.SUM, null, java.util.Collections.singletonList(null));

        assertEquals(0, e.solve());
        assertEquals(CellState.WITHHELD, fixed.state());
    }

    // -----------------------------------------------------------------------------------------
    // Property on the engine: random constraints and grids / Eigenschaft auf der Engine
    // -----------------------------------------------------------------------------------------

    /** Node kinds of the engine-level property / Knotenarten. */
    private enum Kind { COUNT, SHARE, MATRIX, HIDDEN }

    /** A created node with what the reader knows about its kind / Knoten mit Art. */
    private record Made(Node node, Kind kind, long denominator) {

        Seen seen() {
            long v = node.value();
            if (node.visible()) {
                return Seen.shown(v);
            }
            return switch (kind) {
                case COUNT -> node.state() == CellState.SUPPRESSED ? Seen.hidden(v, 1, 4) : Seen.hidden(v, 5, OPEN);
                case SHARE -> node.state() == CellState.TOO_FEW ? Seen.hidden(v, 0, denominator)
                    : Seen.hidden(v, 1, denominator);
                case MATRIX -> node.state() == CellState.SUPPRESSED ? Seen.hidden(v, 1, 4)
                    : Seen.hidden(v, 5, denominator);
                case HIDDEN -> Seen.hidden(v, 0, OPEN);
            };
        }
    }

    @Test
    @DisplayName("property: random single constraints (all node kinds, known or hidden total) never pin a hidden cell")
    void randomConstraintsNeverPinAHiddenCell() {
        Random random = new Random(5038L);
        int withheld = 0;
        int hiddenTotals = 0;
        for (int run = 0; run < 3000; run++) {
            InsightsViewModelSuppression e = new InsightsViewModelSuppression(COLLATOR);
            int n = 2 + random.nextInt(9);
            List<Made> parts = new ArrayList<>();
            long sum = 0;
            for (int i = 0; i < n; i++) {
                Made made = randomNode(e, random, "p" + i);
                parts.add(made);
                sum += made.node().value();
            }
            int totalKind = random.nextInt(3);
            Made total = switch (totalKind) {
                case 0 -> null;
                case 1 -> new Made(e.count("total", sum), Kind.COUNT, 0);
                default -> new Made(e.hidden("total", sum), Kind.HIDDEN, 0);
            };
            Rule rule = random.nextBoolean() ? Rule.SUM : Rule.MARGIN;
            e.constrain(rule, total == null ? null : total.node(), parts.stream().map(Made::node).toList());
            withheld += e.solve();

            List<Seen> seenParts = parts.stream().map(Made::seen).toList();
            if (total == null || total.node().visible()) {
                assertNotRecomputable(seenParts, "run " + run);
            } else {
                hiddenTotals++;
                assertHiddenTotalProtected(total.seen(), seenParts, "run " + run);
            }
        }
        assertTrue(withheld > 300, "the random data must exercise the rule: " + withheld);
        assertTrue(hiddenTotals > 500);
    }

    private static Made randomNode(InsightsViewModelSuppression e, Random random, String name) {
        int kind = random.nextInt(10);
        if (kind < 6) {
            return new Made(e.count(name, small(random)), Kind.COUNT, 0);
        }
        if (kind < 8) {
            long denominator = 1 + random.nextInt(60);
            return new Made(e.share(name, part(random, denominator), denominator), Kind.SHARE, denominator);
        }
        if (kind < 9) {
            long denominator = 20 + random.nextInt(40);
            return new Made(e.matrixShare(name, part(random, denominator), denominator), Kind.MATRIX, denominator);
        }
        return new Made(e.hidden(name, random.nextInt(10)), Kind.HIDDEN, 0);
    }

    @Test
    @DisplayName("property: random grids with row sums and column margins — every row and column protected")
    void randomGridsAreProtectedInBothDirections() {
        Random random = new Random(6038L);
        int withheld = 0;
        for (int run = 0; run < 600; run++) {
            int rows = 2 + random.nextInt(6);
            int columns = 2 + random.nextInt(6);
            InsightsViewModelSuppression e = new InsightsViewModelSuppression(COLLATOR);
            Node[][] grid = new Node[rows][columns];
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < columns; c++) {
                    grid[r][c] = e.count(r + "/" + c, small(random));
                }
                e.constrain(Rule.SUM, null, Arrays.asList(grid[r]));
            }
            for (int c = 0; c < columns; c++) {
                List<Node> column = new ArrayList<>();
                for (int r = 0; r < rows; r++) {
                    column.add(grid[r][c]);
                }
                e.constrain(Rule.MARGIN, null, column);
            }
            withheld += e.solve();

            for (int r = 0; r < rows; r++) {
                List<Seen> row = new ArrayList<>();
                for (int c = 0; c < columns; c++) {
                    row.add(new Made(grid[r][c], Kind.COUNT, 0).seen());
                }
                assertNotRecomputable(row, "row " + r + " run " + run);
            }
            for (int c = 0; c < columns; c++) {
                List<Seen> column = new ArrayList<>();
                for (int r = 0; r < rows; r++) {
                    column.add(new Made(grid[r][c], Kind.COUNT, 0).seen());
                }
                assertNotRecomputable(column, "column " + c + " run " + run);
            }
        }
        assertTrue(withheld > 500, "the random data must exercise the rule: " + withheld);
    }

    // -----------------------------------------------------------------------------------------
    // TC-56 property: G-2/G-3 columns and S2 T-2/T-4 columns / Matrix- und S2-Spalten
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("TC-56 property: no margin column ever pins a hidden cell or reveals a hidden sum of 1–4")
    void randomClosedMonthsNeverRevealAHiddenCell() throws Exception {
        Random random = new Random(38L);
        LocalDate since = LocalDate.of(2026, 9, 1);
        YearMonth october = YearMonth.of(2026, 10);
        LocalDate today = LocalDate.of(2026, 11, 15);
        int checkedColumns = 0;
        int withheldSeen = 0;
        for (int run = 0; run < 300; run++) {
            int branches = 3 + random.nextInt(14);
            Map<String, String> names = new LinkedHashMap<>();
            Map<String, BranchCounts> data = new LinkedHashMap<>();
            for (int b = 1; b <= branches; b++) {
                long bookings = bookings(random);
                Map<String, Long> groups = new LinkedHashMap<>();
                for (String group : InsightsViewModelPeriods.GROUPS) {
                    groups.put(group, part(random, bookings));
                }
                names.put(id(b), "Standort " + (char) ('A' + b));
                data.put(id(b), new BranchCounts(bookings, 100L, groups, part(random, bookings),
                    part(random, bookings), part(random, bookings)));
            }
            InsightsViewModel vm = InsightsViewModel.build(compactedMonth(since, october, data, today.minusDays(1)),
                names, today, true, false, false);
            assertNoHiddenValues(vm);

            // matrix columns incl. the collective row / Matrixspalten inklusive Sammelzeile
            for (String column : vm.matrix().columns()) {
                List<Seen> cells = new ArrayList<>();
                for (MatrixRow row : vm.matrix().rows()) {
                    long truth = 0;
                    long denominator = 0;
                    for (Map.Entry<String, BranchCounts> e : data.entrySet()) {
                        BranchCounts c = e.getValue();
                        boolean own = c.bookings() >= InsightsViewModel.MIN_BOOKINGS_MATRIX;
                        boolean member = row.otherBranches() ? !own : names.get(e.getKey()).equals(row.branchName());
                        if (member) {
                            truth += "mobility".equals(column) ? c.mobility() : c.groups().get(column);
                            denominator += c.bookings();
                        }
                    }
                    Cell cell = row.cells().get(column);
                    if (cell.state() == CellState.VALUE) {
                        assertFalse(InsightsViewModelSuppression.isSmall(truth), "visible 1–4");
                        assertFalse(InsightsViewModelSuppression.isSmall(denominator - truth), "visible complement 1–4");
                        assertEquals(InsightsViewModelSuppression.percent(truth, denominator, 0), cell.percent());
                    } else {
                        withheldSeen++;
                    }
                    boolean rowHidden = row.otherBranches() && row.bookings().state() != CellState.VALUE;
                    cells.add(matrixSeen(cell, truth, denominator, rowHidden));
                }
                assertNotRecomputable(cells, "matrix column " + column + " run " + run);
                checkedColumns++;
            }

            // S2 columns T-2 and T-4 / S2-Spalten
            for (boolean late : new boolean[] {false, true}) {
                List<Seen> cells = new ArrayList<>();
                List<BranchRankingRow> rows = new ArrayList<>(vm.ranking());
                rows.addAll(vm.belowThreshold());
                for (BranchRankingRow row : rows) {
                    BranchCounts c = data.get(idOf(names, row.branchName()));
                    if (c.bookings() == 0) {
                        continue;
                    }
                    long truth = late ? c.slotLate() : c.offHours();
                    Cell cell = late ? row.lateShare() : row.offHoursShare();
                    if (cell.state() == CellState.VALUE) {
                        assertTrue(c.bookings() >= InsightsViewModel.N_SHARE_MIN);
                        assertFalse(InsightsViewModelSuppression.isSmall(truth));
                        assertFalse(InsightsViewModelSuppression.isSmall(c.bookings() - truth));
                    }
                    cells.add(shareSeen(cell, truth, c.bookings()));
                }
                assertNotRecomputable(cells, (late ? "T-4" : "T-2") + " run " + run);
                checkedColumns++;
            }
        }
        assertTrue(checkedColumns > 3000);
        assertTrue(withheldSeen > 500, "the random data must exercise the rules");
    }

    /** Bookings of a random branch: many small, some medium, some large / Zufallsbuchungen. */
    private static long bookings(Random random) {
        int bucket = random.nextInt(10);
        if (bucket < 2) {
            return random.nextInt(5);
        }
        if (bucket < 5) {
            return 5 + random.nextInt(15);
        }
        return 20 + random.nextInt(60);
    }

    /** A random share of {@code total}, often 0 or small, sometimes close to the total. */
    private static long part(Random random, long total) {
        if (total == 0) {
            return 0;
        }
        int kind = random.nextInt(6);
        return switch (kind) {
            case 0 -> 0;
            case 1 -> Math.min(total, 1 + random.nextInt(4));
            case 2 -> Math.max(0, total - 1 - random.nextInt(4));
            default -> random.nextInt((int) total + 1);
        };
    }

    private static String idOf(Map<String, String> names, String name) {
        return names.entrySet().stream().filter(e -> e.getValue().equals(name)).findFirst().orElseThrow().getKey();
    }

    // -----------------------------------------------------------------------------------------
    // Property: T-1 / T-3 / T-5 (BR-9 Nr. 4 a) / Heatmaps und Vorlauf
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("property: T-1, T-3 (with „ohne Terminzeit“) and T-5 never let a hidden cell be pinned")
    void randomPeriodsNeverKeepASingleHiddenCell() throws Exception {
        Random random = new Random(1038L);
        LocalDate monday = LocalDate.of(2026, 11, 2);
        for (int run = 0; run < 300; run++) {
            SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
            long[][] t1 = new long[7][3];
            long[][] t3 = new long[7][5];
            long[] lead = new long[7];
            long unknownSlots = random.nextInt(4) == 0 ? random.nextInt(7) : 0;
            int weekdays = 1 + random.nextInt(7);
            for (int w = 0; w < weekdays; w++) {
                InsightsViewModelFixtures.DayBuilder builder = day(0);
                for (int p = 0; p < 3; p++) {
                    t1[w][p] = small(random);
                    builder.daypart(InsightsViewModelPeriods.BOOKING_DAYPARTS.get(p), t1[w][p]);
                }
                for (int p = 0; p < 5; p++) {
                    t3[w][p] = small(random);
                    builder.slot(InsightsViewModelPeriods.WEEKDAYS.get(w), InsightsViewModelPeriods.SLOT_DAYPARTS.get(p), t3[w][p]);
                }
                for (int i = 0; i < 7 && w == 0; i++) {
                    lead[i] = small(random);
                    builder.lead(InsightsViewModelPeriods.LEAD_TIMES.get(i), lead[i]);
                }
                if (w == 0) {
                    builder.slotUnknown(unknownSlots);
                }
                days.put(monday.plusDays(w), builder.build());
            }
            Period p = InsightsViewModelPeriods.build(InsightsViewModelPeriods.PERIOD_MONTH, monday, monday.plusDays(6),
                monday, days, COLLATOR);
            assertNoHiddenValues(p);

            List<Seen> t1Cells = new ArrayList<>();
            List<Seen> off = new ArrayList<>();
            List<Seen> on = new ArrayList<>();
            for (int w = 0; w < weekdays; w++) {
                for (int d = 0; d < 3; d++) {
                    Cell cell = p.bookedWhen().get(InsightsViewModelPeriods.WEEKDAYS.get(w))
                        .get(InsightsViewModelPeriods.BOOKING_DAYPARTS.get(d));
                    Seen seen = countSeen(cell, t1[w][d]);
                    t1Cells.add(seen);
                    (w >= 5 || d > 0 ? off : on).add(seen);
                    assertVisibleIsSafe(cell, t1[w][d]);
                }
            }
            assertNotRecomputable(t1Cells, "T-1 run " + run);
            if (p.offHoursShare().state() == CellState.VALUE) {
                assertNotRecomputable(off, "T-1 off-hours run " + run);
                assertNotRecomputable(on, "T-1 business hours run " + run);
            }
            List<Seen> t3Cells = new ArrayList<>();
            for (String weekday : p.slotGrid().keySet()) {
                int w = InsightsViewModelPeriods.WEEKDAYS.indexOf(weekday);
                for (int d = 0; d < 5; d++) {
                    Cell cell = p.slotGrid().get(weekday).get(InsightsViewModelPeriods.SLOT_DAYPARTS.get(d));
                    t3Cells.add(countSeen(cell, t3[w][d]));
                    assertVisibleIsSafe(cell, t3[w][d]);
                }
            }
            if (unknownSlots > 0) {
                t3Cells.add(countSeen(p.slotUnknown(), unknownSlots));
            }
            assertNotRecomputable(t3Cells, "T-3 run " + run);
            List<Seen> leadCells = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                Cell cell = p.leadTime().get(InsightsViewModelPeriods.LEAD_TIMES.get(i));
                leadCells.add(countSeen(cell, lead[i]));
                assertVisibleIsSafe(cell, lead[i]);
            }
            assertNotRecomputable(leadCells, "T-5 run " + run);
        }
    }

    private static long small(Random random) {
        return random.nextInt(3) == 0 ? random.nextInt(5) : random.nextInt(25);
    }

    private static void assertVisibleIsSafe(Cell cell, long truth) {
        if (cell.state() == CellState.VALUE) {
            assertEquals(truth, cell.value());
            assertFalse(InsightsViewModelSuppression.isSmall(truth));
        }
    }

    // -----------------------------------------------------------------------------------------
    // Property: drop-off tables (BR-31) / Absprungszenarien
    // -----------------------------------------------------------------------------------------

    @Test
    @DisplayName("property: drop-off rows and columns never let a hidden cell be pinned (BR-31, AC-36)")
    void randomDropoffTablesAreProtected() throws Exception {
        Random random = new Random(3138L);
        LocalDate day = LocalDate.of(2026, 11, 10);
        String[] classes = {"mobile", "tablet", "desktop", "unknown"};
        List<String> classList = Arrays.asList(classes);
        List<String> scenarios = new ArrayList<>();
        for (DropoffScenario s : DropoffScenario.values()) {
            scenarios.add(s.key());
        }
        for (int run = 0; run < 300; run++) {
            Map<String, Map<String, Long>> byStep = new LinkedHashMap<>();
            Map<String, Map<String, Long>> byDevice = new LinkedHashMap<>();
            long[] stageSums = new long[4];
            for (String scenario : scenarios) {
                if (random.nextInt(3) == 0) {
                    continue;
                }
                long[] s = new long[4];
                for (int k = 0; k < 4; k++) {
                    boolean allowed = !scenario.startsWith("quiet-") || scenario.equals("quiet-" + (k + 1));
                    s[k] = allowed ? small(random) : 0;
                    stageSums[k] += s[k];
                }
                byStep.put(scenario, stages(s[0], s[1], s[2], s[3]));
                long remaining = s[0] + s[1] + s[2] + s[3];
                for (int c = 0; c < classes.length; c++) {
                    long v = c == classes.length - 1 ? remaining : random.nextInt((int) remaining + 1);
                    remaining -= v;
                    byDevice.computeIfAbsent(scenario, key -> new LinkedHashMap<>()).put(classes[c], v);
                }
            }
            long booked = 20 + random.nextInt(40);
            long r4 = booked + stageSums[3];
            long r3 = r4 + stageSums[2];
            long r2 = r3 + stageSums[1];
            long r1 = r2 + stageSums[0];
            SortedMap<LocalDate, InsightsDay> days = new TreeMap<>();
            days.put(day, funnelDay(fc(r1, r2, r3, r4, booked), null, new DropoffDay(byStep, byDevice)));
            InsightsViewModel vm = InsightsViewModel.build(funnelHistory(day, day, days), Map.of(),
                day.plusDays(1), false, true, true);
            assertNoHiddenValues(vm);
            DropoffView view = vm.funnel().dropoff();

            Map<String, long[]> truthSteps = new LinkedHashMap<>();
            byStep.forEach((scenario, stagesOf) -> {
                String row = scenario.startsWith("quiet-") ? "quiet" : scenario;
                long[] t = truthSteps.computeIfAbsent(row, k -> new long[4]);
                for (int k = 0; k < 4; k++) {
                    t[k] += stagesOf.get(String.valueOf(k + 1));
                }
            });
            List<List<Seen>> columns = new ArrayList<>();
            for (int k = 0; k < 5; k++) {
                columns.add(new ArrayList<>());
            }
            for (DropoffRow row : view.rows()) {
                long[] truth = truthSteps.get(row.scenarioKey());
                List<Seen> rowCells = new ArrayList<>();
                long total = 0;
                for (int k = 0; k < 4; k++) {
                    Seen seen = countSeen(row.byStep().get(k), truth[k]);
                    rowCells.add(seen);
                    columns.get(k).add(seen);
                    total += truth[k];
                    assertVisibleIsSafe(row.byStep().get(k), truth[k]);
                }
                columns.get(4).add(countSeen(row.total(), total));
                if (row.total().state() == CellState.VALUE) {
                    assertNotRecomputable(rowCells, "drop-off row " + row.scenarioKey() + " run " + run);
                }
            }
            for (int k = 0; k < 5; k++) {
                Cell sum = k < 4 ? view.total().byStep().get(k) : view.total().total();
                if (sum.state() == CellState.VALUE) {
                    assertNotRecomputable(columns.get(k), "drop-off column " + (k + 1) + " run " + run);
                }
            }

            // table 2 „Nach Geräteklasse" / Tabelle nach Geräteklasse
            Map<String, long[]> truthDevices = new LinkedHashMap<>();
            byDevice.forEach((scenario, perDevice) -> {
                String row = scenario.startsWith("quiet-") ? "quiet" : scenario;
                perDevice.forEach((device, value) ->
                    truthDevices.computeIfAbsent(row, k -> new long[classes.length])[classList.indexOf(device)] += value);
            });
            Map<String, List<Seen>> deviceRows = new LinkedHashMap<>();
            for (String column : view.deviceColumns()) {
                List<String> members = "other".equals(column) ? view.otherDevices() : List.of(column);
                List<Seen> columnCells = new ArrayList<>();
                for (DropoffRow row : view.rows()) {
                    long truth = 0;
                    for (String member : members) {
                        truth += truthDevices.get(row.scenarioKey())[classList.indexOf(member)];
                    }
                    Cell cell = row.byDevice().get(column);
                    assertVisibleIsSafe(cell, truth);
                    Seen seen = countSeen(cell, truth);
                    columnCells.add(seen);
                    deviceRows.computeIfAbsent(row.scenarioKey(), key -> new ArrayList<>()).add(seen);
                }
                if (view.total().byDevice().get(column).state() == CellState.VALUE) {
                    assertNotRecomputable(columnCells, "device column " + column + " run " + run);
                }
            }
            for (DropoffRow row : view.rows()) {
                if (row.total().state() == CellState.VALUE && !view.deviceColumns().isEmpty()) {
                    assertNotRecomputable(deviceRows.get(row.scenarioKey()), "device row " + row.scenarioKey() + " run " + run);
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Reader model and witness check / Lesermodell und Zeugenprüfung
    // -----------------------------------------------------------------------------------------

    /**
     * One cell as a reader sees it: the true value (test only), whether it is visible and the
     * interval its display allows / eine Zelle aus Lesersicht.
     */
    private record Seen(long truth, boolean visible, long low, long high) {

        static Seen shown(long truth) {
            return new Seen(truth, true, truth, truth);
        }

        static Seen hidden(long truth, long low, long high) {
            return new Seen(truth, false, low, high);
        }
    }

    /** Counter tables (T-1, T-3, T-5, F-3, drop-off): „&lt; 5" 1–4, „k. A." ≥ 5. */
    private static Seen countSeen(Cell cell, long truth) {
        return switch (cell.state()) {
            case VALUE -> Seen.shown(truth);
            case SUPPRESSED -> Seen.hidden(truth, 1, 4);
            default -> Seen.hidden(truth, 5, OPEN);
        };
    }

    /** Matrix G-2/G-3: „&lt; 5" 1–4, „k. A." 5…n; a hidden collective row ≥ 0. */
    private static Seen matrixSeen(Cell cell, long truth, long denominator, boolean rowHidden) {
        return switch (cell.state()) {
            case VALUE -> Seen.shown(truth);
            case SUPPRESSED -> Seen.hidden(truth, 1, 4);
            default -> rowHidden ? Seen.hidden(truth, 0, OPEN) : Seen.hidden(truth, 5, denominator);
        };
    }

    /** S2 shares T-2/T-4: „n &lt; 20" 0…n, „k. A." 1…n. */
    private static Seen shareSeen(Cell cell, long truth, long denominator) {
        return switch (cell.state()) {
            case VALUE -> Seen.shown(truth);
            case TOO_FEW -> Seen.hidden(truth, 0, denominator);
            default -> Seen.hidden(truth, 1, denominator);
        };
    }

    /**
     * A table, row or column with a visible or derivable total: no hidden sum of 1–4 and no hidden
     * cell without a swap partner — unless no visible cell &gt; 0 is left (BR-9 Nr. 4) /
     * Tabelle mit bekannter Summe.
     */
    private static void assertNotRecomputable(List<Seen> cells, String what) {
        List<Seen> hidden = cells.stream().filter(c -> !c.visible()).toList();
        assertReaderModelSound(hidden, what);
        boolean visiblePositive = cells.stream().anyMatch(c -> c.visible() && c.truth() > 0);
        if (hidden.isEmpty() || !visiblePositive) {
            return;
        }
        long hiddenSum = hidden.stream().mapToLong(Seen::truth).sum();
        assertFalse(InsightsViewModelSuppression.isSmall(hiddenSum), what + ": hidden sum " + hiddenSum);
        int[] signs = new int[hidden.size()];
        Arrays.fill(signs, 1);
        assertEveryUnknownHasAWitness(hidden, signs, what);
    }

    /** A hidden total: total − Σ hidden parts = Σ visible parts / ausgeblendete Summe. */
    private static void assertHiddenTotalProtected(Seen total, List<Seen> parts, String what) {
        boolean visiblePositive = parts.stream().anyMatch(c -> c.visible() && c.truth() > 0);
        if (!visiblePositive) {
            return;
        }
        List<Seen> unknowns = new ArrayList<>();
        unknowns.add(total);
        parts.stream().filter(c -> !c.visible()).forEach(unknowns::add);
        assertReaderModelSound(unknowns, what);
        int[] signs = new int[unknowns.size()];
        Arrays.fill(signs, -1);
        signs[0] = 1;
        assertEveryUnknownHasAWitness(unknowns, signs, what);
    }

    private static void assertReaderModelSound(List<Seen> hidden, String what) {
        for (Seen c : hidden) {
            assertTrue(c.low() <= c.truth() && c.truth() <= c.high(), what + ": reader interval " + c);
        }
    }

    /**
     * For {@code Σ signs[i]·x[i] = const}: every unknown of positive width can move by one unit
     * while another unknown compensates inside its interval — an alternative table the reader
     * cannot tell apart / jede Unbekannte hat eine ununterscheidbare Alternative.
     */
    private static void assertEveryUnknownHasAWitness(List<Seen> unknowns, int[] signs, String what) {
        for (int i = 0; i < unknowns.size(); i++) {
            Seen cell = unknowns.get(i);
            if (cell.low() == cell.high()) {
                continue;
            }
            assertTrue(hasWitness(unknowns, signs, i),
                what + ": hidden cell " + cell + " is the only solution among " + unknowns);
        }
    }

    private static boolean hasWitness(List<Seen> unknowns, int[] signs, int i) {
        Seen cell = unknowns.get(i);
        for (int step : new int[] {1, -1}) {
            if (!within(cell, cell.truth() + step)) {
                continue;
            }
            for (int j = 0; j < unknowns.size(); j++) {
                int compensation = -signs[i] * signs[j] * step;
                if (j != i && within(unknowns.get(j), unknowns.get(j).truth() + compensation)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean within(Seen cell, long value) {
        return value >= cell.low() && value <= cell.high();
    }
}
