package com.autohaus.backend.capacity;

import java.text.Collator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.stream.Collectors;

/**
 * Folds {@link CapacityHistory} into the display model (REQ-038 §10.4/§10.5, BR-14, BR-25, BR-26)
 * / faltet die Historie in das Anzeigemodell. Package-private companion of
 * {@link CapacityViewModel#build}; a pure function over in-memory data — no I/O, so the 00:05
 * rebuild can relabel „morgen frei" → „heute frei" without touching blob or SoftNet (BR-25). /
 * Reine Funktion ohne I/O.
 *
 * <p><b>Which scan is shown (BR-14, AC-10):</b> only scans with a final state count
 * ({@code complete|partial|failed}); a {@code running} claim is ignored everywhere (its day looks
 * like a day without scan). The shown scan is the most recent {@code complete|partial} scan. Is the
 * most recent final scan {@code failed}, the model shows the last {@code complete|partial} scan with
 * {@code showingFallbackScan = true}, {@code state = FAILED} (the state of the most recent scan) and
 * {@code fallbackState} = the state of the shown scan, plus the warnings
 * {@code CAPACITY_SCAN_FAILED} and {@code CAPACITY_SCAN_FAILED_DETAIL}. Without any
 * {@code complete|partial} scan the failed scan itself is shown — every branch „nicht messbar" with
 * its reason, never 0. / Ohne Fallback wird der fehlgeschlagene Scan selbst gezeigt.
 *
 * <p><b>10-day grid (BR-26, N2):</b> the columns are the last {@value CapacityViewModel#LAST_SCAN_DAYS}
 * working days Mo–Sa up to and including the day of the most recent final scan, oldest first. A day
 * without a (final) scan is {@code not-measurable} in every row, with reason {@code null} (renderer:
 * „kein Scan"); a branch error keeps its reason key. The classification needs
 * {@value CapacityViewModel#MIN_MEASURABLE_DAYS} measurable days; with a full window of 10 scans the
 * absolute thresholds apply (≥ 7 of 10 free → mostly free, ≤ 2 → mostly booked — not-measurable days
 * do not count as free, TC-58), with fewer scans the ratios {@value CapacityViewModel#MOSTLY_FREE_MIN}
 * / {@value CapacityViewModel#MOSTLY_BOOKED_MAX} are applied to the measurable days („sinngemäß").
 * Capacity values are operational data; the „&lt; 5" rule does not apply. / Kapazitätswerte sind
 * Betriebsdaten.
 *
 * <p>All visible texts are German literals from §17. Clock times in warnings are formatted in
 * Europe/Berlin (the feature zone; {@code build(...)} has no zone parameter). / Uhrzeiten in
 * Europe/Berlin.
 */
final class CapacityViewModelBuilder {

    /** A scan older than this many days before {@code today} is stale (§9 Observability, TC-50). */
    static final int STALE_AFTER_DAYS = 2;

    // ---- §17 labels / Beschriftungen ----
    // One source for LEVEL_* and REASON_*: CapacitySectionRenderer reads these package-visible
    // constants for its fallback labels and never keeps a copy. / Einzige Quelle; der Renderer
    // liest diese paket-sichtbaren Konstanten.
    static final String LEVEL_FREE_TODAY = "heute frei";
    static final String LEVEL_FREE_TOMORROW = "morgen frei";
    static final String LEVEL_FREE_ON = "am {weekday} {date} frei";
    static final String LEVEL_WITHIN_ONE_WORKDAY = "in 1 Werktag";
    static final String LEVEL_WITHIN_WORKDAYS = "in {n} Werktagen";
    static final String LEVEL_TIGHT = "belegt: ≥ 5 Werktage";
    static final String LEVEL_NONE_FOUND = "keine Online-Termine gefunden";
    static final String LEVEL_NOT_MEASURABLE = "nicht messbar";

    static final String CAPACITY_SCAN_FAILED =
        "Letzter Scan ({date}{time}) fehlgeschlagen – angezeigt: Stand {shown}.";
    /** {@code {time}} of {@code CAPACITY_SCAN_FAILED*}, only with a start time / Uhrzeit-Teil. */
    static final String FAILED_TIME = ", {time} Uhr";
    /** Variant without any complete/partial scan to fall back to (not in §17) / ohne Fallback. */
    static final String CAPACITY_SCAN_FAILED_NO_FALLBACK =
        "Letzter Scan ({date}{time}) fehlgeschlagen – es liegt noch kein vollständiger Scan vor.";
    static final String CAPACITY_SCAN_FAILED_DETAIL =
        "Grund: {reason} (alle {n} Standorte, auch nach erneutem Versuch). Für {when} "
            + "({weekday} {date}) liegt keine Messung vor; kein Wert wird als 0 dargestellt.";
    /** {@code {when}} of {@code CAPACITY_SCAN_FAILED_DETAIL}: the target day is today / „heute". */
    static final String WHEN_TODAY = "heute";
    /** {@code {when}}: the target day is tomorrow / „morgen". */
    static final String WHEN_TOMORROW = "morgen";
    /** {@code {when}}: any other target day / „den Zieltag". */
    static final String WHEN_TARGET_DAY = "den Zieltag";
    static final String CAPACITY_SCAN_PARTIAL =
        "Scan vom {date} unvollständig: {n} Standorte nicht messbar.";
    static final String CAPACITY_SCAN_PARTIAL_ONE =
        "Scan vom {date} unvollständig: 1 Standort nicht messbar.";
    static final String WARN_STALE = "Letzte Auswertung: {date} – Daten möglicherweise veraltet";

    static final String REASON_SOFTNET_UNAVAILABLE = "SoftNet nicht erreichbar";
    static final String REASON_SOFTNET_FORBIDDEN = "kein Zugriff auf die SoftNet-Gruppe";
    static final String REASON_TIMEOUT = "Zeitüberschreitung";
    static final String REASON_NO_REFERENCE_SERVICE = "Referenz-Leistung nicht im Leistungskatalog";
    static final String REASON_NO_BRAND = "keine Marke hinterlegt";
    static final String REASON_UNEXPECTED_RESPONSE = "unerwartete SoftNet-Antwort";

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final double EPSILON = 1e-9;

    private CapacityViewModelBuilder() {
    }

    static CapacityViewModel build(CapacityHistory history, Map<String, String> branchNames,
                                   boolean showSlotCount, LocalDate today, Instant runningSince) {
        SortedMap<LocalDate, CapacityScan> scans = history == null || history.scans() == null
            ? Collections.emptySortedMap() : history.scans();
        Map<String, String> names = branchNames == null ? Map.of() : branchNames;
        LocalDate collectingSince = scans.isEmpty() ? null : scans.firstKey();

        LocalDate latestDay = null;
        CapacityScan latest = null;
        LocalDate shownDay = null;
        CapacityScan shown = null;
        for (Map.Entry<LocalDate, CapacityScan> entry : scans.entrySet()) {
            if (!isFinal(entry.getValue())) {
                continue;
            }
            latestDay = entry.getKey();
            latest = entry.getValue();
            if (latest.state() != ScanState.FAILED) {
                shownDay = latestDay;
                shown = latest;
            }
        }
        if (latest == null) {
            // No scan yet (or only the running claim): rows stay empty — S1 shows
            // CAPACITY_NO_SCAN or CAPACITY_EMPTY_FIRST_SCAN, never zeros (§5.2, BR-19).
            return new CapacityViewModel(null, null, null, false, null, runningSince,
                collectingSince, List.of(), List.of(), List.of());
        }

        boolean latestFailed = latest.state() == ScanState.FAILED;
        boolean fallback = latestFailed && shown != null;
        LocalDate displayDay = shown != null ? shownDay : latestDay;
        CapacityScan display = shown != null ? shown : latest;
        LocalDate targetDay = targetOf(displayDay, display);
        ScanState state = latestFailed ? ScanState.FAILED : display.state();
        ScanState fallbackState = fallback ? display.state() : null;

        List<LocalDate> gridDays =
            CapacityCalendar.lastWorkdays(latestDay, CapacityViewModel.LAST_SCAN_DAYS);
        int scansInWindow = 0;
        for (LocalDate day : gridDays) {
            if (isFinal(scans.get(day))) {
                scansInWindow++;
            }
        }

        List<CapacityRow> rows = new ArrayList<>(names.size());
        for (Map.Entry<String, String> branch : names.entrySet()) {
            rows.add(row(branch.getKey(), branch.getValue(), display, targetDay, gridDays, scans,
                scansInWindow, showSlotCount, today));
        }
        rows.sort(rowOrder());

        List<String> warnings = new ArrayList<>();
        if (latestFailed) {
            warnings.add(failedWarning(latestDay, latest, fallback ? displayDay : null));
            String detail = failedDetail(latestDay, latest, today);
            if (detail != null) {
                warnings.add(detail);
            }
        }
        if (display.state() == ScanState.PARTIAL) {
            warnings.add(partialWarning(displayDay, display));
        }
        if (isStale(latestDay, today)) {
            warnings.add(WARN_STALE.replace("{date}", CapacityCalendar.DATE.format(latestDay)));
        }

        return new CapacityViewModel(displayDay, targetDay, state, fallback, fallbackState,
            runningSince, collectingSince, gridDays, List.copyOf(rows), List.copyOf(warnings),
            display.startedAt(), display.finishedAt(), latestDay);
    }

    // ---- shared with the scheduler / mit dem Scheduler geteilt ----

    /** Day of the most recent scan with a final state, {@code null} without one. */
    static LocalDate latestFinalScanDay(CapacityHistory history) {
        if (history == null || history.scans() == null) {
            return null;
        }
        LocalDate latest = null;
        for (Map.Entry<LocalDate, CapacityScan> entry : history.scans().entrySet()) {
            if (isFinal(entry.getValue())) {
                latest = entry.getKey();
            }
        }
        return latest;
    }

    /** Whether the most recent final scan lies more than {@value #STALE_AFTER_DAYS} days back. */
    static boolean isStale(LocalDate latestScanDay, LocalDate today) {
        return latestScanDay != null && today != null
            && latestScanDay.isBefore(today.minusDays(STALE_AFTER_DAYS));
    }

    /** Stored target day, or the next working day after the scan day (tolerant read). */
    static LocalDate targetOf(LocalDate scanDay, CapacityScan scan) {
        return scan != null && scan.targetDay() != null
            ? scan.targetDay() : CapacityCalendar.nextWorkday(scanDay);
    }

    /** Level of one probe result (§10.4 „Abbildung Scan → Stufe") / Stufe eines Ergebnisses. */
    static CapacityLevel levelOf(ProbeResult result) {
        if (result == null || result.status() == null || result.status() == ProbeStatus.ERROR) {
            return CapacityLevel.NOT_MEASURABLE;
        }
        if (result.status() == ProbeStatus.NONE) {
            return CapacityLevel.NONE_FOUND;
        }
        Integer workdays = result.workdaysToFirstFree();
        if (workdays == null || workdays < 0) {
            // Tolerant read: a missing counter is shown as „—", never guessed (E6 (2)).
            return CapacityLevel.NOT_MEASURABLE;
        }
        if (workdays == 0) {
            return CapacityLevel.FREE_NEXT_DAY;
        }
        return workdays <= 4 ? CapacityLevel.WITHIN_4_WORKDAYS : CapacityLevel.TIGHT;
    }

    /** Grid cell of a level (§10.4 „Abbildung Stufe → Verlaufszelle") / Verlaufszelle. */
    static CapacityDayState dayStateOf(CapacityLevel level) {
        return switch (level) {
            case FREE_NEXT_DAY -> CapacityDayState.FREE;
            case WITHIN_4_WORKDAYS -> CapacityDayState.TIGHT_DAY;
            case TIGHT, NONE_FOUND -> CapacityDayState.BOOKED;
            case NOT_MEASURABLE -> CapacityDayState.NOT_MEASURABLE;
        };
    }

    /**
     * The 10-day classification (BR-26) / die 10-Tage-Einordnung. With a full window of
     * {@value CapacityViewModel#LAST_SCAN_DAYS} scans the denominator is 10 (absolute thresholds
     * ≥ 7 / ≤ 2), otherwise the measurable days (ratio „sinngemäß").
     */
    static CapacityTrend classify(int freeDays, int measurableDays, int scansInWindow) {
        if (measurableDays < CapacityViewModel.MIN_MEASURABLE_DAYS) {
            return CapacityTrend.TOO_FEW_DAYS;
        }
        int denominator = scansInWindow >= CapacityViewModel.LAST_SCAN_DAYS
            ? CapacityViewModel.LAST_SCAN_DAYS : measurableDays;
        double ratio = (double) freeDays / denominator;
        if (ratio >= CapacityViewModel.MOSTLY_FREE_MIN - EPSILON) {
            return CapacityTrend.MOSTLY_FREE;
        }
        if (ratio <= CapacityViewModel.MOSTLY_BOOKED_MAX + EPSILON) {
            return CapacityTrend.MOSTLY_BOOKED;
        }
        return CapacityTrend.MIXED;
    }

    /** Level label relative to the day of the build (BR-25, §17 {@code LEVEL_*}) / Beschriftung. */
    static String labelOf(CapacityLevel level, Integer workdays, LocalDate targetDay,
                          LocalDate today) {
        return switch (level) {
            case FREE_NEXT_DAY -> {
                if (targetDay.equals(today)) {
                    yield LEVEL_FREE_TODAY;
                }
                if (today != null && targetDay.equals(today.plusDays(1))) {
                    yield LEVEL_FREE_TOMORROW;
                }
                yield LEVEL_FREE_ON
                    .replace("{weekday}", CapacityCalendar.weekdayShort(targetDay))
                    .replace("{date}", CapacityCalendar.SHORT_DATE.format(targetDay));
            }
            case WITHIN_4_WORKDAYS -> workdays != null && workdays == 1
                ? LEVEL_WITHIN_ONE_WORKDAY
                : LEVEL_WITHIN_WORKDAYS.replace("{n}", String.valueOf(workdays));
            case TIGHT -> LEVEL_TIGHT;
            case NONE_FOUND -> LEVEL_NONE_FOUND;
            case NOT_MEASURABLE -> LEVEL_NOT_MEASURABLE;
        };
    }

    /** German reason text (§17 {@code REASON_*}) / Grund als Text. */
    static String reasonLabel(ProbeReason reason) {
        return switch (reason) {
            case SOFTNET_UNAVAILABLE -> REASON_SOFTNET_UNAVAILABLE;
            case SOFTNET_FORBIDDEN -> REASON_SOFTNET_FORBIDDEN;
            case TIMEOUT -> REASON_TIMEOUT;
            case NO_REFERENCE_SERVICE -> REASON_NO_REFERENCE_SERVICE;
            case NO_BRAND -> REASON_NO_BRAND;
            case UNEXPECTED_RESPONSE -> REASON_UNEXPECTED_RESPONSE;
        };
    }

    // ---- internals / Interna ----

    private static boolean isFinal(CapacityScan scan) {
        return scan != null && scan.state() != null && scan.state() != ScanState.RUNNING;
    }

    private static CapacityRow row(String branchId, String name, CapacityScan display,
                                   LocalDate targetDay, List<LocalDate> gridDays,
                                   SortedMap<LocalDate, CapacityScan> scans, int scansInWindow,
                                   boolean showSlotCount, LocalDate today) {
        ProbeResult current = resultOf(display, branchId);
        CapacityLevel level = levelOf(current);
        boolean measured = level != CapacityLevel.NOT_MEASURABLE && level != CapacityLevel.NONE_FOUND;
        Integer workdays = measured ? current.workdaysToFirstFree() : null;
        String reasonKey = level == CapacityLevel.NOT_MEASURABLE && current != null
            && current.reason() != null ? current.reason().key() : null;
        Integer slots = showSlotCount && measured ? current.freeSlotsTargetDay() : null;
        LocalDate firstFree = measured ? current.firstFreeDate() : null;

        List<CapacityDayState> cells = new ArrayList<>(gridDays.size());
        List<String> reasons = new ArrayList<>(gridDays.size());
        int freeDays = 0;
        int measurableDays = 0;
        for (LocalDate day : gridDays) {
            CapacityScan scan = scans.get(day);
            ProbeResult result = isFinal(scan) ? resultOf(scan, branchId) : null;
            CapacityDayState cell = dayStateOf(levelOf(result));
            cells.add(cell);
            reasons.add(cell == CapacityDayState.NOT_MEASURABLE && result != null
                && result.reason() != null ? result.reason().key() : null);
            if (cell.measurable()) {
                measurableDays++;
            }
            if (cell == CapacityDayState.FREE) {
                freeDays++;
            }
        }
        return new CapacityRow(name == null ? branchId : name, level,
            labelOf(level, workdays, targetDay, today), workdays, reasonKey, slots,
            Collections.unmodifiableList(cells), Collections.unmodifiableList(reasons),
            freeDays, measurableDays, classify(freeDays, measurableDays, scansInWindow), firstFree);
    }

    private static ProbeResult resultOf(CapacityScan scan, String branchId) {
        return scan == null || scan.branches() == null ? null : scan.branches().get(branchId);
    }

    /**
     * BR-26 order: classification (mostly-free → mixed → mostly-booked → too-few-days), then
     * {@code freeDays} descending, then the level of the shown scan (free-next-day, ascending
     * workdays, none-found, not-measurable), ties by name ({@code Collator} DE).
     */
    private static Comparator<CapacityRow> rowOrder() {
        Collator collator = Collator.getInstance(Locale.GERMAN);
        return Comparator.<CapacityRow>comparingInt(r -> r.classification().ordinal())
            .thenComparing(Comparator.<CapacityRow>comparingInt(r -> r.freeDays()).reversed())
            .thenComparingInt(CapacityViewModelBuilder::levelRank)
            .thenComparing(CapacityRow::branchName, collator);
    }

    private static int levelRank(CapacityRow row) {
        return switch (row.level()) {
            case FREE_NEXT_DAY -> 0;
            case WITHIN_4_WORKDAYS, TIGHT -> row.workdays() == null ? Integer.MAX_VALUE - 2
                : row.workdays();
            case NONE_FOUND -> Integer.MAX_VALUE - 1;
            case NOT_MEASURABLE -> Integer.MAX_VALUE;
        };
    }

    private static String failedWarning(LocalDate failedDay, CapacityScan failed,
                                        LocalDate fallbackDay) {
        String time = failed.startedAt() == null ? ""
            : FAILED_TIME.replace("{time}", CapacityCalendar.TIME.format(failed.startedAt().atZone(ZONE)));
        String template = fallbackDay == null ? CAPACITY_SCAN_FAILED_NO_FALLBACK : CAPACITY_SCAN_FAILED;
        String text = template
            .replace("{date}", CapacityCalendar.DATE.format(failedDay))
            .replace("{time}", time);
        return fallbackDay == null ? text
            : text.replace("{shown}", CapacityCalendar.DATE.format(fallbackDay));
    }

    /**
     * {@code CAPACITY_SCAN_FAILED_DETAIL}: the reason(s) of the failed scan, most frequent first
     * (ties in enum order), and the target day relative to today (BR-25 spirit) / Gründe des
     * fehlgeschlagenen Scans. {@code null} when the scan carries no reason at all.
     */
    private static String failedDetail(LocalDate failedDay, CapacityScan failed, LocalDate today) {
        Map<String, ProbeResult> branches = failed.branches() == null ? Map.of() : failed.branches();
        Map<ProbeReason, Integer> counts = new EnumMap<>(ProbeReason.class);
        for (ProbeResult result : branches.values()) {
            if (result != null && result.reason() != null) {
                counts.merge(result.reason(), 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return null;
        }
        Comparator<Map.Entry<ProbeReason, Integer>> byFrequency =
            Map.Entry.<ProbeReason, Integer>comparingByValue().reversed();
        String reasons = counts.entrySet().stream()
            .sorted(byFrequency.thenComparing(Map.Entry.<ProbeReason, Integer>comparingByKey()))
            .map(entry -> reasonLabel(entry.getKey()))
            .collect(Collectors.joining(", "));
        LocalDate target = targetOf(failedDay, failed);
        String when = target.equals(today) ? WHEN_TODAY
            : today != null && target.equals(today.plusDays(1)) ? WHEN_TOMORROW : WHEN_TARGET_DAY;
        return CAPACITY_SCAN_FAILED_DETAIL
            .replace("{reason}", reasons)
            .replace("{n}", String.valueOf(branches.size()))
            .replace("{when}", when)
            .replace("{weekday}", CapacityCalendar.weekdayShort(target))
            .replace("{date}", CapacityCalendar.SHORT_DATE.format(target));
    }

    /** {@code CAPACITY_SCAN_PARTIAL} with the number of error branches (TC-51) / Partial-Hinweis. */
    private static String partialWarning(LocalDate day, CapacityScan scan) {
        long errors = scan.branches() == null ? 0 : scan.branches().values().stream()
            .filter(r -> r != null && r.status() == ProbeStatus.ERROR)
            .count();
        String template = errors == 1 ? CAPACITY_SCAN_PARTIAL_ONE : CAPACITY_SCAN_PARTIAL;
        return template
            .replace("{date}", CapacityCalendar.DATE.format(day))
            .replace("{n}", String.valueOf(errors));
    }
}
