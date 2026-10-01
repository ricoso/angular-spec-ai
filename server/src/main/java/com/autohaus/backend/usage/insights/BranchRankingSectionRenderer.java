package com.autohaus.backend.usage.insights;

import com.autohaus.backend.common.html.GermanFormat;
import com.autohaus.backend.common.html.HtmlText;
import com.autohaus.backend.usage.StatFormat;
import com.autohaus.backend.usage.insights.CellHtml.Format;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.autohaus.backend.common.html.HtmlText.esc;
import static com.autohaus.backend.common.html.HtmlText.fill;

/**
 * Renders S2 „Online-Buchungen je Standort: Renner und Penner" (REQ-038 §11.2, AC-15) / rendert
 * S2. {@code null} or {@code bookingsEnabled == false} → empty string (BR-4).
 *
 * <p><b>One table / eine Tabelle:</b> {@code table.st-table.rk-table} in a scroll region with
 * rank, branch, bookings (bar plus number), share, year to the previous month, service views,
 * bookings per 100 views „(Näherung)", off-hours share, late slots and the rank change; the
 * optional columns carry {@code .opt} and drop out at ≤ 40em. Branches with 0–4 bookings follow
 * after every ranked row in {@code tbody.rk-low} under the row-group heading „Unter 5 Buchungen:
 * …" — without rank, their counter as „0" or „&lt; 5" (AC-15). / Standorte unter 5 Buchungen stehen
 * ohne Rang in einer eigenen Zeilengruppe.
 *
 * <p><b>No indirect values (BR-9 Nr. 5, AC-20):</b> every cell comes pre-suppressed from the view
 * model; the renderer adds nothing but markup. The bar is {@code aria-hidden} and its width is
 * {@code 0} whenever the bookings cell is not {@code VALUE}, even if a broken model carried a
 * width. / Balkenbreite 0, sobald die Buchungszahl ausgeblendet ist.
 *
 * <p>Stateless: view model in, HTML string out; every dynamic value is escaped. / Zustandslos.
 */
@Component
public class BranchRankingSectionRenderer {

    /** §17 {@code RANKING_HEADING}. */
    public static final String RANKING_HEADING = "Online-Buchungen je Standort: Renner und Penner";
    /** §17 {@code RANKING_RANGE}. */
    public static final String RANKING_RANGE =
        "{month} (abgeschlossener Monat) · Jahr {year} bis Ende {monthName}";

    /** §17 {@code RANKING_COL_*} — Rang. */
    public static final String RANKING_COL_RANK = "Rang";
    /** §17 {@code RANKING_COL_*} — Standort. */
    public static final String RANKING_COL_BRANCH = "Standort";
    /** §17 {@code RANKING_COL_*} — Online-Buchungen. */
    public static final String RANKING_COL_BOOKINGS = "Online-Buchungen";
    /** §17 {@code RANKING_COL_*} — Anteil am Online-Volumen (main line). */
    public static final String RANKING_COL_SHARE = "Anteil";
    /** §17 {@code RANKING_COL_*} — Anteil am Online-Volumen (sub line). */
    public static final String RANKING_COL_SHARE_SUB = "am Online-Volumen";
    /** §17 {@code RANKING_COL_*} — Jahr bis Vormonat. */
    public static final String RANKING_COL_YEAR = "Jahr bis Vormonat";
    /** §17 {@code RANKING_COL_*} — Leistungsaufrufe (main line). */
    public static final String RANKING_COL_VIEWS = "Leistungsaufrufe";
    /** §17 {@code RANKING_COL_*} — (Seitenaufrufe) (sub line, BR-20: „Aufrufe, keine Besucher"). */
    public static final String RANKING_COL_VIEWS_SUB = "Seitenaufrufe";
    /** §17 {@code RANKING_COL_*} — Buchungen je 100 Aufrufe (main line). */
    public static final String RANKING_COL_PER_100 = "Buchungen je 100 Aufrufe";
    /** §17 {@code RANKING_COL_*} — (Näherung) (sub line, BR-20, AC-29). */
    public static final String RANKING_COL_PER_100_SUB = "(Näherung)";
    /** §17 {@code RANKING_COL_*} — Außerhalb Mo–Fr 07–18 Uhr (main line). */
    public static final String RANKING_COL_OFF_HOURS = "Außerhalb";
    /** §17 {@code RANKING_COL_*} — Außerhalb Mo–Fr 07–18 Uhr (sub line). */
    public static final String RANKING_COL_OFF_HOURS_SUB = "Mo–Fr 07–18 Uhr";
    /** §17 {@code RANKING_COL_*} — Späte Termine ab 16 Uhr (main line). */
    public static final String RANKING_COL_LATE = "Späte Termine";
    /** §17 {@code RANKING_COL_*} — Späte Termine ab 16 Uhr (sub line). */
    public static final String RANKING_COL_LATE_SUB = "ab 16 Uhr";
    /** §17 {@code RANKING_COL_*} — Rangänderung. */
    public static final String RANKING_COL_MOVE = "Rangänderung";

    /** §17 {@code RANK_UP} (visible). */
    public static final String RANK_UP = "↑ {n}";
    /** §17 {@code RANK_UP} ({@code .sr}). */
    public static final String RANK_UP_SR = "aufgestiegen um {n}";
    /** §17 {@code RANK_DOWN} (visible). */
    public static final String RANK_DOWN = "↓ {n}";
    /** §17 {@code RANK_DOWN} ({@code .sr}). */
    public static final String RANK_DOWN_SR = "abgestiegen um {n}";
    /** §17 {@code RANK_SAME} (visible). */
    public static final String RANK_SAME = "=";
    /** §17 {@code RANK_SAME} ({@code .sr}). */
    public static final String RANK_SAME_SR = "unverändert";
    /** §17 {@code RANK_NEW}. */
    public static final String RANK_NEW = "neu";
    /** §17 {@code RANK_PREVIOUS}. */
    public static final String RANK_PREVIOUS = "{monthShort}: {rank}";
    /** Screen-reader text of a branch without rank / Langtext „kein Rang". */
    public static final String RANK_NONE_SR = "kein Rang";

    /** §17 {@code RANKING_BELOW_K}. */
    public static final String RANKING_BELOW_K = "Unter 5 Buchungen: {names}";
    /** §17 {@code RANKING_NOTE}. */
    public static final String RANKING_NOTE = "Nur Online: Telefon- und Filialbuchungen sind "
        + "unsichtbar – ein „Penner“ kann offline stark sein. Leistungsaufrufe sind Seitenaufrufe der "
        + "Leistungsauswahl (jedes erneute Betreten zählt), keine Besucher.";
    /** §17 {@code NO_CLOSED_MONTH}. */
    public static final String NO_CLOSED_MONTH = "Noch kein abgeschlossener Monat seit Erfassungsbeginn ({date}).";
    /** §17 {@code NO_CLOSED_MONTH_FIRST}. */
    public static final String NO_CLOSED_MONTH_FIRST = "Die erste Rangliste erscheint am {date}.";

    /** Title of the table (scroll region) / Tabellentitel. */
    public static final String TABLE_TITLE = "Online-Buchungen je Standort";
    /** Caption of the ranking table (screen readers, §11.2) / Caption der Rangliste. */
    public static final String RANKING_CAPTION = "Rangliste der Standorte nach Online-Buchungen im {month} "
        + "mit Anteil am Online-Volumen, Buchungen im Jahr {year} bis Ende {monthName}, Leistungsaufrufen, "
        + "Buchungen je 100 Aufrufe, Anteil außerhalb Mo–Fr 07–18 Uhr, Anteil später Termine ab 16 Uhr und "
        + "Rangänderung gegenüber {previousMonthName}; Standorte mit weniger als 5 Buchungen ohne Rang am Ende.";
    /** The {@code {date}} part of {@code NO_CLOSED_MONTH}, left out without a collection start. */
    private static final String NO_CLOSED_MONTH_DATE_PART = " ({date})";

    /** Number of columns of the ranking table / Spaltenzahl. */
    private static final int COLUMNS = 10;

    /** Renders the section, {@code ""} when there is nothing to render / rendert die Sektion. */
    public String render(InsightsViewModel viewModel) {
        if (viewModel == null || !viewModel.bookingsEnabled()) {
            return "";
        }
        StringBuilder h = new StringBuilder(12288);
        h.append("<section id=\"branches\" class=\"rk\" aria-labelledby=\"branches-h\">");
        h.append("<h2 id=\"branches-h\">").append(esc(RANKING_HEADING)).append("</h2>");
        h.append(HtmlText.warnings(viewModel.warnings()));
        if (!HtmlText.hasWarning(viewModel.warnings(), InsightsViewModel.WARN_HISTORY_UNAVAILABLE)) {
            h.append(body(viewModel));
        }
        h.append("</section>");
        return h.toString();
    }

    private static String body(InsightsViewModel vm) {
        YearMonth month = vm.closedMonth();
        if (month == null) {
            return noClosedMonth(vm.collectingSince());
        }
        List<BranchRankingRow> ranked = vm.ranking() == null ? List.of() : vm.ranking();
        List<BranchRankingRow> low = vm.belowThreshold() == null ? List.of() : vm.belowThreshold();

        StringBuilder h = new StringBuilder(12288);
        h.append("<p class=\"st-range\">").append(fill(RANKING_RANGE,
            "month", esc(GermanFormat.monthYear(month)),
            "year", esc(String.valueOf(month.getYear())),
            "monthName", esc(GermanFormat.monthName(month)))).append("</p>");
        if (!ranked.isEmpty() || !low.isEmpty()) {
            h.append(table(month, ranked, low));
        }
        h.append("<p class=\"st-note\">").append(esc(RANKING_NOTE)).append("</p>");
        return h.toString();
    }

    /**
     * „Noch kein abgeschlossener Monat seit Erfassungsbeginn (…). Die erste Rangliste erscheint am
     * …" (TC-28) — never a table of zeros. / Leerzustand vor dem ersten abgeschlossenen Monat.
     */
    static String noClosedMonth(LocalDate collectingSince) {
        if (collectingSince == null) {
            return HtmlText.empty(esc(NO_CLOSED_MONTH.replace(NO_CLOSED_MONTH_DATE_PART, "")));
        }
        LocalDate firstRanking = collectingSince.withDayOfMonth(1).plusMonths(1);
        return HtmlText.empty(fill(NO_CLOSED_MONTH, "date", esc(GermanFormat.day(collectingSince))) + " "
            + fill(NO_CLOSED_MONTH_FIRST, "date", esc(GermanFormat.day(firstRanking))));
    }

    private static String table(YearMonth month, List<BranchRankingRow> ranked, List<BranchRankingRow> low) {
        YearMonth previous = month.minusMonths(1);
        StringBuilder h = new StringBuilder(12288);
        h.append(HtmlText.scrollOpen(TABLE_TITLE));
        h.append("<table class=\"st-table rk-table\">");
        h.append("<caption class=\"sr\">").append(esc(caption(month, previous))).append("</caption>");
        h.append(head());
        h.append("<tbody>");
        for (BranchRankingRow row : ranked) {
            h.append(row(row, previous));
        }
        h.append("</tbody>");
        if (!low.isEmpty()) {
            List<String> names = new ArrayList<>(low.size());
            for (BranchRankingRow row : low) {
                names.add(row.branchName() == null ? "" : row.branchName());
            }
            h.append("<tbody class=\"rk-low\"><tr><th scope=\"rowgroup\" colspan=\"").append(COLUMNS).append("\">")
                .append(fill(RANKING_BELOW_K, "names", esc(String.join(GermanFormat.LIST_SEPARATOR, names))))
                .append("</th></tr>");
            for (BranchRankingRow row : low) {
                h.append(row(row, previous));
            }
            h.append("</tbody>");
        }
        h.append("</table></div>");
        return h.toString();
    }

    private static String caption(YearMonth month, YearMonth previous) {
        return HtmlText.fillPlain(RANKING_CAPTION,
            "month", GermanFormat.monthYear(month),
            "year", String.valueOf(month.getYear()),
            "monthName", GermanFormat.monthName(month),
            "previousMonthName", GermanFormat.monthName(previous));
    }

    private static String head() {
        return "<thead><tr>"
            + "<th scope=\"col\">" + esc(RANKING_COL_RANK) + "</th>"
            + "<th scope=\"col\" class=\"st-l\">" + esc(RANKING_COL_BRANCH) + "</th>"
            + "<th scope=\"col\" class=\"st-l\">" + esc(RANKING_COL_BOOKINGS) + "</th>"
            + "<th scope=\"col\">" + esc(RANKING_COL_SHARE) + small(RANKING_COL_SHARE_SUB, false) + "</th>"
            + "<th scope=\"col\" class=\"opt\">" + esc(RANKING_COL_YEAR) + "</th>"
            + "<th scope=\"col\" class=\"opt\">" + esc(RANKING_COL_VIEWS) + small(RANKING_COL_VIEWS_SUB, false) + "</th>"
            + "<th scope=\"col\" class=\"opt\">" + esc(RANKING_COL_PER_100) + small(RANKING_COL_PER_100_SUB, false) + "</th>"
            + "<th scope=\"col\" class=\"opt\">" + esc(RANKING_COL_OFF_HOURS) + small(RANKING_COL_OFF_HOURS_SUB, true) + "</th>"
            + "<th scope=\"col\" class=\"opt\">" + esc(RANKING_COL_LATE) + small(RANKING_COL_LATE_SUB, false) + "</th>"
            + "<th scope=\"col\">" + esc(RANKING_COL_MOVE) + "</th>"
            + "</tr></thead>";
    }

    /** Sub line of a column head; the leading space keeps the text readable as one phrase. */
    private static String small(String text, boolean noWrap) {
        return " <span class=\"st-small" + (noWrap ? " st-nw" : "") + "\">" + esc(text) + "</span>";
    }

    /** One ranked or below-threshold row / eine Zeile der Rangliste. */
    private static String row(BranchRankingRow row, YearMonth previous) {
        StringBuilder h = new StringBuilder(2048);
        h.append("<tr>");
        if (row.rank() != null) {
            h.append("<td class=\"st-rank\">").append(esc(String.valueOf(row.rank()))).append("</td>");
        } else {
            h.append("<td class=\"st-rank\">").append(HtmlText.hidden(HtmlText.DASH, RANK_NONE_SR)).append("</td>");
        }
        h.append("<th scope=\"row\">").append(esc(row.branchName())).append("</th>");
        h.append("<td class=\"st-l\"><span class=\"rk-cell\"><span class=\"rk-bar\" aria-hidden=\"true\">")
            .append("<span class=\"rk-fill\" style=\"width:").append(barWidth(row)).append("%\"></span></span>")
            .append(CellHtml.strong(row.bookings(), Format.COUNT)).append("</span></td>");
        h.append(CellHtml.td(row.share(), Format.PERCENT_INT));
        h.append(CellHtml.td(row.bookingsYear(), Format.COUNT, "opt"));
        h.append(CellHtml.td(row.serviceViews(), Format.COUNT, "opt"));
        h.append(CellHtml.td(row.bookingsPer100Views(), Format.RATIO, "opt"));
        h.append(CellHtml.td(row.offHoursShare(), Format.PERCENT_INT, "opt"));
        h.append(CellHtml.td(row.lateShare(), Format.PERCENT_INT, "opt"));
        h.append(rankMove(row, previous));
        h.append("</tr>");
        return h.toString();
    }

    /** Bar width — {@code 0.0} unless the bookings cell is visible (BR-9 Nr. 5). */
    private static String barWidth(BranchRankingRow row) {
        if (!CellHtml.isValue(row.bookings())) {
            return "0.0";
        }
        return StatFormat.cssWidth(CellHtml.clamp(row.barWidth()));
    }

    /**
     * Rank change as arrow ({@code aria-hidden}) plus {@code .sr} text and the previous rank
     * „Sep.: 2" (§11.2). Accepts both the key form ({@code up|down|same|new}) and the symbol form
     * ({@code ↑ 2|↓ 1|=|neu}) of {@code rankMove}. / Rangänderung mit Pfeil und Vormonatsrang.
     */
    private static String rankMove(BranchRankingRow row, YearMonth previous) {
        String prev = row.previousRank() == null ? ""
            : "<span class=\"rk-prev\">" + fill(RANK_PREVIOUS,
                "monthShort", esc(GermanFormat.monthShort(previous)),
                "rank", esc(String.valueOf(row.previousRank()))) + "</span>";
        Move move = Move.of(row.rankMove(), row.rank(), row.previousRank());
        if (move == null) {
            return "<td class=\"st-nd\">" + HtmlText.hidden(HtmlText.DASH,
                row.rank() == null ? RANK_NONE_SR : CellHtml.CELL_NO_DATA_SR) + prev + "</td>";
        }
        String steps = String.valueOf(move.steps(row.rankMove(), row.rank(), row.previousRank()));
        return switch (move) {
            case UP -> "<td>" + arrow(RANK_UP, RANK_UP_SR, steps) + prev + "</td>";
            case DOWN -> "<td>" + arrow(RANK_DOWN, RANK_DOWN_SR, steps) + prev + "</td>";
            case SAME -> "<td>" + HtmlText.hidden(esc(RANK_SAME), RANK_SAME_SR) + prev + "</td>";
            case NEW -> "<td>" + esc(RANK_NEW) + prev + "</td>";
        };
    }

    private static String arrow(String visible, String sr, String steps) {
        return HtmlText.hidden(fill(visible, "n", esc(steps)), HtmlText.fillPlain(sr, "n", steps));
    }

    /** Direction of a rank change / Richtung einer Rangänderung. */
    enum Move {
        UP, DOWN, SAME, NEW;

        /** Parses {@code rankMove}, falling back to the two ranks; {@code null} = no statement. */
        static Move of(String rankMove, Integer rank, Integer previousRank) {
            String key = rankMove == null ? "" : rankMove.trim().toLowerCase(Locale.ROOT);
            if (key.startsWith("up") || key.startsWith("↑")) {
                return UP;
            }
            if (key.startsWith("down") || key.startsWith("↓")) {
                return DOWN;
            }
            if (key.startsWith("same") || key.startsWith("=")) {
                return SAME;
            }
            if (key.startsWith("new") || key.startsWith(RANK_NEW)) {
                return NEW;
            }
            if (rank != null && previousRank != null) {
                int diff = previousRank - rank;
                return diff > 0 ? UP : diff < 0 ? DOWN : SAME;
            }
            return null;
        }

        /** Number of places moved; from the ranks, else the digits of {@code rankMove}. */
        int steps(String rankMove, Integer rank, Integer previousRank) {
            if (rank != null && previousRank != null) {
                return Math.abs(previousRank - rank);
            }
            String digits = rankMove == null ? "" : rankMove.replaceAll("\\D", "");
            if (digits.isEmpty() || digits.length() > 4) {
                return 1;
            }
            return Integer.parseInt(digits);
        }
    }
}
