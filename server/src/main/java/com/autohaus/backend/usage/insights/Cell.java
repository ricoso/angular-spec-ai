package com.autohaus.backend.usage.insights;

/**
 * One displayed value after suppression (REQ-038 BR-9, §10.5) / ein angezeigter Wert nach der
 * Unterdrückung. {@code value}, {@code ratio} and {@code percent} are {@code null} unless
 * {@code state == VALUE} — a suppressed value never leaves the view model, not even indirectly
 * (AC-20): callers must render {@code barWidth = 0.0} and {@code heat = 0} for any non-VALUE cell.
 * / Unterdrückte Werte verlassen das View-Model nie.
 *
 * <p><b>Invariant / Invariante (second line of defence for AC-20):</b> the compact constructor
 * normalises every instance, whoever builds it — {@code state == null} becomes {@code NO_DATA};
 * for any state other than {@code VALUE} the three numbers are {@code null} and {@code heat} is 0;
 * {@code heat} is always clamped to {@code 0..}{@value #MAX_HEAT}. So not even a construction error
 * can carry a hidden number into HTML or {@code insights.json}. / Der kompakte Konstruktor
 * normalisiert jede Instanz: kein Konstruktionsfehler bringt einen verdeckten Wert ins JSON.
 *
 * @param value the counter / der Zähler
 * @param ratio a one-decimal ratio (R-4 „Buchungen je 100 Aufrufe") / Verhältniswert
 * @param percent a percentage, precision per metric (§10.5) / Prozentwert
 * @param heat heat level 1–4, 0 = no colour (suppressed cells and value 0, §10.5)
 * @param state the display state / der Anzeigezustand
 */
public record Cell(Long value, Double ratio, Double percent, int heat, CellState state) {

    /** Highest heat level of both heat scales (§10.5) / höchste Heat-Stufe. */
    public static final int MAX_HEAT = 4;

    /**
     * Normalises the cell (AC-20) / normalisiert die Zelle: missing state → {@code NO_DATA},
     * hidden state → no number and heat 0, heat clamped to {@code 0..4}.
     */
    public Cell {
        if (state == null) {
            state = CellState.NO_DATA;
        }
        if (state != CellState.VALUE) {
            value = null;
            ratio = null;
            percent = null;
            heat = 0;
        } else {
            heat = Math.clamp(heat, 0, MAX_HEAT);
        }
    }

    /** A visible counter / sichtbarer Zähler. */
    public static Cell of(long value) {
        return new Cell(value, null, null, 0, CellState.VALUE);
    }

    /** A visible counter with a heat level / sichtbarer Zähler mit Heat-Stufe. */
    public static Cell of(long value, int heat) {
        return new Cell(value, null, null, heat, CellState.VALUE);
    }

    /** A visible percentage / sichtbarer Prozentwert. */
    public static Cell percentOf(double percent) {
        return new Cell(null, null, percent, 0, CellState.VALUE);
    }

    /** A visible percentage with a heat level / sichtbarer Prozentwert mit Heat-Stufe. */
    public static Cell percentOf(double percent, int heat) {
        return new Cell(null, null, percent, heat, CellState.VALUE);
    }

    /** A visible one-decimal ratio (R-4) / sichtbarer Verhältniswert. */
    public static Cell ratioOf(double ratio) {
        return new Cell(null, ratio, null, 0, CellState.VALUE);
    }

    /** „&lt; 5" (BR-9 Nr. 1) / primär unterdrückt. */
    public static Cell suppressed() {
        return new Cell(null, null, null, 0, CellState.SUPPRESSED);
    }

    /** „k. A." (BR-9 Nr. 4/5) / komplementär unterdrückt bzw. abgeleitet. */
    public static Cell withheld() {
        return new Cell(null, null, null, 0, CellState.WITHHELD);
    }

    /** „n &lt; 20" (BR-9 Nr. 2) / zu wenig Fälle. */
    public static Cell tooFew() {
        return new Cell(null, null, null, 0, CellState.TOO_FEW);
    }

    /** „—" (BR-19) / keine Daten. */
    public static Cell noData() {
        return new Cell(null, null, null, 0, CellState.NO_DATA);
    }
}
