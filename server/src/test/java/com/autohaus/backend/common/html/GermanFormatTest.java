package com.autohaus.backend.common.html;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The German display formats shared by every section (REQ-038 §17 {@code WEEKDAY_*}, §14.1) /
 * die gemeinsamen deutschen Anzeigeformate.
 */
class GermanFormatTest {

    @Test
    @DisplayName("dates: „09.11.2026“, „09.11.“, „—“ without a date")
    void formatsDates() {
        LocalDate day = LocalDate.of(2026, 11, 9);
        assertEquals("09.11.2026", GermanFormat.day(day));
        assertEquals("09.11.", GermanFormat.dayShort(day));
        assertEquals("—", GermanFormat.day(null));
        assertEquals("—", GermanFormat.dayShort(null));
        assertEquals("Mo 09.11.", GermanFormat.weekdayAndDay(day));
        assertEquals("", GermanFormat.weekdayAndDay(null));
    }

    @Test
    @DisplayName("clock times: HH:mm of a local or zoned time; convert instants to the display zone first")
    void formatsClockTimes() {
        assertEquals("22:30", GermanFormat.time(LocalTime.of(22, 30)));
        assertEquals("22:34",
            GermanFormat.time(Instant.parse("2026-11-19T21:34:00Z").atZone(ZoneId.of("Europe/Berlin"))));
        assertEquals("—", GermanFormat.time(null));
    }

    @Test
    @DisplayName("§17 WEEKDAY_*: Mo…So and the long names for screen readers")
    void namesTheWeekdays() {
        assertEquals("Mo", GermanFormat.weekdayShort(DayOfWeek.MONDAY));
        assertEquals("So", GermanFormat.weekdayShort(DayOfWeek.SUNDAY));
        assertEquals("Donnerstag", GermanFormat.WEEKDAY_LONG.get(DayOfWeek.THURSDAY.getValue() - 1));
        assertEquals("", GermanFormat.weekdayShort(null));
        assertEquals(7, GermanFormat.WEEKDAY_SHORT.size());
        assertEquals(7, GermanFormat.WEEKDAY_LONG.size());
    }

    @Test
    void namesTheMonths() {
        assertEquals("Oktober 2026", GermanFormat.monthYear(YearMonth.of(2026, 10)));
        assertEquals("März", GermanFormat.monthName(YearMonth.of(2026, 3)));
        assertEquals("Sep.", GermanFormat.monthShort(YearMonth.of(2026, 9)));
        assertEquals("Mai", GermanFormat.monthShort(YearMonth.of(2026, 5)));
        assertEquals("", GermanFormat.monthYear(null));
        assertEquals("", GermanFormat.monthName(null));
        assertEquals("", GermanFormat.monthShort(null));
        assertEquals(12, GermanFormat.MONTH_NAMES.size());
        assertEquals(12, GermanFormat.MONTH_SHORT.size());
    }

    @Test
    @DisplayName("TC-39: counters with German thousands separators, identical to String.format(GERMANY)")
    void formatsCounters() {
        for (long value : new long[] {0, 4, 999, 1000, 1122, 1_234_567, -42}) {
            assertEquals(String.format(Locale.GERMANY, "%,d", value), GermanFormat.number(value));
        }
        assertEquals("1.122", GermanFormat.number(1122));
    }

    @Test
    @DisplayName("enumerations: „A“, „A und B“, „A, B und C“")
    void joinsWithUnd() {
        assertEquals("", GermanFormat.joinAnd(null));
        assertEquals("", GermanFormat.joinAnd(List.of()));
        assertEquals("Tablet", GermanFormat.joinAnd(List.of("Tablet")));
        assertEquals("Tablet und Unbekannt", GermanFormat.joinAnd(List.of("Tablet", "Unbekannt")));
        assertEquals("11.11., 14.11. und 19.11.", GermanFormat.joinAnd(Arrays.asList("11.11.", "14.11.", "19.11.")));
    }
}
