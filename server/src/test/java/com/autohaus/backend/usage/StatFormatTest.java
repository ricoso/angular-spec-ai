package com.autohaus.backend.usage;

import com.autohaus.backend.common.html.HtmlText;
import com.autohaus.backend.usage.insights.Cell;
import com.autohaus.backend.usage.insights.CellState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The shared number formatting of the status page (REQ-038 TC-39, §14.1) / die gemeinsame
 * Zahlenformatierung der Statusseite.
 *
 * <p>{@link StatFormat} was extracted from the private helpers of {@link UsageSectionRenderer}
 * ({@code UsageSectionRenderer.java:514-536} before REQ-038). The reference implementations below
 * are those helpers verbatim, so every result is compared with the REQ-037 behaviour itself — not
 * with a second opinion. {@code UsageSectionRendererTest} stays unchanged green. / Die Referenz sind
 * die bisherigen Helfer wörtlich.
 */
class StatFormatTest {

    private static final List<Double> DOUBLES = Arrays.asList(null, 0.0, 0.04, 0.05, 0.25, 1.0, 12.35,
        45.8, 66.25, 99.95, 100.0, 1234.56, -3.75);

    @Test
    @DisplayName("TC-39: the §13.4 examples 1122, 66.25 and null")
    void formatsTheExamplesOfTheRequirement() {
        assertEquals("1.122", StatFormat.num(1122));
        assertEquals("66,3", StatFormat.decimal(66.25), "HALF_UP like String.format");
        assertEquals("66,3&nbsp;%", StatFormat.percent(66.25));
        assertEquals("&mdash;", StatFormat.percent(null), "null renders the dash (REQ-037 AC-13)");
        assertEquals("66.3", StatFormat.cssWidth(66.25), "CSS needs a dot");
        assertEquals("0.0", StatFormat.cssWidth(null), "no value → no width");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 4, 5, 999, 1000, 1122, 12_345, 1_234_567, -42})
    @DisplayName("TC-39: num(...) is identical to the former private helper")
    void numMatchesTheFormerHelper(long value) {
        assertEquals(formerNum(value), StatFormat.num(value));
    }

    @Test
    @DisplayName("TC-39: decimal/percent/cssWidth are identical to the former private helpers")
    void decimalPercentAndWidthMatchTheFormerHelpers() {
        for (Double value : DOUBLES) {
            if (value != null) {
                assertEquals(formerDecimal(value), StatFormat.decimal(value), "decimal " + value);
            }
            assertEquals(formerPercent(value), StatFormat.percent(value), "percent " + value);
            assertEquals(formerCssWidth(value), StatFormat.cssWidth(value), "cssWidth " + value);
        }
    }

    @Test
    @DisplayName("§10.5: integer percentages for the location cells, null as dash")
    void formatsIntegerPercentages() {
        assertEquals("38&nbsp;%", StatFormat.percentInt(38.0));
        assertEquals("19&nbsp;%", StatFormat.percentInt(19.0));
        assertEquals("0&nbsp;%", StatFormat.percentInt(0.0));
        assertEquals("&mdash;", StatFormat.percentInt(null));
    }

    @Test
    @DisplayName("§17 CELL_*: the visible short text of every cell state")
    void formatsTheShortTextOfEveryCellState() {
        assertEquals("1.200", StatFormat.cell(Cell.of(1200)));
        assertEquals("0", StatFormat.cell(Cell.of(0)), "0 stays 0 (BR-9 Nr. 1)");
        assertEquals("45,8&nbsp;%", StatFormat.cell(Cell.percentOf(45.8)));
        assertEquals("14,1", StatFormat.cell(Cell.ratioOf(14.1)), "R-4 ratio with one decimal");
        assertEquals("&lt; 5", StatFormat.cell(Cell.suppressed()));
        assertEquals("k.&nbsp;A.", StatFormat.cell(Cell.withheld()));
        assertEquals("n&nbsp;&lt;&nbsp;20", StatFormat.cell(Cell.tooFew()));
        assertEquals("&mdash;", StatFormat.cell(Cell.noData()));
        assertEquals("&mdash;", StatFormat.cell(null), "a missing cell is „keine Daten“");
        assertEquals("&mdash;", StatFormat.cell(new Cell(null, null, null, 0, CellState.VALUE)),
            "a VALUE without any number");
        assertEquals("&mdash;", StatFormat.cell(new Cell(5L, null, null, 0, null)), "a missing state is NO_DATA");
    }

    @Test
    @DisplayName("§17 CELL_*: one source — the HTML short texts are the plain constants, escaped")
    void derivesTheShortTextsFromThePlainConstants() {
        assertEquals("< 5", StatFormat.CELL_SUPPRESSED);
        assertEquals("k. A.", StatFormat.CELL_WITHHELD);
        assertEquals("n < 20", StatFormat.CELL_TOO_FEW);
        assertEquals(HtmlText.esc(StatFormat.CELL_SUPPRESSED), StatFormat.cell(Cell.suppressed()));
        assertEquals(HtmlText.nonBreaking(HtmlText.esc(StatFormat.CELL_WITHHELD)), StatFormat.cell(Cell.withheld()));
        assertEquals(HtmlText.nonBreaking(HtmlText.esc(StatFormat.CELL_TOO_FEW)), StatFormat.cell(Cell.tooFew()));
        assertEquals(HtmlText.DASH, StatFormat.DASH);
    }

    // ---- the private helpers of UsageSectionRenderer before REQ-038, verbatim / wörtlich ----

    private static String formerNum(long value) {
        return String.format(Locale.GERMANY, "%,d", value);
    }

    private static String formerDecimal(double value) {
        return String.format(Locale.GERMANY, "%.1f", value);
    }

    private static String formerPercent(Double value) {
        return value == null ? "&mdash;" : formerDecimal(value) + "&nbsp;%";
    }

    private static String formerCssWidth(Double value) {
        return value == null ? "0.0" : String.format(Locale.ROOT, "%.1f", value);
    }
}
