package com.autohaus.backend.usage;

import com.autohaus.backend.usage.DeviceBreakdown.DevicePercentages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Percentage shares of the device breakdown (REQ-037 TC-15, AC-4).
 *
 * <p>All four shares are computed in one pass with one decimal place; the rounding difference is
 * booked on the largest class (largest remainder), so the four shares add up to exactly
 * {@code 100.0}. With {@code total == 0} all four shares are {@code null} and are rendered as „—",
 * never as {@code 0,0 %}.
 *
 * <p>Prüft, dass sich die vier Anteile exakt auf 100,0 summieren — auch im kritischen Drittel-Fall
 * — und dass bei {@code total == 0} viermal {@code null} zurückkommt.
 */
class DeviceBreakdownTest {

    /** One hundred percent expressed in tenths / 100 % in Zehnteln — the exact target sum. */
    private static final long TENTHS_PER_HUNDRED_PERCENT = 1000L;

    @Test
    void computesSharesThatSumToExactlyOneHundredPercent() {
        // TC-15, case 1: 851 + 116 + 302 + 15 = 1284 visits.
        DeviceBreakdown breakdown = new DeviceBreakdown(851, 116, 302, 15);
        assertEquals(1284L, breakdown.total());

        DevicePercentages shares = breakdown.percentages();

        assertEquals(66.3, shares.mobile());
        assertEquals(9.0, shares.tablet());
        assertEquals(23.5, shares.desktop());
        assertEquals(1.2, shares.unknown());
        assertSharesSumToOneHundred(shares);
    }

    @Test
    void returnsNullSharesWhenNothingWasCounted() {
        // TC-15, case 2: total == 0 -> four nulls, rendered as „—" (AC-4).
        DeviceBreakdown breakdown = new DeviceBreakdown(0, 0, 0, 0);
        assertEquals(0L, breakdown.total());

        DevicePercentages shares = breakdown.percentages();

        assertNull(shares.mobile());
        assertNull(shares.tablet());
        assertNull(shares.desktop());
        assertNull(shares.unknown());
    }

    @Test
    void booksTheRoundingDifferenceOnTheLargestClass() {
        // TC-15, case 3: the critical thirds case — 33.3 + 33.3 + 33.3 would be 99.9, so the
        // missing tenth is booked on the largest class (AC-4, largest remainder).
        // Kritischer Drittel-Fall: die fehlende Zehntelstelle liegt auf der größten Klasse.
        DeviceBreakdown breakdown = new DeviceBreakdown(1, 1, 1, 0);

        DevicePercentages shares = breakdown.percentages();

        assertEquals(33.3, shares.mobile());
        assertEquals(33.3, shares.tablet());
        assertEquals(33.4, shares.desktop());
        assertEquals(0.0, shares.unknown());
        assertSharesSumToOneHundred(shares);
    }

    /**
     * Asserts the exact sum of 100.0 / prüft die exakte Summe. The check runs in tenths (integers)
     * because adding the four {@code double} values may drift in the last bit — the contract of
     * AC-4 is about the rendered one-decimal values, not about binary floating point.
     *
     * @param shares the four shares under test / die vier zu prüfenden Anteile
     */
    private static void assertSharesSumToOneHundred(DevicePercentages shares) {
        long tenths = tenths(shares.mobile()) + tenths(shares.tablet())
            + tenths(shares.desktop()) + tenths(shares.unknown());
        assertEquals(TENTHS_PER_HUNDRED_PERCENT, tenths, "the four shares must sum to exactly 100.0");

        double sum = shares.mobile() + shares.tablet() + shares.desktop() + shares.unknown();
        assertEquals(100.0, sum, 1e-9);
    }

    /** One share in tenths of a percent / ein Anteil in Zehntel-Prozent. */
    private static long tenths(Double share) {
        assertNotNull(share);
        return Math.round(share * 10.0);
    }
}
