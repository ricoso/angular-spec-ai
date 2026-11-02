package com.autohaus.backend.usage.insights;

/**
 * Display state of one value after suppression (REQ-038 BR-9, §10.5) / Anzeigezustand einer Zelle
 * nach der Unterdrückung. Serialized as the Java name in upper case (§10.2 exception):
 * {@code "VALUE"}, {@code "SUPPRESSED"}, … / Wird als Java-Name serialisiert.
 */
public enum CellState {
    /** A visible value / sichtbarer Wert („12"). */
    VALUE,
    /** Primary suppression, 1–4 → „&lt; 5" (BR-9 Nr. 1) / primär unterdrückt. */
    SUPPRESSED,
    /** Complementary suppression or derived from a suppressed counter → „k. A." (BR-9 Nr. 4/5). */
    WITHHELD,
    /** Share with a denominator below 20 → „n &lt; 20" (BR-9 Nr. 2) / zu wenig Fälle. */
    TOO_FEW,
    /** No data (before collection start, missing counter) → „—", never 0 (BR-19). */
    NO_DATA
}
