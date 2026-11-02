package com.autohaus.backend.reporting;

import com.autohaus.backend.capacity.CapacityLevel;
import com.autohaus.backend.capacity.CapacityRow;
import com.autohaus.backend.capacity.CapacityTrend;
import com.autohaus.backend.capacity.CapacityViewModel;
import com.autohaus.backend.capacity.ScanState;
import com.autohaus.backend.usage.insights.BranchRankingRow;
import com.autohaus.backend.usage.insights.Cell;
import com.autohaus.backend.usage.insights.CellState;
import com.autohaus.backend.usage.insights.InsightsViewModel;
import org.springframework.stereotype.Component;

import java.text.Collator;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Computes the S0 impulses I-1..I-4 from the already suppressed view models (REQ-038 BR-21,
 * BR-25) / berechnet die Handlungsimpulse. A pure function over in-memory data — no AI, no
 * randomness, no I/O and no time-of-day dependent choice. / Reine Funktion ohne I/O.
 *
 * <p><b>Thresholds (BR-21, fixed):</b>
 * <ul>
 *   <li><b>I-1</b> {@code free-target-day}: branches with level {@code free-next-day} in the most
 *       recent scan ({@code complete}/{@code partial}, scan day today or yesterday), at most 5,
 *       sorted by K-2 descending while K-2 is shown, otherwise by name; the title is relative to
 *       the day of retrieval (BR-25).</li>
 *   <li><b>I-2</b> {@code promo}: 10-day classification {@code mostly-free} <b>and</b> R-1 of the
 *       closed month in the lower third — position &gt; ⌈2n/3⌉ in the order „ranked rows, then
 *       {@code rk-low}" over all n branches; {@code rk-low} branches always count.</li>
 *   <li><b>I-3</b> {@code bottleneck}: 10-day classification {@code mostly-booked}.</li>
 *   <li><b>I-4</b> {@code top}: the three ranked branches with the most online bookings of the
 *       closed month (visible values only) and the global T-2 share of that month (n ≥ 20
 *       only — the view model already withholds it otherwise).</li>
 * </ul>
 * An impulse without its data basis (source off, „zu wenig Daten", no closed month, failed scan)
 * is omitted; I-1..I-3 are omitted as a whole while a fallback scan is shown (BR-14). /
 * Fehlt die Datenbasis, entfällt der Impuls.
 *
 * <p><b>Order of names:</b> I-2 and I-3 list names alphabetically ({@link Collator} DE) — never by
 * a hidden value (BR-9 Nr. 5); I-4 keeps the public ranking order. / Namen alphabetisch, I-4 nach
 * Rang.
 *
 * <p><b>Source line</b> ({@code Impulse.source}, {@code .ib-src}): „Scan {dd.MM.}" for the
 * capacity-based I-1/I-3, the month label (e.g. „Oktober 2026") for the booking-based I-2/I-4 —
 * the S0 renderer uses it as the {@code {month}} of the texts. / Quellenangabe je Impuls.
 */
@Component
public class BriefingCalculator {

    /** I-1 key / Schlüssel „Luft am Zieltag". */
    public static final String KEY_FREE_TARGET_DAY = "free-target-day";
    /** I-2 key / Schlüssel „Werbe-Kandidat". */
    public static final String KEY_PROMO = "promo";
    /** I-3 key / Schlüssel „Engpass". */
    public static final String KEY_BOTTLENECK = "bottleneck";
    /** I-4 key / Schlüssel „Renner &amp; Abendbuchungen". */
    public static final String KEY_TOP = "top";

    /** At most this many names in I-1 (BR-21) / höchstens 5 Standorte. */
    static final int MAX_FREE_BRANCHES = 5;

    /** Number of top branches in I-4 (BR-21) / Anzahl der Renner. */
    static final int TOP_BRANCHES = 3;

    // ---- titles and source line, German literals (§17 IMPULSE_*) / Beschriftungen ----

    /** §17 {@code IMPULSE_FREE_TODAY_TITLE}. */
    static final String IMPULSE_FREE_TODAY_TITLE = "Luft heute ({weekday} {date})";
    /** §17 {@code IMPULSE_FREE_TOMORROW_TITLE}. */
    static final String IMPULSE_FREE_TOMORROW_TITLE = "Luft morgen ({weekday} {date})";
    /** §17 {@code IMPULSE_FREE_ON_TITLE}. */
    static final String IMPULSE_FREE_ON_TITLE = "Luft am {weekday} {date}";
    /** §17 {@code IMPULSE_PROMO_TITLE}. */
    static final String IMPULSE_PROMO_TITLE = "Werbe-Kandidat";
    /** §17 {@code IMPULSE_BOTTLENECK_TITLE}. */
    static final String IMPULSE_BOTTLENECK_TITLE = "Engpass";
    /** §17 {@code IMPULSE_TOP_TITLE}. */
    static final String IMPULSE_TOP_TITLE = "Renner & Abendbuchungen";
    /** §17 {@code IMPULSE_SOURCE} — source line of the capacity-based tiles. */
    static final String IMPULSE_SOURCE = "Scan {date}";

    /** Short date of titles and source line / Kurzdatum, z. B. {@code 20.11.}. */
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("dd.MM.");

    /** Weekday abbreviations Mo..So (§17 {@code WEEKDAY_*}) / Wochentagskürzel. */
    private static final String[] WEEKDAYS = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};

    /**
     * @param insights K2 view, {@code null} when switched off (then no I-2/I-4); a view with
     *     {@code bookingsEnabled == false} (funnel only, §5.1 row 5) counts as off
     * @param capacity K1 view, {@code null} when switched off (then no I-1/I-2/I-3)
     * @param today the Berlin calendar day of retrieval — titles relative to it (BR-25)
     * @return the briefing, {@code null} when both sources are off (S0 omitted, BR-4)
     */
    public Briefing compute(InsightsViewModel insights, CapacityViewModel capacity, LocalDate today) {
        InsightsViewModel bookings = insights != null && insights.bookingsEnabled() ? insights : null;
        if (bookings == null && capacity == null) {
            return null;
        }
        List<Impulse> impulses = new ArrayList<>(4);
        boolean scanUsable = scanUsable(capacity);

        Impulse free = scanUsable && today != null ? freeTargetDay(capacity, today) : null;
        if (free != null) {
            impulses.add(free);
        }
        Impulse promo = scanUsable && bookings != null ? promo(capacity, bookings) : null;
        if (promo != null) {
            impulses.add(promo);
        }
        Impulse bottleneck = scanUsable ? bottleneck(capacity) : null;
        if (bottleneck != null) {
            impulses.add(bottleneck);
        }
        Impulse top = bookings != null ? top(bookings) : null;
        if (top != null) {
            impulses.add(top);
        }
        return new Briefing(List.copyOf(impulses));
    }

    /**
     * Whether the most recent scan may feed I-1..I-3: present, not a fallback after a failed scan
     * (BR-14, AC-10) and {@code complete}/{@code partial}. / Ob der jüngste Scan taugt.
     */
    private static boolean scanUsable(CapacityViewModel capacity) {
        return capacity != null
            && !capacity.showingFallbackScan()
            && (capacity.state() == ScanState.COMPLETE || capacity.state() == ScanState.PARTIAL)
            && capacity.scanDay() != null
            && capacity.rows() != null;
    }

    /** I-1 „Luft am Zieltag" (BR-21, BR-25). */
    private static Impulse freeTargetDay(CapacityViewModel capacity, LocalDate today) {
        LocalDate scanDay = capacity.scanDay();
        LocalDate targetDay = capacity.targetDay();
        if (targetDay == null || !(scanDay.equals(today) || scanDay.equals(today.minusDays(1)))) {
            return null;
        }
        List<CapacityRow> free = new ArrayList<>();
        for (CapacityRow row : capacity.rows()) {
            if (row != null && row.level() == CapacityLevel.FREE_NEXT_DAY && row.branchName() != null) {
                free.add(row);
            }
        }
        if (free.isEmpty()) {
            return null;
        }
        Collator collator = collator();
        Comparator<CapacityRow> byName = (a, b) -> collator.compare(a.branchName(), b.branchName());
        boolean slotCountShown = free.stream().anyMatch(r -> r.freeSlotsTargetDay() != null);
        if (slotCountShown) {
            // K-2 descending while it is shown; a missing count sorts last, ties by name.
            free.sort(Comparator.<CapacityRow, Integer>comparing(
                    r -> r.freeSlotsTargetDay() == null ? Integer.MIN_VALUE : r.freeSlotsTargetDay(),
                    Comparator.reverseOrder())
                .thenComparing(byName));
        } else {
            free.sort(byName);
        }
        List<CapacityRow> named = free.stream().limit(MAX_FREE_BRANCHES).toList();
        List<String> names = named.stream().map(CapacityRow::branchName).toList();
        // K-2 per named branch while it is shown (§17 IMPULSE_FREE_TEXT); Stream.toList keeps nulls.
        List<Integer> values = slotCountShown
            ? named.stream().map(CapacityRow::freeSlotsTargetDay).toList() : null;
        return new Impulse(KEY_FREE_TARGET_DAY, freeTitle(targetDay, today), names, null,
            scanSource(scanDay), values);
    }

    /**
     * Title of I-1 relative to the day of retrieval (BR-25) / Titel relativ zum Abruftag:
     * target day today → „Luft heute (…)", tomorrow → „Luft morgen (…)", otherwise „Luft am …".
     */
    static String freeTitle(LocalDate targetDay, LocalDate today) {
        String template;
        if (targetDay.equals(today)) {
            template = IMPULSE_FREE_TODAY_TITLE;
        } else if (targetDay.equals(today.plusDays(1))) {
            template = IMPULSE_FREE_TOMORROW_TITLE;
        } else {
            template = IMPULSE_FREE_ON_TITLE;
        }
        return template.replace("{weekday}", weekday(targetDay.getDayOfWeek()))
            .replace("{date}", SHORT_DAY.format(targetDay));
    }

    /** I-2 „Werbe-Kandidat" (BR-21). */
    private static Impulse promo(CapacityViewModel capacity, InsightsViewModel insights) {
        YearMonth month = insights.closedMonth();
        if (month == null) {
            return null;
        }
        Set<String> lowerThird = lowerThird(insights.ranking(), insights.belowThreshold());
        if (lowerThird.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (CapacityRow row : capacity.rows()) {
            if (row != null && row.classification() == CapacityTrend.MOSTLY_FREE
                && row.branchName() != null && lowerThird.contains(row.branchName())
                && !names.contains(row.branchName())) {
                names.add(row.branchName());
            }
        }
        if (names.isEmpty()) {
            return null;
        }
        names.sort(collator());
        return new Impulse(KEY_PROMO, IMPULSE_PROMO_TITLE, List.copyOf(names), null, monthLabel(month));
    }

    /**
     * Names in the lower third of the closed month (BR-21 I-2) / Standorte im unteren Drittel:
     * the overall order is the ranked rows (descending) followed by {@code rk-low}; n counts all
     * of them, positions {@code > ⌈2n/3⌉} are the lower third, and every {@code rk-low} branch
     * belongs to it (0–4 bookings). Example n = 10 → positions 8–10. / Beispiel n = 10.
     */
    static Set<String> lowerThird(List<BranchRankingRow> ranking, List<BranchRankingRow> below) {
        List<BranchRankingRow> ranked = ranking == null ? List.of() : ranking;
        List<BranchRankingRow> low = below == null ? List.of() : below;
        int n = ranked.size() + low.size();
        int cutoff = (2 * n + 2) / 3; // ⌈2n/3⌉ in integer arithmetic
        Set<String> names = new HashSet<>();
        for (int i = 0; i < ranked.size(); i++) {
            BranchRankingRow row = ranked.get(i);
            if (i + 1 > cutoff && row != null && row.branchName() != null) {
                names.add(row.branchName());
            }
        }
        for (BranchRankingRow row : low) {
            if (row != null && row.branchName() != null) {
                names.add(row.branchName());
            }
        }
        return names;
    }

    /** I-3 „Engpass" (BR-21). */
    private static Impulse bottleneck(CapacityViewModel capacity) {
        List<String> names = new ArrayList<>();
        for (CapacityRow row : capacity.rows()) {
            if (row != null && row.classification() == CapacityTrend.MOSTLY_BOOKED
                && row.branchName() != null && !names.contains(row.branchName())) {
                names.add(row.branchName());
            }
        }
        if (names.isEmpty()) {
            return null;
        }
        names.sort(collator());
        return new Impulse(KEY_BOTTLENECK, IMPULSE_BOTTLENECK_TITLE, List.copyOf(names), null,
            scanSource(capacity.scanDay()));
    }

    /** I-4 „Renner &amp; Abendbuchungen" (BR-21). */
    private static Impulse top(InsightsViewModel insights) {
        YearMonth month = insights.closedMonth();
        if (month == null) {
            return null;
        }
        List<String> names = new ArrayList<>(TOP_BRANCHES);
        if (insights.ranking() != null) {
            for (BranchRankingRow row : insights.ranking()) {
                if (names.size() >= TOP_BRANCHES) {
                    break;
                }
                if (row != null && row.branchName() != null && visible(row.bookings())) {
                    names.add(row.branchName());
                }
            }
        }
        Cell share = insights.closedMonthOffHoursShare();
        Integer percent = visible(share) && share.percent() != null
            ? (int) Math.round(share.percent()) : null;
        if (names.isEmpty() && percent == null) {
            return null;
        }
        return new Impulse(KEY_TOP, IMPULSE_TOP_TITLE, List.copyOf(names), percent, monthLabel(month));
    }

    private static boolean visible(Cell cell) {
        return cell != null && cell.state() == CellState.VALUE;
    }

    /** „Scan 19.11." (§17 {@code IMPULSE_SOURCE}). */
    private static String scanSource(LocalDate scanDay) {
        return IMPULSE_SOURCE.replace("{date}", SHORT_DAY.format(scanDay));
    }

    /** German month label, e.g. „Oktober 2026" / deutscher Monatsname mit Jahr. */
    static String monthLabel(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, Locale.GERMAN) + " "
            + month.getYear();
    }

    /** Weekday abbreviation Mo..So (§17 {@code WEEKDAY_*}) / Wochentagskürzel. */
    static String weekday(DayOfWeek day) {
        return WEEKDAYS[day.getValue() - 1];
    }

    /** A fresh German collator — {@link Collator} instances are not shared across threads. */
    private static Collator collator() {
        return Collator.getInstance(Locale.GERMAN);
    }
}
