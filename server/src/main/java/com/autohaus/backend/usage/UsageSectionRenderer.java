package com.autohaus.backend.usage;

import com.autohaus.backend.reporting.ReportHtmlGenerator;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * Renders the usage statistics section of the internal status page (REQ-037 §11.2, AC-2) /
 * rendert die Nutzungsstatistik-Sektion der internen Statusseite.
 *
 * <p>The output is a single {@code <section class="us" aria-labelledby="us-h">} that
 * {@link ReportHtmlGenerator#renderPage(UsageStats, String)} places directly after
 * {@code <header class="hd">} and before {@code <section class="ai">}. / Die Sektion ist das erste
 * inhaltliche Element der Seite.
 *
 * <p><b>Contract / Vertrag:</b> the CSS class names come verbatim from the table in §11.2
 * (kebab-case, no BEM — the style of the existing generator); the matching rules live in
 * {@link ReportHtmlGenerator} so that the page stays free of external resources. There is
 * <b>no JavaScript</b> (AC-3): the device bars are plain {@code style="width:66.3%"} attributes,
 * which the CSP of AC-20 ({@code style-src 'self' 'unsafe-inline'}) permits. / Kein JavaScript,
 * keine externen Ressourcen.
 *
 * <p><b>Labels are German literals</b> (§11, §18): the status page is internal and monolingual, so
 * no i18n keys are created. Identifiers, CSS classes and JSON keys stay English. / Sichtbare
 * Beschriftungen sind deutsche Literale.
 *
 * <p><b>Never „0" instead of „—"</b> (AC-12, AC-13): a period without any day row renders as
 * {@code .us-empty}, a conversion rate with divisor 0 as „—". Both are states, not zeros. /
 * Fehlende Daten erscheinen nie als Null.
 *
 * <p>Every value taken from the model passes through {@link ReportHtmlGenerator#esc(String)}
 * (TC-14); the numbers formatted here contain digits and German separators only. / Alle
 * dynamischen Werte werden maskiert.
 */
@Component
public class UsageSectionRenderer {

    /**
     * Minimum number of visits a period needs before its device breakdown is shown (BR-12, AC-19).
     * Below that the four bars are replaced by a {@code .us-thin} hint — small case numbers must
     * not become re-identifiable. / Mindestfallzahl für die Geräteverteilung.
     */
    public static final int MIN_VISITS_FOR_BREAKDOWN = 20;

    /** Age in days after which the rollup counts as stale and a {@code .us-warn} appears (§9). */
    private static final int ROLLUP_STALE_DAYS = 3;

    /** Display zone of every date on the page (BR-7) / Anzeigezone aller Datumsangaben. */
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    /** Day format of the status page / Tagesformat der Statusseite. */
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** Timestamp format, identical to the page header / Zeitstempelformat wie im Seitenkopf. */
    private static final DateTimeFormatter TS =
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'Uhr'").withZone(ZONE);

    /** The em dash used for „no data" / der Gedankenstrich für „keine Daten". */
    private static final String DASH = "&mdash;";

    /** Device rows in display order / Gerätezeilen in Anzeigereihenfolge. */
    private static final String[] DEVICE_LABELS = {"Mobil", "Tablet", "Desktop", "Unbekannt"};

    /** CSS modifier per device row, aligned with {@link #DEVICE_LABELS}. */
    private static final String[] DEVICE_MODIFIERS = {"mobile", "tablet", "desktop", "unknown"};

    /** Month names of the monthly series (AC-18) / Monatsnamen der Monatsreihe. */
    private static final String[] MONTH_NAMES = {
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember"
    };

    /**
     * Renders the whole section / rendert die gesamte Sektion.
     *
     * @param usage the snapshot from {@link UsageStatsStore}; {@code null} (feature switched off,
     *     AC-22) yields an empty string so the caller can drop the section without a replacement /
     *     {@code null} liefert einen Leerstring
     * @return the section markup, never {@code null} / das Sektions-Markup, nie {@code null}
     */
    public String render(UsageStats usage) {
        if (usage == null) {
            return "";
        }

        StringBuilder h = new StringBuilder(16384);
        h.append("<section class=\"us\" aria-labelledby=\"us-h\">");
        h.append("<h2 id=\"us-h\">Nutzungsstatistik</h2>");
        h.append("<p class=\"us-intro\">").append(intro()).append("</p>");
        h.append(monthBlock(usage));
        h.append(yearBlock(usage));
        h.append("</section>");
        return h.toString();
    }

    /**
     * Anonymity note plus the four limitations that belong on the page itself (§18): visits are app
     * starts, live numbers lag behind, tablets are slightly undercounted, missing periods show „—".
     * / Anonymitätshinweis und Erwartungsmanagement.
     */
    private static String intro() {
        return "Anonyme Reichweitenmessung aus den Server-Logs: die Geräteklasse wird aus dem "
            + "User-Agent abgeleitet (<code class=\"mono\">mobile</code> | "
            + "<code class=\"mono\">tablet</code> | <code class=\"mono\">desktop</code> | "
            + "<code class=\"mono\">unknown</code>) &mdash; der rohe User-Agent wird weder geloggt "
            + "noch gespeichert; keine Cookies, keine IP-Adressen, kein Fingerprinting, kein "
            + "Drill-down. &bdquo;Besuche&ldquo; sind App-Starts (Sitzungen), keine eindeutigen "
            + "Personen: ein Reload zählt erneut, ein Besuch über Mitternacht zählt an beiden Tagen. "
            + "Der laufende Tag ist wegen der Ingestion-Latenz von 5&ndash;15 Minuten unvollständig; "
            + "Tablets werden durch den iPadOS-Desktop-Modus leicht unterzählt. Zeiträume ohne "
            + "Erfassung zeigen &bdquo;&mdash;&ldquo;, niemals &bdquo;0&ldquo;.";
    }

    // -----------------------------------------------------------------------------------------
    // Blocks / Blöcke
    // -----------------------------------------------------------------------------------------

    /** Current month, badge „laufend" (AC-3) / laufender Monat mit Badge. */
    private static String monthBlock(UsageStats usage) {
        PeriodUsage p = usage.month();
        if (p == null) {
            return "";
        }

        StringBuilder h = new StringBuilder(4096);
        h.append("<div class=\"us-block\">");
        h.append("<div class=\"us-hd\"><h3 class=\"us-title\">").append(esc(p.label()))
            .append("</h3><span class=\"us-badge us-badge-live\">laufend</span></div>");

        h.append("<p class=\"us-range\">").append(esc(p.rangeLabel()));
        if (usage.liveAvailable()) {
            h.append(" &middot; live aus Log Analytics, Ingestion-Latenz 5&ndash;15&nbsp;Min.");
        }
        h.append("</p>");

        h.append(warnings(usage));
        h.append(periodBody(usage, p, "us-m"));
        h.append("</div>");
        return h.toString();
    }

    /** Running year, badge „Rollup", plus the monthly series (AC-18) / Jahresblock mit Monatsreihe. */
    private static String yearBlock(UsageStats usage) {
        PeriodUsage p = usage.year();
        if (p == null) {
            return "";
        }

        StringBuilder h = new StringBuilder(8192);
        h.append("<div class=\"us-block\">");
        h.append("<div class=\"us-hd\"><h3 class=\"us-title\">").append(esc(p.label()))
            .append("</h3><span class=\"us-badge\">Rollup</span></div>");

        h.append("<p class=\"us-range\">").append(esc(p.rangeLabel()));
        if (usage.collectingSince() != null) {
            h.append(" &middot; Erfassung seit ").append(esc(DAY.format(usage.collectingSince())));
        }
        if (usage.lastRollupDay() != null) {
            h.append(" &middot; letzter Rollup: ").append(esc(DAY.format(usage.lastRollupDay())));
        }
        h.append("</p>");

        h.append(warnings(usage));
        h.append(periodBody(usage, p, "us-y"));
        h.append(monthlyTable(usage));
        h.append("</div>");
        return h.toString();
    }

    /**
     * Tiles, rates and device breakdown of one period — or the empty state (AC-12) / Kacheln,
     * Quoten und Geräteverteilung eines Zeitraums, sonst der Leerzustand.
     *
     * <p>{@code hasData == false} means „no day row in this range" and is deliberately not the same
     * as „all counters are zero": a month before the feature went live must never show „0".
     */
    private static String periodBody(UsageStats usage, PeriodUsage p, String idPrefix) {
        if (!p.hasData()) {
            return emptyState(usage);
        }
        return tiles(p) + rates(p) + devices(p, idPrefix);
    }

    /** „Keine Daten für diesen Zeitraum (Erfassung seit …)" instead of zeros (AC-12). */
    private static String emptyState(UsageStats usage) {
        StringBuilder h = new StringBuilder(256);
        h.append("<p class=\"us-empty\">Keine Daten für diesen Zeitraum (");
        if (usage.collectingSince() != null) {
            h.append("Erfassung seit ").append(esc(DAY.format(usage.collectingSince())));
        } else {
            h.append("Erfassung noch nicht begonnen");
        }
        h.append(").</p>");
        return h.toString();
    }

    // -----------------------------------------------------------------------------------------
    // Warnings / Störungshinweise (§9 Observability, AC-11)
    // -----------------------------------------------------------------------------------------

    /**
     * Renders every applicable {@code .us-warn} band / rendert alle zutreffenden Hinweisbänder.
     *
     * <p>Both blocks carry the same bands because both sums are fed from both sources (TC-9:
     * „Monat und Jahr zeigen … plus den Hinweis"). A missing source is named in plain text with a
     * left border — never by colour alone (WCAG 1.4.1). / Eine fehlende Quelle wird benannt, nicht
     * durch Nullen kaschiert.
     */
    private static String warnings(UsageStats usage) {
        StringBuilder h = new StringBuilder(512);
        if (!usage.liveAvailable()) {
            String asOf = usage.liveAsOf() != null
                ? TS.format(usage.liveAsOf())
                : "noch kein erfolgreicher Abruf";
            h.append("<p class=\"us-warn\">Live-Daten derzeit nicht verfügbar &mdash; Stand: ")
                .append(esc(asOf))
                .append(". Azure Log Analytics antwortet nicht; angezeigt werden die persistierten "
                    + "Tageswerte, der laufende Tag fehlt.</p>");
        }
        if (!usage.historyAvailable()) {
            h.append("<p class=\"us-warn\">Historie derzeit nicht verfügbar &mdash; Azure Blob "
                + "Storage nicht erreichbar. Sobald die Datei wieder gelesen werden kann, "
                + "erscheinen die Zahlen ohne Datenverlust.</p>");
        }
        if (rollupStale(usage)) {
            h.append("<p class=\"us-warn\">Letzter Rollup: ")
                .append(esc(DAY.format(usage.lastRollupDay())))
                .append(" &mdash; Historie möglicherweise unvollständig.</p>");
        }
        return h.toString();
    }

    /**
     * True when the newest persisted day is more than {@link #ROLLUP_STALE_DAYS} days old. The
     * reference day is the end of the year range (that is „today", BR-7), so the renderer needs no
     * clock of its own. / Referenztag ist das Ende des Jahreszeitraums.
     */
    private static boolean rollupStale(UsageStats usage) {
        if (usage.lastRollupDay() == null) {
            return false;
        }
        LocalDate reference = usage.year() != null && usage.year().rangeTo() != null
            ? usage.year().rangeTo()
            : LocalDate.now(ZONE);
        return ChronoUnit.DAYS.between(usage.lastRollupDay(), reference) > ROLLUP_STALE_DAYS;
    }

    // -----------------------------------------------------------------------------------------
    // Funnel tiles / Funnel-Kacheln (AC-5)
    // -----------------------------------------------------------------------------------------

    /**
     * Three tiles: visits → searches → bookings, each with its absolute number. The request counts
     * appear only as a subtitle — a conversion rate must never be derived from them (AC-5). / Drei
     * Kacheln mit Absolutzahlen; Request-Zahlen nur als Untertitel.
     */
    private static String tiles(PeriodUsage p) {
        StringBuilder h = new StringBuilder(2048);
        h.append("<div class=\"cats us-tiles\">");
        h.append(tile("Besuche", "App-Starts", true, p.visitCount(),
            "Sitzungen &middot; ein Reload zählt erneut &middot; keine eindeutigen Personen"));
        h.append(tile("Terminsuchen", "/snaps", false, p.sessionsWithSearch(),
            requestSubtitle(p.searchRequests(), p.searchRequestsOk())));
        h.append(tile("Buchungen", "/appointments", false, p.sessionsWithBooking(),
            requestSubtitle(p.bookingRequests(), p.bookingRequestsOk())));
        h.append("</div>");
        return h.toString();
    }

    /**
     * One neutral info tile / eine neutrale Info-Kachel.
     *
     * @param plainSlot {@code true} renders the endpoint slot as {@code .cat-ep-plain} (plain text
     *     instead of monospace), which the „Besuche" tile needs for „App-Starts"
     */
    private static String tile(String name, String slot, boolean plainSlot, long value, String sub) {
        return "<div class=\"cat cat-info\"><div class=\"cat-hd\"><span class=\"dot\"></span>"
            + "<span class=\"cat-name\">" + esc(name) + "</span>"
            + "<span class=\"" + (plainSlot ? "cat-ep-plain" : "cat-ep") + "\">" + esc(slot)
            + "</span></div><div class=\"cat-num\">" + esc(num(value)) + "</div>"
            + "<div class=\"cat-sub\">" + sub + "</div></div>";
    }

    /** „1.180 Anfragen · 1.141 ok · 39 Fehler" — failures stay visible (BR-5). */
    private static String requestSubtitle(long requests, long requestsOk) {
        long failed = Math.max(0L, requests - requestsOk);
        return esc(num(requests)) + " Anfragen &middot; " + esc(num(requestsOk)) + " ok &middot; "
            + esc(num(failed)) + " Fehler";
    }

    // -----------------------------------------------------------------------------------------
    // Conversion rates / Quoten (BR-6, AC-5, AC-13)
    // -----------------------------------------------------------------------------------------

    /**
     * The three session-based rates. Each subtitle names numerator and denominator in plain text,
     * so a „—" is readable as „no denominator" rather than as a defect. / Drei sitzungsbasierte
     * Quoten mit Zähler und Nenner im Klartext.
     */
    private static String rates(PeriodUsage p) {
        StringBuilder h = new StringBuilder(1024);
        h.append("<div class=\"us-rates\">");
        h.append(rate(p.searchRate(), "Suchquote",
            p.visitCount() > 0
                ? esc(num(p.sessionsWithSearch())) + " von " + esc(num(p.visitCount()))
                    + " Besuchen mit Terminsuche"
                : "Kein Nenner: 0 Besuche"));
        h.append(rate(p.bookingRate(), "Buchungsquote",
            p.visitCount() > 0
                ? "Gesamt-Conversion &middot; " + esc(num(p.sessionsWithBooking())) + " von "
                    + esc(num(p.visitCount())) + " Besuchen mit Buchung"
                : "Gesamt-Conversion &middot; kein Nenner: 0 Besuche"));
        h.append(rate(p.closeRate(), "Abschlussquote",
            p.sessionsWithSearch() > 0
                ? esc(num(p.sessionsWithBooking())) + " von " + esc(num(p.sessionsWithSearch()))
                    + " Terminsuch-Sitzungen mit Buchung"
                : "Kein Nenner: 0 Terminsuch-Sitzungen"));
        h.append("</div>");
        return h.toString();
    }

    /** One rate tile; {@code null} renders „—" (divisor 0, AC-13). */
    private static String rate(Double value, String label, String sub) {
        return "<div class=\"us-rate\"><div class=\"us-rate-val\">" + percent(value)
            + "</div><div class=\"us-rate-lbl\">" + esc(label) + "</div>"
            + "<div class=\"us-rate-sub\">" + sub + "</div></div>";
    }

    // -----------------------------------------------------------------------------------------
    // Device breakdown / Geräteverteilung (AC-4, AC-19)
    // -----------------------------------------------------------------------------------------

    /**
     * Four bars with absolute number and share, or the {@code .us-thin} hint below the minimum case
     * count (BR-12). Visits, funnel and rates stay visible either way. / Vier Balken oder der
     * Mindestfallzahl-Hinweis.
     */
    private static String devices(PeriodUsage p, String idPrefix) {
        String headingId = idPrefix + "-dev";
        StringBuilder h = new StringBuilder(2048);
        h.append("<h4 class=\"us-subtitle\" id=\"").append(headingId).append("\">Geräteverteilung</h4>");

        if (p.visitCount() < MIN_VISITS_FOR_BREAKDOWN) {
            h.append("<p class=\"us-thin\" aria-labelledby=\"").append(headingId)
                .append("\">Zu wenig Daten für eine Geräteverteilung (n &lt; ")
                .append(MIN_VISITS_FOR_BREAKDOWN).append(").</p>");
            return h.toString();
        }

        DeviceBreakdown visits = p.visits() != null ? p.visits() : new DeviceBreakdown(0L, 0L, 0L, 0L);
        DeviceBreakdown.DevicePercentages shares = visits.percentages();
        long[] counts = {visits.mobile(), visits.tablet(), visits.desktop(), visits.unknown()};
        Double[] percentages = {shares.mobile(), shares.tablet(), shares.desktop(), shares.unknown()};

        h.append("<p class=\"us-range\">Eine Geräteklasse je Sitzung &middot; Basis: ")
            .append(esc(num(visits.total()))).append(" Besuche</p>");
        h.append("<ul class=\"dev\" aria-labelledby=\"").append(headingId).append("\">");
        for (int i = 0; i < DEVICE_LABELS.length; i++) {
            h.append(deviceRow(DEVICE_LABELS[i], DEVICE_MODIFIERS[i], counts[i], percentages[i]));
        }
        h.append("</ul>");
        return h.toString();
    }

    /**
     * One device row / eine Gerätezeile.
     *
     * <p>The bar width is a pure style attribute with a dot as decimal separator (CSS syntax),
     * while the visible text uses the German comma; both describe the same value, so the share is
     * never conveyed by the bar alone (AC-4, WCAG 1.4.1). {@code role="img"} plus {@code aria-label}
     * give screen readers the same three facts. / Balkenbreite als reines Style-Attribut.
     */
    private static String deviceRow(String label, String modifier, long count, Double share) {
        String ariaLabel = label + ": " + num(count) + " Besuche, "
            + (share == null ? "kein Anteil" : decimal(share) + " Prozent");
        return "<li class=\"dev-row\"><span class=\"dev-lbl\">" + esc(label) + "</span>"
            + "<span class=\"dev-bar\" role=\"img\" aria-label=\"" + esc(ariaLabel) + "\">"
            + "<span class=\"dev-fill dev-fill-" + modifier + "\" style=\"width:"
            + cssWidth(share) + "%\"></span></span>"
            + "<span class=\"dev-val\"><b>" + esc(num(count)) + "</b> &middot; " + percent(share)
            + "</span></li>";
    }

    // -----------------------------------------------------------------------------------------
    // Monthly series / Monatsverlauf (AC-18, BR-14)
    // -----------------------------------------------------------------------------------------

    /**
     * Twelve rows of the running year, summed from the very same day rows that feed month and year
     * (BR-14) — no additional data source and, on purpose, <b>no device columns</b> (§11.2). The
     * „Terminsuchen (ok)" column carries {@code .opt} on {@code <th>} and {@code <td>} and drops out
     * at ≤ 40em, which leaves four columns and no horizontal page scrolling (AC-17). / Monatsreihe
     * ohne Geräte-Spalten.
     */
    private static String monthlyTable(UsageStats usage) {
        List<PeriodUsage> months = usage.months();
        PeriodUsage year = usage.year();
        if (months == null || months.isEmpty() || year == null || !year.hasData()) {
            return "";
        }

        String yearLabel = year.rangeFrom() != null ? String.valueOf(year.rangeFrom().getYear()) : "";
        LocalDate currentMonthStart = currentMonthStart(usage);

        StringBuilder h = new StringBuilder(8192);
        h.append("<h4 class=\"us-subtitle\" id=\"us-months-h\">Monatsverlauf");
        if (!yearLabel.isEmpty()) {
            h.append(' ').append(esc(yearLabel));
        }
        h.append("</h4>");
        h.append("<div class=\"us-scroll\" role=\"region\" aria-labelledby=\"us-months-h\" tabindex=\"0\">");
        h.append("<table class=\"us-months\">");
        h.append("<caption class=\"sr\">Monatsverlauf");
        if (!yearLabel.isEmpty()) {
            h.append(' ').append(esc(yearLabel));
        }
        h.append(" &mdash; Besuche, Terminsuchen, Buchungen und Buchungsquote je Kalendermonat; "
            + "Monate ohne Erfassung zeigen einen Gedankenstrich.</caption>");
        h.append("<thead><tr><th scope=\"col\">Monat</th><th scope=\"col\">Besuche</th>"
            + "<th scope=\"col\" class=\"opt\">Terminsuchen (ok)</th>"
            + "<th scope=\"col\">Buchungen (ok)</th><th scope=\"col\">Buchungsquote</th></tr></thead>");

        h.append("<tbody>");
        for (int i = 0; i < months.size(); i++) {
            h.append(monthRow(months.get(i), i, currentMonthStart));
        }
        h.append("</tbody>");

        h.append("<tfoot><tr><th scope=\"row\">Jahressumme</th><td>")
            .append(esc(num(year.visitCount()))).append("</td><td class=\"opt\">")
            .append(esc(num(year.sessionsWithSearch()))).append("</td><td>")
            .append(esc(num(year.sessionsWithBooking()))).append("</td><td>")
            .append(percent(year.bookingRate())).append("</td></tr></tfoot>");

        h.append("</table></div>");
        h.append("<p class=\"us-intro\">Die Jahreszahlen sind die Summe der nächtlich persistierten "
            + "Tageswerte (Azure Blob Storage, ausschließlich ganzzahlige Zähler) plus der laufende "
            + "Tag aus Log Analytics. Die Historie wächst erst ab Inbetriebnahme: Monate vor dem "
            + "Erfassungsbeginn und künftige Monate stehen auf &bdquo;&mdash;&ldquo;, nicht auf "
            + "&bdquo;0&ldquo;.</p>");
        return h.toString();
    }

    /** One table row; the running month is marked by {@code .us-cur} and a real DOM badge (AC-18). */
    private static String monthRow(PeriodUsage m, int index, LocalDate currentMonthStart) {
        boolean current = isCurrentMonth(m, index, currentMonthStart);
        String name = m.label() != null && !m.label().isBlank()
            ? m.label()
            : MONTH_NAMES[Math.min(index, MONTH_NAMES.length - 1)];

        StringBuilder h = new StringBuilder(512);
        h.append(current ? "<tr class=\"us-cur\">" : "<tr>");
        h.append("<th scope=\"row\">").append(esc(name));
        if (current) {
            h.append(" <span class=\"us-badge us-badge-live\">laufend</span>");
        }
        h.append("</th>");

        if (m.hasData()) {
            h.append("<td>").append(esc(num(m.visitCount()))).append("</td>");
            h.append("<td class=\"opt\">").append(esc(num(m.sessionsWithSearch()))).append("</td>");
            h.append("<td>").append(esc(num(m.sessionsWithBooking()))).append("</td>");
            h.append(m.bookingRate() != null
                ? "<td>" + percent(m.bookingRate()) + "</td>"
                : "<td class=\"us-nd\">" + DASH + "</td>");
        } else {
            String reason = isFuture(m, index, currentMonthStart)
                ? " keine Daten, künftiger Monat"
                : " keine Daten, vor Erfassungsbeginn";
            h.append("<td class=\"us-nd\">").append(DASH).append("<span class=\"sr\">")
                .append(esc(reason)).append("</span></td>");
            h.append("<td class=\"us-nd opt\">").append(DASH).append("</td>");
            h.append("<td class=\"us-nd\">").append(DASH).append("</td>");
            h.append("<td class=\"us-nd\">").append(DASH).append("</td>");
        }
        h.append("</tr>");
        return h.toString();
    }

    /** First day of the month currently running, taken from the month period (BR-7). */
    private static LocalDate currentMonthStart(UsageStats usage) {
        if (usage.month() != null && usage.month().rangeFrom() != null) {
            return usage.month().rangeFrom().withDayOfMonth(1);
        }
        return LocalDate.now(ZONE).withDayOfMonth(1);
    }

    private static boolean isCurrentMonth(PeriodUsage m, int index, LocalDate currentMonthStart) {
        if (m.rangeFrom() != null) {
            return m.rangeFrom().getYear() == currentMonthStart.getYear()
                && m.rangeFrom().getMonthValue() == currentMonthStart.getMonthValue();
        }
        return index + 1 == currentMonthStart.getMonthValue();
    }

    private static boolean isFuture(PeriodUsage m, int index, LocalDate currentMonthStart) {
        if (m.rangeFrom() != null) {
            return m.rangeFrom().withDayOfMonth(1).isAfter(currentMonthStart);
        }
        return index + 1 > currentMonthStart.getMonthValue();
    }

    // -----------------------------------------------------------------------------------------
    // Formatting / Formatierung
    // -----------------------------------------------------------------------------------------

    /** German thousands separators, e.g. {@code 1.122} / deutsche Tausendertrennung. */
    private static String num(long value) {
        return String.format(Locale.GERMANY, "%,d", value);
    }

    /** One decimal with a German comma, e.g. {@code 66,3} / eine Nachkommastelle mit Komma. */
    private static String decimal(double value) {
        return String.format(Locale.GERMANY, "%.1f", value);
    }

    /** Visible percentage, {@code null} renders „—" (AC-13) / sichtbarer Prozentwert. */
    private static String percent(Double value) {
        return value == null ? DASH : decimal(value) + "&nbsp;%";
    }

    /**
     * Bar width for the style attribute — CSS needs a dot as decimal separator, so this value is
     * formatted with {@link Locale#ROOT} and never with the display locale. / Balkenbreite mit
     * Punkt als Dezimaltrennzeichen.
     */
    private static String cssWidth(Double value) {
        return value == null ? "0.0" : String.format(Locale.ROOT, "%.1f", value);
    }

    /**
     * HTML escaping, shared with the page generator so both use exactly one implementation (TC-14).
     * / HTML-Maskierung, gemeinsam mit dem Seitengenerator.
     */
    private static String esc(String value) {
        return ReportHtmlGenerator.esc(value);
    }
}
