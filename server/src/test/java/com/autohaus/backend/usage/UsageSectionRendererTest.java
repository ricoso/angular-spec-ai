package com.autohaus.backend.usage;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rendering of the usage section (REQ-037 §11.2) / Rendering der Nutzungsstatistik-Sektion.
 *
 * <p>Covers TC-7 (empty history shows „—" instead of „0"), TC-14 (escaping), TC-19 (monthly
 * series in the year block), TC-20 (minimum case count for the device breakdown) and the CSS
 * class contract of §11.2, which the requirement declares binding („Tests assertieren gegen die
 * Namen dieser Tabelle"). / Deckt TC-7, TC-14, TC-19, TC-20 und den Klassenvertrag ab.
 *
 * <p>The renderer is a pure function over {@link UsageStats}, so the fixtures are built directly
 * as records — no Spring context, no mocks. / Reine Funktion, daher Record-Fixtures.
 */
class UsageSectionRendererTest {

    private final UsageSectionRenderer renderer = new UsageSectionRenderer();

    // -----------------------------------------------------------------------------------------
    // TC-7: empty history is not a pile of zeros / leere Historie ist nicht „0"
    // -----------------------------------------------------------------------------------------

    @Test
    void emptyHistoryRendersNoDataInsteadOfZeros() {
        String html = renderer.render(UsageStats.unavailable());

        assertTrue(html.contains("class=\"us-empty\""), "empty period must use .us-empty");
        assertTrue(html.contains("Keine Daten"), "empty period must say „Keine Daten\"");
        assertFalse(html.contains("0 Besuche"), "an unrecorded period must never be shown as „0 Besuche\"");
        assertFalse(html.contains("class=\"dev-fill"), "no device bars without data");
        assertFalse(html.contains("class=\"us-months\""), "no monthly table without a year value");
    }

    @Test
    void emptyHistoryNamesBothUnavailableSources() {
        String html = renderer.render(UsageStats.unavailable());

        assertTrue(html.contains("class=\"us-warn\""), "a missing source must be named, not hidden");
        assertTrue(html.contains("Live-Daten derzeit nicht verfügbar"));
        assertTrue(html.contains("Historie derzeit nicht verfügbar"));
    }

    /**
     * REQ-037 BR-15: right after going live the blob history is still empty, but a collection start
     * is configured. The page must then name that date — „Erfassung seit 22.09.2026" — instead of
     * claiming „Erfassung noch nicht begonnen", which would be wrong the moment the first visit is
     * counted. / Der konfigurierte Beginn erscheint auch ohne Historie.
     */
    @Test
    void namesTheConfiguredCollectionStartEvenWithoutAnyHistory() {
        LocalDate start = LocalDate.of(2026, 9, 22);
        PeriodUsage month = emptyPeriod("Aktueller Monat (September 2026)", start, start);
        PeriodUsage year = emptyPeriod("Jahresübersicht 2026", start, start);
        UsageStats usage = new UsageStats(month, year, List.of(), null, true, true, start, null);

        String html = renderer.render(usage);

        assertTrue(html.contains("Erfassung seit 22.09.2026"),
            "the configured start must be named even while the history is empty");
        assertFalse(html.contains("Erfassung noch nicht begonnen"),
            "collection has begun — only the persisted history is still empty");
        assertTrue(html.contains("class=\"us-empty\""),
            "no rows yet means „Keine Daten\", not „0\"");
    }

    @Test
    void switchedOffFeatureRendersNothing() {
        // AC-22: usage == null drops the section without any replacement / ersatzlos.
        assertEquals("", renderer.render(null));
    }

    // -----------------------------------------------------------------------------------------
    // TC-14: escaping / Maskierung
    // -----------------------------------------------------------------------------------------

    @Test
    void escapesEveryDynamicValue() {
        PeriodUsage month = period(
            "<script>alert(1)</script>",
            "01.09.2026 \"–\" 21.09.2026 & Europe/Berlin <b>",
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 21),
            new DeviceBreakdown(612, 44, 289, 9), 1180, 1141, 174, 168, 431, 143,
            45.2, 15.0, 33.2);
        UsageStats usage = new UsageStats(month, month, List.of(), Instant.parse("2026-09-21T09:45:03Z"),
            true, true, LocalDate.of(2026, 8, 27), LocalDate.of(2026, 9, 20));

        String html = renderer.render(usage);

        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), "label must be escaped");
        assertFalse(html.contains("<script>"), "no raw script tag may survive");
        assertTrue(html.contains("&lt;b&gt;"), "markup inside a range label must be escaped too");
        assertTrue(html.contains("&quot;"), "quotes in dynamic values must be escaped");
        assertTrue(html.contains("&amp;"), "ampersands in dynamic values must be escaped");
    }

    // -----------------------------------------------------------------------------------------
    // TC-19: monthly series inside the year block (AC-18)
    // -----------------------------------------------------------------------------------------

    @Test
    void yearBlockRendersTwelveMonthRowsWithGapsAndRunningMonth() {
        UsageStats usage = fullYear();
        assertEquals(12, usage.months().size(), "AC-18: always twelve month rows");

        String html = renderer.render(usage);

        assertTrue(html.contains("class=\"us-months\""), "the monthly table is part of the year block");
        assertTrue(html.contains("<div class=\"us-scroll\" role=\"region\" "
            + "aria-labelledby=\"us-months-h\" tabindex=\"0\">"), "AC-17: focusable region fallback");

        // January: before collection started -> „—" plus a real DOM reason, never „0".
        assertTrue(html.contains("<th scope=\"row\">Januar</th>"));
        assertTrue(html.contains("<td class=\"us-nd\">&mdash;"));
        assertTrue(html.contains("keine Daten, vor Erfassungsbeginn"));

        // September: running month, marked by a class AND a real DOM badge (never ::after).
        assertTrue(html.contains("<tr class=\"us-cur\">"), "the running month carries .us-cur");
        assertTrue(html.contains("<span class=\"us-badge us-badge-live\">laufend</span>"),
            "the badge is a real DOM element with text, not ::after content");

        // <tfoot> carries the year total.
        assertTrue(html.contains("<tfoot>"));
        assertTrue(html.contains("Jahressumme"));
        assertTrue(html.contains("4.195"), "year visits in the footer");
        assertTrue(html.contains("1.901"), "year search sessions in the footer");
        assertTrue(html.contains("14,9&nbsp;%"), "year booking rate in the footer");

        // No device columns in the table (§11.2) — the breakdown stays a bar list.
        assertFalse(html.contains("<th scope=\"col\">Mobil</th>"));
        assertFalse(html.contains("<th scope=\"col\">Tablet</th>"));
    }

    @Test
    void monthsBeforeAndAfterCollectionAreDashesNotZeros() {
        String html = renderer.render(fullYear());

        int january = html.indexOf("<th scope=\"row\">Januar</th>");
        int february = html.indexOf("<th scope=\"row\">Februar</th>");
        assertTrue(january > -1 && february > january);
        String januaryRow = html.substring(january, february);
        assertTrue(januaryRow.contains("us-nd"));
        assertFalse(januaryRow.contains(">0<"), "AC-12: a month without data must not show „0\"");

        assertTrue(html.contains("keine Daten, künftiger Monat"), "future months are named as such");
    }

    // -----------------------------------------------------------------------------------------
    // TC-20: minimum case count for the device breakdown (BR-12, AC-19)
    // -----------------------------------------------------------------------------------------

    @Test
    void belowMinimumCaseCountTheBarsAreReplacedByAHint() {
        String html = renderer.render(flatUsage(new DeviceBreakdown(10, 3, 5, 1)));

        assertEquals(19, new DeviceBreakdown(10, 3, 5, 1).total(), "fixture precondition");
        assertTrue(html.contains("class=\"us-thin\""), "below n=20 the hint replaces the bars");
        assertTrue(html.contains("Zu wenig Daten"));
        assertFalse(html.contains("class=\"dev-fill"), "no bars below the minimum case count");

        // Visits, funnel and rates stay visible (BR-12) / bleiben sichtbar.
        assertTrue(html.contains("class=\"cat-num\""));
        assertTrue(html.contains("class=\"us-rates\""));
        assertTrue(html.contains("class=\"us-rate-val\""));
    }

    @Test
    void atMinimumCaseCountTheFourBarsAppear() {
        String html = renderer.render(flatUsage(new DeviceBreakdown(10, 3, 6, 1)));

        assertEquals(UsageSectionRenderer.MIN_VISITS_FOR_BREAKDOWN,
            new DeviceBreakdown(10, 3, 6, 1).total(), "fixture precondition");
        assertFalse(html.contains("class=\"us-thin\""));
        assertTrue(html.contains("dev-fill dev-fill-mobile"));
        assertTrue(html.contains("dev-fill dev-fill-tablet"));
        assertTrue(html.contains("dev-fill dev-fill-desktop"));
        assertTrue(html.contains("dev-fill dev-fill-unknown"));
    }

    // -----------------------------------------------------------------------------------------
    // §11.2: the binding HTML/CSS contract / der verbindliche HTML/CSS-Vertrag
    // -----------------------------------------------------------------------------------------

    @Test
    void rendersEveryClassNameOfTheContractTable() {
        String html = renderer.render(fullYear());

        for (String expected : List.of(
            "<section class=\"us\" aria-labelledby=\"us-h\">",
            "<h2 id=\"us-h\">Nutzungsstatistik</h2>",
            "class=\"us-intro\"",
            "class=\"us-block\"",
            "class=\"us-hd\"",
            "class=\"us-title\"",
            "class=\"us-badge us-badge-live\"",
            "class=\"us-badge\"",
            "class=\"us-range\"",
            "class=\"cats us-tiles\"",
            "class=\"cat cat-info\"",
            "class=\"cat-hd\"",
            "class=\"dot\"",
            "class=\"cat-name\"",
            "class=\"cat-ep\"",
            "class=\"cat-num\"",
            "class=\"cat-sub\"",
            "class=\"us-rates\"",
            "class=\"us-rate\"",
            "class=\"us-rate-val\"",
            "class=\"us-rate-lbl\"",
            "class=\"us-rate-sub\"",
            "class=\"us-subtitle\"",
            "class=\"dev\"",
            "class=\"dev-row\"",
            "class=\"dev-lbl\"",
            "class=\"dev-bar\"",
            "class=\"dev-val\"",
            "dev-fill dev-fill-mobile",
            "dev-fill dev-fill-tablet",
            "dev-fill dev-fill-desktop",
            "dev-fill dev-fill-unknown",
            "class=\"us-scroll\"",
            "class=\"us-months\"",
            "class=\"us-cur\"",
            "class=\"us-nd\"",
            "class=\"opt\"",
            "class=\"sr\"")) {
            assertTrue(html.contains(expected), "§11.2 contract: missing " + expected);
        }
    }

    @Test
    void containsNoScriptAndNoExternalResource() {
        String html = renderer.render(fullYear());

        // AC-3 / AC-20: pure server HTML, the bar width is the only style attribute.
        assertFalse(html.contains("<script"), "the section must stay JavaScript-free");
        assertFalse(html.contains("<link "), "no external stylesheet");
        assertFalse(html.contains("<img"), "no images (img-src 'none')");
        assertTrue(html.contains("style=\"width:"), "bar width is a plain style attribute");
    }

    @Test
    void deviceBarsCarryTheShareAsTextAndAsAriaLabel() {
        String html = renderer.render(fullYear());

        // AC-4 / WCAG 1.4.1: the share is never conveyed by the bar alone.
        assertTrue(html.contains("role=\"img\""));
        assertTrue(html.contains("aria-label=\"Mobil: 612 Besuche, 64,2 Prozent\""));
        assertTrue(html.contains("<b>612</b> &middot; 64,2&nbsp;%"));
    }

    @Test
    void ratesWithoutDenominatorRenderAsDash() {
        PeriodUsage empty = period("Aktueller Monat (Januar 2026)", "01.01.2026 - 01.01.2026",
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1),
            new DeviceBreakdown(0, 0, 0, 0), 0, 0, 0, 0, 0, 0, null, null, null);
        UsageStats usage = new UsageStats(empty, empty, List.of(), null, true, true,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));

        String html = renderer.render(usage);

        // AC-13: divisor 0 → „—", never NaN or Infinity.
        assertTrue(html.contains("<div class=\"us-rate-val\">&mdash;</div>"));
        assertFalse(html.contains("NaN"));
        assertFalse(html.contains("Infinity"));
        assertTrue(html.contains("Kein Nenner: 0 Besuche"));
    }

    // -----------------------------------------------------------------------------------------
    // REQ-043 K-1/K-3: Anfragen → Terminsuchen → Buchungen, each split into ok · Fehler
    // -----------------------------------------------------------------------------------------

    @Test
    void everyTileSplitsItsBigNumberIntoOkAndFailures() {
        UsageStats base = fullYear();
        PeriodUsage month = withFailedSessions(base.month(), 25L, 7L, base.month().rangeFrom());
        UsageStats usage = new UsageStats(month, month, List.of(), base.liveAsOf(),
            true, true, base.collectingSince(), base.lastRollupDay());

        String html = renderer.render(usage);

        assertTrue(html.contains("<span class=\"cat-name\">Anfragen</span><span class=\"cat-ep\">/snaps</span>"
            + "</div><div class=\"cat-num\">1.180</div><div class=\"cat-sub\">1.141 ok &middot; 39 Fehler</div>"),
            "Anfragen are the search calls, split into ok and failed");
        assertTrue(html.contains("<div class=\"cat-num\">456</div><div class=\"cat-sub\">431 ok &middot; 25 Fehler</div>"),
            "Terminsuchen = 431 ok + 25 failed sessions");
        assertTrue(html.contains("<div class=\"cat-num\">150</div><div class=\"cat-sub\">143 ok &middot; 7 Fehler</div>"),
            "Buchungen = 143 ok + 7 failed sessions");
        assertFalse(html.contains(" Anfragen &middot;"), "no second „Anfragen\" inside a tile any more");
        assertFalse(html.contains("API-Aufrufe"), "the server load is no longer on the page");
        assertFalse(html.contains("Fehler erfasst ab"), "recorded from the first day, nothing partial");
        assertFalse(html.contains("<span class=\"cat-name\">Besuche</span>"), "no „Besuche\" tile");
        // The visits stay where they are a denominator: rates and device split.
        assertTrue(html.contains("431 von 954 Besuchen mit Terminsuche"));
    }

    @Test
    void aPartialFailureCountNamesItsFirstDay() {
        UsageStats base = fullYear();
        PeriodUsage year = withFailedSessions(base.year(), 25L, 7L, LocalDate.of(2026, 9, 15));
        UsageStats usage = new UsageStats(year, year, List.of(), base.liveAsOf(),
            true, true, base.collectingSince(), base.lastRollupDay());

        String html = renderer.render(usage);

        assertTrue(html.contains("1.901 ok &middot; 25 Fehler &middot; Fehler erfasst ab 15.09.2026"));
        assertTrue(html.contains("<div class=\"cat-num\">1.926</div>"), "1.901 ok + 25 failed");
    }

    @Test
    void unrecordedFailuresAreNamedNeverShownAsZero() {
        String html = renderer.render(fullYear());

        assertTrue(html.contains("<div class=\"cat-num\">431</div><div class=\"cat-sub\">431 ok &middot; "
            + "Fehler nicht erfasst</div>"), "without the counter only the ok sessions are known");
        assertFalse(html.contains("431 ok &middot; 0 Fehler"), "„nicht erfasst\" is not „0 Fehler\"");
    }

    @Test
    void monthlyTableCountsTheSearchCallsAsRequests() {
        String html = renderer.render(fullYear());

        assertTrue(html.contains("<th scope=\"col\">Anfragen</th>"));
        assertTrue(html.contains("<th scope=\"row\">August</th><td>340</td>"),
            "every recorded month carries its search calls — no gap before REQ-043");
        assertTrue(html.contains("<th scope=\"row\">Jahressumme</th><td>5.210</td>"), "year total");
    }

    // -----------------------------------------------------------------------------------------
    // Fixtures / Testdaten
    // -----------------------------------------------------------------------------------------

    /** Copy of {@code p} carrying the REQ-043 K-3 failed-session counters. */
    private static PeriodUsage withFailedSessions(PeriodUsage p, Long search, Long booking,
                                                  LocalDate from) {
        return new PeriodUsage(p.label(), p.rangeLabel(), p.rangeFrom(), p.rangeTo(), p.hasData(),
            p.visits(), p.visitCount(), p.searchRequests(), p.searchRequestsOk(), p.bookingRequests(),
            p.bookingRequestsOk(), p.sessionsWithSearch(), p.sessionsWithBooking(), p.searchRate(),
            p.bookingRate(), p.closeRate(), null, null, null, search, booking, from);
    }

    /** The §12.2 example snapshot: September 2026 inside the running year 2026. */
    private static UsageStats fullYear() {
        PeriodUsage month = period(
            "Aktueller Monat (September 2026)", "01.09.2026 - 21.09.2026 · Europe/Berlin",
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 21),
            new DeviceBreakdown(612, 44, 289, 9), 1180, 1141, 174, 168, 431, 143,
            45.2, 15.0, 33.2);
        PeriodUsage year = period(
            "Jahresübersicht 2026", "01.01.2026 - 21.09.2026 · Europe/Berlin",
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 21),
            new DeviceBreakdown(2743, 198, 1204, 50), 5210, 5041, 782, 755, 1901, 623,
            45.3, 14.9, 32.8);

        // January..July: before collection started. August/September: recorded.
        // October..December: still in the future. All of them „—", never „0" (AC-12).
        List<PeriodUsage> months = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            LocalDate from = LocalDate.of(2026, m, 1);
            LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
            if (m == 8) {
                months.add(period(MONTH_NAMES[m - 1], label(from, to), from, to,
                    new DeviceBreakdown(180, 12, 84, 3), 340, 330, 48, 46, 120, 40,
                    43.1, 14.4, 33.3));
            } else if (m == 9) {
                months.add(period(MONTH_NAMES[m - 1], label(from, LocalDate.of(2026, 9, 21)),
                    from, LocalDate.of(2026, 9, 21),
                    new DeviceBreakdown(612, 44, 289, 9), 1180, 1141, 174, 168, 431, 143,
                    45.2, 15.0, 33.2));
            } else {
                months.add(emptyPeriod(MONTH_NAMES[m - 1], from, to));
            }
        }

        return new UsageStats(month, year, List.copyOf(months),
            Instant.parse("2026-09-21T09:45:03Z"), true, true,
            LocalDate.of(2026, 8, 27), LocalDate.of(2026, 9, 20));
    }

    /**
     * Month and year carry the same small breakdown and no monthly table, so a single assertion
     * covers the whole document (TC-20). / Beide Blöcke mit derselben kleinen Fallzahl.
     */
    private static UsageStats flatUsage(DeviceBreakdown visits) {
        PeriodUsage month = period("Aktueller Monat (September 2026)",
            "01.09.2026 - 21.09.2026 · Europe/Berlin",
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 21),
            visits, 24, 23, 6, 5, 9, 4, 47.4, 21.1, 44.4);
        PeriodUsage year = period("Jahresübersicht 2026",
            "01.01.2026 - 21.09.2026 · Europe/Berlin",
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 21),
            visits, 24, 23, 6, 5, 9, 4, 47.4, 21.1, 44.4);
        return new UsageStats(month, year, List.of(), Instant.parse("2026-09-21T09:45:03Z"),
            true, true, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20));
    }

    private static final String[] MONTH_NAMES = {
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember"
    };

    private static String label(LocalDate from, LocalDate to) {
        return from + " - " + to + " · Europe/Berlin";
    }

    private static PeriodUsage emptyPeriod(String name, LocalDate from, LocalDate to) {
        return new PeriodUsage(name, label(from, to), from, to, false,
            new DeviceBreakdown(0, 0, 0, 0), 0, 0, 0, 0, 0, 0, 0, null, null, null);
    }

    private static PeriodUsage period(String name, String rangeLabel, LocalDate from, LocalDate to,
                                      DeviceBreakdown visits,
                                      long searchRequests, long searchRequestsOk,
                                      long bookingRequests, long bookingRequestsOk,
                                      long sessionsWithSearch, long sessionsWithBooking,
                                      Double searchRate, Double bookingRate, Double closeRate) {
        return new PeriodUsage(name, rangeLabel, from, to, true, visits, visits.total(),
            searchRequests, searchRequestsOk, bookingRequests, bookingRequestsOk,
            sessionsWithSearch, sessionsWithBooking, searchRate, bookingRate, closeRate);
    }
}
