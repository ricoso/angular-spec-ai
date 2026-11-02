package com.autohaus.backend.reporting;

import com.autohaus.backend.capacity.CapacityViewModel;
import com.autohaus.backend.capacity.ScanState;
import com.autohaus.backend.usage.UsageStats;
import com.autohaus.backend.usage.insights.DropoffView;
import com.autohaus.backend.usage.insights.FunnelView;
import com.autohaus.backend.usage.insights.InsightsViewModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S7 „Messgrenzen und Datenschutz" (REQ-038 §11.2, AC-29, BR-19) / Rendering von S7.
 *
 * <p>Covers the S7 part of TC-38: „Erfassung seit …" per <b>active</b> source (and only for those),
 * „—" instead of a missing date, the fixed display rules and the limits of the active sources. /
 * Erfassungsbeginn je aktiver Quelle, Regeln und Messgrenzen.
 */
class LimitsSectionRendererTest {

    private final LimitsSectionRenderer renderer = new LimitsSectionRenderer();

    private static final LocalDate USAGE_SINCE = LocalDate.of(2026, 8, 27);
    private static final LocalDate BOOKINGS_SINCE = LocalDate.of(2026, 9, 1);
    private static final LocalDate FUNNEL_SINCE = LocalDate.of(2026, 11, 1);
    private static final LocalDate DROPOFF_SINCE = LocalDate.of(2026, 11, 2);
    private static final LocalDate CAPACITY_SINCE = LocalDate.of(2026, 11, 3);

    @Test
    @DisplayName("robustness: no page -> nothing")
    void rendersNothingWithoutAPage() {
        assertThat(renderer.render(null)).isEmpty();
    }

    @Test
    @DisplayName("AC-29 / TC-38: all sources active -> five 'Erfassung seit' entries with their dates")
    void namesTheCollectionStartOfEveryActiveSource() {
        String html = renderer.render(new StatisticsPage(usage(USAGE_SINCE),
            insights(true, true, true), capacity(CAPACITY_SINCE), Briefing.empty()));

        assertThat(html)
            .startsWith("<section id=\"limits\" class=\"st-limits\" aria-labelledby=\"limits-h\">")
            .contains("<h2 id=\"limits-h\">Messgrenzen und Datenschutz</h2>")
            .contains("<h3 id=\"limits-since-h\">Erfassung seit</h3><dl aria-labelledby=\"limits-since-h\">")
            .contains("<dt>Nutzung (Besuche, Geräteklassen)</dt><dd>27.08.2026</dd>")
            .contains("<dt>Buchungsmerkmale und Leistungsaufrufe</dt><dd>01.09.2026</dd>")
            .contains("<dt>Buchungsstrecke</dt><dd>01.11.2026</dd>")
            .contains("<dt>Absprungszenarien</dt><dd>02.11.2026</dd>")
            .contains("<dt>Kapazitäts-Scan</dt><dd>03.11.2026</dd>")
            .endsWith("</section>");
        // Fixed order of the sources / feste Reihenfolge.
        assertThat(html.indexOf("Nutzung (Besuche")).isLessThan(html.indexOf("Buchungsmerkmale und Leistungsaufrufe"));
        assertThat(html.indexOf("<dt>Buchungsstrecke")).isLessThan(html.indexOf("<dt>Absprungszenarien"));
        assertThat(html.indexOf("<dt>Absprungszenarien")).isLessThan(html.indexOf("<dt>Kapazitäts-Scan"));
    }

    @Test
    @DisplayName("TC-38: the display rules and all limits of the active sources are listed")
    void listsTheRulesAndTheLimitsOfTheActiveSources() {
        String html = renderer.render(new StatisticsPage(usage(USAGE_SINCE),
            insights(true, true, true), capacity(CAPACITY_SINCE), null));

        assertThat(html)
            .contains("<h3>Darstellungsregeln</h3><ul>")
            .contains("erscheinen als „&lt; 5“; 0 bleibt 0.")
            .contains("Anteile in % erst ab 20 Fällen, sonst „zu wenig Daten (n &lt; 20)“.")
            .contains("„k. A.“: zusätzlich ausgeblendet")
            .contains("„—“: keine Daten (z. B. vor dem Erfassungsbeginn) – nie „0“.")
            .contains("Werte je Standort nur für abgeschlossene Kalendermonate")
            .contains("Nur Aggregate. Keine Cookies")
            .contains("<h3>Messgrenzen</h3><ul>")
            .contains("<li><b>Kapazität:</b> SoftNet liefert gerankte Vorschläge")
            .contains("<li><b>Nur Online:</b> Telefon- und Filialbuchungen")
            .contains("<li><b>Kleine Fallzahlen:</b>")
            .contains("<li>Leistungsaufrufe sind Seitenaufrufe, keine Besucher.</li>")
            .contains("<li><b>Servicegruppen:</b>")
            .contains("<li>Terminzeiten spiegeln Angebot und Nachfrage zugleich.</li>")
            .contains("<li>Buchungsmerkmale werden nicht im Moment der Buchung geschrieben, sondern im Speicher "
                + "gesammelt und einmal täglich (23:59:59 Uhr) gebündelt und gemischt protokolliert – so lässt "
                + "sich keine Statistikzeile einer einzelnen Buchung zuordnen. Bei einem Absturz der Anwendung "
                + "(nicht bei Neustart oder Deploy) fehlen die Buchungsmerkmale des laufenden Tages.</li>")
            .contains("<li><b>Buchungsstrecke:</b>")
            .contains("<li><b>Absprungszenarien:</b> Ein Reload beginnt eine neue Sitzung")
            .contains("<li><b>Historie:</b>")
            .contains("<li>Radwechsel ist nur in der Saison buchbar.</li>");
    }

    @Test
    @DisplayName("§10.2: the booking-batch limit follows the booking insights — after the slots, before the funnel")
    void namesTheDailyShuffledFlushOnlyWithTheBookingInsights() {
        String withBookings = renderer.render(new StatisticsPage(usage(USAGE_SINCE),
            insights(true, true, false), null, null));
        String withoutBookings = renderer.render(new StatisticsPage(usage(USAGE_SINCE),
            insights(false, true, false), capacity(CAPACITY_SINCE), null));

        String batch = "<li>" + LimitsSectionRenderer.LIMIT_BOOKING_BATCH + "</li>";
        assertThat(withBookings).contains(batch);
        assertThat(withBookings.indexOf("Terminzeiten spiegeln")).isLessThan(withBookings.indexOf(batch));
        assertThat(withBookings.indexOf(batch)).isLessThan(withBookings.indexOf("<b>Buchungsstrecke:</b>"));
        assertThat(withoutBookings).doesNotContain("Buchungsmerkmale werden nicht im Moment der Buchung");
        assertThat(renderer.render(new StatisticsPage(usage(USAGE_SINCE), null, null, null)))
            .doesNotContain("23:59:59");
        assertThat(LimitsSectionRenderer.LIMIT_BOOKING_BATCH).doesNotContain(": ")
            .as("no short label — rendered as plain text").contains("(23:59:59 Uhr)");
    }

    @Test
    @DisplayName("§5.1 row 1: usage only -> one source, only the history limit, no foreign limits")
    void showsOnlyTheUsageSourceForTheDefaultDeployment() {
        String html = renderer.render(new StatisticsPage(usage(USAGE_SINCE), null, null, null));

        assertThat(html).contains("<dt>Nutzung (Besuche, Geräteklassen)</dt><dd>27.08.2026</dd>")
            .doesNotContain("Buchungsmerkmale und Leistungsaufrufe</dt>")
            .doesNotContain("<dt>Buchungsstrecke")
            .doesNotContain("<dt>Absprungszenarien")
            .doesNotContain("<dt>Kapazitäts-Scan")
            .doesNotContain("<b>Kapazität:</b>")
            .doesNotContain("<b>Nur Online:</b>")
            .doesNotContain("<b>Buchungsstrecke:</b>")
            .doesNotContain("Radwechsel")
            .contains("<b>Historie:</b>");
        // The display rules are always shown / Darstellungsregeln immer.
        assertThat(html).contains("Darstellungsregeln");
    }

    @Test
    @DisplayName("AC-35: funnel active but drop-off off -> no drop-off source and limit")
    void omitsTheDropOffScenariosWhileSwitchedOff() {
        String html = renderer.render(new StatisticsPage(usage(USAGE_SINCE),
            insights(false, true, false), null, null));

        assertThat(html).contains("<dt>Buchungsstrecke</dt><dd>01.11.2026</dd>")
            .contains("<b>Buchungsstrecke:</b>")
            .doesNotContain("Absprungszenarien")
            .doesNotContain("Buchungsmerkmale und Leistungsaufrufe</dt>")
            .doesNotContain("<b>Nur Online:</b>");
    }

    @Test
    @DisplayName("BR-19 / AC-23: a missing collection start renders the dash, never 0")
    void rendersTheDashForAMissingDate() {
        InsightsViewModel noDates = new InsightsViewModel(LocalDate.of(2026, 10, 22), true, false,
            null, null, null, null, false, 0, List.of(), List.of(), null, null, null, null, null, List.of());

        String html = renderer.render(new StatisticsPage(usage(null), noDates, capacity(null), null));

        assertThat(html)
            .contains("<dt>Nutzung (Besuche, Geräteklassen)</dt><dd>&mdash;</dd>")
            .contains("<dt>Buchungsmerkmale und Leistungsaufrufe</dt><dd>&mdash;</dd>")
            .contains("<dt>Kapazitäts-Scan</dt><dd>&mdash;</dd>")
            .doesNotContain("<dd>0</dd>");
    }

    @Test
    @DisplayName("§10.1: a page without any source still renders S7 without the dl")
    void rendersTheRulesWithoutAnySource() {
        String html = renderer.render(new StatisticsPage(null, null, null, null));

        assertThat(html).contains("Erfassung seit").doesNotContain("<dl")
            .contains("Darstellungsregeln").contains("<b>Historie:</b>");
    }

    @Test
    @DisplayName("TC-38: no .us-* class and no forbidden element (BR-22)")
    void usesNoUsageClasses() {
        String html = renderer.render(new StatisticsPage(usage(USAGE_SINCE),
            insights(true, true, true), capacity(CAPACITY_SINCE), null));

        assertThat(html).doesNotContain("class=\"us").doesNotContain("<script").doesNotContain("<img");
    }

    // ---- fixtures / Testdaten ----

    private static UsageStats usage(LocalDate collectingSince) {
        UsageStats empty = UsageStats.unavailable();
        return new UsageStats(empty.month(), empty.year(), empty.months(), null, false, false,
            collectingSince, null);
    }

    private static InsightsViewModel insights(boolean bookings, boolean funnel, boolean dropoff) {
        FunnelView funnelView = funnel
            ? new FunnelView(null, List.of(), List.of(), List.of(), Map.of(),
                dropoff ? new DropoffView(DROPOFF_SINCE, List.of(), null, List.of(), List.of()) : null)
            : null;
        return new InsightsViewModel(LocalDate.of(2026, 11, 18), bookings, funnel, BOOKINGS_SINCE,
            funnel ? FUNNEL_SINCE : null, null, null, false, 0, bookings ? List.of() : null,
            bookings ? List.of() : null, null, null, null, null, funnelView, List.of());
    }

    private static CapacityViewModel capacity(LocalDate collectingSince) {
        return new CapacityViewModel(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 19),
            ScanState.COMPLETE, false, null, null, collectingSince, List.of(), List.of(), List.of());
    }
}
