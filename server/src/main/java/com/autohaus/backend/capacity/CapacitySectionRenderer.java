package com.autohaus.backend.capacity;

import com.autohaus.backend.common.html.GermanFormat;
import com.autohaus.backend.common.html.HtmlText;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.autohaus.backend.common.html.HtmlText.esc;
import static com.autohaus.backend.common.html.HtmlText.fill;

/**
 * Renders S1 „Freie Online-Termine je Standort" incl. the 10-day grid (REQ-038 §11.2, N2) /
 * rendert S1 mit dem 10-Tage-Raster. {@code null} → empty string (source off, BR-4).
 *
 * <p><b>Contract / Vertrag:</b> stateless renderer — view model in, HTML string out. Classes and
 * the section id come verbatim from §11.2/§16 ({@code section#capacity.cap},
 * {@code table.st-table.cap-table}, {@code span.cap-pill*}, {@code td.cap-day*},
 * {@code td.cap-sum}, {@code span.cap-class}); visible labels are the German literals of §17.
 * Every dynamic value (branch names, labels, warnings) passes through {@link HtmlText#esc}. /
 * Zustandsloser Renderer; Klassen nach §11.2, Texte nach §17.
 *
 * <p><b>Dependencies / Abhängigkeiten (§9):</b> only the capacity view model and the neutral
 * package {@code common.html} — no {@code usage/insights} type. The level and reason texts
 * ({@code LEVEL_*}, {@code REASON_*}) have their one source in {@link CapacityViewModelBuilder};
 * the fallback labels for hand-built views below reuse those constants. / Stufen- und Grundtexte
 * stehen nur im Builder.
 *
 * <p><b>Colour is never the only carrier (BR-23, AC-26):</b> the level pill shows its text, every
 * grid cell its short text „frei"/„knapp"/„belegt"/„—", a not measurable cell names its reason
 * in {@code title}, in {@code .sr} and in the footnote. / Farbe ist nie alleiniger Träger.
 *
 * <p><b>No „&lt; 5" here (BR-26):</b> free slots are operational data without personal
 * reference, so the K-2 total is shown as is — including 0–4. A failed probe is „nicht messbar"
 * with its reason and never 0 (BR-14, AC-10). / Kapazitätswerte werden nicht unterdrückt.
 *
 * <p>Warnings (partial scan, failed scan with fallback, stale scan, history unavailable) are
 * built by the view model and shown here as {@code p.st-warn} each. / Warnhinweise kommen fertig
 * formatiert aus dem View-Model.
 */
@Component
public class CapacitySectionRenderer {

    // -----------------------------------------------------------------------------------------
    // Labels (§17) / Beschriftungen
    // -----------------------------------------------------------------------------------------

    /** §17 {@code CAPACITY_HEADING}. */
    public static final String CAPACITY_HEADING = "Freie Online-Termine je Standort";

    /** Part of {@code CAPACITY_RANGE}: the shown scan / Scan-Teil. */
    public static final String RANGE_SCAN = "Kapazitäts-Scan vom {scanDate}";
    /** Part of {@code CAPACITY_RANGE_FALLBACK}: the shown fallback scan / Scan-Teil beim Fallback. */
    public static final String RANGE_FALLBACK_SCAN = "Letzter vollständiger Scan: {scanDate}";
    /**
     * Part of both range lines: the scan times {@code {from}–{to}} from
     * {@link CapacityViewModel#scanStartedAt()} / {@link CapacityViewModel#scanFinishedAt()}
     * (Europe/Berlin); a view without them leaves the part out. / Scan-Uhrzeiten, sonst entfällt
     * der Teil.
     */
    public static final String RANGE_TIMES = ", {from}–{to} Uhr";
    /** Part of {@code CAPACITY_RANGE}: the target day, left out without one / Zieltag-Teil. */
    public static final String RANGE_TARGET = " · Zieltag: {weekday} {targetDate} (nächster Werktag)";
    /** Part of {@code CAPACITY_RANGE_FALLBACK}: the target day of the fallback scan / Zieltag damals. */
    public static final String RANGE_FALLBACK_TARGET = " · Zieltag damals: {weekday} {targetDate}";
    /** Part of both range lines: the working days / Werktage. */
    public static final String RANGE_WORKDAYS = " · Werktage Mo–Sa";
    /** Part of both range lines: the grid window, left out without grid days / Verlaufs-Teil. */
    public static final String RANGE_TREND = " · Verlauf: letzte 10 Scans ({trendFrom}–{trendTo})";

    /**
     * §17 {@code CAPACITY_RANGE} — placeholders named apart where §17 reuses {@code {date}}; the
     * line is assembled from the {@code RANGE_*} parts above. / aus den Teilen zusammengesetzt.
     */
    public static final String CAPACITY_RANGE =
        RANGE_SCAN + RANGE_TIMES + RANGE_TARGET + RANGE_WORKDAYS + RANGE_TREND;

    /** §17 {@code CAPACITY_RANGE_FALLBACK} (latest scan failed, BR-14). */
    public static final String CAPACITY_RANGE_FALLBACK =
        RANGE_FALLBACK_SCAN + RANGE_TIMES + RANGE_FALLBACK_TARGET + RANGE_WORKDAYS + RANGE_TREND;

    /** §17 {@code CAPACITY_COL_*} — Standort. */
    public static final String CAPACITY_COL_BRANCH = "Standort";
    /** §17 {@code CAPACITY_COL_*} — Nächster freier Online-Termin. */
    public static final String CAPACITY_COL_NEXT = "Nächster freier Online-Termin";
    /** §17 {@code CAPACITY_COL_*} — Frei am Zieltag (K-2 header). */
    public static final String CAPACITY_COL_FREE_TARGET = "Frei am Zieltag";
    /** §17 {@code CAPACITY_COL_*} — the approximation remark of K-2 (BR-20, AC-29). */
    public static final String CAPACITY_COL_FREE_TARGET_SUB = "Referenz-Leistung, Näherung";
    /** §17 {@code CAPACITY_COL_*} / {@code TREND_HEADING} — Verlauf letzte 10 Tage. */
    public static final String CAPACITY_COL_TREND = "Verlauf letzte 10 Tage";
    /** §17 {@code CAPACITY_COL_*} — an X von 10 Tagen frei. */
    public static final String CAPACITY_COL_SUM = "an X von 10 Tagen frei";
    /** §17 {@code CAPACITY_COL_*} — Einordnung. */
    public static final String CAPACITY_COL_CLASS = "Einordnung";
    /** §17 {@code CAPACITY_COL_SUB_TREND}. */
    public static final String CAPACITY_COL_SUB_TREND = "Zustand des jeweiligen Zieltags";
    /** §17 {@code CAPACITY_COL_SUB_ASOF} (fallback scan only). */
    public static final String CAPACITY_COL_SUB_ASOF = "Stand {date}";

    /** §17 {@code REASON_PREFIX}. */
    public static final String REASON_PREFIX = "Grund: {reason}";
    /**
     * {@code {n}} of {@code LEVEL_WITHIN_WORKDAYS} for a hand-built row without a workday count /
     * Zahl, wenn die Zeile keine Werktage trägt.
     */
    public static final String WORKDAYS_UNKNOWN = "1–4";

    /** §17 {@code SLOTS_TOTAL_SR} (K-2, {@code .sr}). */
    public static final String SLOTS_TOTAL_SR = "freie Online-Termine am Zieltag";

    /** §17 {@code DAY_FREE}. */
    public static final String DAY_FREE = "frei";
    /** §17 {@code DAY_TIGHT}. */
    public static final String DAY_TIGHT = "knapp";
    /** §17 {@code DAY_BOOKED}. */
    public static final String DAY_BOOKED = "belegt";
    /** §17 {@code DAY_NA} (visible) — the plain dash / sichtbarer Strich. */
    public static final String DAY_NA = HtmlText.DASH_TEXT;
    /** §17 {@code DAY_NA} ({@code .sr}/{@code title}) with a reason. */
    public static final String DAY_NA_SR = "nicht messbar: {reason}";
    /** §17 {@code DAY_NA} ({@code .sr}/{@code title}) for a day without scan. */
    public static final String DAY_NA_NO_SCAN = "kein Scan";

    /** §17 {@code TREND_SUM}. */
    public static final String TREND_SUM = "an {x} von {n} Tagen frei";

    /** §17 {@code CLASS_MOSTLY_FREE}. */
    public static final String CLASS_MOSTLY_FREE = "meist frei";
    /** §17 {@code CLASS_MIXED}. */
    public static final String CLASS_MIXED = "wechselnd";
    /** §17 {@code CLASS_MOSTLY_BOOKED}. */
    public static final String CLASS_MOSTLY_BOOKED = "meist belegt";
    /** §17 {@code CLASS_TOO_FEW_DAYS} (visible part, §11.5). */
    public static final String CLASS_TOO_FEW_DAYS = "zu wenig Daten";
    /** §17 {@code CLASS_TOO_FEW_DAYS} (explanation, {@code .sr}). */
    public static final String CLASS_TOO_FEW_DAYS_DETAIL = "unter 5 messbare Tage";

    /** §17 {@code CAPACITY_LEGEND_PILL_*} — free. */
    public static final String CAPACITY_LEGEND_PILL_FREE = "am Zieltag online frei (relativ zum Abruftag)";
    /** §17 {@code CAPACITY_LEGEND_PILL_*} — within 4 workdays. */
    public static final String CAPACITY_LEGEND_PILL_WITHIN = "erster freier Termin in 1–4 Werktagen";
    /** §17 {@code CAPACITY_LEGEND_PILL_*} — booked up. */
    public static final String CAPACITY_LEGEND_PILL_TIGHT = "oder keine Online-Termine gefunden";
    /** §17 {@code CAPACITY_LEGEND_PILL_*} — not measurable. */
    public static final String CAPACITY_LEGEND_PILL_NA = "mit Grund, nie 0";
    /** §17 {@code CAPACITY_LEGEND_DAY_*} — frei. */
    public static final String CAPACITY_LEGEND_DAY_FREE = "frei = am Zieltag online frei";
    /** §17 {@code CAPACITY_LEGEND_DAY_*} — knapp. */
    public static final String CAPACITY_LEGEND_DAY_TIGHT = "knapp = erster freier Termin in 1–4 Werktagen";
    /** §17 {@code CAPACITY_LEGEND_DAY_*} — belegt. */
    public static final String CAPACITY_LEGEND_DAY_BOOKED =
        "belegt = ≥ 5 Werktage oder keine Online-Termine gefunden";
    /** §17 {@code CAPACITY_LEGEND_DAY_*} — nicht messbar. */
    public static final String CAPACITY_LEGEND_DAY_NA =
        "— = nicht messbar oder kein Scan (Grund in der Fußnote)";
    /** §17 {@code CAPACITY_LEGEND_CLASS}. */
    public static final String CAPACITY_LEGEND_CLASS = "Einordnung ab 5 messbaren Tagen: meist frei "
        + "≥ 7 von 10 Tagen frei · wechselnd 3–6 · meist belegt ≤ 2 (bei weniger messbaren Tagen "
        + "sinngemäß: ≥ 70 % / ≤ 20 %), sonst „zu wenig Daten“";
    /** Workday count of the example pill „in 2 Werktagen" in the legend / Beispielwert der Legende. */
    public static final int LEGEND_EXAMPLE_WORKDAYS = 2;

    /** §17 {@code CAPACITY_NOTE} (BR-20). */
    public static final String CAPACITY_NOTE = "Gemessen mit der Referenz-Leistung „Wischerblätter "
        + "erneuern“ (kleinste Einheit). Das ist die online buchbare Restkapazität, nicht die "
        + "Werkstattauslastung. Freie Termine sind Betriebsdaten ohne Personenbezug; die "
        + "„< 5“-Regel gilt hier nicht.";

    /** §17 {@code CAPACITY_NO_SCAN}. */
    public static final String CAPACITY_NO_SCAN =
        "Noch kein Kapazitäts-Scan – der erste läuft heute um {time} Uhr.";
    /**
     * §17 {@code CAPACITY_EMPTY_FIRST_SCAN} ({@code .st-empty}, first run in progress) — it contains
     * the sentence of §17 {@code CAPACITY_SCAN_RUNNING}, which has no text of its own. / enthält den
     * Satz „Der erste Kapazitäts-Scan läuft seit … Uhr".
     */
    public static final String CAPACITY_EMPTY_FIRST_SCAN = "Noch keine Messung. Der erste "
        + "Kapazitäts-Scan läuft seit {time} Uhr (gestartet {date}; üblich 3–5 Minuten). "
        + "Erfassung seit {since}.";

    /** Footnote naming every „—" cell; {@code {entries}} joined by {@link #FOOTNOTE_SEPARATOR}. */
    public static final String FOOTNOTE = "„—“ nicht messbar: {entries}.";
    /** One footnote entry: branch (or „alle Standorte"), days and reason / ein Fußnoten-Eintrag. */
    public static final String FOOTNOTE_ENTRY = "{subject} {days}: {reason}";
    /** Separator of the footnote entries / Trenner der Fußnoten-Einträge. */
    public static final String FOOTNOTE_SEPARATOR = " · ";
    /** Footnote subject when a day failed for every branch / „alle Standorte". */
    public static final String FOOTNOTE_ALL_BRANCHES = "alle Standorte";
    /** A run of three or more consecutive grid days in the footnote / Tagesbereich. */
    public static final String DAY_RANGE = "{from}–{to}";

    /** Visible title of the table (scroll region) / Tabellentitel. */
    public static final String TABLE_TITLE = "freie Online-Termine je Standort";
    /** Title of the table while a fallback scan is shown / Tabellentitel beim Fallback-Scan. */
    public static final String TABLE_TITLE_FALLBACK = TABLE_TITLE + ", Stand {date}";
    /**
     * Caption of the table: sort rule and reading of the grid (BR-26, §11.2); {@code {scan}} is
     * {@link #CAPTION_SCAN} or {@link #CAPTION_SCAN_FALLBACK}, {@code {slots}} is
     * {@link #CAPTION_SLOTS} with the K-2 column. / Sortierregel und Lesart als Caption.
     */
    public static final String CAPTION = CAPACITY_HEADING + "{scan}, sortiert nach der Einordnung des "
        + "10-Tage-Verlaufs (meist frei zuerst, dann wechselnd, meist belegt, zu wenig Daten), "
        + "innerhalb der Einordnung nach Zahl der freien Tage: nächster freier Online-Termin, "
        + "{slots}Zustand des Zieltags je Scan der letzten 10 Werktage und Zusammenfassung.";
    /** {@code {scan}} of {@link #CAPTION} for the latest scan / Scan-Teil der Caption. */
    public static final String CAPTION_SCAN = ", Scan vom {date}";
    /** {@code {scan}} of {@link #CAPTION} for a fallback scan / Scan-Teil beim Fallback. */
    public static final String CAPTION_SCAN_FALLBACK = ", Stand des letzten vollständigen Scans vom {date}";
    /** {@code {slots}} of {@link #CAPTION} with the K-2 column / K-2-Teil der Caption. */
    public static final String CAPTION_SLOTS = "freie Termine am Zieltag, ";
    /** Accessible name of the legend / Name der Legende. */
    public static final String LEGEND_ARIA = "Legende Stufen und Verlauf";

    /**
     * Scheduled scan time of the default cron {@code 0 30 22 * * *} (AC-8) — the view model does not
     * carry the configured time yet (contract request), so {@code CAPACITY_NO_SCAN} names the
     * default. / Standard-Uhrzeit des Scans, solange das View-Model sie nicht trägt.
     */
    public static final LocalTime DEFAULT_SCAN_TIME = LocalTime.of(22, 30);

    /** Display zone (BR-12) / Anzeigezone. */
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    /**
     * Renders the section, {@code ""} when there is nothing to render (source off, BR-4) /
     * rendert die Sektion.
     */
    public String render(CapacityViewModel viewModel) {
        if (viewModel == null) {
            return "";
        }
        StringBuilder h = new StringBuilder(16384);
        h.append("<section id=\"capacity\" class=\"cap\" aria-labelledby=\"capacity-h\">");
        h.append("<h2 id=\"capacity-h\">").append(esc(CAPACITY_HEADING)).append("</h2>");
        h.append(HtmlText.warnings(viewModel.warnings()));

        List<CapacityRow> rows = viewModel.rows() == null ? List.of() : viewModel.rows();
        if (rows.isEmpty()) {
            h.append(emptyState(viewModel));
        } else {
            h.append(range(viewModel));
            h.append(table(viewModel, rows));
            h.append(footnote(viewModel, rows));
            h.append("<p class=\"st-note\">").append(esc(CAPACITY_NOTE)).append("</p>");
            h.append(legend());
        }
        h.append("</section>");
        return h.toString();
    }

    // -----------------------------------------------------------------------------------------
    // Empty states / Leerzustände (§11.5)
    // -----------------------------------------------------------------------------------------

    /**
     * No rows: first scan running (runningSince), history unavailable (warning only, E4) or no
     * scan yet. Never zeros. / Keine Zeilen: erster Lauf, Historie nicht verfügbar oder kein Scan.
     */
    private static String emptyState(CapacityViewModel vm) {
        if (HtmlText.hasWarning(vm.warnings(), CapacityViewModel.WARN_HISTORY_UNAVAILABLE)) {
            return "";
        }
        StringBuilder h = new StringBuilder(512);
        if (vm.runningSince() != null) {
            ZonedDateTime started = vm.runningSince().atZone(ZONE);
            LocalDate since = vm.collectingSince() != null ? vm.collectingSince() : started.toLocalDate();
            h.append(HtmlText.empty(fill(CAPACITY_EMPTY_FIRST_SCAN,
                "time", esc(GermanFormat.time(started)),
                "date", esc(GermanFormat.day(started.toLocalDate())),
                "since", esc(GermanFormat.day(since)))));
        } else {
            h.append(HtmlText.empty(fill(CAPACITY_NO_SCAN, "time", esc(GermanFormat.time(DEFAULT_SCAN_TIME)))));
        }
        h.append("<p class=\"st-note\">").append(esc(CAPACITY_NOTE)).append("</p>");
        return h.toString();
    }

    // -----------------------------------------------------------------------------------------
    // Range line / Zeitraumzeile
    // -----------------------------------------------------------------------------------------

    /**
     * {@code CAPACITY_RANGE} or, for a fallback scan, {@code CAPACITY_RANGE_FALLBACK} — without the
     * parts the view does not carry (scan times, target day and grid days are optional for
     * hand-built views). / Ohne die Teile, die die Sicht nicht trägt.
     */
    private static String range(CapacityViewModel vm) {
        if (vm.scanDay() == null) {
            return "";
        }
        boolean fallback = vm.showingFallbackScan();
        boolean withTimes = vm.scanStartedAt() != null && vm.scanFinishedAt() != null;
        List<LocalDate> days = gridDays(vm);
        String template = fallback ? CAPACITY_RANGE_FALLBACK : CAPACITY_RANGE;
        if (!withTimes) {
            template = template.replace(RANGE_TIMES, "");
        }
        if (vm.targetDay() == null) {
            template = template.replace(fallback ? RANGE_FALLBACK_TARGET : RANGE_TARGET, "");
        }
        if (days.isEmpty()) {
            template = template.replace(RANGE_TREND, "");
        }
        return "<p class=\"st-range\">" + fill(template,
            "from", withTimes ? esc(GermanFormat.time(vm.scanStartedAt().atZone(ZONE))) : "",
            "to", withTimes ? esc(GermanFormat.time(vm.scanFinishedAt().atZone(ZONE))) : "",
            "scanDate", esc(GermanFormat.day(vm.scanDay())),
            "weekday", vm.targetDay() == null ? "" : esc(GermanFormat.weekdayShort(vm.targetDay().getDayOfWeek())),
            "targetDate", esc(GermanFormat.day(vm.targetDay())),
            "trendFrom", days.isEmpty() ? "" : esc(GermanFormat.dayShort(days.getFirst())),
            "trendTo", days.isEmpty() ? "" : esc(GermanFormat.dayShort(days.getLast()))) + "</p>";
    }

    // -----------------------------------------------------------------------------------------
    // Table / Tabelle
    // -----------------------------------------------------------------------------------------

    private static String table(CapacityViewModel vm, List<CapacityRow> rows) {
        List<LocalDate> days = gridDays(vm);
        boolean showSlots = rows.stream().anyMatch(r -> r.freeSlotsTargetDay() != null);
        String title = vm.showingFallbackScan() && vm.scanDay() != null
            ? HtmlText.fillPlain(TABLE_TITLE_FALLBACK, "date", GermanFormat.day(vm.scanDay()))
            : TABLE_TITLE;

        StringBuilder h = new StringBuilder(12288);
        h.append(HtmlText.scrollOpen(title));
        h.append("<table class=\"st-table cap-table\">");
        h.append("<caption class=\"sr\">").append(esc(caption(vm, showSlots))).append("</caption>");
        h.append("<colgroup span=\"").append(showSlots ? 3 : 2).append("\"></colgroup>");
        if (!days.isEmpty()) {
            h.append("<colgroup span=\"").append(days.size()).append("\"></colgroup>");
        }
        h.append("<colgroup span=\"2\"></colgroup>");
        h.append(head(vm, days, showSlots));
        h.append("<tbody>");
        for (CapacityRow row : rows) {
            h.append(row(vm, row, days.size(), showSlots));
        }
        h.append("</tbody></table></div>");
        return h.toString();
    }

    /** Sort rule and reading of the grid as caption (BR-26, §11.2) / Sortierregel als Caption. */
    private static String caption(CapacityViewModel vm, boolean showSlots) {
        String scan = vm.scanDay() == null ? ""
            : HtmlText.fillPlain(vm.showingFallbackScan() ? CAPTION_SCAN_FALLBACK : CAPTION_SCAN,
                "date", GermanFormat.day(vm.scanDay()));
        return HtmlText.fillPlain(CAPTION, "scan", scan, "slots", showSlots ? CAPTION_SLOTS : "");
    }

    private static String head(CapacityViewModel vm, List<LocalDate> days, boolean showSlots) {
        StringBuilder h = new StringBuilder(2048);
        String rowspan = days.isEmpty() ? "" : " rowspan=\"2\"";
        h.append("<thead><tr>");
        h.append("<th scope=\"col\"").append(rowspan).append(" class=\"st-l\">")
            .append(esc(CAPACITY_COL_BRANCH)).append("</th>");
        h.append("<th scope=\"col\"").append(rowspan).append(" class=\"st-l\">").append(esc(CAPACITY_COL_NEXT));
        if (vm.showingFallbackScan() && vm.scanDay() != null) {
            h.append("<span class=\"st-small\">")
                .append(fill(CAPACITY_COL_SUB_ASOF, "date", esc(GermanFormat.dayShort(vm.scanDay()))))
                .append("</span>");
        }
        h.append("</th>");
        if (showSlots) {
            h.append("<th scope=\"col\"").append(rowspan).append('>').append(esc(CAPACITY_COL_FREE_TARGET))
                .append("<span class=\"st-small\">").append(esc(CAPACITY_COL_FREE_TARGET_SUB))
                .append("</span></th>");
        }
        if (!days.isEmpty()) {
            h.append("<th scope=\"colgroup\" colspan=\"").append(days.size()).append("\" class=\"st-l\">")
                .append(esc(CAPACITY_COL_TREND)).append("<span class=\"st-small\">")
                .append(esc(CAPACITY_COL_SUB_TREND)).append("</span></th>");
        }
        h.append("<th scope=\"col\"").append(rowspan).append('>').append(esc(CAPACITY_COL_SUM)).append("</th>");
        h.append("<th scope=\"col\"").append(rowspan).append(" class=\"st-l\">")
            .append(esc(CAPACITY_COL_CLASS)).append("</th>");
        h.append("</tr>");
        if (!days.isEmpty()) {
            h.append("<tr>");
            for (LocalDate day : days) {
                h.append("<th scope=\"col\">").append(esc(GermanFormat.dayShort(day)))
                    .append("<span class=\"st-small\">")
                    .append(day == null ? "" : esc(GermanFormat.weekdayShort(day.getDayOfWeek())))
                    .append("</span></th>");
            }
            h.append("</tr>");
        }
        h.append("</thead>");
        return h.toString();
    }

    /** One branch row: pill, K-2 total, 10 grid cells, summary, class (AC-9). */
    private static String row(CapacityViewModel vm, CapacityRow row, int dayCount, boolean showSlots) {
        StringBuilder h = new StringBuilder(2048);
        h.append("<tr><th scope=\"row\">").append(esc(row.branchName())).append("</th>");
        h.append("<td class=\"st-l\">").append(pill(vm, row)).append("</td>");
        if (showSlots) {
            if (row.freeSlotsTargetDay() != null) {
                h.append("<td><span class=\"cap-total\">").append(esc(GermanFormat.number(row.freeSlotsTargetDay())))
                    .append("</span><span class=\"sr\"> ").append(esc(SLOTS_TOTAL_SR)).append("</span></td>");
            } else {
                h.append("<td class=\"st-nd\">").append(HtmlText.noData()).append("</td>");
            }
        }
        for (int i = 0; i < dayCount; i++) {
            h.append(dayCell(stateAt(row, i), reasonAt(row, i)));
        }
        h.append("<td class=\"cap-sum\">").append(fill(TREND_SUM,
            "x", "<b>" + esc(GermanFormat.number(freeDays(row))) + "</b>",
            "n", esc(GermanFormat.number(measurableDays(row))))).append("</td>");
        h.append("<td class=\"st-l\">").append(classification(row.classification())).append("</td>");
        h.append("</tr>");
        return h.toString();
    }

    /**
     * Level pill plus {@code .cap-when} (§11.2): free → target day; 1–4 and ≥ 5 workdays → date of
     * the first free slot; not measurable → „Grund: …". / Stufen-Pille mit Datum bzw. Grund.
     */
    private static String pill(CapacityViewModel vm, CapacityRow row) {
        CapacityLevel level = row.level() == null ? CapacityLevel.NOT_MEASURABLE : row.level();
        String modifier = switch (level) {
            case FREE_NEXT_DAY -> " cap-pill-free";
            case WITHIN_4_WORKDAYS -> "";
            case TIGHT, NONE_FOUND -> " cap-pill-tight";
            case NOT_MEASURABLE -> " cap-pill-na";
        };
        String label = row.levelLabel() != null && !row.levelLabel().isBlank()
            ? row.levelLabel()
            : defaultLabel(level, row.workdays(), vm.targetDay());
        String when = switch (level) {
            case FREE_NEXT_DAY, WITHIN_4_WORKDAYS, TIGHT -> GermanFormat.weekdayAndDay(
                level == CapacityLevel.FREE_NEXT_DAY
                    ? vm.targetDay()
                    : row.firstFreeDate() != null
                        ? row.firstFreeDate()
                        : firstFreeDate(vm.targetDay(), row.workdays()));
            case NONE_FOUND -> "";
            case NOT_MEASURABLE -> row.reasonKey() == null ? ""
                : HtmlText.fillPlain(REASON_PREFIX, "reason", reason(row.reasonKey()));
        };
        return "<span class=\"cap-pill" + modifier + "\">" + esc(label) + "</span>"
            + (when.isEmpty() ? "" : "<span class=\"cap-when\">" + esc(when) + "</span>");
    }

    /**
     * Fallback label for a hand-built row without {@code levelLabel} — the §17 texts of
     * {@link CapacityViewModelBuilder}; BR-25 needs {@code today}, so FREE names the date. /
     * Ersatz-Beschriftung aus den Builder-Konstanten.
     */
    private static String defaultLabel(CapacityLevel level, Integer workdays, LocalDate targetDay) {
        return switch (level) {
            case FREE_NEXT_DAY -> targetDay == null ? CapacityViewModelBuilder.LEVEL_FREE_TOMORROW
                : HtmlText.fillPlain(CapacityViewModelBuilder.LEVEL_FREE_ON,
                    "weekday", GermanFormat.weekdayShort(targetDay.getDayOfWeek()),
                    "date", GermanFormat.dayShort(targetDay));
            case WITHIN_4_WORKDAYS -> workdays != null && workdays == 1
                ? CapacityViewModelBuilder.LEVEL_WITHIN_ONE_WORKDAY
                : HtmlText.fillPlain(CapacityViewModelBuilder.LEVEL_WITHIN_WORKDAYS,
                    "n", workdays == null ? WORKDAYS_UNKNOWN : String.valueOf(workdays));
            case TIGHT -> CapacityViewModelBuilder.LEVEL_TIGHT;
            case NONE_FOUND -> CapacityViewModelBuilder.LEVEL_NONE_FOUND;
            case NOT_MEASURABLE -> CapacityViewModelBuilder.LEVEL_NOT_MEASURABLE;
        };
    }

    /**
     * Fallback for rows without {@link CapacityRow#firstFreeDate()}: target day advanced by
     * {@code workdays} working days Mo–Sa (§10.4 definition of {@code workdaysToFirstFree});
     * {@code null} without data. / Ersatz, wenn die Zeile kein Datum trägt.
     */
    static LocalDate firstFreeDate(LocalDate targetDay, Integer workdays) {
        if (targetDay == null || workdays == null || workdays < 0) {
            return null;
        }
        LocalDate date = targetDay;
        int remaining = workdays;
        while (remaining > 0) {
            date = date.plusDays(1);
            if (date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                remaining--;
            }
        }
        return date;
    }

    /** One grid cell with its short text (BR-23, BR-26) / eine Verlaufszelle mit Kürzel. */
    private static String dayCell(CapacityDayState state, String reasonKey) {
        return switch (state) {
            case FREE -> "<td class=\"cap-day cap-day-free\">" + esc(DAY_FREE) + "</td>";
            case TIGHT_DAY -> "<td class=\"cap-day cap-day-tight\">" + esc(DAY_TIGHT) + "</td>";
            case BOOKED -> "<td class=\"cap-day cap-day-booked\">" + esc(DAY_BOOKED) + "</td>";
            case NOT_MEASURABLE -> {
                String text = naText(reasonKey);
                yield "<td class=\"cap-day cap-day-na\" title=\"" + esc(text) + "\">"
                    + HtmlText.hidden(esc(DAY_NA), text) + "</td>";
            }
        };
    }

    /** „nicht messbar: Zeitüberschreitung" or „kein Scan" / Grundtext einer „—"-Zelle. */
    private static String naText(String reasonKey) {
        return reasonKey == null ? DAY_NA_NO_SCAN : HtmlText.fillPlain(DAY_NA_SR, "reason", reason(reasonKey));
    }

    private static String classification(CapacityTrend trend) {
        if (trend == null) {
            return "<span class=\"st-nd\">" + HtmlText.noData() + "</span>";
        }
        return switch (trend) {
            case MOSTLY_FREE -> "<span class=\"cap-class\">" + esc(CLASS_MOSTLY_FREE) + "</span>";
            case MIXED -> "<span class=\"cap-class\">" + esc(CLASS_MIXED) + "</span>";
            case MOSTLY_BOOKED -> "<span class=\"cap-class\">" + esc(CLASS_MOSTLY_BOOKED) + "</span>";
            case TOO_FEW_DAYS -> "<span class=\"cap-class\">" + esc(CLASS_TOO_FEW_DAYS)
                + "</span><span class=\"sr\"> (" + esc(CLASS_TOO_FEW_DAYS_DETAIL) + ")</span>";
        };
    }

    // -----------------------------------------------------------------------------------------
    // Footnote and legend / Fußnote und Legende
    // -----------------------------------------------------------------------------------------

    /**
     * Names every „—" cell with branch, day and reason (BR-26, §11.5); a day that failed for every
     * branch is named once as „alle Standorte". / Nennt jede „—"-Zelle mit Grund.
     */
    private static String footnote(CapacityViewModel vm, List<CapacityRow> rows) {
        List<LocalDate> days = gridDays(vm);
        if (days.isEmpty()) {
            return "";
        }
        List<String> entries = new ArrayList<>();
        boolean[] allFailed = new boolean[days.size()];
        Map<String, List<Integer>> allByReason = new LinkedHashMap<>();
        if (rows.size() > 1) {
            for (int i = 0; i < days.size(); i++) {
                String common = commonNaReason(rows, i);
                if (common != null) {
                    allFailed[i] = true;
                    allByReason.computeIfAbsent(common, k -> new ArrayList<>()).add(i);
                }
            }
        }
        allByReason.forEach((reason, columns) ->
            entries.add(footnoteEntry(FOOTNOTE_ALL_BRANCHES, dayList(days, columns), reasonLabel(reason))));
        for (CapacityRow row : rows) {
            Map<String, List<Integer>> byReason = new LinkedHashMap<>();
            for (int i = 0; i < days.size(); i++) {
                if (!allFailed[i] && stateAt(row, i) == CapacityDayState.NOT_MEASURABLE) {
                    byReason.computeIfAbsent(reasonToken(reasonAt(row, i)), k -> new ArrayList<>()).add(i);
                }
            }
            byReason.forEach((reason, columns) -> entries.add(footnoteEntry(Objects.toString(row.branchName(), ""),
                dayList(days, columns), reasonLabel(reason))));
        }
        if (entries.isEmpty()) {
            return "";
        }
        return "<p class=\"st-note\">"
            + esc(HtmlText.fillPlain(FOOTNOTE, "entries", String.join(FOOTNOTE_SEPARATOR, entries))) + "</p>";
    }

    private static String footnoteEntry(String subject, String days, String reason) {
        return HtmlText.fillPlain(FOOTNOTE_ENTRY, "subject", subject, "days", days, "reason", reason);
    }

    /** Reason token shared by every row in column {@code i}, else {@code null}. */
    private static String commonNaReason(List<CapacityRow> rows, int column) {
        String common = null;
        for (CapacityRow row : rows) {
            if (stateAt(row, column) != CapacityDayState.NOT_MEASURABLE) {
                return null;
            }
            String token = reasonToken(reasonAt(row, column));
            if (common == null) {
                common = token;
            } else if (!common.equals(token)) {
                return null;
            }
        }
        return common;
    }

    /** Null-safe map key for a reason / Schlüssel eines Grundes. */
    private static String reasonToken(String reasonKey) {
        return reasonKey == null ? "" : reasonKey;
    }

    private static String reasonLabel(String token) {
        return token.isEmpty() ? DAY_NA_NO_SCAN : reason(token);
    }

    /**
     * „11.11., 14.11. und 19.11." — runs of three or more consecutive grid columns collapse to
     * „09.11.–16.11.". / Datumsliste, zusammenhängende Läufe als Bereich.
     */
    private static String dayList(List<LocalDate> days, List<Integer> columns) {
        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < columns.size()) {
            int j = i;
            while (j + 1 < columns.size() && columns.get(j + 1) == columns.get(j) + 1) {
                j++;
            }
            if (j - i >= 2) {
                parts.add(HtmlText.fillPlain(DAY_RANGE,
                    "from", GermanFormat.dayShort(days.get(columns.get(i))),
                    "to", GermanFormat.dayShort(days.get(columns.get(j)))));
            } else {
                for (int k = i; k <= j; k++) {
                    parts.add(GermanFormat.dayShort(days.get(columns.get(k))));
                }
            }
            i = j + 1;
        }
        return GermanFormat.joinAnd(parts);
    }

    /** Legend of pills, grid cells and classification thresholds (§17 {@code CAPACITY_LEGEND_*}). */
    private static String legend() {
        return "<ul class=\"st-legend\" aria-label=\"" + esc(LEGEND_ARIA) + "\">"
            + "<li><span class=\"cap-pill cap-pill-free\">" + esc(CapacityViewModelBuilder.LEVEL_FREE_TOMORROW)
            + "</span>" + esc(CAPACITY_LEGEND_PILL_FREE) + "</li>"
            + "<li><span class=\"cap-pill\">" + esc(HtmlText.fillPlain(CapacityViewModelBuilder.LEVEL_WITHIN_WORKDAYS,
                "n", String.valueOf(LEGEND_EXAMPLE_WORKDAYS))) + "</span>"
            + esc(CAPACITY_LEGEND_PILL_WITHIN) + "</li>"
            + "<li><span class=\"cap-pill cap-pill-tight\">" + esc(CapacityViewModelBuilder.LEVEL_TIGHT) + "</span>"
            + esc(CAPACITY_LEGEND_PILL_TIGHT) + "</li>"
            + "<li><span class=\"cap-pill cap-pill-na\">" + esc(CapacityViewModelBuilder.LEVEL_NOT_MEASURABLE)
            + "</span>" + esc(CAPACITY_LEGEND_PILL_NA) + "</li>"
            + "<li><span class=\"st-sw cap-day-free\" aria-hidden=\"true\"></span>" + esc(CAPACITY_LEGEND_DAY_FREE) + "</li>"
            + "<li><span class=\"st-sw cap-day-tight\" aria-hidden=\"true\"></span>" + esc(CAPACITY_LEGEND_DAY_TIGHT) + "</li>"
            + "<li><span class=\"st-sw cap-day-booked\" aria-hidden=\"true\"></span>" + esc(CAPACITY_LEGEND_DAY_BOOKED) + "</li>"
            + "<li><span class=\"st-sw cap-day-na\" aria-hidden=\"true\"></span>" + esc(CAPACITY_LEGEND_DAY_NA) + "</li>"
            + "<li>" + esc(CAPACITY_LEGEND_CLASS) + "</li>"
            + "</ul>";
    }

    // -----------------------------------------------------------------------------------------
    // Row accessors / Zeilen-Zugriffe
    // -----------------------------------------------------------------------------------------

    /** The grid days of the view, never {@code null} / die Rastertage. */
    private static List<LocalDate> gridDays(CapacityViewModel vm) {
        return vm.last10ScanDays() == null ? List.of() : vm.last10ScanDays();
    }

    /** Grid state of column {@code i}; missing entries count as „kein Scan" (BR-26). */
    private static CapacityDayState stateAt(CapacityRow row, int i) {
        List<CapacityDayState> last10 = row.last10();
        if (last10 == null || i >= last10.size() || last10.get(i) == null) {
            return CapacityDayState.NOT_MEASURABLE;
        }
        return last10.get(i);
    }

    private static String reasonAt(CapacityRow row, int i) {
        List<String> reasons = row.last10Reasons();
        if (reasons == null || i >= reasons.size()) {
            return null;
        }
        return reasons.get(i);
    }

    private static int freeDays(CapacityRow row) {
        if (row.freeDays() != null) {
            return row.freeDays();
        }
        return row.last10() == null ? 0
            : (int) row.last10().stream().filter(s -> s == CapacityDayState.FREE).count();
    }

    private static int measurableDays(CapacityRow row) {
        if (row.measurableDays() != null) {
            return row.measurableDays();
        }
        return row.last10() == null ? 0
            : (int) row.last10().stream().filter(s -> s != null && s.measurable()).count();
    }

    /**
     * Label of a reason key — the §17 {@code REASON_*} text of {@link CapacityViewModelBuilder}; an
     * unknown key is shown as is (escaped by the caller). / Grundtext; unbekannte Schlüssel roh.
     */
    static String reason(String reasonKey) {
        if (reasonKey == null) {
            return "";
        }
        ProbeReason reason = ProbeReason.fromKey(reasonKey);
        return reason == null ? reasonKey : CapacityViewModelBuilder.reasonLabel(reason);
    }
}
