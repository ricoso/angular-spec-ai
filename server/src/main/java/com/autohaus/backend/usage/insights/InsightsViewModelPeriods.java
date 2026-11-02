package com.autohaus.backend.usage.insights;

import com.autohaus.backend.usage.insights.InsightsViewModelSuppression.Node;
import com.autohaus.backend.usage.insights.InsightsViewModelSuppression.Rule;

import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import static com.autohaus.backend.usage.insights.InsightsViewModel.N_SHARE_MIN;
import static com.autohaus.backend.usage.insights.InsightsViewModelSuppression.countCell;
import static com.autohaus.backend.usage.insights.InsightsViewModelSuppression.hiddenCell;
import static com.autohaus.backend.usage.insights.InsightsViewModelSuppression.percent;
import static com.autohaus.backend.usage.insights.InsightsViewModelSuppression.shareState;

/**
 * Global metrics of one display period — G-1, T-1, T-2, T-3, T-5 (REQ-038 §10.5, BR-10) /
 * globale Kennzahlen eines Anzeigezeitraums. Package-private part of
 * {@link InsightsViewModelBuilder}; no location dimension ever enters here (NFR-Datenschutz-5). /
 * Paket-privater Teil des Builders, ohne Standort-Dimension.
 *
 * <p><b>Counting / Zählweise:</b> a day counts when it lies in {@code [max(from, collectingSince),
 * to]} and carries a {@code bookings} counter; days before the collection start are never read
 * (BR-19). A table whose source maps are missing on every counted day is {@code NO_DATA} („—"),
 * never 0. T-1 rows of a weekday without any counted day are {@code NO_DATA} as well (thin state
 * of the mockup). / Tage vor dem Erfassungsbeginn werden nie gelesen; fehlende Quellen sind „—".
 *
 * <p><b>Suppression / Unterdrückung:</b> every counter follows BR-9 Nr. 1; T-1, T-3 (plus
 * „ohne Terminzeit") and T-5 each form a {@link Rule#SUM} constraint against the period bookings
 * (BR-9 Nr. 4 a). A visible T-2 tile additionally makes the off-hours and the business-hours part
 * of T-1 derivable, so both regions are constrained as well — otherwise one hidden cell per region
 * could be recomputed from the tile. G-1 counts mentions (not additive) and has no sum rule
 * (BR-9 Nr. 4, last bullet). Heat levels of T-1/T-3 are relative to the largest <b>visible</b>
 * cell ({@link HeatLegend}); hidden cells and 0 stay level 0 (§10.5). / Die T-2-Kachel macht die
 * beiden T-1-Bereiche ableitbar, daher je eine eigene Summenbedingung.
 */
final class InsightsViewModelPeriods {

    /** §17 {@code PERIOD_MONTH} without the range (the renderer appends it) / Beschriftung. */
    static final String PERIOD_MONTH = "aktueller Monat bis gestern";

    /** §17 {@code PERIOD_YEAR} without the range / Beschriftung. */
    static final String PERIOD_YEAR = "laufendes Jahr bis gestern";

    /** G-3 rental-car key inside {@link Period#groups()} (§17 {@code MOBILITY_NOTE}) / Mietwagen. */
    static final String MOBILITY_KEY = "mobility";

    /** Weekday keys {@code MO..SU} (T-1 rows, T-3 rows) / Wochentagsschlüssel. */
    static final List<String> WEEKDAYS = Arrays.stream(SlotWeekday.values())
        .filter(w -> w != SlotWeekday.UNKNOWN).map(SlotWeekday::key).toList();

    /** Daypart keys of the booking moment (T-1 columns) / Tagesabschnitte der Buchung. */
    static final List<String> BOOKING_DAYPARTS = Arrays.stream(BookingDaypart.values())
        .map(BookingDaypart::key).toList();

    /** Slot daypart keys {@code early..late} (T-3 columns) / Tagesabschnitte des Termins. */
    static final List<String> SLOT_DAYPARTS = Arrays.stream(SlotDaypart.values())
        .filter(p -> p != SlotDaypart.UNKNOWN).map(SlotDaypart::key).toList();

    /** Lead-time keys incl. {@code unknown} (T-5) / Vorlauf-Schlüssel. */
    static final List<String> LEAD_TIMES = Arrays.stream(LeadTimeBucket.values())
        .map(LeadTimeBucket::key).toList();

    /** G-1 keys in catalogue order / Servicegruppen in Katalog-Reihenfolge. */
    static final List<String> GROUPS = Arrays.stream(ServiceGroup.values())
        .map(ServiceGroup::key).toList();

    /** REQ-042 (G-4): entry-variant keys in display order / Einstiegs-Varianten. */
    static final List<String> VARIANTS = List.of(
        UsageEventLogger.VARIANT_DEFAULT, UsageEventLogger.VARIANT_TIRE_CHANGE, UsageEventLogger.VARIANT_RECALL);

    /** Index of Saturday in {@link #WEEKDAYS} — Sa/So count fully as off-hours (T-2). */
    private static final int SATURDAY = 5;

    /** Index of Sunday in {@link #WEEKDAYS} — its T-3 row is shown only when needed. */
    private static final int SUNDAY = 6;

    private InsightsViewModelPeriods() {
    }

    /**
     * Builds one period / baut einen Zeitraum.
     *
     * @param label {@link #PERIOD_MONTH} or {@link #PERIOD_YEAR}
     * @param from first day of the nominal period / erster Tag des Zeitraums
     * @param to last day („gestern") / letzter Tag
     * @param collectingSince first counted day of K2, {@code null} = never collected
     * @param days global day rows / globale Tageszeilen
     * @param collator tie-break order of the suppression engine / Sortierung für Gleichstand
     */
    static Period build(String label, LocalDate from, LocalDate to, LocalDate collectingSince,
                        SortedMap<LocalDate, InsightsDay> days, Collator collator) {
        LocalDate start = collectingSince == null ? null
            : collectingSince.isAfter(from) ? collectingSince : from;
        boolean hasRange = start != null && !start.isAfter(to);
        String range = InsightsViewModelBuilder.rangeLabel(hasRange ? start : from, to);
        if (!hasRange) {
            return noData(label, range);
        }
        Aggregate a = new Aggregate();
        for (Map.Entry<LocalDate, InsightsDay> entry : days.subMap(start, to.plusDays(1)).entrySet()) {
            InsightsDay day = entry.getValue();
            if (day != null && day.bookings() != null) {
                a.add(entry.getKey(), day);
            }
        }
        if (a.days == 0) {
            return noData(label, range);
        }
        return a.toPeriod(label, range, collator);
    }

    /** A period without any counted day — every cell „—", never 0 (BR-19) / Zeitraum ohne Daten. */
    static Period noData(String label, String range) {
        Map<String, Cell> groups = new LinkedHashMap<>();
        GROUPS.forEach(key -> groups.put(key, Cell.noData()));
        groups.put(MOBILITY_KEY, Cell.noData());
        Map<String, Cell> lead = new LinkedHashMap<>();
        LEAD_TIMES.forEach(key -> lead.put(key, Cell.noData()));
        Map<String, Cell> variants = new LinkedHashMap<>();
        VARIANTS.forEach(key -> variants.put(key, Cell.noData()));
        return new Period(label, range, Cell.noData(), Collections.unmodifiableMap(groups),
            noDataGrid(WEEKDAYS, BOOKING_DAYPARTS), noDataGrid(WEEKDAYS.subList(0, SUNDAY), SLOT_DAYPARTS),
            Collections.unmodifiableMap(lead), Cell.noData(), null, null, null,
            Collections.unmodifiableMap(variants));
    }

    private static Map<String, Map<String, Cell>> noDataGrid(List<String> rows, List<String> columns) {
        Map<String, Map<String, Cell>> grid = new LinkedHashMap<>();
        for (String row : rows) {
            Map<String, Cell> cells = new LinkedHashMap<>();
            columns.forEach(column -> cells.put(column, Cell.noData()));
            grid.put(row, Collections.unmodifiableMap(cells));
        }
        return Collections.unmodifiableMap(grid);
    }

    /** Null-safe, never negative / null-sicher, nie negativ. */
    static long nn(Long value) {
        return value == null || value < 0 ? 0L : value;
    }

    /** Sums of the counted days of one period / Summen der gezählten Tage. */
    private static final class Aggregate {
        private int days;
        private long bookings;
        private final long[][] bookedWhen = new long[WEEKDAYS.size()][BOOKING_DAYPARTS.size()];
        private final boolean[] bookedWhenRow = new boolean[WEEKDAYS.size()];
        private boolean anyBookedWhen;
        private long offHours;
        private final long[][] slots = new long[WEEKDAYS.size()][SLOT_DAYPARTS.size()];
        private boolean anySlots;
        private long slotUnknown;
        private final long[] lead = new long[LEAD_TIMES.size()];
        private boolean anyLead;
        private final long[] groups = new long[GROUPS.size()];
        private boolean anyGroups;
        private long groupBookings;
        private long mobility;
        private boolean anyMobility;
        private long mobilityBookings;
        // REQ-042 (G-4): entry-variant distribution, additive to bookings.
        private final long[] variants = new long[VARIANTS.size()];
        private boolean anyVariants;
        private long variantBookings;

        void add(LocalDate date, InsightsDay day) {
            days++;
            long dayBookings = nn(day.bookings());
            bookings += dayBookings;
            int weekday = date.getDayOfWeek().getValue() - 1;
            if (day.bookingDaypart() != null) {
                anyBookedWhen = true;
                bookedWhenRow[weekday] = true;
                for (int p = 0; p < BOOKING_DAYPARTS.size(); p++) {
                    long v = nn(day.bookingDaypart().get(BOOKING_DAYPARTS.get(p)));
                    bookedWhen[weekday][p] += v;
                    if (isOffHours(weekday, p)) {
                        offHours += v;
                    }
                }
            }
            if (day.slotGrid() != null) {
                anySlots = true;
                for (int w = 0; w < WEEKDAYS.size(); w++) {
                    Map<String, Long> row = day.slotGrid().get(WEEKDAYS.get(w));
                    if (row == null) {
                        continue;
                    }
                    for (int p = 0; p < SLOT_DAYPARTS.size(); p++) {
                        slots[w][p] += nn(row.get(SLOT_DAYPARTS.get(p)));
                    }
                }
            }
            slotUnknown += nn(day.slotUnknown());
            if (day.leadTime() != null) {
                anyLead = true;
                for (int i = 0; i < LEAD_TIMES.size(); i++) {
                    lead[i] += nn(day.leadTime().get(LEAD_TIMES.get(i)));
                }
            }
            if (day.groups() != null) {
                anyGroups = true;
                groupBookings += dayBookings;
                for (int i = 0; i < GROUPS.size(); i++) {
                    groups[i] += nn(day.groups().get(GROUPS.get(i)));
                }
            }
            if (day.mobility() != null) {
                anyMobility = true;
                mobilityBookings += dayBookings;
                mobility += nn(day.mobility());
            }
            if (day.variants() != null) {
                anyVariants = true;
                variantBookings += dayBookings;
                for (int i = 0; i < VARIANTS.size(); i++) {
                    variants[i] += nn(day.variants().get(VARIANTS.get(i)));
                }
            }
        }

        /** Outside Mo–Fr 07–18 Uhr: Sa/So completely, Mo–Fr evening and night (T-2, BR-12). */
        private static boolean isOffHours(int weekday, int daypart) {
            return weekday >= SATURDAY || daypart > 0;
        }

        Period toPeriod(String label, String range, Collator collator) {
            InsightsViewModelSuppression engine = new InsightsViewModelSuppression(collator);
            Node bookingsNode = engine.count("bookings", bookings);

            // T-1 + regions derivable from a visible T-2 tile / T-1 und die beiden Bereiche.
            Node[][] t1 = new Node[WEEKDAYS.size()][BOOKING_DAYPARTS.size()];
            long t1Total = 0;
            List<Node> t1Parts = new ArrayList<>();
            List<Node> offParts = new ArrayList<>();
            List<Node> onParts = new ArrayList<>();
            if (anyBookedWhen) {
                for (int w = 0; w < WEEKDAYS.size(); w++) {
                    if (!bookedWhenRow[w]) {
                        continue;
                    }
                    for (int p = 0; p < BOOKING_DAYPARTS.size(); p++) {
                        Node node = engine.count(WEEKDAYS.get(w) + "/" + BOOKING_DAYPARTS.get(p), bookedWhen[w][p]);
                        t1[w][p] = node;
                        t1Total += bookedWhen[w][p];
                        t1Parts.add(node);
                        (isOffHours(w, p) ? offParts : onParts).add(node);
                    }
                }
                engine.constrain(Rule.SUM, null, t1Parts);
            }
            CellState offHoursState = anyBookedWhen ? shareState(offHours, t1Total) : CellState.NO_DATA;
            if (offHoursState == CellState.VALUE) {
                engine.constrain(Rule.SUM, null, offParts);
                engine.constrain(Rule.SUM, null, onParts);
            }

            // T-3 + „ohne Terminzeit" (E12) / Terminzeiten.
            Node[][] t3 = new Node[WEEKDAYS.size()][SLOT_DAYPARTS.size()];
            Node slotUnknownNode = engine.countLastResort("slotUnknown", slotUnknown);
            if (anySlots) {
                List<Node> t3Parts = new ArrayList<>();
                for (int w = 0; w < WEEKDAYS.size(); w++) {
                    for (int p = 0; p < SLOT_DAYPARTS.size(); p++) {
                        t3[w][p] = engine.count(WEEKDAYS.get(w) + "/" + SLOT_DAYPARTS.get(p), slots[w][p]);
                        t3Parts.add(t3[w][p]);
                    }
                }
                t3Parts.add(slotUnknownNode);
                engine.constrain(Rule.SUM, null, t3Parts);
            }

            // T-5 / Vorlauf.
            Node[] leadNodes = new Node[LEAD_TIMES.size()];
            long leadTotal = 0;
            if (anyLead) {
                for (int i = 0; i < LEAD_TIMES.size(); i++) {
                    leadNodes[i] = engine.count(LEAD_TIMES.get(i), lead[i]);
                    leadTotal += lead[i];
                }
                engine.constrain(Rule.SUM, null, Arrays.asList(leadNodes));
            }

            // G-1 (mentions, not additive — no sum rule) / Nennungen ohne Summenregel.
            Node[] groupNodes = new Node[GROUPS.size()];
            for (int i = 0; i < GROUPS.size() && anyGroups; i++) {
                groupNodes[i] = engine.count(GROUPS.get(i), groups[i]);
            }
            Node mobilityNode = anyMobility ? engine.count(MOBILITY_KEY, mobility) : null;

            // G-4 (additive — each booking has exactly one entry variant) / Einstiegs-Variante.
            Node[] variantNodes = new Node[VARIANTS.size()];
            if (anyVariants) {
                for (int i = 0; i < VARIANTS.size(); i++) {
                    variantNodes[i] = engine.count("variant/" + VARIANTS.get(i), variants[i]);
                }
                engine.constrain(Rule.SUM, null, Arrays.asList(variantNodes));
            }

            engine.solve();

            Map<String, Map<String, Cell>> bookedWhenCells = new LinkedHashMap<>();
            HeatLegend bookedWhenLegend = legend(t1);
            for (int w = 0; w < WEEKDAYS.size(); w++) {
                Map<String, Cell> row = new LinkedHashMap<>();
                for (int p = 0; p < BOOKING_DAYPARTS.size(); p++) {
                    row.put(BOOKING_DAYPARTS.get(p), heatCell(t1[w][p], bookedWhenLegend));
                }
                bookedWhenCells.put(WEEKDAYS.get(w), Collections.unmodifiableMap(row));
            }

            boolean sundayNeeded = Arrays.stream(slots[SUNDAY]).sum() > 0;
            int t3Rows = sundayNeeded ? WEEKDAYS.size() : SUNDAY;
            Node[][] shownT3 = Arrays.copyOf(t3, t3Rows);
            HeatLegend slotLegend = legend(shownT3);
            Map<String, Map<String, Cell>> slotCells = new LinkedHashMap<>();
            for (int w = 0; w < t3Rows; w++) {
                Map<String, Cell> row = new LinkedHashMap<>();
                for (int p = 0; p < SLOT_DAYPARTS.size(); p++) {
                    row.put(SLOT_DAYPARTS.get(p), heatCell(t3[w][p], slotLegend));
                }
                slotCells.put(WEEKDAYS.get(w), Collections.unmodifiableMap(row));
            }

            Map<String, Cell> leadCells = new LinkedHashMap<>();
            for (int i = 0; i < LEAD_TIMES.size(); i++) {
                leadCells.put(LEAD_TIMES.get(i), anyLead ? countWithShare(leadNodes[i], leadTotal) : Cell.noData());
            }

            Map<String, Cell> groupCells = new LinkedHashMap<>();
            for (int i = 0; i < GROUPS.size(); i++) {
                groupCells.put(GROUPS.get(i), anyGroups ? countWithShare(groupNodes[i], groupBookings) : Cell.noData());
            }
            groupCells.put(MOBILITY_KEY, anyMobility ? countWithShare(mobilityNode, mobilityBookings) : Cell.noData());

            Map<String, Cell> variantCells = new LinkedHashMap<>();
            for (int i = 0; i < VARIANTS.size(); i++) {
                variantCells.put(VARIANTS.get(i),
                    anyVariants ? countWithShare(variantNodes[i], variantBookings) : Cell.noData());
            }

            Cell offHoursShare = offHoursState == CellState.VALUE
                ? Cell.percentOf(percent(offHours, t1Total, 0)) : hiddenCell(offHoursState);
            Cell slotUnknownCell = slotUnknown == 0 ? null : countCell(slotUnknownNode);

            return new Period(label, range, countCell(bookingsNode),
                Collections.unmodifiableMap(groupCells),
                Collections.unmodifiableMap(bookedWhenCells),
                Collections.unmodifiableMap(slotCells),
                Collections.unmodifiableMap(leadCells),
                offHoursShare, slotUnknownCell, bookedWhenLegend, slotLegend,
                Collections.unmodifiableMap(variantCells));
        }
    }

    /** Relative legend over the visible cells &gt; 0, {@code null} when there is none (§10.5). */
    private static HeatLegend legend(Node[][] grid) {
        long max = 0;
        for (Node[] row : grid) {
            for (Node node : row) {
                if (node != null && node.visible() && node.value() > max) {
                    max = node.value();
                }
            }
        }
        return max > 0 ? HeatLegend.ofMax(max) : null;
    }

    /** A heat cell — level only for visible values &gt; 0 / Heat-Zelle. */
    private static Cell heatCell(Node node, HeatLegend legend) {
        if (node == null) {
            return Cell.noData();
        }
        if (!node.visible()) {
            return hiddenCell(node.state());
        }
        return Cell.of(node.value(), legend == null ? 0 : legend.levelOf(node.value()));
    }

    /**
     * Counter plus one-decimal share of the period (G-1, T-5, §10.5); below 20 cases only the
     * counter (mockup thin state) / Zähler plus Anteil, unter 20 Fällen nur der Zähler.
     */
    private static Cell countWithShare(Node node, long denominator) {
        if (node == null) {
            return Cell.noData();
        }
        if (!node.visible()) {
            return hiddenCell(node.state());
        }
        Double share = denominator >= N_SHARE_MIN ? percent(node.value(), denominator, 1) : null;
        return new Cell(node.value(), null, share, 0, CellState.VALUE);
    }
}
