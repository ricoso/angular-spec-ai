package com.autohaus.backend.usage.insights;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.autohaus.backend.usage.insights.InsightsViewModel.K_MIN;
import static com.autohaus.backend.usage.insights.InsightsViewModel.N_SHARE_MIN;

/**
 * The one suppression engine of the insights view (REQ-038 BR-9 Nr. 1, 2, 4, 5; BR-31) / die
 * eine Unterdrückungs-Engine der Insights-Sicht. Every table of {@link InsightsViewModel} is
 * modelled as {@link Node}s (the true counter plus its display state) and linear
 * {@link #constrain constraints} „total = Σ parts"; {@link #solve()} then applies the secondary
 * rule until no constraint lets a hidden value be recomputed. / Jede Tabelle wird als Knoten
 * plus lineare Nebenbedingungen modelliert; {@code solve()} wendet die Sekundärregel an, bis
 * nichts mehr zurückrechenbar ist.
 *
 * <p><b>Reader model / Lesermodell:</b> a reader knows the rules and, for every hidden cell, the
 * interval its display allows — „&lt; 5" is 1–4; a cell withheld by this engine was a visible
 * value &gt; 0 and is therefore ≥ {@value InsightsViewModel#K_MIN} (and at most its denominator
 * for a share); „n &lt; 20" is 0…n; a share „k. A." is 1…n − 1; a matrix cell with a complement of
 * 1–4 is n − 4…n − 1; anything else (collective row, branch unknown to {@code setup.json}) is
 * ≥ 0. / Der Leser kennt je ausgeblendeter Zelle das Intervall, das ihre Anzeige zulässt.
 *
 * <p><b>Secondary rule / Sekundärregel (BR-9 Nr. 4, identical for {@link Rule#SUM} and
 * {@link Rule#MARGIN}):</b> with a known total (visible, or a margin derivable elsewhere) the
 * hidden sum {@code S = total − Σ visible parts} is derivable. The smallest visible part &gt; 0 is
 * additionally withheld — repeated until the fixpoint — as long as
 * <ol type="a">
 *   <li>exactly one part is hidden, <b>or</b></li>
 *   <li>{@code S} is 1–4, <b>or</b></li>
 *   <li>the hidden parts resolve uniquely: {@code S} equals the sum of their lower bounds (e.g.
 *       two „&lt; 5" with sum 2 = 1 + 1, or „&lt; 5" plus „k. A." with sum 6 = 1 + 5) or of their
 *       upper bounds (e.g. two „&lt; 5" with sum 8 = 4 + 4).</li>
 * </ol>
 * Technically (a) and (c) are one test: some hidden cell's interval collapses to a single value
 * under the constraint ({@link #determinable}). A constraint whose <b>total</b> is hidden is
 * checked the same way with the total as an additional unknown — in particular a hidden total
 * never follows from all-visible parts. A constraint without a total node ({@code total == null})
 * stands for a margin that is visible or derivable elsewhere on the page (G-1, I-4, the T-2 tile,
 * the period bookings) — deliberately treated as <b>known</b>, the conservative reading. / Ohne
 * Summenknoten gilt die Randsumme als bekannt (konservativ).
 *
 * <p>Ties among equally small candidates are broken by name ({@link Collator} DE, e.g. branch
 * names, TC-56) and then by creation order — never by a hidden value. A newly hidden cell is
 * {@code WITHHELD} („k. A."), never {@code SUPPRESSED}, because its value may be ≥ 5 (BR-9 Nr. 4).
 * Nodes carrying no data ({@code NO_DATA}) are never part of a constraint; their value is unknown
 * to everyone. The rule stops when no visible part &gt; 0 is left. / Gleichstand nach Name, dann
 * Anlagereihenfolge; neu ausgeblendet heißt „k. A."; ohne sichtbare Zelle &gt; 0 endet die Regel.
 *
 * <p>The engine never leaves the builder: renderers and JSON only ever see the resulting
 * {@link Cell}s (§19 „Unterdrückung an genau einer Stelle"). / Verlässt den Builder nie.
 */
final class InsightsViewModelSuppression {

    /**
     * Where a constraint comes from (BR-9 Nr. 4) / Herkunft einer Bedingung. Both follow the same
     * secondary rule (a)–(c) of the class comment; the distinction only documents the source
     * table. / Beide folgen derselben Sekundärregel; die Unterscheidung dokumentiert die Quelle.
     */
    enum Rule {
        /** BR-9 Nr. 4 a — table or row with a visible sum / Tabelle bzw. Zeile mit Summe. */
        SUM,
        /** BR-9 Nr. 4 b — column against a derivable margin / Spalte gegen Randsumme. */
        MARGIN
    }

    /** Upper bound „unbounded" of a reader interval / unbeschränkte Obergrenze. */
    private static final long UNBOUNDED = Long.MAX_VALUE;

    /** Lower bound „unbounded" of a negated interval / unbeschränkte Untergrenze. */
    private static final long UNBOUNDED_BELOW = Long.MIN_VALUE;

    /**
     * One displayed (or deliberately invisible) counter / ein angezeigter bzw. bewusst
     * unsichtbarer Zähler. {@code value} is the true count and never leaves the builder unless the
     * final state is {@code VALUE}. {@code low..high} is what a reader can know about the value
     * while it is hidden (class Javadoc); it always contains the true value. / Der wahre Wert
     * verlässt den Builder nur im Zustand VALUE; {@code low..high} ist das Leserwissen.
     */
    static final class Node {
        private final String name;
        private final int order;
        private final long value;
        private final boolean candidate;
        private final boolean lastResort;
        private final long cap;
        private CellState state;
        private long low;
        private long high;

        private Node(String name, int order, long value, CellState state, boolean candidate,
                     boolean lastResort, long low, long high, long cap) {
            this.name = name == null ? "" : name;
            this.order = order;
            this.value = Math.max(0L, value);
            this.state = Objects.requireNonNull(state, "state");
            this.candidate = candidate;
            this.lastResort = lastResort;
            this.cap = cap;
            // the interval must contain the truth even for inconsistent input / enthält den Wert
            this.low = Math.min(Math.max(0L, low), this.value);
            this.high = Math.max(high, this.value);
        }

        /** The true counter / der wahre Zähler. */
        long value() {
            return value;
        }

        /** The display state after {@link #solve()} / Anzeigezustand. */
        CellState state() {
            return state;
        }

        /** Whether the node is visible / ob der Knoten sichtbar ist. */
        boolean visible() {
            return state == CellState.VALUE;
        }

        /** Display name used for tie-breaks / Name für den Gleichstand. */
        String name() {
            return name;
        }

        /** Lower bound a reader knows while hidden / Untergrenze des Leserwissens. */
        long low() {
            return low;
        }

        /** Upper bound a reader knows while hidden, {@link Long#MAX_VALUE} = none / Obergrenze. */
        long high() {
            return high;
        }

        /** Secondary suppression: „k. A.", now ≥ 5 and at most {@code cap} / zusätzlich „k. A.". */
        private void withhold() {
            state = CellState.WITHHELD;
            low = Math.min(K_MIN, value);
            high = Math.max(cap, value);
        }
    }

    private record Constraint(Rule rule, Node total, List<Node> parts) {}

    private final Collator collator;
    private final List<Constraint> constraints = new ArrayList<>();
    private final Comparator<Node> candidateOrder;
    private int order;

    InsightsViewModelSuppression(Collator collator) {
        this.collator = Objects.requireNonNull(collator, "collator");
        this.candidateOrder = Comparator
            .comparing((Node n) -> n.lastResort)
            .thenComparingLong(n -> n.value)
            .thenComparing(n -> n.name, this.collator)
            .thenComparingInt(n -> n.order);
    }

    // ---- node factories / Knoten-Fabriken ----

    /** A counter under the primary rule: 1–4 → {@code SUPPRESSED} (BR-9 Nr. 1) / Zähler. */
    Node count(String name, long value) {
        return countNode(name, value, false);
    }

    /**
     * A counter that is withheld only when nothing else is left (e.g. „ohne Terminzeit" next to
     * T-3) / Zähler, der nur als letztes Mittel ausgeblendet wird.
     */
    Node countLastResort(String name, long value) {
        return countNode(name, value, true);
    }

    private Node countNode(String name, long value, boolean lastResort) {
        CellState state = countState(value);
        long low = state == CellState.SUPPRESSED ? 1 : value;
        long high = state == CellState.SUPPRESSED ? K_MIN - 1 : value;
        return add(name, value, state, true, lastResort, low, high, UNBOUNDED);
    }

    /**
     * A share whose numerator is the node value (R-2, T-2, T-4): denominator &lt; 20 →
     * {@code TOO_FEW}, numerator or complement 1–4 → {@code WITHHELD} (BR-9 Nr. 2, 5) / Anteil.
     */
    Node share(String name, long numerator, long denominator) {
        CellState state = shareState(numerator, denominator);
        long cap = Math.max(0L, denominator);
        long low = switch (state) {
            case TOO_FEW -> 0;
            case WITHHELD -> 1;
            default -> numerator;
        };
        long high = switch (state) {
            case TOO_FEW -> cap;
            case WITHHELD -> cap - 1;
            default -> numerator;
        };
        return add(name, numerator, state, true, false, low, high, cap);
    }

    /**
     * A G-2/G-3 matrix cell: numerator 1–4 → {@code SUPPRESSED}, complement 1–4 →
     * {@code WITHHELD} (BR-9 Nr. 1, 5; the row has ≥ 20 bookings by construction) / Matrixzelle.
     */
    Node matrixShare(String name, long numerator, long denominator) {
        CellState state = matrixState(numerator, denominator);
        long cap = Math.max(0L, denominator);
        long low = switch (state) {
            case SUPPRESSED -> 1;
            case WITHHELD -> cap - (K_MIN - 1);
            default -> numerator;
        };
        long high = switch (state) {
            case SUPPRESSED -> K_MIN - 1;
            case WITHHELD -> cap - 1;
            default -> numerator;
        };
        return add(name, numerator, state, true, false, low, high, cap);
    }

    /**
     * A counter that is hidden from the start and never displayed differently — the collective row
     * below three branches, a branch unknown to {@code setup.json} (BR-9 Nr. 3) / von Beginn an
     * ausgeblendet, nie Kandidat.
     */
    Node hidden(String name, long value) {
        return add(name, value, CellState.WITHHELD, false, false, 0, UNBOUNDED, UNBOUNDED);
    }

    private Node add(String name, long value, CellState state, boolean candidate, boolean lastResort,
                     long low, long high, long cap) {
        return new Node(name, order++, value, state, candidate, lastResort, low, high, cap);
    }

    // ---- constraints / Nebenbedingungen ----

    /**
     * Registers „total = Σ parts" / registriert eine Nebenbedingung. {@code null} parts are
     * dropped (cells without data); a constraint with fewer than one part is ignored.
     *
     * @param rule BR-9 Nr. 4 a or 4 b (same secondary rule, source documentation)
     * @param total the sum cell, or {@code null} for a known margin (conservative)
     * @param parts the addends / die Summanden
     */
    void constrain(Rule rule, Node total, List<Node> parts) {
        List<Node> present = new ArrayList<>(parts.size());
        for (Node part : parts) {
            if (part != null) {
                present.add(part);
            }
        }
        if (!present.isEmpty()) {
            constraints.add(new Constraint(rule, total, List.copyOf(present)));
        }
    }

    /**
     * Applies the secondary rule until no constraint changes anything (monotone: every step turns
     * one visible node into {@code WITHHELD}, so it terminates) / wendet die Sekundärregel bis
     * zum Fixpunkt an.
     *
     * @return the number of additionally withheld nodes / Anzahl zusätzlich ausgeblendeter Knoten
     */
    int solve() {
        int withheld = 0;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Constraint constraint : constraints) {
                while (applyOnce(constraint)) {
                    withheld++;
                    changed = true;
                }
            }
        }
        return withheld;
    }

    private boolean applyOnce(Constraint c) {
        if (!leaks(c)) {
            return false;
        }
        Node pick = null;
        for (Node part : c.parts()) {
            if (part.visible() && part.candidate && part.value > 0
                && (pick == null || candidateOrder.compare(part, pick) < 0)) {
                pick = part;
            }
        }
        if (pick == null) {
            // No visible cell > 0 left — the rule ends here (BR-9 Nr. 4 „keine sichtbare Zelle
            // > 0 übrig"). / Keine sichtbare Zelle > 0 mehr.
            return false;
        }
        pick.withhold();
        return true;
    }

    /** Whether the constraint currently lets a hidden value be recomputed / ob sie etwas verrät. */
    private static boolean leaks(Constraint c) {
        List<Node> hidden = new ArrayList<>();
        long visibleSum = 0;
        for (Node part : c.parts()) {
            if (part.visible()) {
                visibleSum += part.value;
            } else {
                hidden.add(part);
            }
        }
        boolean totalKnown = c.total() == null || c.total().visible();
        if (!totalKnown) {
            // total − Σ hidden parts = Σ visible parts; the total is one more unknown / Summe
            // ist eine weitere Unbekannte.
            return determinable(List.of(c.total()), hidden, visibleSum);
        }
        if (hidden.isEmpty()) {
            return false;
        }
        long hiddenSum = 0;
        for (Node part : hidden) {
            hiddenSum += part.value;
        }
        // (b) derivable hidden sum 1–4; (a)/(c) unique resolution / (b) Summe 1–4, (a)/(c) eindeutig
        return isSmall(hiddenSum) || determinable(hidden, List.of(), hiddenSum);
    }

    /**
     * Whether {@code Σ plus − Σ minus = rhs} pins one unknown to a single value although its
     * reader interval allows more than one (BR-9 Nr. 4 a, c) / ob die Gleichung eine Unbekannte
     * eindeutig festlegt. For integer intervals the feasible values of one unknown form the
     * interval {@code [max(lo, rhs − Σ others' max), min(hi, rhs − Σ others' min)]}; it collapses
     * exactly when only one unknown is uncertain, or every unknown sits at its lower or at its
     * upper bound. / Genau dann, wenn nur eine Unbekannte unsicher ist oder alle an ihrer Unter-
     * bzw. Obergrenze liegen.
     */
    static boolean determinable(List<Node> plus, List<Node> minus, long rhs) {
        int n = plus.size() + minus.size();
        long[] lows = new long[n];
        long[] highs = new long[n];
        long finiteLow = 0;
        long finiteHigh = 0;
        int infiniteLow = 0;
        int infiniteHigh = 0;
        for (int i = 0; i < n; i++) {
            boolean positive = i < plus.size();
            Node node = positive ? plus.get(i) : minus.get(i - plus.size());
            lows[i] = positive ? node.low : negate(node.high);
            highs[i] = positive ? node.high : -node.low;
            if (lows[i] == UNBOUNDED_BELOW) {
                infiniteLow++;
            } else {
                finiteLow += lows[i];
            }
            if (highs[i] == UNBOUNDED) {
                infiniteHigh++;
            } else {
                finiteHigh += highs[i];
            }
        }
        for (int i = 0; i < n; i++) {
            boolean ownLowInfinite = lows[i] == UNBOUNDED_BELOW;
            boolean ownHighInfinite = highs[i] == UNBOUNDED;
            if (!ownLowInfinite && !ownHighInfinite && lows[i] == highs[i]) {
                continue; // known without the constraint / auch ohne Bedingung bekannt
            }
            boolean othersLowInfinite = infiniteLow - (ownLowInfinite ? 1 : 0) > 0;
            boolean othersHighInfinite = infiniteHigh - (ownHighInfinite ? 1 : 0) > 0;
            long othersLow = finiteLow - (ownLowInfinite ? 0 : lows[i]);
            long othersHigh = finiteHigh - (ownHighInfinite ? 0 : highs[i]);
            long lower = othersHighInfinite ? lows[i] : Math.max(lows[i], rhs - othersHigh);
            long upper = othersLowInfinite ? highs[i] : Math.min(highs[i], rhs - othersLow);
            if (lower != UNBOUNDED_BELOW && upper != UNBOUNDED && lower >= upper) {
                return true;
            }
        }
        return false;
    }

    private static long negate(long bound) {
        return bound == UNBOUNDED ? UNBOUNDED_BELOW : -bound;
    }

    // ---- state and rounding helpers / Zustands- und Rundungshelfer ----

    /** 1–4 / Bereich der Primärunterdrückung (BR-9 Nr. 1). */
    static boolean isSmall(long value) {
        return value >= 1 && value < K_MIN;
    }

    /** Primary rule: 1–4 → {@code SUPPRESSED}, 0 stays 0 (BR-9 Nr. 1) / Primärregel. */
    static CellState countState(long value) {
        return isSmall(value) ? CellState.SUPPRESSED : CellState.VALUE;
    }

    /** Share rule: n &lt; 20 → TOO_FEW; numerator or complement 1–4 → WITHHELD (BR-9 Nr. 2, 5). */
    static CellState shareState(long numerator, long denominator) {
        if (denominator < N_SHARE_MIN) {
            return CellState.TOO_FEW;
        }
        if (isSmall(numerator) || isSmall(denominator - numerator)) {
            return CellState.WITHHELD;
        }
        return CellState.VALUE;
    }

    /** Matrix rule: numerator 1–4 → SUPPRESSED, complement 1–4 → WITHHELD (TC-26). */
    static CellState matrixState(long numerator, long denominator) {
        if (isSmall(numerator)) {
            return CellState.SUPPRESSED;
        }
        if (isSmall(denominator - numerator)) {
            return CellState.WITHHELD;
        }
        return CellState.VALUE;
    }

    /** {@code 100·numerator/denominator}, HALF_UP to {@code scale} decimals (§10.5) / Prozent. */
    static double percent(long numerator, long denominator, int scale) {
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(denominator), scale, RoundingMode.HALF_UP)
            .doubleValue();
    }

    /** A value-free cell of the given hidden state / wertlose Zelle des Zustands. */
    static Cell hiddenCell(CellState state) {
        return switch (state) {
            case SUPPRESSED -> Cell.suppressed();
            case WITHHELD -> Cell.withheld();
            case TOO_FEW -> Cell.tooFew();
            case NO_DATA, VALUE -> Cell.noData();
        };
    }

    /** The counter cell of a node — the value only if visible / Zählerzelle eines Knotens. */
    static Cell countCell(Node node) {
        if (node == null) {
            return Cell.noData();
        }
        return node.visible() ? Cell.of(node.value()) : hiddenCell(node.state());
    }

    /**
     * An integer share cell (R-2, T-2, T-4, I-4) of a node whose value is the numerator / ganz-
     * zahlige Anteilszelle.
     */
    static Cell shareCell(Node node, long denominator) {
        if (node == null) {
            return Cell.noData();
        }
        return node.visible() ? Cell.percentOf(percent(node.value(), denominator, 0))
            : hiddenCell(node.state());
    }

    /** An integer share cell straight from two counters (no secondary rule) / direkte Anteilszelle. */
    static Cell shareCell(long numerator, long denominator) {
        CellState state = shareState(numerator, denominator);
        return state == CellState.VALUE ? Cell.percentOf(percent(numerator, denominator, 0))
            : hiddenCell(state);
    }
}
