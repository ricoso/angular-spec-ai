package com.autohaus.backend.usage;

import com.autohaus.backend.config.UsageStatsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Turns day rows into the display periods of the status page / setzt aus Tageszeilen die
 * Anzeigezeiträume der Statusseite zusammen (REQ-037 §4 Step 3).
 *
 * <p>Deterministic and free of I/O: the blob history and the live days are handed in, the service
 * only sums, labels and divides. That keeps it testable with a fixed {@link Clock} (TC-5, TC-18)
 * and keeps every query in the scheduler. / Kein I/O, rein deterministisch.
 *
 * <p><b>Two sources, one clear boundary (BR-9) / zwei Quellen, klare Grenze:</b> days that are in
 * the blob come <b>exclusively</b> from the blob; all days <b>after</b> the last persisted day —
 * the running day and, while the nightly rollup has not run or has failed, the previous day too —
 * come <b>exclusively</b> live from Log Analytics, at most {@value #MAX_LIVE_DAYS} days back. No
 * day is ever counted from both sources. Without this rule the page would silently lose a whole
 * day every night between 00:00 and the 03:30 rollup. / Blob gewinnt, Live schließt den Rand; nie
 * ein Tag aus beiden Quellen.
 *
 * <p><b>Rates (BR-6) / Quoten:</b> search, booking and close rate are session based and rounded to
 * one decimal place. A divisor of {@code 0} yields {@code null}, rendered as „—" — never
 * {@code 0,0 %}, never {@code NaN}. / Divisor 0 ergibt {@code null}.
 *
 * <p><b>Monthly series (BR-14) / Monatsreihe:</b> the twelve month rows of the year block are
 * built purely by summing the very same day rows (AC-18). They need no additional query and are
 * therefore no new data category. / Keine zusätzliche Datenquelle.
 *
 * <p>The bean is unconditional — without the scheduler it is simply never fed (Alt Flow C). /
 * Bean ist unbedingt.
 */
@Component
public class UsageStatsService {

    private static final Logger log = LoggerFactory.getLogger(UsageStatsService.class);

    /**
     * How far back live days may close the gap at the edge (BR-9) / wie weit Live-Tage den Rand
     * schließen dürfen: today and the two days before it. Anything older is the catch-up's job
     * (AC-9).
     */
    public static final int MAX_LIVE_DAYS = 3;

    /** Fallback when the property is unset — same zone as the rest of the feature (BR-7). */
    private static final String DEFAULT_ZONE = "Europe/Berlin";

    /** Display date format of the status page / Datumsformat der Statusseite. */
    private static final DateTimeFormatter DISPLAY_DATE =
        DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY);

    /** Percent with one decimal place is computed in tenths (BR-6). / Rechnung in Zehnteln. */
    private static final double TENTHS_PER_HUNDRED_PERCENT = 1000.0;

    private final ZoneId zone;
    private final Clock clock;

    @Autowired
    public UsageStatsService(UsageStatsProperties properties) {
        this.zone = resolveZone(properties);
        this.clock = Clock.system(this.zone);
    }

    /** Visible for testing / für Tests: lets TC-5 and TC-18 pin "today" to a fixed instant. */
    UsageStatsService(UsageStatsProperties properties, Clock clock) {
        this.zone = resolveZone(properties);
        this.clock = clock;
    }

    /**
     * Builds the complete render input: current month, running year, the twelve month rows and the
     * availability flags / baut das vollständige Render-Eingabemodell.
     *
     * <p>Blob days and live days are merged per BR-9 before anything is summed, so no day is
     * counted twice. {@code collectingSince} and {@code lastRollupDay} describe the <b>persisted</b>
     * history only — they say how far the blob reaches, not how far the page can see. / Blob-Tage
     * und Live-Tage werden nach BR-9 zusammengeführt.
     *
     * @param history the deserialized blob file, may be {@link UsageHistory#empty()} or
     *     {@code null} / deserialisierte Blob-Datei
     * @param historyAvailable {@code false} after a blob failure (E2) → hint on the page (TC-9)
     * @param liveDays live day rows from Log Analytics, keyed by calendar day, may be empty
     * @param liveAvailable {@code false} after a Log Analytics failure (E1) → hint (TC-8)
     * @param liveAsOf timestamp of the last successful live refresh, {@code null} when never
     * @return a fully populated {@link UsageStats}; never {@code null}, never with {@code null}
     *     structure fields
     */
    public UsageStats buildPeriods(UsageHistory history, boolean historyAvailable,
                                   Map<LocalDate, DailyUsage> liveDays, boolean liveAvailable,
                                   Instant liveAsOf) {
        LocalDate today = today();
        SortedMap<LocalDate, DailyUsage> blobDays = parseHistory(history);
        SortedMap<LocalDate, DailyUsage> days = merge(blobDays, liveDays, today);

        LocalDate monthFrom = today.withDayOfMonth(1);
        LocalDate yearFrom = today.withDayOfYear(1);

        PeriodUsage month = buildPeriod(
            "Aktueller Monat (" + monthName(today.getMonth()) + " " + today.getYear() + ")",
            days, monthFrom, today);
        PeriodUsage year = buildPeriod("Jahresübersicht " + today.getYear(), days, yearFrom, today);

        return new UsageStats(
            month,
            year,
            buildMonthlySeries(days, today),
            liveAsOf,
            liveAvailable,
            historyAvailable,
            blobDays.isEmpty() ? null : blobDays.firstKey(),
            blobDays.isEmpty() ? null : blobDays.lastKey());
    }

    /**
     * The twelve month rows of the running year, January to December (AC-18, BR-14) / die zwölf
     * Monatszeilen des laufenden Jahres.
     *
     * <p>Built by summing the same day rows that already feed month and year — no additional
     * query. Months before collection started and future months carry {@code hasData == false} and
     * render as „—", not as „0" (AC-12). The running month ends at {@code today}, past and future
     * months at their last calendar day (§12.2). / Reine Summation derselben Tageszeilen.
     *
     * @param days merged day rows (blob + live), keyed by calendar day / zusammengeführte Tage
     * @param today the current day in the display zone / aktueller Tag in der Anzeigezone
     * @return exactly twelve entries in calendar order / genau zwölf Einträge
     */
    public List<PeriodUsage> buildMonthlySeries(SortedMap<LocalDate, DailyUsage> days,
                                                LocalDate today) {
        List<PeriodUsage> months = new ArrayList<>(12);
        for (Month month : Month.values()) {
            LocalDate from = LocalDate.of(today.getYear(), month, 1);
            LocalDate to = month == today.getMonth()
                ? today
                : from.withDayOfMonth(from.lengthOfMonth());
            months.add(buildPeriod(monthName(month), days, from, to));
        }
        return List.copyOf(months);
    }

    /**
     * One session-based conversion rate, rounded to one decimal place (BR-6) / eine
     * sitzungsbasierte Quote auf eine Nachkommastelle.
     *
     * <p>{@code 431 / 954} → {@code 45.2}. A divisor of {@code 0} — and a negative or impossible
     * input — yields {@code null}, which the renderer shows as „—". Never {@code NaN}, never
     * {@code Infinity}, never a misleading {@code 0,0 %} (TC-6). / Divisor 0 ergibt {@code null}.
     *
     * @param numerator e.g. sessions with a search / z. B. Sitzungen mit Terminsuche
     * @param denominator e.g. visits / z. B. Besuche
     * @return the percentage with one decimal, or {@code null} when it is undefined
     */
    public static Double computeConversionRate(long numerator, long denominator) {
        if (denominator <= 0L) {
            return null;
        }
        double ratio = (double) numerator / (double) denominator;
        return Math.round(ratio * TENTHS_PER_HUNDRED_PERCENT) / 10.0;
    }

    /**
     * Merges blob days and live days per BR-9 / führt Blob- und Live-Tage nach BR-9 zusammen.
     *
     * <p>Every day that is in the blob is taken from the blob. A live day is only used when it
     * lies <b>after</b> the last persisted day, is not in the future and is at most
     * {@value #MAX_LIVE_DAYS} days old — that is the running day plus every not-yet-rolled
     * previous day. Older gaps belong to the nightly catch-up (AC-9), not to the page. / Blob
     * gewinnt; Live schließt ausschließlich den Rand.
     *
     * @param history the blob history, may be {@code null} / Blob-Historie
     * @param liveDays live day rows, may be {@code null} / Live-Tage
     * @return the merged day rows in calendar order / zusammengeführte Tage in Kalenderfolge
     */
    public SortedMap<LocalDate, DailyUsage> mergeDays(UsageHistory history,
                                                      Map<LocalDate, DailyUsage> liveDays) {
        return merge(parseHistory(history), liveDays, today());
    }

    /** The current day in the display zone (BR-7). / aktueller Tag in der Anzeigezone. */
    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone);
    }

    /** The zone all day, month and year boundaries use (BR-7). / Zone aller Grenzen. */
    public ZoneId zone() {
        return zone;
    }

    // ---- internals ----

    private static SortedMap<LocalDate, DailyUsage> merge(SortedMap<LocalDate, DailyUsage> blobDays,
                                                          Map<LocalDate, DailyUsage> liveDays,
                                                          LocalDate today) {
        SortedMap<LocalDate, DailyUsage> merged = new TreeMap<>(blobDays);
        if (liveDays == null || liveDays.isEmpty()) {
            return merged;
        }
        LocalDate lastPersisted = blobDays.isEmpty() ? null : blobDays.lastKey();
        LocalDate oldestLive = today.minusDays(MAX_LIVE_DAYS - 1L);
        for (Map.Entry<LocalDate, DailyUsage> entry : liveDays.entrySet()) {
            LocalDate day = entry.getKey();
            if (day == null || entry.getValue() == null) {
                continue;
            }
            boolean withinWindow = !day.isAfter(today) && !day.isBefore(oldestLive);
            boolean afterBlob = lastPersisted == null || day.isAfter(lastPersisted);
            // merged.containsKey is belt and braces: a day present in the blob is never overwritten
            // by a live value, even if the blob had gaps. / Blob gewinnt immer.
            if (withinWindow && afterBlob && !merged.containsKey(day)) {
                merged.put(day, entry.getValue());
            }
        }
        return merged;
    }

    /**
     * Sums the day rows of a range into one display period / summiert die Tageszeilen eines
     * Zeitraums zu einem Anzeigezeitraum.
     *
     * <p>{@code hasData} reflects whether the range holds <b>any</b> day row at all — a month with
     * rows that are all zero has {@code hasData == true} and legitimately shows „0 Besuche" (Alt
     * Flow B), a month before collection started has {@code hasData == false} and shows „—"
     * (AC-12). / „keine Daten" ist nicht dasselbe wie lauter Nullen.
     */
    private PeriodUsage buildPeriod(String label, SortedMap<LocalDate, DailyUsage> days,
                                           LocalDate from, LocalDate to) {
        SortedMap<LocalDate, DailyUsage> range = days.subMap(from, to.plusDays(1));
        boolean hasData = !range.isEmpty();

        DailyUsage total = DailyUsage.empty();
        for (DailyUsage day : range.values()) {
            total = total.plus(day);
        }

        long visitCount = total.visits().total();
        return new PeriodUsage(
            label,
            rangeLabel(from, to),
            from,
            to,
            hasData,
            total.visits(),
            visitCount,
            total.searchRequests(),
            total.searchRequestsOk(),
            total.bookingRequests(),
            total.bookingRequestsOk(),
            total.sessionsWithSearch(),
            total.sessionsWithBooking(),
            computeConversionRate(total.sessionsWithSearch(), visitCount),
            computeConversionRate(total.sessionsWithBooking(), visitCount),
            computeConversionRate(total.sessionsWithBooking(), total.sessionsWithSearch()));
    }

    /**
     * Parses the blob's ISO date keys into a sorted map / parst die ISO-Schlüssel der Blob-Datei.
     * Unparseable keys are skipped rather than propagated — the usable rest of the file still
     * renders (E4 is decided in the blob client). / Nicht parsebare Keys werden übersprungen.
     */
    private static SortedMap<LocalDate, DailyUsage> parseHistory(UsageHistory history) {
        SortedMap<LocalDate, DailyUsage> days = new TreeMap<>();
        if (history == null || history.days() == null) {
            return days;
        }
        for (Map.Entry<String, DailyUsage> entry : history.days().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            try {
                days.put(LocalDate.parse(entry.getKey().trim()), entry.getValue());
            } catch (RuntimeException e) {
                log.debug("Skipping unparseable usage history key: {}", entry.getKey());
            }
        }
        return days;
    }

    /** „01.09.2026 – 21.09.2026 · Europe/Berlin" — display only, the dates carry the machine value. */
    private String rangeLabel(LocalDate from, LocalDate to) {
        return DISPLAY_DATE.format(from) + " – " + DISPLAY_DATE.format(to)
            + " · " + zone.getId();
    }

    /** German month name / deutscher Monatsname, z. B. „September". */
    private static String monthName(Month month) {
        return month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.GERMAN);
    }

    /** Configured zone, with a safe default so a missing property cannot break the page. */
    private static ZoneId resolveZone(UsageStatsProperties properties) {
        String configured = properties == null ? null : properties.zone();
        if (configured == null || configured.isBlank()) {
            return ZoneId.of(DEFAULT_ZONE);
        }
        try {
            return ZoneId.of(configured.trim());
        } catch (RuntimeException e) {
            log.warn("Unknown usage.zone '{}' — falling back to {}", configured, DEFAULT_ZONE);
            return ZoneId.of(DEFAULT_ZONE);
        }
    }
}
