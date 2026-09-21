package com.autohaus.backend.usage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Render input for the usage section / Render-Eingabemodell der Statistik-Sektion.
 *
 * <p>Deliberately NOT a field of {@code ReportModel}: the usage statistics are independent of the
 * (LLM-gated) error report and are composed by the {@code ReportController} (REQ-037 §1.2
 * Excluded). / Bewusst kein Feld in {@code ReportModel}.
 *
 * <p>This record is serialized directly by Jackson for {@code GET /internal/report/usage.json}, so
 * the component names are the JSON field names (§12.2). / Die Feldnamen entsprechen exakt den
 * Record-Komponenten.
 *
 * @param month the current month / aktueller Monat
 * @param year the running calendar year / laufendes Kalenderjahr
 * @param months AC-18: always 12 entries (January..December of the running year); months before
 *     collection started or in the future have {@code hasData == false} / immer 12 Einträge
 * @param liveAsOf last successful Log Analytics refresh, {@code null} when never / null, wenn nie
 * @param liveAvailable {@code false} → hint „Live-Daten derzeit nicht verfügbar"
 * @param historyAvailable {@code false} → hint „Historie derzeit nicht verfügbar"
 * @param collectingSince first persisted day, {@code null} when the history is empty
 * @param lastRollupDay most recent day in the blob; older than 3 days → {@code .us-warn} (§9)
 */
public record UsageStats(
    PeriodUsage month,
    PeriodUsage year,
    List<PeriodUsage> months,
    Instant liveAsOf,
    boolean liveAvailable,
    boolean historyAvailable,
    LocalDate collectingSince,
    LocalDate lastRollupDay
) {

    /** Display zone for all day, month and year boundaries (BR-7). / Anzeigezone. */
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Europe/Berlin");

    /** Display date format of the status page / Datumsformat der Statusseite. */
    private static final DateTimeFormatter DISPLAY_DATE =
        DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY);

    /**
     * The empty state / Leerzustand: month and year with {@code hasData == false}, 12 empty month
     * entries, {@code liveAvailable == false}, {@code historyAvailable == false}. Never throws and
     * never returns {@code null} structure fields — the section renders as „keine Daten" instead of
     * zeros (AC-12). / Wirft nie, liefert nie null-Felder.
     *
     * <p>The labels are built from the current date in {@code Europe/Berlin} (BR-7) so the headings
     * read the same as on the normal path. / Beschriftungen wie im Normalfall.
     */
    public static UsageStats unavailable() {
        LocalDate today = LocalDate.now(DISPLAY_ZONE);
        LocalDate monthFrom = today.withDayOfMonth(1);
        LocalDate yearFrom = today.withDayOfYear(1);

        PeriodUsage month = emptyPeriod(
            "Aktueller Monat (" + monthName(today.getMonth()) + " " + today.getYear() + ")",
            monthFrom,
            today);
        PeriodUsage year = emptyPeriod("Jahresübersicht " + today.getYear(), yearFrom, today);

        List<PeriodUsage> months = new ArrayList<>(12);
        for (Month m : Month.values()) {
            LocalDate from = LocalDate.of(today.getYear(), m, 1);
            months.add(emptyPeriod(monthName(m), from, from.withDayOfMonth(from.lengthOfMonth())));
        }

        return new UsageStats(month, year, List.copyOf(months), null, false, false, null, null);
    }

    /** German month name / deutscher Monatsname, z. B. „September". */
    private static String monthName(Month month) {
        return month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.GERMAN);
    }

    /** A period without any data — zero counters, {@code hasData == false}, rates „—". */
    private static PeriodUsage emptyPeriod(String label, LocalDate from, LocalDate to) {
        String rangeLabel = DISPLAY_DATE.format(from) + " – " + DISPLAY_DATE.format(to)
            + " · " + DISPLAY_ZONE.getId();
        return new PeriodUsage(
            label,
            rangeLabel,
            from,
            to,
            false,
            new DeviceBreakdown(0L, 0L, 0L, 0L),
            0L,
            0L, 0L,
            0L, 0L,
            0L, 0L,
            null, null, null);
    }
}
