package com.autohaus.backend.usage.insights;

import com.autohaus.backend.usage.insights.InsightsViewModelSuppression.Node;
import com.autohaus.backend.usage.insights.InsightsViewModelSuppression.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import static com.autohaus.backend.usage.insights.InsightsViewModel.MIN_DROPOFFS_DEVICE;
import static com.autohaus.backend.usage.insights.InsightsViewModel.MIN_SESSIONS_DEVICE;
import static com.autohaus.backend.usage.insights.InsightsViewModel.N_SHARE_MIN;
import static com.autohaus.backend.usage.insights.InsightsViewModelPeriods.nn;
import static com.autohaus.backend.usage.insights.InsightsViewModelSuppression.countCell;
import static com.autohaus.backend.usage.insights.InsightsViewModelSuppression.percent;

/**
 * S6 — global funnel F-1..F-3 and drop-off scenarios F-4 (REQ-038 BR-8, BR-9 Nr. 7, BR-27,
 * BR-31, BR-32, §10.5) / globaler Trichter und Absprungszenarien. Package-private part of
 * {@link InsightsViewModelBuilder}; never combined with a location (BR-7, BR-28). / Nie mit einem
 * Standort verbunden.
 *
 * <p><b>Period / Zeitraum:</b> deliberately <b>„aktueller Monat bis gestern"</b> — the funnel is a
 * location-free metric (BR-10) and one month keeps the tables readable; the year view would repeat
 * the same shape with larger numbers (mockup note). Counted are the days from
 * {@code max(1st of the month, funnelCollectingSince)} to {@code asOf} whose cumulative counters are
 * complete and monotone; the drop-off tables use the same days from
 * {@code dropoffCollectingSince} on. / Bewusst der laufende Monat bis gestern.
 *
 * <p><b>Suppression / Unterdrückung:</b> all counters follow BR-9 Nr. 1. Because
 * {@code erreicht(k) = erreicht(k+1) + Ausstieg(k)}, every stage pair forms a {@link Rule#SUM}
 * constraint, so a hidden stage can never be recomputed from its neighbours or from „Ausstieg";
 * the percentages are {@code WITHHELD} as soon as the stage or its base is hidden and „n &lt; 20"
 * below 20 sessions (BR-9 Nr. 2, 5). Device columns follow BR-9 Nr. 7 (classes below
 * {@value InsightsViewModel#MIN_SESSIONS_DEVICE} sessions → „Sonstige"; classes without any session
 * are left out, they hide nothing), each F-3 row is a {@link Rule#SUM} constraint with „Alle".
 * The drop-off tables apply BR-31: row rule against „Gesamt", column rule ({@link Rule#MARGIN})
 * against the sum row, device columns below {@value InsightsViewModel#MIN_DROPOFFS_DEVICE}
 * drop-offs → „Sonstige". When the drop-off days equal the funnel days, the sum row <b>is</b>
 * „Ausstieg nach Stufe" (AC-33) — both share one node, so a cell hidden in one table is hidden in
 * the other. / Summenzeile und Ausstieg teilen sich denselben Knoten.
 *
 * <p><b>Not applicable / nicht anwendbar:</b> stage 1 has no {@code percentOfPrevious} and stage 5
 * („Gebucht") no {@code droppedHere} — both are {@code null}; {@link FunnelView#period()} carries
 * only {@code label} and {@code rangeLabel} (its metric fields are empty). / Stufe 1 ohne Vorstufe,
 * Stufe 5 ohne Ausstieg; der Zeitraum trägt nur Beschriftungen.
 *
 * <p><b>Order / Reihenfolge:</b> scenario rows with a visible total are sorted descending, ties in
 * catalogue order (§8); rows with a hidden total follow in catalogue order — sorting by a hidden
 * value would betray it (AC-20); the aggregated A-0 row „quiet" is always last. Rows without any
 * drop-off are omitted. / Ausgeblendete Summen nie nach Wert sortiert.
 *
 * <p>The scenario × device margin is read from {@link DropoffDay#byScenarioDevice()} (scenario →
 * device class → sessions, §10.6); a day without that margin or with a foreign device class leaves
 * the device table out. / Ohne lesbare Geräte-Randverteilung entfällt die Geräte-Tabelle.
 */
final class InsightsViewModelFunnel {

    private static final Logger log = LoggerFactory.getLogger(InsightsViewModelFunnel.class);

    /** Stage keys in funnel order (BR-8) / Stufenschlüssel. */
    static final List<String> STAGE_KEYS = List.of("visits", "services", "notes", "search", "booking");

    /** Device classes in display order (REQ-037) / Geräteklassen in Anzeigereihenfolge. */
    static final List<String> DEVICE_CLASSES = List.of("mobile", "tablet", "desktop", "unknown");

    /** Column key of „Sonstige ({classes})" (§16) / Spaltenschlüssel „Sonstige". */
    static final String OTHER_DEVICES = "other";

    /** Scenario key of the aggregated A-0 row / Schlüssel der A-0-Sammelzeile. */
    static final String QUIET_SCENARIO = "quiet";

    /** Scenario key of the sum row {@link DropoffView#total()} / Schlüssel der Summenzeile. */
    static final String TOTAL_ROW = "total";

    // ---- §17 SC_*_HINT and HINT_ARTIFACT (BR-32): the single source of the fixed action hints,
    // package-visible so that FunnelSectionRenderer references them instead of own copies; texts
    // from the scenario catalogue §8 without the code references. / Einzige Quelle der festen
    // Handlungsimpulse, paket-sichtbar für den Renderer; Texte aus dem Szenario-Katalog §8. ----

    /** A-1 (§8 Handlungsimpuls) / Handlungsimpuls. */
    static final String SC_SERVER_ERROR_HINT = "Bugs-Tab prüfen";
    /** A-2. */
    static final String SC_ACCESS_DENIED_HINT = "Session-TTL und Rate-Limit prüfen";
    /** A-3 (§19 Frage 15). */
    static final String SC_SLOT_EXPIRED_HINT =
        "„Erneut versuchen“ sendet denselben Snap – Sackgasse, eigenes Bugfix-REQ";
    /** A-4. */
    static final String SC_SLOT_CONFLICT_HINT = "hohe Nachfrage bzw. langes Formular";
    /** A-5. */
    static final String SC_BOOKING_REJECTED_HINT = "Eingaben bzw. SoftNet-Validierung prüfen";
    /** A-6. */
    static final String SC_SEARCH_REJECTED_HINT = "Leistungszuordnung prüfen";
    /** A-7. */
    static final String SC_ERROR_THEN_QUIT_HINT =
        "wie „Technischer Fehler“; der Fehler hat die Sitzung nachhaltig gestört";
    /** A-8 (plus {@link #HINT_ARTIFACT}, BR-32). */
    static final String SC_REQUESTS_LOST_HINT = "Netz bzw. Circuit Breaker";
    /** A-9. */
    static final String SC_NO_SLOTS_HINT = "Kapazität bzw. Terminangebot, zusammen mit S1 lesen";
    /** A-10. */
    static final String SC_FEW_SLOTS_HINT = "Terminangebot zu schmal";
    /** A-11. */
    static final String SC_SLOW_HINT = "Performance bzw. SoftNet";
    /** A-12. */
    static final String SC_MANY_SEARCHES_HINT = "Termine passen nicht";
    /** A-0 (plus {@link #HINT_ARTIFACT}, BR-32; „Folge-REQ 1" = Step-Beacon, §8). */
    static final String SC_QUIET_HINT =
        "Stufe 4: Formular bzw. Übersicht; genauer nur mit dem Step-Beacon (Folge-REQ 1)";
    /** §17 {@code HINT_ARTIFACT} — appended for A-0 and A-8 (BR-32) / Messartefakt. */
    static final String HINT_ARTIFACT = "Messartefakt möglich";

    /** Number of funnel stages / Anzahl Trichterstufen. */
    private static final int STAGES = STAGE_KEYS.size();

    /** Drop-off stages 1–4 / Abbruchstufen. */
    private static final int DROP_STAGES = STAGES - 1;

    /** Row keys: the twelve primary scenarios in catalogue order plus „quiet" / Zeilenschlüssel. */
    private static final List<String> ROW_KEYS;

    /** Index of the aggregated A-0 row / Index der A-0-Zeile. */
    private static final int QUIET_ROW;

    static {
        List<String> keys = new ArrayList<>();
        for (DropoffScenario scenario : DropoffScenario.values()) {
            if (scenario.ordinal() < DropoffScenario.QUIET_1.ordinal()) {
                keys.add(scenario.key());
            }
        }
        QUIET_ROW = keys.size();
        keys.add(QUIET_SCENARIO);
        ROW_KEYS = List.copyOf(keys);
    }

    private InsightsViewModelFunnel() {
    }

    /**
     * Builds S6 / baut S6.
     *
     * @param days global day rows / globale Tageszeilen
     * @param asOf „gestern" / Stichtag
     * @param funnelSince first counted funnel day, {@code null} = never collected
     * @param dropoffEnabled {@code usage.dropoff.enabled} (N1)
     * @param dropoffSince first counted drop-off day, {@code null} = never collected
     * @param collator tie-break order / Sortierung für Gleichstand
     */
    static FunnelView build(SortedMap<LocalDate, InsightsDay> days, LocalDate asOf, LocalDate funnelSince,
                            boolean dropoffEnabled, LocalDate dropoffSince, Collator collator) {
        LocalDate monthStart = asOf.withDayOfMonth(1);
        LocalDate start = funnelSince == null ? null : funnelSince.isAfter(monthStart) ? funnelSince : monthStart;
        boolean hasRange = start != null && !start.isAfter(asOf);
        Period period = new Period(InsightsViewModelPeriods.PERIOD_MONTH,
            InsightsViewModelBuilder.rangeLabel(hasRange ? start : monthStart, asOf),
            null, Map.of(), Map.of(), Map.of(), Map.of(), null, null, null, null);

        long[] all = new long[STAGES];
        long[][] byClass = new long[DEVICE_CLASSES.size()][STAGES];
        boolean devicesOk = true;
        List<LocalDate> funnelDays = new ArrayList<>();
        if (hasRange) {
            for (Map.Entry<LocalDate, InsightsDay> entry : days.subMap(start, asOf.plusDays(1)).entrySet()) {
                FunnelDay funnel = entry.getValue() == null ? null : entry.getValue().funnel();
                long[] counts = funnel == null ? null : counts(funnel.all());
                if (counts == null) {
                    continue;
                }
                funnelDays.add(entry.getKey());
                add(all, counts);
                devicesOk &= addDevices(funnel.byDevice(), counts, byClass);
            }
        }
        DropoffView emptyDropoff = dropoffEnabled ? emptyDropoff(dropoffSince) : null;
        if (funnelDays.isEmpty()) {
            return new FunnelView(period, noDataStages(), List.of(), List.of(), Map.of(), emptyDropoff);
        }

        InsightsViewModelSuppression engine = new InsightsViewModelSuppression(collator);
        Node[] reached = new Node[STAGES];
        for (int k = 0; k < STAGES; k++) {
            reached[k] = engine.count(STAGE_KEYS.get(k), all[k]);
        }
        Node[] dropped = new Node[DROP_STAGES];
        for (int k = 0; k < DROP_STAGES; k++) {
            dropped[k] = engine.count("dropped/" + (k + 1), all[k] - all[k + 1]);
            engine.constrain(Rule.SUM, reached[k], List.of(reached[k + 1], dropped[k]));
        }

        // F-3 device columns (BR-9 Nr. 7) / Geräteklassen.
        Grouping funnelGrouping = Grouping.empty();
        Map<String, Node[]> deviceNodes = new LinkedHashMap<>();
        if (devicesOk) {
            long[] visits = new long[DEVICE_CLASSES.size()];
            for (int c = 0; c < DEVICE_CLASSES.size(); c++) {
                visits[c] = byClass[c][0];
            }
            funnelGrouping = Grouping.of(visits, MIN_SESSIONS_DEVICE);
            for (String column : funnelGrouping.columns()) {
                Node[] cells = new Node[DROP_STAGES];
                for (int k = 0; k < DROP_STAGES; k++) {
                    long value = 0;
                    for (int c : funnelGrouping.members(column)) {
                        value += byClass[c][k] - byClass[c][k + 1];
                    }
                    cells[k] = engine.count(column + "/" + (k + 1), value);
                }
                deviceNodes.put(column, cells);
            }
            for (int k = 0; k < DROP_STAGES && !deviceNodes.isEmpty(); k++) {
                List<Node> row = new ArrayList<>();
                for (Node[] cells : deviceNodes.values()) {
                    row.add(cells[k]);
                }
                engine.constrain(Rule.SUM, dropped[k], row);
            }
        }

        DropoffDraft dropoff = null;
        if (dropoffEnabled) {
            dropoff = dropoffDraft(days, funnelDays, monthStart, dropoffSince, engine, reached, dropped,
                funnelGrouping, deviceNodes);
        }

        engine.solve();

        List<FunnelStage> stages = new ArrayList<>();
        for (int k = 0; k < STAGES; k++) {
            stages.add(new FunnelStage(STAGE_KEYS.get(k), countCell(reached[k]),
                percentCell(reached[k], reached[0]),
                k == 0 ? null : percentCell(reached[k], reached[k - 1]),
                k < DROP_STAGES ? countCell(dropped[k]) : null));
        }
        Map<String, List<Cell>> byDevice = new LinkedHashMap<>();
        deviceNodes.forEach((column, cells) ->
            byDevice.put(column, Arrays.stream(cells).map(InsightsViewModelSuppression::countCell).toList()));
        DropoffView dropoffView = dropoff == null ? emptyDropoff : dropoff.finish(dropoffSince);
        return new FunnelView(period, List.copyOf(stages), funnelGrouping.columns(),
            funnelGrouping.otherKeys(), Collections.unmodifiableMap(byDevice), dropoffView);
    }

    // ---- funnel helpers / Trichter-Helfer ----

    /** The five cumulative counters, {@code null} when incomplete or not monotone / kumuliert. */
    static long[] counts(FunnelCounts c) {
        if (c == null || c.visits() == null || c.services() == null || c.notes() == null
            || c.search() == null || c.booking() == null) {
            return null;
        }
        long[] v = {c.visits(), c.services(), c.notes(), c.search(), c.booking()};
        for (int k = 0; k < STAGES; k++) {
            if (v[k] < 0 || (k > 0 && v[k] > v[k - 1])) {
                return null;
            }
        }
        return v;
    }

    private static void add(long[] sum, long[] values) {
        for (int k = 0; k < sum.length; k++) {
            sum[k] += values[k];
        }
    }

    /**
     * Adds one day's device counters when they are complete and add up to „Alle" — otherwise the
     * whole breakdown is dropped (only „Alle" remains), never a column that does not add up /
     * übernimmt die Geräte-Zähler nur, wenn sie „Alle" ergeben.
     */
    private static boolean addDevices(Map<String, FunnelCounts> byDevice, long[] all, long[][] target) {
        if (byDevice == null || !DEVICE_CLASSES.containsAll(byDevice.keySet())) {
            return false;
        }
        long[][] day = new long[DEVICE_CLASSES.size()][];
        long[] sum = new long[STAGES];
        for (int c = 0; c < DEVICE_CLASSES.size(); c++) {
            FunnelCounts fc = byDevice.get(DEVICE_CLASSES.get(c));
            day[c] = fc == null ? new long[STAGES] : counts(fc);
            if (day[c] == null) {
                return false;
            }
            add(sum, day[c]);
        }
        if (!Arrays.equals(sum, all)) {
            return false;
        }
        for (int c = 0; c < DEVICE_CLASSES.size(); c++) {
            add(target[c], day[c]);
        }
        return true;
    }

    /** One-decimal stage percentage (F-1/F-2, §10.5) / Prozent einer Stufe. */
    private static Cell percentCell(Node numerator, Node denominator) {
        if (!numerator.visible() || !denominator.visible()) {
            return Cell.withheld();
        }
        if (denominator.value() < N_SHARE_MIN) {
            return Cell.tooFew();
        }
        return Cell.percentOf(percent(numerator.value(), denominator.value(), 1));
    }

    private static List<FunnelStage> noDataStages() {
        List<FunnelStage> stages = new ArrayList<>();
        for (int k = 0; k < STAGES; k++) {
            stages.add(new FunnelStage(STAGE_KEYS.get(k), Cell.noData(), Cell.noData(),
                k == 0 ? null : Cell.noData(), k < DROP_STAGES ? Cell.noData() : null));
        }
        return List.copyOf(stages);
    }

    // ---- device grouping (BR-9 Nr. 7, BR-31) / Geräteklassen ----

    /**
     * Visible device classes and the classes merged into „Sonstige" / sichtbare und
     * zusammengefasste Klassen.
     *
     * @param visible indexes into {@link #DEVICE_CLASSES}, display order
     * @param other indexes merged into {@link #OTHER_DEVICES}, display order
     */
    record Grouping(List<Integer> visible, List<Integer> other) {

        static Grouping empty() {
            return new Grouping(List.of(), List.of());
        }

        /**
         * Classes below {@code min} → „Sonstige"; while „Sonstige" stays below {@code min} the
         * smallest visible class joins it; no own class left → no breakdown (only „Alle").
         * Classes without any case are left out — they hide nothing. / Zusammenfassung nach
         * BR-9 Nr. 7.
         */
        static Grouping of(long[] totals, long min) {
            List<Integer> visible = new ArrayList<>();
            List<Integer> other = new ArrayList<>();
            long otherSum = 0;
            for (int c = 0; c < totals.length; c++) {
                if (totals[c] >= min) {
                    visible.add(c);
                } else if (totals[c] > 0) {
                    other.add(c);
                    otherSum += totals[c];
                }
            }
            while (!other.isEmpty() && otherSum < min && !visible.isEmpty()) {
                // smallest visible class; on a tie the later class in display order joins
                Integer smallest = visible.stream()
                    .min(Comparator.comparingLong((Integer c) -> totals[c]).thenComparingInt(c -> -c))
                    .orElseThrow();
                visible.remove(smallest);
                other.add(smallest);
                otherSum += totals[smallest];
            }
            if (visible.isEmpty()) {
                return empty();
            }
            Collections.sort(other);
            return new Grouping(List.copyOf(visible), List.copyOf(other));
        }

        /** Column keys: visible classes plus {@code other}; empty = only „Alle" / Spalten. */
        List<String> columns() {
            List<String> columns = new ArrayList<>();
            visible.forEach(c -> columns.add(DEVICE_CLASSES.get(c)));
            if (!other.isEmpty()) {
                columns.add(OTHER_DEVICES);
            }
            return List.copyOf(columns);
        }

        /** Keys of the merged classes (header „Sonstige ({classes})") / zusammengefasste Klassen. */
        List<String> otherKeys() {
            return other.stream().map(DEVICE_CLASSES::get).toList();
        }

        /** Class indexes behind a column key / Klassen hinter einer Spalte. */
        List<Integer> members(String column) {
            return OTHER_DEVICES.equals(column) ? other : List.of(DEVICE_CLASSES.indexOf(column));
        }
    }

    // ---- drop-off scenarios (N1) / Absprungszenarien ----

    /** Drop-off view without counted days — „—" with „Erfassung seit …" (AC-35) / leere Sicht. */
    static DropoffView emptyDropoff(LocalDate collectingSince) {
        List<Cell> noData = List.of(Cell.noData(), Cell.noData(), Cell.noData(), Cell.noData());
        DropoffRow total = new DropoffRow(TOTAL_ROW, null, noData, Cell.noData(), Cell.noData(), Map.of());
        return new DropoffView(collectingSince, List.of(), total, List.of(), List.of());
    }

    /** Solved later; holds the nodes of both drop-off tables / Entwurf beider Tabellen. */
    private record DropoffDraft(Node[][] steps, Node[] totals, Node[] sums, Node grandTotal,
                                Grouping grouping, Map<String, Node[]> deviceCells,
                                Map<String, Node> deviceSums) {

        DropoffView finish(LocalDate collectingSince) {
            List<String> columns = grouping.columns();
            List<Integer> visibleRows = new ArrayList<>();
            List<Integer> hiddenRows = new ArrayList<>();
            for (int r = 0; r < QUIET_ROW; r++) {
                if (totals[r].value() > 0) {
                    (totals[r].visible() ? visibleRows : hiddenRows).add(r);
                }
            }
            visibleRows.sort(Comparator.comparingLong((Integer r) -> -totals[r].value())
                .thenComparingInt(r -> r));
            List<Integer> order = new ArrayList<>(visibleRows);
            order.addAll(hiddenRows);
            if (totals[QUIET_ROW].value() > 0) {
                order.add(QUIET_ROW);
            }
            List<DropoffRow> rows = new ArrayList<>();
            for (int r : order) {
                Map<String, Cell> devices = new LinkedHashMap<>();
                for (String column : columns) {
                    devices.put(column, countCell(deviceCells.get(column)[r]));
                }
                rows.add(new DropoffRow(ROW_KEYS.get(r), hint(r), cells(steps[r]), countCell(totals[r]),
                    share(totals[r], grandTotal), Collections.unmodifiableMap(devices)));
            }
            Map<String, Cell> sumDevices = new LinkedHashMap<>();
            for (String column : columns) {
                sumDevices.put(column, countCell(deviceSums.get(column)));
            }
            Cell totalShare = !grandTotal.visible() ? Cell.withheld()
                : grandTotal.value() < N_SHARE_MIN ? Cell.tooFew() : Cell.percentOf(100.0);
            DropoffRow total = new DropoffRow(TOTAL_ROW, null, cells(sums), countCell(grandTotal),
                totalShare, Collections.unmodifiableMap(sumDevices));
            return new DropoffView(collectingSince, List.copyOf(rows), total, columns, grouping.otherKeys());
        }

        private static List<Cell> cells(Node[] nodes) {
            return Arrays.stream(nodes).map(InsightsViewModelSuppression::countCell).toList();
        }

        /** Share of all drop-offs, one decimal, from 20 drop-offs (BR-31) / Anteil. */
        private static Cell share(Node total, Node grandTotal) {
            if (!grandTotal.visible()) {
                return Cell.withheld();
            }
            if (grandTotal.value() < N_SHARE_MIN) {
                return Cell.tooFew();
            }
            if (!total.visible()) {
                return Cell.withheld();
            }
            return Cell.percentOf(percent(total.value(), grandTotal.value(), 1));
        }
    }

    private static DropoffDraft dropoffDraft(SortedMap<LocalDate, InsightsDay> days,
                                             List<LocalDate> funnelDays, LocalDate monthStart,
                                             LocalDate dropoffSince, InsightsViewModelSuppression engine,
                                             Node[] reached, Node[] dropped, Grouping funnelGrouping,
                                             Map<String, Node[]> funnelDevices) {
        if (dropoffSince == null) {
            return null;
        }
        LocalDate start = dropoffSince.isAfter(monthStart) ? dropoffSince : monthStart;
        int rows = ROW_KEYS.size();
        long[][] steps = new long[rows][DROP_STAGES];
        long[][] devices = new long[rows][DEVICE_CLASSES.size()];
        boolean devicesOk = true;
        int discarded = 0;
        int counted = 0;
        for (LocalDate date : funnelDays) {
            DropoffDay day = date.isBefore(start) ? null : days.get(date).funnel().dropoff();
            if (day == null) {
                continue;
            }
            counted++;
            if (day.byScenarioStep() != null) {
                for (Map.Entry<String, Map<String, Long>> entry : day.byScenarioStep().entrySet()) {
                    DropoffScenario scenario = DropoffScenario.fromKey(entry.getKey());
                    if (scenario == null) {
                        discarded++;
                        continue;
                    }
                    if (entry.getValue() == null) {
                        continue;
                    }
                    int row = rowOf(scenario);
                    for (Map.Entry<String, Long> stage : entry.getValue().entrySet()) {
                        int k = stageIndex(stage.getKey());
                        if (k >= 0) {
                            steps[row][k] += nn(stage.getValue());
                        }
                    }
                }
            }
            if (day.byScenarioDevice() == null) {
                devicesOk = false;
                continue;
            }
            for (Map.Entry<String, Map<String, Long>> entry : day.byScenarioDevice().entrySet()) {
                DropoffScenario scenario = DropoffScenario.fromKey(entry.getKey());
                if (scenario == null) {
                    discarded++;
                    continue;
                }
                if (entry.getValue() == null) {
                    continue;
                }
                for (Map.Entry<String, Long> perDevice : entry.getValue().entrySet()) {
                    int device = DEVICE_CLASSES.indexOf(perDevice.getKey());
                    if (device < 0) {
                        // a foreign device class — the margin cannot be read / nicht lesbar
                        devicesOk = false;
                        continue;
                    }
                    devices[rowOf(scenario)][device] += nn(perDevice.getValue());
                }
            }
        }
        if (discarded > 0) {
            // E11 / TC-63: foreign scenario keys never reach the page; no values logged.
            log.warn("usage insights view: {} drop-off counter(s) with an unknown scenario discarded (E11)",
                discarded);
        }
        if (counted == 0) {
            return null;
        }

        long[] rowTotals = new long[rows];
        long grand = 0;
        long[] stageSums = new long[DROP_STAGES];
        long[] classTotals = new long[DEVICE_CLASSES.size()];
        for (int r = 0; r < rows; r++) {
            rowTotals[r] = Arrays.stream(steps[r]).sum();
            grand += rowTotals[r];
            for (int k = 0; k < DROP_STAGES; k++) {
                stageSums[k] += steps[r][k];
            }
            if (Arrays.stream(devices[r]).sum() != rowTotals[r]) {
                devicesOk = false;
            }
            for (int c = 0; c < DEVICE_CLASSES.size(); c++) {
                classTotals[c] += devices[r][c];
            }
        }

        // Same days and consistent with the funnel → the sum row IS „Ausstieg" (AC-33).
        boolean linked = counted == funnelDays.size() && grand == reached[0].value() - reached[STAGES - 1].value();
        for (int k = 0; k < DROP_STAGES && linked; k++) {
            linked = stageSums[k] == dropped[k].value();
        }

        Node[][] stepNodes = new Node[rows][DROP_STAGES];
        Node[] totalNodes = new Node[rows];
        for (int r = 0; r < rows; r++) {
            for (int k = 0; k < DROP_STAGES; k++) {
                stepNodes[r][k] = engine.count(ROW_KEYS.get(r) + "/" + (k + 1), steps[r][k]);
            }
            totalNodes[r] = engine.count(ROW_KEYS.get(r), rowTotals[r]);
            engine.constrain(Rule.SUM, totalNodes[r], Arrays.asList(stepNodes[r]));
        }
        Node[] sumNodes = new Node[DROP_STAGES];
        for (int k = 0; k < DROP_STAGES; k++) {
            sumNodes[k] = linked ? dropped[k] : engine.count(TOTAL_ROW + "/" + (k + 1), stageSums[k]);
            List<Node> column = new ArrayList<>();
            for (int r = 0; r < rows; r++) {
                column.add(stepNodes[r][k]);
            }
            engine.constrain(Rule.MARGIN, sumNodes[k], column);
        }
        Node grandNode = engine.count(TOTAL_ROW, grand);
        engine.constrain(Rule.MARGIN, grandNode, Arrays.asList(totalNodes));
        engine.constrain(Rule.SUM, grandNode, Arrays.asList(sumNodes));
        if (linked) {
            engine.constrain(Rule.SUM, reached[0], List.of(reached[STAGES - 1], grandNode));
        }

        Grouping grouping = devicesOk ? Grouping.of(classTotals, MIN_DROPOFFS_DEVICE) : Grouping.empty();
        Map<String, Node[]> deviceCells = new LinkedHashMap<>();
        Map<String, Node> deviceSums = new LinkedHashMap<>();
        for (String column : grouping.columns()) {
            Node[] cells = new Node[rows];
            long sum = 0;
            for (int r = 0; r < rows; r++) {
                long value = 0;
                for (int c : grouping.members(column)) {
                    value += devices[r][c];
                }
                cells[r] = engine.count(ROW_KEYS.get(r) + "|" + column, value);
                sum += value;
            }
            Node sumNode = engine.count(TOTAL_ROW + "|" + column, sum);
            engine.constrain(Rule.MARGIN, sumNode, Arrays.asList(cells));
            deviceCells.put(column, cells);
            deviceSums.put(column, sumNode);
            // The same class set in F-3 has the same drop-offs: Σ Ausstieg(column) = sum cell.
            Node[] funnelColumn = linked ? sameColumn(column, grouping, funnelGrouping, funnelDevices) : null;
            if (funnelColumn != null && Arrays.stream(funnelColumn).mapToLong(Node::value).sum() == sum) {
                engine.constrain(Rule.MARGIN, sumNode, Arrays.asList(funnelColumn));
            }
        }
        if (!deviceSums.isEmpty()) {
            for (int r = 0; r < rows; r++) {
                List<Node> row = new ArrayList<>();
                for (Node[] cells : deviceCells.values()) {
                    row.add(cells[r]);
                }
                engine.constrain(Rule.SUM, totalNodes[r], row);
            }
            engine.constrain(Rule.SUM, grandNode, new ArrayList<>(deviceSums.values()));
        }
        return new DropoffDraft(stepNodes, totalNodes, sumNodes, grandNode, grouping, deviceCells, deviceSums);
    }

    /** The F-3 column with exactly the same classes, else {@code null} / gleiche Spalte in F-3. */
    private static Node[] sameColumn(String column, Grouping dropoffGrouping, Grouping funnelGrouping,
                                     Map<String, Node[]> funnelDevices) {
        List<Integer> members = dropoffGrouping.members(column);
        for (Map.Entry<String, Node[]> candidate : funnelDevices.entrySet()) {
            if (funnelGrouping.members(candidate.getKey()).equals(members)) {
                return candidate.getValue();
            }
        }
        return null;
    }

    private static int rowOf(DropoffScenario scenario) {
        return scenario.ordinal() >= DropoffScenario.QUIET_1.ordinal() ? QUIET_ROW : scenario.ordinal();
    }

    /** Stage key {@code "1".."4"} → index, else −1 / Stufenschlüssel. */
    private static int stageIndex(String key) {
        if (key == null || key.length() != 1) {
            return -1;
        }
        int stage = key.charAt(0) - '1';
        return stage >= 0 && stage < DROP_STAGES ? stage : -1;
    }

    /** The fixed action hint of a row (BR-32) / fester Handlungsimpuls einer Zeile. */
    static String hint(int row) {
        if (row == QUIET_ROW) {
            return SC_QUIET_HINT + " – " + HINT_ARTIFACT;
        }
        DropoffScenario scenario = DropoffScenario.values()[row];
        String hint = switch (scenario) {
            case SERVER_ERROR -> SC_SERVER_ERROR_HINT;
            case ACCESS_DENIED -> SC_ACCESS_DENIED_HINT;
            case SLOT_EXPIRED -> SC_SLOT_EXPIRED_HINT;
            case SLOT_CONFLICT -> SC_SLOT_CONFLICT_HINT;
            case BOOKING_REJECTED -> SC_BOOKING_REJECTED_HINT;
            case SEARCH_REJECTED -> SC_SEARCH_REJECTED_HINT;
            case ERROR_THEN_QUIT -> SC_ERROR_THEN_QUIT_HINT;
            case REQUESTS_LOST -> SC_REQUESTS_LOST_HINT;
            case NO_SLOTS -> SC_NO_SLOTS_HINT;
            case FEW_SLOTS -> SC_FEW_SLOTS_HINT;
            case SLOW_RESPONSE -> SC_SLOW_HINT;
            case MANY_SEARCHES -> SC_MANY_SEARCHES_HINT;
            case QUIET_1, QUIET_2, QUIET_3, QUIET_4 -> SC_QUIET_HINT;
        };
        return scenario.measurementArtefactPossible() ? hint + " – " + HINT_ARTIFACT : hint;
    }
}
