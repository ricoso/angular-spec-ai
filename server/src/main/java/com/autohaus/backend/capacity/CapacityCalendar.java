package com.autohaus.backend.capacity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Working-day arithmetic of the capacity scan (REQ-038 §4 Step 2, §5.7, BR-26) /
 * Werktags-Arithmetik des Kapazitäts-Scans. Working days are Monday to Saturday; public holidays
 * are deliberately <b>not</b> considered — that is a documented measurement limit on the page
 * ({@code LIMIT_CAPACITY}, S7). / Werktage sind Mo–Sa; Feiertage zählen bewusst als Werktage.
 *
 * <p>Package-private helper shared by {@link CapacityProbe}, {@link CapacityScanScheduler} and
 * {@link CapacityViewModelBuilder} so all three count the same way. / Gemeinsamer Helfer, damit
 * Probe, Scheduler und View-Model identisch zählen.
 */
final class CapacityCalendar {

    /** German weekday abbreviations Mo…So (§17 {@code WEEKDAY_*}) / Wochentags-Kürzel. */
    private static final String[] WEEKDAY_SHORT = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};

    /** {@code dd.MM.yyyy} — full date in warnings (§17) / vollständiges Datum. */
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** {@code dd.MM.} — short date in labels („am Mo 23.11. frei", §17) / Kurzdatum. */
    static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM.");

    /** {@code HH:mm} — clock time in warnings / Uhrzeit. */
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private CapacityCalendar() {
    }

    /** Whether the day is a working day Mo–Sa / ob der Tag ein Werktag (Mo–Sa) ist. */
    static boolean isWorkday(LocalDate day) {
        return day.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    /**
     * The target day of a scan: the next working day Mo–Sa <b>after</b> the scan day (§5.7) / der
     * Zieltag: nächster Werktag nach dem Scan-Tag. Saturday and Sunday both lead to Monday.
     */
    static LocalDate nextWorkday(LocalDate day) {
        LocalDate next = day.plusDays(1);
        while (!isWorkday(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    /**
     * Counts the working days d with {@code fromInclusive ≤ d < toExclusive} (§10.4
     * {@code workdaysToFirstFree}) / zählt die Werktage im halboffenen Intervall. {@code 0} when
     * {@code toExclusive} is not after {@code fromInclusive}.
     */
    static int workdaysBetween(LocalDate fromInclusive, LocalDate toExclusive) {
        int count = 0;
        for (LocalDate day = fromInclusive; day.isBefore(toExclusive); day = day.plusDays(1)) {
            if (isWorkday(day)) {
                count++;
            }
        }
        return count;
    }

    /**
     * The last {@code count} working days up to and including {@code endInclusive}, oldest first
     * (BR-26: column heads of the 10-day grid) / die letzten {@code count} Werktage bis
     * einschließlich {@code endInclusive}, älteste zuerst. A Sunday end day starts at the Saturday
     * before it.
     */
    static List<LocalDate> lastWorkdays(LocalDate endInclusive, int count) {
        Deque<LocalDate> days = new ArrayDeque<>(Math.max(0, count));
        LocalDate day = endInclusive;
        while (days.size() < count) {
            if (isWorkday(day)) {
                days.addFirst(day);
            }
            day = day.minusDays(1);
        }
        return List.copyOf(days);
    }

    /** „Mo" … „So" (§17 {@code WEEKDAY_*}) / Wochentags-Kürzel. */
    static String weekdayShort(LocalDate day) {
        return WEEKDAY_SHORT[day.getDayOfWeek().getValue() - 1];
    }
}
