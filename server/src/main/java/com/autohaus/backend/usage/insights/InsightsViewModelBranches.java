package com.autohaus.backend.usage.insights;

import com.autohaus.backend.usage.insights.InsightsViewModelSuppression.Node;
import com.autohaus.backend.usage.insights.InsightsViewModelSuppression.Rule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import static com.autohaus.backend.usage.insights.InsightsViewModel.K_MIN;
import static com.autohaus.backend.usage.insights.InsightsViewModel.MIN_BOOKINGS_MATRIX;
import static com.autohaus.backend.usage.insights.InsightsViewModel.MIN_BRANCHES_OTHER_ROW;
import static com.autohaus.backend.usage.insights.InsightsViewModel.N_SHARE_MIN;
import static com.autohaus.backend.usage.insights.InsightsViewModelPeriods.nn;
import static com.autohaus.backend.usage.insights.InsightsViewModelSuppression.hiddenCell;
import static com.autohaus.backend.usage.insights.InsightsViewModelSuppression.percent;

/**
 * Location metrics of the closed month — S2 ranking, G-2/G-3 matrix and I-4 (REQ-038 BR-9, BR-10,
 * §10.5) / Standortkennzahlen des abgeschlossenen Vormonats. Package-private part of
 * {@link InsightsViewModelBuilder}. / Paket-privater Teil des Builders.
 *
 * <p><b>Periods / Zeiträume (BR-10):</b> the closed month is the month before {@code today}
 * (between 00:05 and the rollup on the 1st it is already shown, with the pending warning, §5.8);
 * it is {@code null} as long as no month has closed since the collection start. Its per-branch
 * data come from {@code branchMonths[M]} once compacted, otherwise from the sum of the
 * {@code branchDays} of M (never before {@code collectingSince}). „Jahr bis Vormonat" sums the
 * months of the <b>running</b> year up to M — in January there is none, so it is „—" (TC-28). /
 * Jahreswert nur für das laufende Jahr.
 *
 * <p><b>Suppression / Unterdrückung:</b>
 * <ul>
 *   <li>R-1 with 1–4 → „&lt; 5", <b>without</b> complementary suppression (BR-9 Nr. 6); ranked are
 *       branches with ≥ 5 bookings (descending, ties by name), the rest is {@code belowThreshold}
 *       in alphabetical order — the ranking stays monotone and never sorts by a hidden value.</li>
 *   <li>R-2, T-2, T-4 and I-4 are shares (n ≥ 20, never with numerator or complement 1–4); R-4 is
 *       „k. A." when R-1 or R-3 is hidden and „n &lt; 20" below 20 views (BR-9 Nr. 2, 5).</li>
 *   <li>The S2 columns T-2 and T-4 are {@link Rule#MARGIN} constraints against the global margin
 *       (I-4, T-2 tile, T-3 „spät"); rows of the {@code rk-low} group count as hidden. A branch
 *       with 0 bookings is left out: its value is known to be 0. / Zeilen mit 0 Buchungen tragen
 *       keine verborgene Information.</li>
 *   <li>G-2/G-3: own row from 20 bookings, the collective row „weitere Standorte" only with ≥ 3
 *       contributing branches and ≥ 20 bookings, otherwise completely „k. A." (BR-9 Nr. 3); each
 *       column is a {@link Rule#MARGIN} constraint against the G-1 margin of the month; there is
 *       deliberately no row rule (rows are not additive). A collective row of branches that all
 *       have 0 bookings carries nothing and is left out.</li>
 *   <li>Branch ids missing from {@code setup.json} have no display name: they are never shown but
 *       stay in every total and enter the constraints as permanently hidden cells. / Unbekannte
 *       Standorte bleiben in den Summen, werden aber nie gezeigt.</li>
 * </ul>
 */
final class InsightsViewModelBranches {

    /** §17 {@code MATRIX_OTHER_BRANCHES} — label of the collective row / Sammelzeile. */
    static final String MATRIX_OTHER_BRANCHES = "weitere Standorte (unter 20 Buchungen)";

    /** G-3 column key / Spaltenschlüssel Mietwagen. */
    static final String MOBILITY_COLUMN = "mobility";

    /** {@link GroupMatrix#columns()}: the ten {@link ServiceGroup} keys plus {@code mobility}. */
    static final List<String> MATRIX_COLUMNS;

    static {
        List<String> columns = new ArrayList<>(InsightsViewModelPeriods.GROUPS);
        columns.add(MOBILITY_COLUMN);
        MATRIX_COLUMNS = List.copyOf(columns);
    }

    /** {@code rankMove}: better rank than in the month before / aufgestiegen. */
    static final String RANK_UP = "up";
    /** {@code rankMove}: worse rank / abgestiegen. */
    static final String RANK_DOWN = "down";
    /** {@code rankMove}: unchanged / unverändert. */
    static final String RANK_SAME = "same";
    /** {@code rankMove}: not ranked in the month before / neu in der Rangliste. */
    static final String RANK_NEW = "new";

    /** Minimum bookings for a rank (BR-9 Nr. 1, AC-15) / Mindestbuchungen für einen Rang. */
    private static final long MIN_BOOKINGS_RANK = K_MIN;

    /** A branch absent from a present month map has 0 of everything (§10.6) / fehlend = 0. */
    private static final BranchCounts ZERO = new BranchCounts(0L, 0L, Map.of(), 0L, 0L, 0L);

    private InsightsViewModelBranches() {
    }

    /**
     * Result of the closed-month part / Ergebnis des Vormonatsteils.
     *
     * @param closedMonth the previous month, {@code null} before the first closed month
     * @param partialSince collection start when it lies after the 1st of the month (BR-19)
     * @param incomplete days missing in the compacted month (§5.8) / fehlende Tage
     * @param missingDays how many / wie viele
     * @param ranking S2 ranked rows / gerankte Zeilen
     * @param belowThreshold S2 group „Unter 5 Buchungen" / Gruppe unter 5
     * @param matrix G-2/G-3, {@code null} before the first closed month
     * @param offHoursShare I-4 global T-2 share, {@code null} before the first closed month
     */
    record Result(YearMonth closedMonth, LocalDate partialSince, boolean incomplete, int missingDays,
                  List<BranchRankingRow> ranking, List<BranchRankingRow> belowThreshold,
                  GroupMatrix matrix, Cell offHoursShare) {

        static Result none() {
            return new Result(null, null, false, 0, List.of(), List.of(), null, null);
        }
    }

    /** One branch of a month / ein Standort eines Monats. */
    private record Entry(String id, String name, BranchCounts counts) {

        /** Bookings, {@code null} = counter missing / Buchungen. */
        Long bookings() {
            return counts.bookings() == null ? null : nn(counts.bookings());
        }
    }

    /** A matrix row whose cells are read after {@code solve()} / Matrixzeile im Aufbau. */
    private record RowDraft(String name, Cell bookings, long denominator, Map<String, Node> cells,
                            boolean other) {}

    /** Everything the rows need after {@code solve()} / Kontext für die Zeilen. */
    private record Context(InsightsHistory history, YearMonth month, LocalDate today,
                           LocalDate collectingSince, long total, long maxBookings,
                           Map<YearMonth, Map<String, BranchCounts>> cache,
                           Map<String, Node> offHours, Map<String, Node> late) {}

    /**
     * Builds the closed-month part / baut den Vormonatsteil.
     *
     * @param history the loaded history / geladene Historie
     * @param branchNames display names by branchId / Anzeigenamen
     * @param today the Berlin day of the build / Tag des Aufbaus
     * @param collectingSince first counted day of K2 / Erfassungsbeginn
     * @param collator name order (DE) / Namensreihenfolge
     */
    static Result build(InsightsHistory history, Map<String, String> branchNames, LocalDate today,
                        LocalDate collectingSince, Collator collator) {
        YearMonth month = YearMonth.from(today).minusMonths(1);
        if (collectingSince == null || collectingSince.isAfter(month.atEndOfMonth())) {
            return Result.none();
        }
        LocalDate partialSince = collectingSince.isAfter(month.atDay(1)) ? collectingSince : null;
        int missingDays = history.branchMonths().containsKey(month)
            ? missingDays(month, collectingSince, history.days()) : 0;
        Map<YearMonth, Map<String, BranchCounts>> cache = new HashMap<>();
        Map<String, BranchCounts> data = monthData(history, month, collectingSince, cache);
        if (data == null) {
            return new Result(month, partialSince, missingDays > 0, missingDays, List.of(), List.of(),
                new GroupMatrix(MATRIX_COLUMNS, List.of()), Cell.noData());
        }

        List<Entry> named = new ArrayList<>();
        List<Entry> unnamed = new ArrayList<>();
        for (Map.Entry<String, String> branch : branchNames.entrySet()) {
            BranchCounts counts = data.get(branch.getKey());
            String name = branch.getValue() == null ? branch.getKey() : branch.getValue();
            named.add(new Entry(branch.getKey(), name, counts == null ? ZERO : counts));
        }
        for (Map.Entry<String, BranchCounts> branch : data.entrySet()) {
            if (!branchNames.containsKey(branch.getKey()) && branch.getValue() != null) {
                unnamed.add(new Entry(branch.getKey(), branch.getKey(), branch.getValue()));
            }
        }
        long total = 0;
        for (BranchCounts counts : data.values()) {
            total += counts == null ? 0 : nn(counts.bookings());
        }

        Comparator<Entry> byName = Comparator.comparing(Entry::name, collator);
        Comparator<Entry> byBookings = Comparator
            .comparingLong((Entry e) -> -e.bookings()).thenComparing(byName);

        InsightsViewModelSuppression engine = new InsightsViewModelSuppression(collator);
        Map<String, Node> offHours = new HashMap<>();
        Map<String, Node> late = new HashMap<>();
        shareColumns(engine, named, unnamed, offHours, late);
        List<RowDraft> matrixRows = matrixDraft(engine, named, unnamed, byBookings, byName);

        engine.solve();

        List<Entry> ranked = named.stream()
            .filter(e -> e.bookings() != null && e.bookings() >= MIN_BOOKINGS_RANK)
            .sorted(byBookings).toList();
        List<Entry> below = named.stream()
            .filter(e -> e.bookings() == null || e.bookings() < MIN_BOOKINGS_RANK)
            .sorted(byName).toList();

        Map<String, BranchCounts> previous = monthData(history, month.minusMonths(1), collectingSince, cache);
        Map<String, Integer> previousRanks = previous == null ? null : ranks(previous, branchNames, collator);
        long maxBookings = ranked.isEmpty() ? 0 : ranked.getFirst().bookings();
        Context ctx = new Context(history, month, today, collectingSince, total, maxBookings, cache,
            offHours, late);

        List<BranchRankingRow> rankingRows = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            Entry e = ranked.get(i);
            int rank = i + 1;
            Integer previousRank = previousRanks == null ? null : previousRanks.get(e.id());
            String move = previousRanks == null ? null
                : previousRank == null ? RANK_NEW
                : previousRank > rank ? RANK_UP
                : previousRank < rank ? RANK_DOWN : RANK_SAME;
            rankingRows.add(row(e, rank, move, previousRank, ctx));
        }
        List<BranchRankingRow> belowRows = new ArrayList<>();
        for (Entry e : below) {
            belowRows.add(row(e, null, null, null, ctx));
        }

        return new Result(month, partialSince, missingDays > 0, missingDays,
            List.copyOf(rankingRows), List.copyOf(belowRows), finishMatrix(matrixRows),
            offHoursShare(data));
    }

    // ---- S2 rows / Zeilen der Rangliste ----

    private static BranchRankingRow row(Entry e, Integer rank, String move, Integer previousRank,
                                        Context ctx) {
        Long bookings = e.bookings();
        Cell bookingsCell = bookings == null ? Cell.noData() : countOf(bookings);
        Cell share = bookings == null ? Cell.noData()
            : InsightsViewModelSuppression.shareCell(bookings, ctx.total());
        Cell serviceViews = e.counts().serviceViews() == null ? Cell.noData()
            : countOf(nn(e.counts().serviceViews()));
        Cell per100 = per100(bookingsCell, serviceViews, bookings, nn(e.counts().serviceViews()));
        double barWidth = bookingsCell.state() == CellState.VALUE && ctx.maxBookings() > 0
            ? BigDecimal.valueOf(bookings).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(ctx.maxBookings()), 1, RoundingMode.HALF_UP).doubleValue()
            : 0.0;
        return new BranchRankingRow(e.name(), rank, move, previousRank, bookingsCell, share,
            yearBookings(e.id(), ctx), serviceViews, per100,
            columnShare(ctx.offHours().get(e.id()), bookings, e.counts().offHours()),
            columnShare(ctx.late().get(e.id()), bookings, e.counts().slotLate()),
            barWidth);
    }

    /** R-4 „Buchungen je 100 Aufrufe (Näherung)", one decimal (BR-9 Nr. 2, 5) / R-4. */
    private static Cell per100(Cell bookings, Cell serviceViews, Long bookingsValue, long views) {
        if (bookings.state() == CellState.NO_DATA || serviceViews.state() == CellState.NO_DATA) {
            return Cell.noData();
        }
        if (bookings.state() != CellState.VALUE || serviceViews.state() != CellState.VALUE) {
            return Cell.withheld();
        }
        if (views < N_SHARE_MIN) {
            return Cell.tooFew();
        }
        return Cell.ratioOf(BigDecimal.valueOf(bookingsValue).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(views), 1, RoundingMode.HALF_UP).doubleValue());
    }

    /** T-2 / T-4 cell of a branch after the column rule / Spaltenzelle nach der Spaltenregel. */
    private static Cell columnShare(Node node, Long bookings, Long counter) {
        if (bookings == null || counter == null) {
            return Cell.noData();
        }
        if (node == null) {
            // 0 bookings: nothing to share (n < 20), not part of the column rule / 0 Buchungen.
            return Cell.tooFew();
        }
        return InsightsViewModelSuppression.shareCell(node, bookings);
    }

    /** „Jahr bis Vormonat" of the running year, primary rule only (BR-9 Nr. 6) / Jahreswert. */
    private static Cell yearBookings(String branchId, Context ctx) {
        YearMonth month = ctx.month();
        if (month.getYear() != ctx.today().getYear()) {
            return Cell.noData();
        }
        long sum = 0;
        boolean any = false;
        for (YearMonth m = YearMonth.of(month.getYear(), 1); !m.isAfter(month); m = m.plusMonths(1)) {
            Map<String, BranchCounts> data = monthData(ctx.history(), m, ctx.collectingSince(), ctx.cache());
            if (data == null) {
                continue;
            }
            any = true;
            BranchCounts counts = data.get(branchId);
            sum += counts == null ? 0 : nn(counts.bookings());
        }
        return any ? countOf(sum) : Cell.noData();
    }

    /** A counter under the primary rule only / Zähler nur mit Primärregel. */
    private static Cell countOf(long value) {
        CellState state = InsightsViewModelSuppression.countState(value);
        return state == CellState.VALUE ? Cell.of(value) : hiddenCell(state);
    }

    /** Ranks of a month among the named branches (≥ 5 bookings, ties by name) / Ränge. */
    private static Map<String, Integer> ranks(Map<String, BranchCounts> data,
                                              Map<String, String> branchNames, Collator collator) {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, String> branch : branchNames.entrySet()) {
            BranchCounts counts = data.get(branch.getKey());
            if (counts != null && counts.bookings() != null && nn(counts.bookings()) >= MIN_BOOKINGS_RANK) {
                String name = branch.getValue() == null ? branch.getKey() : branch.getValue();
                entries.add(new Entry(branch.getKey(), name, counts));
            }
        }
        entries.sort(Comparator.comparingLong((Entry e) -> -e.bookings())
            .thenComparing(Entry::name, collator));
        Map<String, Integer> ranks = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            ranks.put(entries.get(i).id(), i + 1);
        }
        return ranks;
    }

    /** I-4: global T-2 share of the month, integer (BR-21, BR-9 Nr. 5) / globaler T-2-Anteil. */
    private static Cell offHoursShare(Map<String, BranchCounts> data) {
        long n = 0;
        long k = 0;
        boolean any = false;
        for (BranchCounts counts : data.values()) {
            if (counts == null || counts.bookings() == null || counts.offHours() == null) {
                continue;
            }
            any = true;
            n += nn(counts.bookings());
            k += nn(counts.offHours());
        }
        return any ? InsightsViewModelSuppression.shareCell(k, n) : Cell.noData();
    }

    // ---- S2 columns T-2 / T-4 (BR-9 Nr. 4 b) ----

    private static void shareColumns(InsightsViewModelSuppression engine, List<Entry> named,
                                     List<Entry> unnamed, Map<String, Node> offHours,
                                     Map<String, Node> late) {
        List<Node> offParts = new ArrayList<>();
        List<Node> lateParts = new ArrayList<>();
        for (Entry e : named) {
            Long bookings = e.bookings();
            if (bookings == null || bookings == 0) {
                continue;
            }
            if (e.counts().offHours() != null) {
                Node node = engine.share(e.name(), nn(e.counts().offHours()), bookings);
                offHours.put(e.id(), node);
                offParts.add(node);
            }
            if (e.counts().slotLate() != null) {
                Node node = engine.share(e.name(), nn(e.counts().slotLate()), bookings);
                late.put(e.id(), node);
                lateParts.add(node);
            }
        }
        for (Entry e : unnamed) {
            offParts.add(engine.hidden(e.id(), nn(e.counts().offHours())));
            lateParts.add(engine.hidden(e.id(), nn(e.counts().slotLate())));
        }
        engine.constrain(Rule.MARGIN, null, offParts);
        engine.constrain(Rule.MARGIN, null, lateParts);
    }

    // ---- G-2 / G-3 matrix (BR-9 Nr. 3, 4 b) ----

    private static List<RowDraft> matrixDraft(InsightsViewModelSuppression engine, List<Entry> named,
                                              List<Entry> unnamed, Comparator<Entry> byBookings,
                                              Comparator<Entry> byName) {
        List<Entry> own = named.stream()
            .filter(e -> e.bookings() != null && e.bookings() >= MIN_BOOKINGS_MATRIX)
            .sorted(byBookings).toList();
        List<Entry> others = named.stream()
            .filter(e -> e.bookings() != null && e.bookings() < MIN_BOOKINGS_MATRIX)
            .sorted(byName).toList();
        long otherBookings = others.stream().mapToLong(Entry::bookings).sum();
        long contributors = others.stream().filter(e -> e.bookings() > 0).count();
        boolean otherVisible = contributors >= MIN_BRANCHES_OTHER_ROW
            && otherBookings >= MIN_BOOKINGS_MATRIX;

        List<RowDraft> rows = new ArrayList<>();
        Map<String, List<Node>> columns = new LinkedHashMap<>();
        MATRIX_COLUMNS.forEach(column -> columns.put(column, new ArrayList<>()));
        for (Entry e : own) {
            Map<String, Node> cells = new LinkedHashMap<>();
            for (String column : MATRIX_COLUMNS) {
                Long count = mentions(e.counts(), column);
                Node node = count == null ? null : engine.matrixShare(e.name(), count, e.bookings());
                cells.put(column, node);
                columns.get(column).add(node);
            }
            rows.add(new RowDraft(e.name(), Cell.of(e.bookings()), e.bookings(), cells, false));
        }
        if (otherBookings > 0) {
            Map<String, Node> cells = new LinkedHashMap<>();
            for (String column : MATRIX_COLUMNS) {
                long count = 0;
                for (Entry e : others) {
                    count += nn(mentions(e.counts(), column));
                }
                Node node = otherVisible
                    ? engine.matrixShare(MATRIX_OTHER_BRANCHES, count, otherBookings)
                    : engine.hidden(MATRIX_OTHER_BRANCHES, count);
                cells.put(column, node);
                columns.get(column).add(node);
            }
            rows.add(new RowDraft(MATRIX_OTHER_BRANCHES,
                otherVisible ? Cell.of(otherBookings) : Cell.withheld(), otherBookings, cells, true));
        }
        for (Entry e : unnamed) {
            for (String column : MATRIX_COLUMNS) {
                columns.get(column).add(engine.hidden(e.id(), nn(mentions(e.counts(), column))));
            }
        }
        columns.values().forEach(parts -> engine.constrain(Rule.MARGIN, null, parts));
        return rows;
    }

    /** Turns the solved drafts into cells with the fixed heat scale (§10.5) / Zellen der Matrix. */
    private static GroupMatrix finishMatrix(List<RowDraft> drafts) {
        List<MatrixRow> rows = new ArrayList<>();
        for (RowDraft draft : drafts) {
            Map<String, Cell> cells = new LinkedHashMap<>();
            for (String column : MATRIX_COLUMNS) {
                Node node = draft.cells().get(column);
                cells.put(column, matrixCell(node, draft.denominator()));
            }
            rows.add(new MatrixRow(draft.name(), draft.bookings(), Collections.unmodifiableMap(cells),
                draft.other()));
        }
        return new GroupMatrix(MATRIX_COLUMNS, List.copyOf(rows));
    }

    /** Integer share plus fixed heat level; hidden → no value, heat 0 (BR-9 Nr. 5) / Matrixzelle. */
    private static Cell matrixCell(Node node, long denominator) {
        if (node == null) {
            return Cell.noData();
        }
        if (!node.visible()) {
            return hiddenCell(node.state());
        }
        double pct = percent(node.value(), denominator, 0);
        return Cell.percentOf(pct, node.value() == 0 ? 0 : shareHeat(pct));
    }

    /** G-2 fixed scale: 1–14 → 1, 15–29 → 2, 30–44 → 3, ≥ 45 → 4 (§10.5) / feste Heat-Skala. */
    static int shareHeat(double percent) {
        if (percent >= 45) {
            return 4;
        }
        if (percent >= 30) {
            return 3;
        }
        if (percent >= 15) {
            return 2;
        }
        return 1;
    }

    /** Mentions of a matrix column, {@code null} = counter missing / Nennungen einer Spalte. */
    private static Long mentions(BranchCounts counts, String column) {
        if (MOBILITY_COLUMN.equals(column)) {
            return counts.mobility() == null ? null : nn(counts.mobility());
        }
        if (counts.groups() == null) {
            return null;
        }
        return nn(counts.groups().get(column));
    }

    // ---- month data / Monatsdaten ----

    /**
     * Per-branch data of a month: {@code branchMonths[M]} once compacted, else the summed
     * {@code branchDays} from the collection start; {@code null} when the month has no counted
     * day at all (→ „—", never 0) / Standortdaten eines Monats, {@code null} ohne jeden Tag.
     */
    static Map<String, BranchCounts> monthData(InsightsHistory history, YearMonth month,
                                               LocalDate collectingSince,
                                               Map<YearMonth, Map<String, BranchCounts>> cache) {
        if (cache.containsKey(month)) {
            return cache.get(month);
        }
        Map<String, BranchCounts> result = computeMonthData(history, month, collectingSince);
        cache.put(month, result);
        return result;
    }

    private static Map<String, BranchCounts> computeMonthData(InsightsHistory history, YearMonth month,
                                                              LocalDate collectingSince) {
        if (collectingSince == null || collectingSince.isAfter(month.atEndOfMonth())) {
            return null;
        }
        Map<String, BranchCounts> compacted = history.branchMonths().get(month);
        if (compacted != null) {
            return compacted;
        }
        LocalDate from = collectingSince.isAfter(month.atDay(1)) ? collectingSince : month.atDay(1);
        LocalDate end = month.atEndOfMonth().plusDays(1);
        SortedMap<LocalDate, Map<String, BranchCounts>> branchDays = history.branchDays().subMap(from, end);
        boolean anyGlobal = history.days().subMap(from, end).values().stream()
            .anyMatch(day -> day != null && day.bookings() != null);
        if (branchDays.isEmpty() && !anyGlobal) {
            return null;
        }
        Map<String, Accumulator> sums = new LinkedHashMap<>();
        for (Map<String, BranchCounts> day : branchDays.values()) {
            if (day == null) {
                continue;
            }
            day.forEach((branchId, counts) -> {
                if (counts != null) {
                    sums.computeIfAbsent(branchId, id -> new Accumulator()).add(counts);
                }
            });
        }
        Map<String, BranchCounts> result = new LinkedHashMap<>();
        sums.forEach((branchId, sum) -> result.put(branchId, sum.toCounts()));
        return result;
    }

    /** Days of a compacted month without a global day row (§5.8) / fehlende Tage. */
    static int missingDays(YearMonth month, LocalDate collectingSince,
                           SortedMap<LocalDate, InsightsDay> days) {
        LocalDate from = collectingSince.isAfter(month.atDay(1)) ? collectingSince : month.atDay(1);
        int missing = 0;
        for (LocalDate d = from; !d.isAfter(month.atEndOfMonth()); d = d.plusDays(1)) {
            InsightsDay day = days.get(d);
            if (day == null || day.bookings() == null) {
                missing++;
            }
        }
        return missing;
    }

    /** Null-tolerant sum of branch counters — {@code null} only while every day was null. */
    private static final class Accumulator {
        private Long bookings;
        private Long serviceViews;
        private Map<String, Long> groups;
        private Long mobility;
        private Long offHours;
        private Long slotLate;

        void add(BranchCounts c) {
            bookings = plus(bookings, c.bookings());
            serviceViews = plus(serviceViews, c.serviceViews());
            mobility = plus(mobility, c.mobility());
            offHours = plus(offHours, c.offHours());
            slotLate = plus(slotLate, c.slotLate());
            if (c.groups() != null) {
                if (groups == null) {
                    groups = new LinkedHashMap<>();
                }
                c.groups().forEach((key, value) -> groups.merge(key, nn(value), Long::sum));
            }
        }

        private static Long plus(Long sum, Long value) {
            if (value == null) {
                return sum;
            }
            return (sum == null ? 0L : sum) + nn(value);
        }

        BranchCounts toCounts() {
            return new BranchCounts(bookings, serviceViews,
                groups == null ? null : Collections.unmodifiableMap(groups), mobility, offHours, slotLate);
        }
    }
}
