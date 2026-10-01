package com.autohaus.backend.capacity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The precomputed capacity view (REQ-038 TC-50, TC-51, TC-54, TC-58, TC-59 — capacity parts; AC-9,
 * AC-10, BR-14, BR-25, BR-26) / die vorberechnete Kapazitäts-Sicht.
 *
 * <p>Pure function tests — no Spring, no clock: {@code today} is passed in, exactly like the 00:05
 * rebuild does (BR-25). The main fixture is the 10-scan window 12.10.–22.10.2026 of §12.6 (Sunday
 * 18.10. is not a column). / Reine Funktion, {@code today} als Parameter.
 */
class CapacityViewModelTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static final String A = "68f0a1b2c3d4e5f6a7b8c9a1";
    private static final String B = "68f0a1b2c3d4e5f6a7b8c9b2";
    private static final String C = "68f0a1b2c3d4e5f6a7b8c9c3";

    /** The ten scan days of the §12.6 example, oldest first / die zehn Scan-Tage aus §12.6. */
    private static final List<LocalDate> WINDOW = List.of(
        day(10, 12), day(10, 13), day(10, 14), day(10, 15), day(10, 16),
        day(10, 17), day(10, 19), day(10, 20), day(10, 21), day(10, 22));

    // ---- TC-58: cells and classification / Zellen und Einordnung (N2, BR-26) ----

    /**
     * TC-58: cell mapping per BR-26 and the classification — A 7 × free, 2 × tight, 1 × booked →
     * {@code mostly-free} (7 of 10); B 2 × free, 6 × booked, 2 × error → {@code freeDays = 2},
     * {@code measurableDays = 8}, {@code mostly-booked} (≤ 2 of 10 with a full window;
     * not-measurable days do not count); C only 4 measurable days → {@code too-few-days}. /
     * Nicht messbare Tage zählen nicht mit.
     */
    @Test
    @DisplayName("TC-58: cell mapping, freeDays/measurableDays and classification")
    void mapsCellsAndClassifiesTheTenDayWindow() {
        List<ProbeResult> a = List.of(ok(0), ok(0), ok(2), ok(0), ok(0), ok(4), ok(0), ok(0),
            ok(7), ok(0));
        List<ProbeResult> b = List.of(ok(0), none(), ok(5), err(ProbeReason.TIMEOUT), ok(6), ok(9),
            none(), ok(0), none(), err(ProbeReason.SOFTNET_UNAVAILABLE));
        List<ProbeResult> c = new ArrayList<>(Collections.nCopies(10, null));
        c.set(0, ok(0));
        c.set(3, ok(0));
        c.set(6, ok(1));
        c.set(9, none());
        CapacityViewModel vm = build(window(Map.of(A, a, B, b, C, c)), names(), day(10, 22));

        assertEquals(WINDOW, vm.last10ScanDays(), "oldest first, Mo–Sa only");
        CapacityRow rowA = row(vm, "Autohaus A");
        assertEquals(List.of(free(), free(), tight(), free(), free(), tight(), free(), free(),
            booked(), free()), rowA.last10());
        assertEquals(7, rowA.freeDays());
        assertEquals(10, rowA.measurableDays());
        assertEquals(CapacityTrend.MOSTLY_FREE, rowA.classification());
        assertEquals(Collections.nCopies(10, null), rowA.last10Reasons());

        CapacityRow rowB = row(vm, "Autohaus B");
        assertEquals(List.of(free(), booked(), booked(), na(), booked(), booked(), booked(), free(),
            booked(), na()), rowB.last10());
        assertEquals(2, rowB.freeDays());
        assertEquals(8, rowB.measurableDays());
        assertEquals(CapacityTrend.MOSTLY_BOOKED, rowB.classification());
        assertEquals("timeout", rowB.last10Reasons().get(3));
        assertEquals("softnet-unavailable", rowB.last10Reasons().get(9));
        assertNull(rowB.last10Reasons().get(0));

        CapacityRow rowC = row(vm, "Autohaus C");
        assertEquals(4, rowC.measurableDays());
        assertEquals(CapacityTrend.TOO_FEW_DAYS, rowC.classification(), "no I-2/I-3 basis");
        assertNull(rowC.last10Reasons().get(1), "branch missing in a scan → no reason");
    }

    /**
     * TC-58: a working day without any scan is {@code not-measurable} in every row, with reason
     * {@code null} („kein Scan"); with fewer than 10 scans the ratios apply to the measurable days
     * („sinngemäß": 7 of 9 → 78 % → mostly-free, 4 of 9 → mixed). / Tag ohne Scan in allen
     * Zeilen „—".
     */
    @Test
    @DisplayName("TC-58: a day without scan is not-measurable in all rows")
    void aDayWithoutScanIsNotMeasurableEverywhere() {
        SortedMap<LocalDate, CapacityScan> scans = window(Map.of(
            A, pattern("FFFFFFFFTT"), B, pattern("FFFFFTTTTT")));
        scans.remove(day(10, 15));
        CapacityViewModel vm = build(history(scans), names(), day(10, 22));

        assertEquals(WINDOW, vm.last10ScanDays(), "the missing day keeps its column");
        for (CapacityRow row : vm.rows()) {
            assertEquals(na(), row.last10().get(3), row.branchName());
            assertNull(row.last10Reasons().get(3));
        }
        assertEquals(9, row(vm, "Autohaus A").measurableDays());
        assertEquals(7, row(vm, "Autohaus A").freeDays());
        assertEquals(CapacityTrend.MOSTLY_FREE, row(vm, "Autohaus A").classification(), "7 of 9");
        assertEquals(4, row(vm, "Autohaus B").freeDays());
        assertEquals(CapacityTrend.MIXED, row(vm, "Autohaus B").classification(), "4 of 9");
        assertEquals(0, row(vm, "Autohaus C").measurableDays(), "branch C in no scan at all");
    }

    /** BR-26 thresholds, directly / Schwellen direkt: full window absolute, otherwise ratio. */
    @Test
    void classifiesWithTheBr26Thresholds() {
        assertEquals(CapacityTrend.MOSTLY_FREE, CapacityViewModelBuilder.classify(7, 10, 10));
        assertEquals(CapacityTrend.MIXED, CapacityViewModelBuilder.classify(6, 10, 10));
        assertEquals(CapacityTrend.MIXED, CapacityViewModelBuilder.classify(3, 10, 10));
        assertEquals(CapacityTrend.MOSTLY_BOOKED, CapacityViewModelBuilder.classify(2, 10, 10));
        assertEquals(CapacityTrend.MOSTLY_BOOKED, CapacityViewModelBuilder.classify(2, 8, 10));
        assertEquals(CapacityTrend.MIXED, CapacityViewModelBuilder.classify(6, 7, 10),
            "full window: 6 of 10, errors do not count as free");
        assertEquals(CapacityTrend.MOSTLY_FREE, CapacityViewModelBuilder.classify(5, 7, 7), "71 %");
        assertEquals(CapacityTrend.MOSTLY_BOOKED, CapacityViewModelBuilder.classify(1, 5, 5), "20 %");
        assertEquals(CapacityTrend.MIXED, CapacityViewModelBuilder.classify(2, 6, 6), "33 %");
        assertEquals(CapacityTrend.TOO_FEW_DAYS, CapacityViewModelBuilder.classify(4, 4, 10));
    }

    // ---- TC-59: order and column heads / Sortierung und Spaltenköpfe (N2) ----

    /**
     * TC-59: mostly-free (9 before 7) → mixed → mostly-booked → too-few-days; within a
     * classification by {@code freeDays}, then the level of the shown scan, then the name with the
     * German collator (Ä sorts with A). The JSON order is the page order (§12.6). / Reihenfolge wie
     * auf der Seite.
     */
    @Test
    @DisplayName("TC-59: rows sorted per BR-26, JSON order identical")
    void sortsRowsPerBr26() throws Exception {
        Map<String, List<ProbeResult>> byBranch = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        addBranch(byBranch, names, "68f0a1b2c3d4e5f6a7b8c901", "Zentrum Belegt", "FBBBBBBBBB");
        addBranch(byBranch, names, "68f0a1b2c3d4e5f6a7b8c902", "Autohaus Neu", "FF--------");
        addBranch(byBranch, names, "68f0a1b2c3d4e5f6a7b8c903", "Autohaus Wechsel", "FFFFFBBBBN");
        addBranch(byBranch, names, "68f0a1b2c3d4e5f6a7b8c904", "Bergstadt", "BBBBBFFFFF");
        addBranch(byBranch, names, "68f0a1b2c3d4e5f6a7b8c905", "Äpfelhaus", "BBBBBFFFFF");
        addBranch(byBranch, names, "68f0a1b2c3d4e5f6a7b8c906", "Mitte Sechs", "FFFFFFBBBB");
        addBranch(byBranch, names, "68f0a1b2c3d4e5f6a7b8c907", "Autohaus Sieben", "FFFFFFFBBT");
        addBranch(byBranch, names, "68f0a1b2c3d4e5f6a7b8c908", "Zentrum Neun", "FFFFFFFFFT");

        CapacityViewModel vm = build(window(byBranch), names, day(10, 22));

        List<String> order = vm.rows().stream().map(CapacityRow::branchName).toList();
        // 9 before 7; within "mixed" 6 before 5; among the 5s the free level first, then the
        // German collator (Ä with A); "none-found" after the free level despite its name.
        assertEquals(List.of("Zentrum Neun", "Autohaus Sieben", "Mitte Sechs", "Äpfelhaus",
            "Bergstadt", "Autohaus Wechsel", "Zentrum Belegt", "Autohaus Neu"), order);
        assertEquals(List.of(CapacityTrend.MOSTLY_FREE, CapacityTrend.MOSTLY_FREE,
                CapacityTrend.MIXED, CapacityTrend.MIXED, CapacityTrend.MIXED, CapacityTrend.MIXED,
                CapacityTrend.MOSTLY_BOOKED, CapacityTrend.TOO_FEW_DAYS),
            vm.rows().stream().map(CapacityRow::classification).toList());

        JsonNode tree = mapper().readTree(mapper().writeValueAsString(vm));
        List<String> jsonOrder = new ArrayList<>();
        tree.path("rows").forEach(r -> jsonOrder.add(r.path("branchName").asText()));
        assertEquals(order, jsonOrder, "JSON order equals the page order");
        assertEquals("2026-10-12", tree.path("last10ScanDays").get(0).asText(), "oldest first");
        assertEquals("2026-10-22", tree.path("last10ScanDays").get(9).asText());
    }

    /**
     * TC-24 (capacity.json part), §12.6: the view serializes with the lower-case enum keys —
     * {@code "level":"free-next-day"}, {@code "classification":"mostly-free"}, cells
     * {@code "free"}/{@code "not-measurable"}, {@code "state":"partial"}. / Schlüssel wie §12.6.
     */
    @Test
    @DisplayName("TC-24: capacity.json keys and enum values as in §12.6")
    void serializesWithTheSpecifiedKeys() throws Exception {
        SortedMap<LocalDate, CapacityScan> scans = window(Map.of(
            A, pattern("FFFFFFFFF-"), B, pattern("FFFFFFFFF-")));
        scans.put(day(10, 22), scan(ScanState.PARTIAL, day(10, 22), Map.of(A,
            err(ProbeReason.SOFTNET_FORBIDDEN), B, ok(0))));

        JsonNode tree = mapper().readTree(mapper().writeValueAsString(
            build(history(scans), names(), day(10, 22))));

        assertEquals("2026-10-22", tree.path("scanDay").asText());
        assertEquals("2026-10-23", tree.path("targetDay").asText());
        assertEquals("partial", tree.path("state").asText());
        assertFalse(tree.path("showingFallbackScan").asBoolean());
        assertTrue(tree.path("fallbackState").isNull());
        JsonNode first = tree.path("rows").get(0);
        assertEquals("Autohaus B", first.path("branchName").asText());
        assertEquals("free-next-day", first.path("level").asText());
        assertEquals("morgen frei", first.path("levelLabel").asText());
        assertEquals("mostly-free", first.path("classification").asText());
        assertEquals("free", first.path("last10").get(0).asText());
        JsonNode second = tree.path("rows").get(1);
        assertEquals("not-measurable", second.path("level").asText());
        assertEquals("nicht messbar", second.path("levelLabel").asText());
        assertEquals("softnet-forbidden", second.path("reasonKey").asText());
        assertEquals("not-measurable", second.path("last10").get(9).asText());
        assertTrue(second.path("workdays").isNull());
        assertEquals("Scan vom 22.10.2026 unvollständig: 1 Standort nicht messbar.",
            tree.path("warnings").get(0).asText());
    }

    // ---- TC-54: labels relative to the day of retrieval / Beschriftung relativ (BR-25) ----

    /**
     * TC-54: scan Thu 19.11., target Fri 20.11. — built on 19.11. „morgen frei", on 20.11. „heute
     * frei"; scan Sat 21.11., target Mon 23.11. — 21.11. „am Mo 23.11. frei", 22.11. „morgen frei",
     * 23.11. „heute frei". The key stays {@code free-next-day}. / Schlüssel bleibt gleich.
     */
    @Test
    @DisplayName("TC-54: free-next-day label relative to the day of retrieval")
    void labelsTheFreeLevelRelativeToToday() {
        CapacityHistory thursday = history(single(day(11, 19), Map.of(A, ok(0))));
        CapacityHistory saturday = history(single(day(11, 21), Map.of(A, ok(0))));

        assertLabel("morgen frei", thursday, day(11, 19));
        assertLabel("heute frei", thursday, day(11, 20));
        assertLabel("am Mo 23.11. frei", saturday, day(11, 21));
        assertLabel("morgen frei", saturday, day(11, 22));
        assertLabel("heute frei", saturday, day(11, 23));
        CapacityViewModel vm = build(saturday, names(), day(11, 23));
        assertEquals(day(11, 23), vm.targetDay());
        assertEquals(CapacityLevel.FREE_NEXT_DAY, vm.rows().get(0).level());
        assertEquals(0, vm.rows().get(0).workdays());
    }

    /** AC-9/§17: the other level texts / die übrigen Stufentexte. */
    @Test
    void labelsTheOtherLevels() {
        CapacityHistory history = history(single(day(10, 22), Map.of(
            A, ok(1), B, ok(3), C, ok(5))));
        CapacityViewModel vm = build(history, names(), day(10, 22));

        assertEquals("in 1 Werktag", row(vm, "Autohaus A").levelLabel());
        assertEquals(CapacityLevel.WITHIN_4_WORKDAYS, row(vm, "Autohaus A").level());
        assertEquals("in 3 Werktagen", row(vm, "Autohaus B").levelLabel());
        assertEquals("belegt: ≥ 5 Werktage", row(vm, "Autohaus C").levelLabel());
        assertEquals(CapacityLevel.TIGHT, row(vm, "Autohaus C").level());

        CapacityViewModel none = build(history(single(day(10, 22), Map.of(A, none()))),
            Map.of(A, "Autohaus A"), day(10, 22));
        assertEquals("keine Online-Termine gefunden", none.rows().get(0).levelLabel());
        assertEquals(CapacityLevel.NONE_FOUND, none.rows().get(0).level());
        assertNull(none.rows().get(0).workdays());
        assertEquals("am Fr 23.10. frei", CapacityViewModelBuilder.labelOf(
            CapacityLevel.FREE_NEXT_DAY, 0, day(10, 23), day(10, 20)));
    }

    // ---- AC-10 / BR-14: failed scan with fallback / fehlgeschlagener Scan ----

    /**
     * AC-10: the most recent scan (19.11.) failed → the view shows the last complete scan (18.11.)
     * with {@code showingFallbackScan}, {@code state = FAILED}, {@code fallbackState = COMPLETE},
     * the warnings {@code CAPACITY_SCAN_FAILED} + {@code _DETAIL}; the failed day stays in the grid
     * as „—" with its reason. / Fallback auf den letzten vollständigen Scan.
     */
    @Test
    @DisplayName("AC-10/TC-37 data: failed latest scan falls back to the last complete scan")
    void fallsBackToTheLastCompleteScan() {
        SortedMap<LocalDate, CapacityScan> scans = new TreeMap<>();
        scans.put(day(11, 18), scan(ScanState.COMPLETE, day(11, 18), Map.of(A, ok(0), B, ok(2),
            C, none())));
        scans.put(day(11, 19), scan(ScanState.FAILED, day(11, 19), Map.of(
            A, err(ProbeReason.SOFTNET_UNAVAILABLE), B, err(ProbeReason.SOFTNET_UNAVAILABLE),
            C, err(ProbeReason.SOFTNET_UNAVAILABLE))));

        CapacityViewModel vm = build(history(scans), names(), day(11, 19));

        assertTrue(vm.showingFallbackScan());
        assertEquals(ScanState.FAILED, vm.state());
        assertEquals(ScanState.COMPLETE, vm.fallbackState());
        assertEquals(day(11, 18), vm.scanDay());
        assertEquals(day(11, 19), vm.targetDay(), "target day of the shown scan");
        assertEquals(List.of(
            "Letzter Scan (19.11.2026, 22:30 Uhr) fehlgeschlagen – angezeigt: Stand 18.11.2026.",
            "Grund: SoftNet nicht erreichbar (alle 3 Standorte, auch nach erneutem Versuch). Für "
                + "morgen (Fr 20.11.) liegt keine Messung vor; kein Wert wird als 0 dargestellt."),
            vm.warnings());
        CapacityRow rowA = row(vm, "Autohaus A");
        assertEquals(CapacityLevel.FREE_NEXT_DAY, rowA.level(), "level from the fallback scan");
        assertEquals("heute frei", rowA.levelLabel(), "target 19.11. = today");
        assertEquals(day(11, 19), vm.last10ScanDays().get(9), "the failed day is a column");
        assertEquals(na(), rowA.last10().get(9));
        assertEquals("softnet-unavailable", rowA.last10Reasons().get(9));
    }

    /**
     * §17 {@code CAPACITY_RANGE} / {@code META_STATISTICS} / {@code META_SCAN_FAILED} and §11.2
     * {@code .cap-when}: the view carries the start and end of the <b>shown</b> scan, the day of the
     * latest scan (the failed one while the fallback is shown) and each row's first free date. /
     * Scan-Zeiten des gezeigten Scans, Tag des jüngsten Scans und erster freier Tag je Zeile.
     */
    @Test
    @DisplayName("§17 CAPACITY_RANGE/META_*: scan times of the shown scan, latest scan day, first free date")
    void carriesScanTimesLatestScanDayAndFirstFreeDate() {
        SortedMap<LocalDate, CapacityScan> scans = new TreeMap<>();
        ProbeResult inTwoDays = new ProbeResult(ProbeStatus.OK, null, day(11, 21), 2, 4);
        scans.put(day(11, 18), scan(ScanState.COMPLETE, day(11, 18), Map.of(A, inTwoDays, B, none())));

        CapacityViewModel complete = build(history(scans), names(), day(11, 19));

        CapacityScan shown = scans.get(day(11, 18));
        assertEquals(shown.startedAt(), complete.scanStartedAt());
        assertEquals(shown.finishedAt(), complete.scanFinishedAt());
        assertEquals(day(11, 18), complete.latestScanDay(), "no fallback: latest = shown");
        assertEquals(day(11, 21), row(complete, "Autohaus A").firstFreeDate());
        assertNull(row(complete, "Autohaus B").firstFreeDate(), "none-found: no date");

        scans.put(day(11, 19), scan(ScanState.FAILED, day(11, 19), Map.of(
            A, err(ProbeReason.TIMEOUT), B, err(ProbeReason.TIMEOUT))));
        CapacityViewModel fallback = build(history(scans), names(), day(11, 19));

        assertTrue(fallback.showingFallbackScan());
        assertEquals(shown.startedAt(), fallback.scanStartedAt(), "times of the shown fallback scan");
        assertEquals(day(11, 19), fallback.latestScanDay(), "the failed scan's day for META_SCAN_FAILED");
        CapacityViewModel noScan = build(history(new TreeMap<>()), names(), day(11, 19));
        assertNull(noScan.scanStartedAt());
        assertNull(noScan.latestScanDay());
        assertEquals(fallback.scanStartedAt(), fallback.withAdditionalWarnings(List.of("x")).scanStartedAt(),
            "withAdditionalWarnings keeps the new fields");
    }

    /** AC-10: a partial fallback scan additionally carries the partial hint. / Partial-Fallback. */
    @Test
    void aPartialFallbackAddsThePartialHint() {
        SortedMap<LocalDate, CapacityScan> scans = new TreeMap<>();
        scans.put(day(11, 18), scan(ScanState.PARTIAL, day(11, 18), Map.of(A, ok(0),
            B, err(ProbeReason.TIMEOUT), C, err(ProbeReason.SOFTNET_FORBIDDEN))));
        scans.put(day(11, 19), scan(ScanState.FAILED, day(11, 19), Map.of(
            A, err(ProbeReason.TIMEOUT), B, err(ProbeReason.SOFTNET_UNAVAILABLE),
            C, err(ProbeReason.TIMEOUT))));

        CapacityViewModel vm = build(history(scans), names(), day(11, 21));

        assertEquals(ScanState.PARTIAL, vm.fallbackState());
        assertEquals(3, vm.warnings().size());
        assertTrue(vm.warnings().get(1).startsWith(
            "Grund: Zeitüberschreitung, SoftNet nicht erreichbar (alle 3 Standorte"),
            "reasons by frequency: " + vm.warnings().get(1));
        assertTrue(vm.warnings().get(1).contains("Für den Zieltag (Fr 20.11.)"), vm.warnings().get(1));
        assertEquals("Scan vom 18.11.2026 unvollständig: 2 Standorte nicht messbar.",
            vm.warnings().get(2));
        CapacityRow rowB = row(vm, "Autohaus B");
        assertEquals(CapacityLevel.NOT_MEASURABLE, rowB.level());
        assertEquals("timeout", rowB.reasonKey(), "BR-14: nicht messbar mit Grund, nie 0");
        assertNull(rowB.workdays());
    }

    /**
     * BR-14: only failed scans so far → the failed scan itself is shown, every branch „nicht
     * messbar" with its reason, no fallback flag. / Ohne Fallback der Fehl-Scan selbst.
     */
    @Test
    void withoutAnyCompleteScanTheFailedScanIsShown() {
        CapacityViewModel vm = build(history(single(day(10, 22), ScanState.FAILED, Map.of(
            A, err(ProbeReason.NO_REFERENCE_SERVICE), B, err(ProbeReason.SOFTNET_UNAVAILABLE)))),
            names(), day(10, 22));

        assertFalse(vm.showingFallbackScan());
        assertNull(vm.fallbackState());
        assertEquals(ScanState.FAILED, vm.state());
        assertEquals(day(10, 22), vm.scanDay());
        assertEquals("Letzter Scan (22.10.2026, 22:30 Uhr) fehlgeschlagen – es liegt noch kein "
            + "vollständiger Scan vor.", vm.warnings().get(0));
        for (CapacityRow row : vm.rows()) {
            assertEquals(CapacityLevel.NOT_MEASURABLE, row.level(), row.branchName());
            assertNull(row.workdays());
            assertEquals(CapacityTrend.TOO_FEW_DAYS, row.classification());
        }
        assertEquals("no-reference-service", row(vm, "Autohaus A").reasonKey());
        assertNull(row(vm, "Autohaus C").reasonKey(), "missing in the scan → no reason");
    }

    // ---- TC-51 / TC-50: partial and stale / unvollständig und veraltet ----

    /** TC-51 (a): exactly „Scan vom 22.10.2026 unvollständig: 2 Standorte nicht messbar." */
    @Test
    @DisplayName("TC-51(a): partial scan warning with the number of error branches")
    void warnsAboutAPartialScan() {
        CapacityViewModel vm = build(history(single(day(10, 22), ScanState.PARTIAL, Map.of(
            A, ok(0), B, err(ProbeReason.TIMEOUT), C, err(ProbeReason.SOFTNET_UNAVAILABLE)))),
            names(), day(10, 22));

        assertEquals(List.of("Scan vom 22.10.2026 unvollständig: 2 Standorte nicht messbar."),
            vm.warnings());
        assertEquals(ScanState.PARTIAL, vm.state());
    }

    /**
     * TC-50: the most recent scan 3 days before {@code today} → {@code WARN_STALE}; 2 days → no
     * hint. / Scan 3 Tage alt → Hinweis, 2 Tage → keiner.
     */
    @Test
    @DisplayName("TC-50: stale hint when the latest scan is more than 2 days old")
    void warnsAboutAStaleScan() {
        CapacityHistory history = history(single(day(10, 19), Map.of(A, ok(0))));

        assertEquals(List.of("Letzte Auswertung: 19.10.2026 – Daten möglicherweise veraltet"),
            build(history, names(), day(10, 22)).warnings());
        assertEquals(List.of(), build(history, names(), day(10, 21)).warnings());
        assertTrue(CapacityViewModelBuilder.isStale(day(10, 19), day(10, 22)));
        assertFalse(CapacityViewModelBuilder.isStale(day(10, 19), day(10, 21)));
        assertFalse(CapacityViewModelBuilder.isStale(null, day(10, 21)));
    }

    // ---- empty, running, slots / leer, laufend, K-2 ----

    /**
     * §5.2: without any scan the rows stay empty (S1 shows {@code CAPACITY_NO_SCAN}), never zeros;
     * during the first run only {@code runningSince} and {@code collectingSince} are set. / Ohne
     * Scan keine Zeilen.
     */
    @Test
    void anEmptyOrRunningHistoryHasNoRows() {
        Instant since = Instant.parse("2026-10-22T20:31:00Z");
        CapacityViewModel empty = CapacityViewModel.build(CapacityHistory.empty(), names(), false,
            day(10, 22), null);
        CapacityViewModel nullHistory = CapacityViewModel.build(null, null, false, day(10, 22), null);
        CapacityViewModel running = CapacityViewModel.build(history(single(day(10, 22),
            ScanState.RUNNING, Map.of())), names(), false, day(10, 22), since);

        assertTrue(empty.rows().isEmpty());
        assertNull(empty.scanDay());
        assertNull(empty.collectingSince());
        assertTrue(empty.last10ScanDays().isEmpty());
        assertTrue(empty.warnings().isEmpty());
        assertTrue(nullHistory.rows().isEmpty());
        assertTrue(running.rows().isEmpty(), "the running claim is not a measurement");
        assertEquals(since, running.runningSince());
        assertEquals(day(10, 22), running.collectingSince(), "Erfassung seit = first scan day");
        assertNull(running.state());
    }

    /**
     * TC-55 data: a running claim next to yesterday's complete scan — the complete scan stays
     * visible, the running day is no column. / Der laufende Tag ist keine Spalte.
     */
    @Test
    void aRunningClaimKeepsThePreviousScanVisible() {
        SortedMap<LocalDate, CapacityScan> scans = new TreeMap<>();
        scans.put(day(10, 21), scan(ScanState.COMPLETE, day(10, 21), Map.of(A, ok(0))));
        scans.put(day(10, 22), new CapacityScan(ScanState.RUNNING,
            Instant.parse("2026-10-22T20:31:00Z"), null, day(10, 23), Map.of()));
        Instant since = Instant.parse("2026-10-22T20:31:00Z");

        CapacityViewModel vm = CapacityViewModel.build(history(scans), Map.of(A, "Autohaus A"),
            false, day(10, 22), since);

        assertEquals(day(10, 21), vm.scanDay());
        assertEquals(ScanState.COMPLETE, vm.state());
        assertEquals(day(10, 21), vm.last10ScanDays().get(9));
        assertEquals(since, vm.runningSince());
        assertEquals(day(10, 21), vm.collectingSince());
    }

    /**
     * AC-9/N2: K-2 is one total per branch and target day, only with
     * {@code CAPACITY_SHOW_SLOT_COUNT}; never for {@code none}/{@code error}. / K-2 nur mit
     * Schalter.
     */
    @Test
    void showsTheSlotTotalOnlyWithTheSwitch() {
        CapacityHistory history = history(single(day(10, 22), ScanState.PARTIAL, Map.of(
            A, new ProbeResult(ProbeStatus.OK, null, day(10, 23), 0, 13), B, none(),
            C, err(ProbeReason.TIMEOUT))));

        CapacityViewModel on = CapacityViewModel.build(history, names(), true, day(10, 22), null);
        CapacityViewModel off = CapacityViewModel.build(history, names(), false, day(10, 22), null);

        assertEquals(13, row(on, "Autohaus A").freeSlotsTargetDay());
        assertNull(row(on, "Autohaus B").freeSlotsTargetDay());
        assertNull(row(on, "Autohaus C").freeSlotsTargetDay());
        assertNull(row(off, "Autohaus A").freeSlotsTargetDay());
    }

    /**
     * One row per branch of {@code setup.json}: a branch missing in the scan is „nicht messbar"
     * without reason; a scan branch not in {@code setup.json} gets no row; the collecting start is
     * the first scan day. / Eine Zeile je Standort aus setup.json.
     */
    @Test
    void buildsOneRowPerSetupBranch() {
        SortedMap<LocalDate, CapacityScan> scans = new TreeMap<>();
        scans.put(day(10, 5), scan(ScanState.COMPLETE, day(10, 5), Map.of(A, ok(0))));
        scans.put(day(10, 22), scan(ScanState.COMPLETE, day(10, 22), Map.of(
            A, ok(0), "68f0a1b2c3d4e5f6a7b8c9ff", ok(0))));

        CapacityViewModel vm = build(history(scans), names(), day(10, 22));

        assertEquals(3, vm.rows().size());
        assertEquals(CapacityLevel.NOT_MEASURABLE, row(vm, "Autohaus B").level());
        assertNull(row(vm, "Autohaus B").reasonKey());
        assertEquals(day(10, 5), vm.collectingSince());
        assertTrue(vm.rows().stream().noneMatch(r -> r.branchName().contains("c9ff")));
    }

    /**
     * A Sunday scan (target Monday) is shown; the grid ends on the Saturday before it (Mo–Sa
     * columns only). A stored scan without {@code targetDay} gets the next working day. / Sonntags-
     * Scan: Raster endet am Samstag.
     */
    @Test
    void aSundayScanIsShownButIsNoColumn() {
        LocalDate sunday = day(10, 25);
        SortedMap<LocalDate, CapacityScan> scans = new TreeMap<>();
        scans.put(sunday, new CapacityScan(ScanState.COMPLETE, null, null, null, Map.of(A, ok(0))));

        CapacityViewModel vm = build(history(scans), Map.of(A, "Autohaus A"), sunday);

        assertEquals(sunday, vm.scanDay());
        assertEquals(day(10, 26), vm.targetDay(), "targetDay missing → next working day");
        assertEquals(day(10, 24), vm.last10ScanDays().get(9));
        assertEquals("morgen frei", vm.rows().get(0).levelLabel());
        assertEquals(na(), vm.rows().get(0).last10().get(9), "no scan on Saturday");
    }

    /** Tolerant read: {@code ok} without counter is „—", never guessed. / Fehlender Zähler → „—". */
    @Test
    void anOkResultWithoutCounterIsNotMeasurable() {
        CapacityViewModel vm = build(history(single(day(10, 22), Map.of(
            A, new ProbeResult(ProbeStatus.OK, null, day(10, 23), null, null)))),
            Map.of(A, "Autohaus A"), day(10, 22));

        assertEquals(CapacityLevel.NOT_MEASURABLE, vm.rows().get(0).level());
        assertEquals(CapacityDayState.NOT_MEASURABLE, vm.rows().get(0).last10().get(9));
    }

    /** E4: the scheduler's hint is appended once; duplicates are skipped. / Hinweis einmalig. */
    @Test
    void appendsAdditionalWarningsOnce() {
        CapacityViewModel vm = build(history(single(day(10, 19), Map.of(A, ok(0)))), names(),
            day(10, 22));

        CapacityViewModel extended = vm.withAdditionalWarnings(
            List.of(CapacityViewModel.WARN_HISTORY_UNAVAILABLE))
            .withAdditionalWarnings(List.of(CapacityViewModel.WARN_HISTORY_UNAVAILABLE));

        assertEquals(2, extended.warnings().size());
        assertEquals(CapacityViewModel.WARN_HISTORY_UNAVAILABLE, extended.warnings().get(1));
        assertSame(vm, vm.withAdditionalWarnings(List.of()));
    }

    /**
     * §10.2: the view enums resolve their own keys and nothing else (strict, E6); the shared
     * helpers are {@code null}-safe. / Strenge Schlüssel, null-sichere Helfer.
     */
    @Test
    void resolvesTheViewEnumKeysStrictly() {
        for (CapacityLevel level : CapacityLevel.values()) {
            assertSame(level, CapacityLevel.fromKey(level.key()));
        }
        for (CapacityDayState state : CapacityDayState.values()) {
            assertSame(state, CapacityDayState.fromKey(state.key()));
        }
        for (CapacityTrend trend : CapacityTrend.values()) {
            assertSame(trend, CapacityTrend.fromKey(trend.key()));
        }
        assertNull(CapacityLevel.fromKey("knapp"));
        assertNull(CapacityDayState.fromKey("FREE"));
        assertNull(CapacityTrend.fromKey(null));
        assertNull(CapacityViewModelBuilder.latestFinalScanDay(null));
        assertNull(CapacityViewModelBuilder.latestFinalScanDay(CapacityHistory.empty()));
        assertEquals(CapacityLevel.NOT_MEASURABLE, CapacityViewModelBuilder.levelOf(null));
        assertEquals(CapacityLevel.NOT_MEASURABLE, CapacityViewModelBuilder.levelOf(
            new ProbeResult(ProbeStatus.OK, null, null, -1, null)));
        assertEquals(day(10, 23), CapacityViewModelBuilder.targetOf(day(10, 22), null));
    }

    /**
     * Tolerant data: a scan without branch map, a failed scan without start time and without
     * reasons, a {@code null} name. / Tolerante Daten.
     */
    @Test
    void toleratesIncompleteScans() {
        SortedMap<LocalDate, CapacityScan> scans = new TreeMap<>();
        scans.put(day(10, 21), new CapacityScan(ScanState.PARTIAL, null, null, null, null));
        scans.put(day(10, 22), new CapacityScan(ScanState.FAILED, null, null, null,
            Map.of(A, new ProbeResult(ProbeStatus.ERROR, null, null, null, null))));
        Map<String, String> names = new LinkedHashMap<>();
        names.put(A, null);

        CapacityViewModel vm = build(history(scans), names, day(10, 22));

        assertTrue(vm.showingFallbackScan());
        assertEquals(List.of(
            "Letzter Scan (22.10.2026) fehlgeschlagen – angezeigt: Stand 21.10.2026.",
            "Scan vom 21.10.2026 unvollständig: 0 Standorte nicht messbar."), vm.warnings());
        assertEquals(A, vm.rows().get(0).branchName(), "id as fallback name");
        assertEquals(CapacityLevel.NOT_MEASURABLE, vm.rows().get(0).level());
    }

    /** §17 reason texts / Gründe als Text. */
    @Test
    void labelsEveryReason() {
        assertEquals("SoftNet nicht erreichbar",
            CapacityViewModelBuilder.reasonLabel(ProbeReason.SOFTNET_UNAVAILABLE));
        assertEquals("kein Zugriff auf die SoftNet-Gruppe",
            CapacityViewModelBuilder.reasonLabel(ProbeReason.SOFTNET_FORBIDDEN));
        assertEquals("Zeitüberschreitung", CapacityViewModelBuilder.reasonLabel(ProbeReason.TIMEOUT));
        assertEquals("Referenz-Leistung nicht im Leistungskatalog",
            CapacityViewModelBuilder.reasonLabel(ProbeReason.NO_REFERENCE_SERVICE));
        assertEquals("keine Marke hinterlegt",
            CapacityViewModelBuilder.reasonLabel(ProbeReason.NO_BRAND));
        assertEquals("unerwartete SoftNet-Antwort",
            CapacityViewModelBuilder.reasonLabel(ProbeReason.UNEXPECTED_RESPONSE));
    }

    // ---- helpers / Hilfen ----

    private static void assertLabel(String expected, CapacityHistory history, LocalDate today) {
        CapacityViewModel vm = build(history, Map.of(A, "Autohaus A"), today);
        assertEquals(expected, vm.rows().get(0).levelLabel(), "built on " + today);
        assertEquals(CapacityLevel.FREE_NEXT_DAY, vm.rows().get(0).level());
    }

    private static CapacityViewModel build(CapacityHistory history, Map<String, String> names,
                                           LocalDate today) {
        return CapacityViewModel.build(history, names, false, today, null);
    }

    private static CapacityViewModel build(SortedMap<LocalDate, CapacityScan> scans,
                                           Map<String, String> names, LocalDate today) {
        return build(history(scans), names, today);
    }

    private static CapacityRow row(CapacityViewModel vm, String name) {
        return vm.rows().stream().filter(r -> r.branchName().equals(name)).findFirst()
            .orElseThrow(() -> new AssertionError("no row " + name));
    }

    private static Map<String, String> names() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put(A, "Autohaus A");
        names.put(B, "Autohaus B");
        names.put(C, "Autohaus C");
        return names;
    }

    /** One complete scan per window day built from per-branch result lists (null = missing). */
    private static SortedMap<LocalDate, CapacityScan> window(Map<String, List<ProbeResult>> byBranch) {
        SortedMap<LocalDate, CapacityScan> scans = new TreeMap<>();
        for (int i = 0; i < WINDOW.size(); i++) {
            Map<String, ProbeResult> results = new TreeMap<>();
            for (Map.Entry<String, List<ProbeResult>> entry : byBranch.entrySet()) {
                ProbeResult result = entry.getValue().get(i);
                if (result != null) {
                    results.put(entry.getKey(), result);
                }
            }
            scans.put(WINDOW.get(i), scan(ScanState.COMPLETE, WINDOW.get(i), results));
        }
        return scans;
    }

    /**
     * One result per window day from a pattern / ein Ergebnis je Fenstertag aus einem Muster:
     * {@code F} free (0 workdays), {@code T} tight day (2), {@code B} booked (6), {@code N} none,
     * {@code E} error/timeout, {@code -} branch missing in that scan.
     */
    private static List<ProbeResult> pattern(String cells) {
        List<ProbeResult> results = new ArrayList<>();
        for (char cell : cells.toCharArray()) {
            results.add(switch (cell) {
                case 'F' -> ok(0);
                case 'T' -> ok(2);
                case 'B' -> ok(6);
                case 'N' -> none();
                case 'E' -> err(ProbeReason.TIMEOUT);
                default -> null;
            });
        }
        return results;
    }

    private static void addBranch(Map<String, List<ProbeResult>> byBranch,
                                  Map<String, String> names, String id, String name,
                                  String cells) {
        byBranch.put(id, pattern(cells));
        names.put(id, name);
    }

    private static SortedMap<LocalDate, CapacityScan> single(LocalDate day,
                                                            Map<String, ProbeResult> results) {
        return single(day, ScanState.COMPLETE, results);
    }

    private static SortedMap<LocalDate, CapacityScan> single(LocalDate day, ScanState state,
                                                            Map<String, ProbeResult> results) {
        SortedMap<LocalDate, CapacityScan> scans = new TreeMap<>();
        scans.put(day, scan(state, day, results));
        return scans;
    }

    /** A scan started 22:30 Berlin, 5 minutes long, target = next working day. */
    private static CapacityScan scan(ScanState state, LocalDate day, Map<String, ProbeResult> results) {
        Instant started = day.atTime(LocalTime.of(22, 30)).atZone(BERLIN).toInstant();
        return new CapacityScan(state, started, started.plusSeconds(300),
            CapacityCalendar.nextWorkday(day), results);
    }

    private static CapacityHistory history(SortedMap<LocalDate, CapacityScan> scans) {
        return new CapacityHistory(1, "wiper-blades", scans);
    }

    private static ProbeResult ok(int workdays) {
        return new ProbeResult(ProbeStatus.OK, null, null, workdays, 3);
    }

    private static ProbeResult none() {
        return new ProbeResult(ProbeStatus.NONE, null, null, null, null);
    }

    private static ProbeResult err(ProbeReason reason) {
        return new ProbeResult(ProbeStatus.ERROR, reason, null, null, null);
    }

    private static CapacityDayState free() {
        return CapacityDayState.FREE;
    }

    private static CapacityDayState tight() {
        return CapacityDayState.TIGHT_DAY;
    }

    private static CapacityDayState booked() {
        return CapacityDayState.BOOKED;
    }

    private static CapacityDayState na() {
        return CapacityDayState.NOT_MEASURABLE;
    }

    private static LocalDate day(int month, int dayOfMonth) {
        return LocalDate.of(2026, month, dayOfMonth);
    }

    private static ObjectMapper mapper() {
        return JsonMapper.builder().addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    }
}
