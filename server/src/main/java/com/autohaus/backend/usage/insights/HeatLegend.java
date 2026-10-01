package com.autohaus.backend.usage.insights;

/**
 * Runtime bounds of the relative heat legend for T-1/T-3 (REQ-038 §10.5) / Laufzeit-Grenzen der
 * relativen Heat-Legende. With {@code upper1 = ⌊max/4⌋}, {@code upper2 = ⌊max/2⌋},
 * {@code upper3 = ⌊3·max/4⌋}: value ≤ upper1 → level 1, ≤ upper2 → 2, ≤ upper3 → 3, above → 4.
 * Suppressed cells and value 0 stay level 0 and never contribute to {@code max}. / Grenzen der
 * relativen Skala; unterdrückte Zellen zählen nicht.
 *
 * @param max the largest visible cell / größte sichtbare Zelle
 * @param upper1 upper bound of level 1 / Obergrenze Stufe 1
 * @param upper2 upper bound of level 2 / Obergrenze Stufe 2
 * @param upper3 upper bound of level 3 / Obergrenze Stufe 3
 */
public record HeatLegend(long max, long upper1, long upper2, long upper3) {

    /** Builds the bounds from the largest visible cell / bildet die Grenzen aus dem Maximum. */
    public static HeatLegend ofMax(long max) {
        return new HeatLegend(max, max / 4, max / 2, 3 * max / 4);
    }

    /** The heat level of a visible value under this legend / Heat-Stufe eines sichtbaren Werts. */
    public int levelOf(long value) {
        if (value <= 0 || max <= 0) {
            return 0;
        }
        if (value <= upper1) {
            return 1;
        }
        if (value <= upper2) {
            return 2;
        }
        if (value <= upper3) {
            return 3;
        }
        return 4;
    }
}
