package com.autohaus.backend.usage;

/**
 * Visits per device class (REQ-037) / Besuche je Geräteklasse. Anonyme Zähler, keine IDs.
 *
 * <p>The four classes are exhaustive (BR-1): every value read from a log line that is not exactly
 * one of {@code mobile}/{@code tablet}/{@code desktop}/{@code unknown} — an empty string from
 * pre-deploy lines in particular — is mapped to {@code unknown} before it reaches this record, so
 * {@link #total()} always equals the period's visit count (AC-4). / Die vier Klassen sind
 * abschließend; fremde Werte werden vorher auf {@code unknown} abgebildet.
 *
 * @param mobile visits classified as mobile / Besuche der Klasse „mobil"
 * @param tablet visits classified as tablet / Besuche der Klasse „Tablet"
 * @param desktop visits classified as desktop / Besuche der Klasse „Desktop"
 * @param unknown visits that could not be classified / nicht klassifizierbare Besuche
 */
public record DeviceBreakdown(
    long mobile,
    long tablet,
    long desktop,
    long unknown
) {

    /** Percent expressed in tenths — one decimal place (BR-6). / Prozent in Zehnteln. */
    private static final long TENTHS_PER_HUNDRED_PERCENT = 1000L;

    /** Sum of all four classes / Summe aller vier Klassen. */
    public long total() {
        return mobile + tablet + desktop + unknown;
    }

    /**
     * Computes all four shares in ONE pass / berechnet alle vier Anteile in EINEM Durchgang: one
     * decimal place, largest-remainder correction booked on the largest class, sum exactly
     * {@code 100.0} (AC-4). A single-value method cannot do this — it does not know the other
     * classes. / Eine Einzelwert-Methode kann das nicht leisten.
     *
     * <p>Arithmetic runs in tenths of a percent (integers), so the sum is exact and free of
     * floating-point drift; only the final division by ten produces the {@code Double}. /
     * Die Rechnung läuft in Zehntel-Prozent, damit die Summe exakt 100,0 ergibt.
     *
     * <p><b>Tie-break / Gleichstand:</b> when several classes share the largest count, the last one
     * in the order mobile → tablet → desktop → unknown carries the remainder, so
     * {@code (1, 1, 1, 0)} yields {@code 33.3 / 33.3 / 33.4} (TC-15).
     *
     * @return the four shares, or four {@code null} values when {@link #total()} is {@code 0}
     *     (rendered as „—") / vier {@code null}-Werte bei {@code total == 0}
     */
    public DevicePercentages percentages() {
        long total = total();
        if (total == 0L) {
            return new DevicePercentages(null, null, null, null);
        }

        long mobileTenths = tenths(mobile, total);
        long tabletTenths = tenths(tablet, total);
        long desktopTenths = tenths(desktop, total);
        long unknownTenths = tenths(unknown, total);

        // Largest-remainder correction: the rounding difference is booked on the largest class, so
        // the four shares add up to exactly 100.0 (AC-4).
        // Largest-Remainder-Korrektur: Rundungsdifferenz auf die größte Klasse.
        long remainder = TENTHS_PER_HUNDRED_PERCENT
            - (mobileTenths + tabletTenths + desktopTenths + unknownTenths);
        long largest = Math.max(Math.max(mobile, tablet), Math.max(desktop, unknown));
        if (unknown == largest) {
            unknownTenths += remainder;
        } else if (desktop == largest) {
            desktopTenths += remainder;
        } else if (tablet == largest) {
            tabletTenths += remainder;
        } else {
            mobileTenths += remainder;
        }

        return new DevicePercentages(
            mobileTenths / 10.0,
            tabletTenths / 10.0,
            desktopTenths / 10.0,
            unknownTenths / 10.0);
    }

    /** Class-wise sum / klassenweise Summe — used to aggregate days into periods. */
    public DeviceBreakdown plus(DeviceBreakdown other) {
        if (other == null) {
            return this;
        }
        return new DeviceBreakdown(
            mobile + other.mobile(),
            tablet + other.tablet(),
            desktop + other.desktop(),
            unknown + other.unknown());
    }

    /** Share of one class in tenths of a percent / Anteil einer Klasse in Zehntel-Prozent. */
    private static long tenths(long count, long total) {
        return Math.round(count * (double) TENTHS_PER_HUNDRED_PERCENT / total);
    }

    /**
     * Percentage shares per class / Prozentanteile je Klasse; {@code null} when
     * {@link DeviceBreakdown#total()} is {@code 0} — rendered as „—", never as {@code 0,0 %}.
     *
     * @param mobile share of mobile visits / Anteil mobiler Besuche
     * @param tablet share of tablet visits / Anteil der Tablet-Besuche
     * @param desktop share of desktop visits / Anteil der Desktop-Besuche
     * @param unknown share of unclassified visits / Anteil unbekannter Besuche
     */
    public record DevicePercentages(Double mobile, Double tablet, Double desktop, Double unknown) {}
}
