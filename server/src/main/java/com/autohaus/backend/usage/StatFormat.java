package com.autohaus.backend.usage;

import com.autohaus.backend.common.html.GermanFormat;
import com.autohaus.backend.common.html.HtmlText;
import com.autohaus.backend.usage.insights.Cell;

import java.util.Locale;

/**
 * Number formatting of the status page (REQ-038 §14.1, TC-39) / Zahlenformatierung der
 * Statusseite. Extracted verbatim from the private helpers of {@link UsageSectionRenderer} so all
 * sections format identically; the REQ-037 markup stays unchanged. / Aus dem REQ-037-Renderer
 * extrahiert, Verhalten identisch.
 *
 * <p><b>One source for the cell short texts / eine Quelle für die Zell-Kurztexte:</b> the visible
 * texts of the hidden cell states (§17 {@code CELL_*}) are defined here once;
 * {@link #cell(Cell)} turns them into HTML and {@code CellHtml} renders every cell through that
 * method. The {@code .sr} long texts belong to {@code CellHtml}. / Die sichtbaren Kurztexte stehen
 * nur hier.
 */
public final class StatFormat {

    /** Visible dash for „keine Daten" (REQ-037 AC-13) / sichtbarer Strich für „keine Daten". */
    public static final String DASH = HtmlText.DASH;

    /** §17 {@code CELL_SUPPRESSED} — visible short text of {@code SUPPRESSED} (plain) / Kurztext „&lt; 5". */
    public static final String CELL_SUPPRESSED = "< 5";
    /** §17 {@code CELL_WITHHELD} — visible short text of {@code WITHHELD} (plain) / Kurztext „k. A.". */
    public static final String CELL_WITHHELD = "k. A.";
    /**
     * §17 {@code CELL_TOO_FEW} — visible short text of {@code TOO_FEW} (plain) / Kurztext „n &lt; 20".
     * §17 {@code CELL_NO_DATA} „—" is {@link #DASH} itself.
     */
    public static final String CELL_TOO_FEW = "n < 20";

    private StatFormat() {
    }

    /** German thousands separators, e.g. {@code 1.122} / deutsche Tausendertrennung. */
    public static String num(long value) {
        return GermanFormat.number(value);
    }

    /** One decimal with a German comma, e.g. {@code 66,3} / eine Nachkommastelle mit Komma. */
    public static String decimal(double value) {
        return String.format(Locale.GERMANY, "%.1f", value);
    }

    /** Visible percentage, {@code null} renders „—" (AC-13) / sichtbarer Prozentwert. */
    public static String percent(Double value) {
        return value == null ? DASH : decimal(value) + "&nbsp;%";
    }

    /** Integer percentage for the location cells (§10.5) / ganzzahliger Prozentwert. */
    public static String percentInt(Double value) {
        return value == null ? DASH : String.format(Locale.GERMANY, "%.0f", value) + "&nbsp;%";
    }

    /**
     * Bar width for the style attribute — CSS needs a dot as decimal separator, so this value is
     * formatted with {@link Locale#ROOT} and never with the display locale. / Balkenbreite mit
     * Punkt als Dezimaltrennzeichen.
     */
    public static String cssWidth(Double value) {
        return value == null ? "0.0" : String.format(Locale.ROOT, "%.1f", value);
    }

    /**
     * The visible short text of a cell as HTML (REQ-038 §11.2, §17 {@code CELL_*}) / sichtbarer
     * Kurztext einer Zelle: the counter for {@code VALUE}, otherwise the state text — „k.&nbsp;A." and
     * „n&nbsp;&lt;&nbsp;20" with non-breaking blanks, „&lt; 5" as in the §11.2 markup. A missing cell
     * or state counts as {@code NO_DATA} (BR-19). Renderers add the {@code .sr} long text and the
     * state class themselves. / Zähler bzw. Zustandstext; fehlende Zelle = „keine Daten".
     */
    public static String cell(Cell cell) {
        if (cell == null || cell.state() == null) {
            return DASH;
        }
        return switch (cell.state()) {
            case VALUE -> cell.value() != null ? num(cell.value())
                : cell.percent() != null ? percent(cell.percent())
                : cell.ratio() != null ? decimal(cell.ratio())
                : DASH;
            case SUPPRESSED -> HtmlText.esc(CELL_SUPPRESSED);
            case WITHHELD -> HtmlText.nonBreaking(HtmlText.esc(CELL_WITHHELD));
            case TOO_FEW -> HtmlText.nonBreaking(HtmlText.esc(CELL_TOO_FEW));
            case NO_DATA -> DASH;
        };
    }
}
