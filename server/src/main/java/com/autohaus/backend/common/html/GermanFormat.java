package com.autohaus.backend.common.html;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Locale;

/**
 * German display formats of the internal status page — dates, clock times, weekday and month
 * names, counters and enumerations (REQ-038 §17 {@code WEEKDAY_*}, §14.1) / deutsche
 * Anzeigeformate der Statusseite.
 *
 * <p>Neutral like {@link HtmlText}: every section formats „19.11.2026", „19.11.", „22:31",
 * „Mo"…„So" and „Oktober 2026" through this class, so there is one formatter per pattern instead of
 * a copy in each renderer. All results are <b>plain text</b>; the caller escapes them. The page is
 * German only by design (§17), there is no locale parameter. / Ein Formatter je Muster; Ergebnisse
 * sind Klartext.
 */
public final class GermanFormat {

    /** {@code dd.MM.yyyy} — full date, e.g. „19.11.2026" / vollständiges Datum. */
    public static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** {@code dd.MM.} — date without year, e.g. „19.11." / Datum ohne Jahr. */
    public static final DateTimeFormatter DAY_SHORT_FORMAT = DateTimeFormatter.ofPattern("dd.MM.");

    /** {@code HH:mm} — clock time, e.g. „22:31" / Uhrzeit. */
    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /** §17 {@code WEEKDAY_*} — abbreviations Mo…So / Wochentagskürzel. */
    public static final List<String> WEEKDAY_SHORT = List.of("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So");

    /** Weekday names for screen readers / ausgeschriebene Wochentage für Screenreader. */
    public static final List<String> WEEKDAY_LONG = List.of(
        "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag");

    /** German month names / deutsche Monatsnamen. */
    public static final List<String> MONTH_NAMES = List.of(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember");

    /** Month abbreviations for „Sep.: 2" (§17 {@code RANK_PREVIOUS}) / Monatskürzel. */
    public static final List<String> MONTH_SHORT = List.of(
        "Jan.", "Feb.", "März", "Apr.", "Mai", "Juni",
        "Juli", "Aug.", "Sep.", "Okt.", "Nov.", "Dez.");

    /** Separator between the items of an enumeration / Trenner einer Aufzählung. */
    public static final String LIST_SEPARATOR = ", ";

    /** Joint before the last item of an enumeration („A, B und C") / Verbindung vor dem letzten Glied. */
    public static final String LIST_AND = " und ";

    private GermanFormat() {
    }

    /**
     * Counter with German thousands separators, e.g. „1.122" / Zähler mit Tausenderpunkt. The one
     * implementation behind {@code StatFormat.num} (TC-39).
     */
    public static String number(long value) {
        return String.format(Locale.GERMANY, "%,d", value);
    }

    /** „19.11.2026", „—" without a date / Tagesdatum. */
    public static String day(LocalDate date) {
        return date == null ? HtmlText.DASH_TEXT : DAY_FORMAT.format(date);
    }

    /** „19.11.", „—" without a date / Tagesdatum ohne Jahr. */
    public static String dayShort(LocalDate date) {
        return date == null ? HtmlText.DASH_TEXT : DAY_SHORT_FORMAT.format(date);
    }

    /**
     * „22:31" of a time with hour and minute ({@code LocalTime}, {@code ZonedDateTime}, …) — convert
     * an {@code Instant} to the display zone first; „—" without a time. / Uhrzeit.
     */
    public static String time(TemporalAccessor time) {
        return time == null ? HtmlText.DASH_TEXT : TIME_FORMAT.format(time);
    }

    /** „Mo" … „So", {@code ""} without a day / Wochentagskürzel. */
    public static String weekdayShort(DayOfWeek day) {
        return day == null ? "" : WEEKDAY_SHORT.get(day.getValue() - 1);
    }

    /** „Fr 20.11." — weekday and short date, {@code ""} without a date / Wochentag und Datum. */
    public static String weekdayAndDay(LocalDate date) {
        return date == null ? "" : weekdayShort(date.getDayOfWeek()) + " " + dayShort(date);
    }

    /** „Oktober 2026", {@code ""} without a month / Monat und Jahr. */
    public static String monthYear(YearMonth month) {
        return month == null ? "" : monthName(month) + " " + month.getYear();
    }

    /** „Oktober", {@code ""} without a month / Monatsname. */
    public static String monthName(YearMonth month) {
        return month == null ? "" : MONTH_NAMES.get(month.getMonthValue() - 1);
    }

    /** „Okt.", {@code ""} without a month / Monatskürzel. */
    public static String monthShort(YearMonth month) {
        return month == null ? "" : MONTH_SHORT.get(month.getMonthValue() - 1);
    }

    /**
     * „A", „A und B", „A, B und C" / Aufzählung mit „und" vor dem letzten Glied.
     *
     * @param items the plain items, may be {@code null}
     * @return the enumeration, {@code ""} without items / die Aufzählung
     */
    public static String joinAnd(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        if (items.size() == 1) {
            return items.getFirst();
        }
        return String.join(LIST_SEPARATOR, items.subList(0, items.size() - 1)) + LIST_AND + items.getLast();
    }
}
