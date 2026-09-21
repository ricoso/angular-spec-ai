package com.autohaus.backend.usage;

import com.autohaus.backend.config.UsageStatsProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Period building, conversion rates and the monthly series (REQ-037 TC-6, TC-7, TC-18, TC-19) /
 * Perioden, Quoten und Monatsreihe.
 *
 * <p>The service is free of I/O: history and live days are handed in, so „today" can be pinned
 * with a fixed {@link Clock} through the package-private constructor. / Kein I/O, fester Clock.
 */
class UsageStatsServiceTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    // ---- TC-6: conversion incl. division by zero / Conversion inkl. Division durch Null ----

    /**
     * TC-6/BR-6: {@code Math.round(value * 1000) / 10.0} — one decimal place, session based.
     * {@code 431/954 → 45.2}, {@code 143/954 → 15.0}, {@code 143/431 → 33.2}. / Eine
     * Nachkommastelle.
     */
    @Test
    void computesTheThreeSessionBasedRatesWithOneDecimal() {
        assertEquals(45.2, UsageStatsService.computeConversionRate(431, 954));
        assertEquals(15.0, UsageStatsService.computeConversionRate(143, 954));
        assertEquals(33.2, UsageStatsService.computeConversionRate(143, 431));
    }

    /**
     * TC-6/AC-13: a divisor of {@code 0} yields {@code null}, rendered as „—" — never a division by
     * zero, never {@code NaN}, never {@code Infinity}, never a misleading {@code 0,0 %}. / Divisor
     * 0 ergibt {@code null}.
     */
    @Test
    void returnsNullInsteadOfDividingByZero() {
        Double rate = UsageStatsService.computeConversionRate(0, 0);

        assertNull(rate);
        assertNull(UsageStatsService.computeConversionRate(5, 0));
        assertNull(UsageStatsService.computeConversionRate(0, -3));
    }

    /** TC-6: the same holds on the assembled period — all three rates are „—" at 0 visits. */
    @Test
    void periodWithoutVisitsHasAllThreeRatesNull() {
        UsageStatsService service = service("2026-09-21T10:00:00Z");
        SortedMap<LocalDate, DailyUsage> ignored = service.mergeDays(UsageHistory.empty(), Map.of());
        assertTrue(ignored.isEmpty());

        UsageStats stats = service.buildPeriods(
            history(Map.of(LocalDate.of(2026, 9, 20), DailyUsage.empty())),
            true, Map.of(), true, Instant.parse("2026-09-21T10:00:00Z"));

        PeriodUsage month = stats.month();
        // Alt Flow B: the period was recorded (hasData), it simply holds zeros.
        assertTrue(month.hasData());
        assertEquals(0L, month.visitCount());
        assertNull(month.searchRate());
        assertNull(month.bookingRate());
        assertNull(month.closeRate());
    }

    // ---- TC-7: an empty history is not a row of zeros / leere Historie ≠ Nullen ----

    /**
     * TC-7/AC-12: {@link UsageHistory#empty()} and no live day → {@code hasData == false} for month,
     * year and every month row. The renderer turns that into „Keine Daten" („—"), <b>not</b> into
     * „0 Besuche" (asserted in {@code UsageSectionRendererTest}). / „keine Daten" ist nicht dasselbe
     * wie lauter Nullen.
     */
    @Test
    void emptyHistoryIsNoDataRatherThanZeros() {
        UsageStatsService service = service("2026-09-21T10:00:00Z");

        UsageStats stats = service.buildPeriods(UsageHistory.empty(), false, Map.of(), false, null);

        assertFalse(stats.month().hasData());
        assertFalse(stats.year().hasData());
        assertEquals(0L, stats.month().visitCount());
        assertNull(stats.month().searchRate());
        assertNull(stats.collectingSince());
        assertNull(stats.lastRollupDay());
        assertFalse(stats.historyAvailable());
        assertFalse(stats.liveAvailable());
        assertEquals(12, stats.months().size());
        assertTrue(stats.months().stream().noneMatch(PeriodUsage::hasData));
    }

    // ---- TC-5 (period half) / Monats- und Jahresgrenze in Europe/Berlin ----

    /**
     * TC-5: at {@code 2026-10-01T00:15+02:00} the month block holds <b>only</b> 01.10., while the
     * year block holds September <b>and</b> October. The KQL half of TC-5 — the lower bound
     * {@code 2026-09-30T22:00:00Z} instead of local midnight — is asserted in
     * {@code UsageStatsAggregatorTest}. / Die KQL-Hälfte prüft der Aggregator-Test.
     */
    @Test
    void monthAndYearBoundariesFollowEuropeBerlin() {
        // 2026-10-01T00:15+02:00 == 2026-09-30T22:15:00Z — in UTC it is still September.
        UsageStatsService service = service("2026-09-30T22:15:00Z");
        Map<LocalDate, DailyUsage> days = new LinkedHashMap<>();
        days.put(LocalDate.of(2026, 9, 29), day(10, 0, 0, 0));
        days.put(LocalDate.of(2026, 9, 30), day(20, 0, 0, 0));
        days.put(LocalDate.of(2026, 10, 1), day(3, 0, 0, 0));

        UsageStats stats = service.buildPeriods(history(days), true, Map.of(), true, null);

        assertEquals(LocalDate.of(2026, 10, 1), stats.month().rangeFrom());
        assertEquals(LocalDate.of(2026, 10, 1), stats.month().rangeTo());
        assertEquals(3L, stats.month().visitCount(), "the month block holds only 01.10.");
        assertEquals(LocalDate.of(2026, 1, 1), stats.year().rangeFrom());
        assertEquals(33L, stats.year().visitCount(), "the year block holds September and October");
    }

    // ---- TC-18: the midnight gap / Mitternachtslücke (BR-9) ----

    /**
     * TC-18/BR-9: at 01:00 the nightly rollup (03:30) has not run yet, so 20.09. is neither in the
     * blob nor the running day. Days in the blob come exclusively from the blob, all days after it
     * come exclusively live — the month and the year therefore contain 20.09., and no day is ever
     * counted from both sources. Without this rule the page would silently lose a whole day every
     * night. / Der Vortag wird live nachgezogen, der 19.09. bleibt Blob-Wert.
     */
    @Test
    void pullsTheNotYetRolledPreviousDayFromLiveWithoutDoubleCounting() {
        // 2026-09-21T01:00+02:00 == 2026-09-20T23:00:00Z
        UsageStatsService service = service("2026-09-20T23:00:00Z");
        assertEquals(LocalDate.of(2026, 9, 21), service.today());

        Map<LocalDate, DailyUsage> blob = new LinkedHashMap<>();
        blob.put(LocalDate.of(2026, 9, 18), day(30, 0, 0, 0));
        blob.put(LocalDate.of(2026, 9, 19), day(40, 0, 0, 0));

        Map<LocalDate, DailyUsage> live = new LinkedHashMap<>();
        // A live value for a day the blob already owns must be ignored (blob wins) — the deviating
        // number makes a double count visible. / Blob gewinnt für den 19.09.
        live.put(LocalDate.of(2026, 9, 19), day(999, 0, 0, 0));
        live.put(LocalDate.of(2026, 9, 20), day(50, 0, 0, 0));
        live.put(LocalDate.of(2026, 9, 21), day(7, 0, 0, 0));

        UsageStats stats = service.buildPeriods(history(blob), true, live, true,
            Instant.parse("2026-09-20T23:00:00Z"));

        // 30 + 40 + 50 + 7 — the 19th counted once, with its blob value.
        assertEquals(127L, stats.month().visitCount());
        assertEquals(127L, stats.year().visitCount());
        assertEquals(LocalDate.of(2026, 9, 19), stats.lastRollupDay());
        assertEquals(LocalDate.of(2026, 9, 18), stats.collectingSince());
    }

    /** BR-9: live days older than the three-day edge belong to the catch-up, not to the page. */
    @Test
    void ignoresLiveDaysBeyondTheThreeDayEdge() {
        UsageStatsService service = service("2026-09-20T23:00:00Z");
        Map<LocalDate, DailyUsage> live = new LinkedHashMap<>();
        live.put(LocalDate.of(2026, 9, 17), day(500, 0, 0, 0));
        live.put(LocalDate.of(2026, 9, 21), day(7, 0, 0, 0));

        UsageStats stats = service.buildPeriods(UsageHistory.empty(), false, live, true, null);

        assertEquals(7L, stats.month().visitCount());
    }

    // ---- TC-19: the monthly series of the year block / Monatsreihe im Jahresblock ----

    /**
     * TC-19/AC-18/BR-14: twelve rows built purely by summing the same day rows — no additional
     * query, no new data category. Months before collection started and future months carry
     * {@code hasData == false} and render as „—" (AC-12); the {@code <tfoot>} sum equals the year
     * value. / Zwölf Zeilen, reine Summation.
     */
    @Test
    void buildsTwelveMonthRowsFromTheSameDayRows() {
        UsageStatsService service = service("2026-09-21T10:00:00Z");
        Map<LocalDate, DailyUsage> blob = new LinkedHashMap<>();
        for (int dayOfMonth = 27; dayOfMonth <= 31; dayOfMonth++) {
            blob.put(LocalDate.of(2026, 8, dayOfMonth), day(10, 5, 2, 1));
        }
        for (int dayOfMonth = 1; dayOfMonth <= 20; dayOfMonth++) {
            blob.put(LocalDate.of(2026, 9, dayOfMonth), day(12, 6, 3, 2));
        }

        UsageStats stats = service.buildPeriods(history(blob), true, Map.of(), true, null);
        List<PeriodUsage> months = stats.months();

        assertEquals(12, months.size());
        for (Month month : List.of(Month.JANUARY, Month.FEBRUARY, Month.MARCH, Month.APRIL,
            Month.MAY, Month.JUNE, Month.JULY)) {
            assertFalse(months.get(month.getValue() - 1).hasData(),
                month + " is before collection started and must render as „—\"");
        }
        assertTrue(months.get(Month.AUGUST.getValue() - 1).hasData());
        assertTrue(months.get(Month.SEPTEMBER.getValue() - 1).hasData());
        for (Month month : List.of(Month.OCTOBER, Month.NOVEMBER, Month.DECEMBER)) {
            assertFalse(months.get(month.getValue() - 1).hasData(), month + " is in the future");
        }

        assertEquals(50L, months.get(Month.AUGUST.getValue() - 1).visitCount());
        assertEquals(240L, months.get(Month.SEPTEMBER.getValue() - 1).visitCount());
        assertEquals("August", months.get(Month.AUGUST.getValue() - 1).label());

        // <tfoot>: the sum of the twelve rows is the year value (AC-18).
        long sum = months.stream().mapToLong(PeriodUsage::visitCount).sum();
        assertEquals(stats.year().visitCount(), sum);
        assertEquals(290L, sum);
    }

    /** AC-18: the running month ends at today, past and future months at their last day. */
    @Test
    void theRunningMonthRowEndsAtToday() {
        UsageStatsService service = service("2026-09-21T10:00:00Z");

        List<PeriodUsage> months = service
            .buildPeriods(UsageHistory.empty(), false, Map.of(), false, null).months();

        assertEquals(LocalDate.of(2026, 9, 21), months.get(Month.SEPTEMBER.getValue() - 1).rangeTo());
        assertEquals(LocalDate.of(2026, 8, 31), months.get(Month.AUGUST.getValue() - 1).rangeTo());
        assertEquals(LocalDate.of(2026, 10, 31), months.get(Month.OCTOBER.getValue() - 1).rangeTo());
    }

    // ---- helpers ----

    private static UsageStatsService service(String nowUtc) {
        return new UsageStatsService(properties(),
            Clock.fixed(Instant.parse(nowUtc), BERLIN));
    }

    private static UsageStatsProperties properties() {
        return new UsageStatsProperties(true, "0 */15 * * * *", "0 30 3 * * *", "Europe/Berlin",
            24, 60, new UsageStatsProperties.Storage("", "usage-stats", "usage-daily.json"));
    }

    /** Blob history with ISO date keys — the day is exclusively the map key (§10.3). */
    private static UsageHistory history(Map<LocalDate, DailyUsage> days) {
        Map<String, DailyUsage> isoDays = new LinkedHashMap<>();
        days.forEach((day, usage) -> isoDays.put(day.toString(), usage));
        return new UsageHistory(UsageHistory.CURRENT_SCHEMA_VERSION,
            Instant.parse("2026-09-21T01:30:12Z"), isoDays);
    }

    /** A day row with {@code mobile} visits plus the funnel counters. */
    private static DailyUsage day(long mobileVisits, long searchOk, long bookingOk,
                                  long sessionsWithBooking) {
        return new DailyUsage(
            new DeviceBreakdown(mobileVisits, 0L, 0L, 0L),
            searchOk, searchOk, bookingOk, bookingOk, searchOk, sessionsWithBooking);
    }
}
