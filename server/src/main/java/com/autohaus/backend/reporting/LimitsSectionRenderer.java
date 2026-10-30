package com.autohaus.backend.reporting;

import com.autohaus.backend.capacity.CapacityViewModel;
import com.autohaus.backend.usage.StatFormat;
import com.autohaus.backend.usage.UsageStats;
import com.autohaus.backend.usage.insights.InsightsViewModel;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.autohaus.backend.reporting.ReportHtmlGenerator.esc;

/**
 * Renders S7 „Messgrenzen und Datenschutz" (REQ-038 §11.2, AC-29) / rendert S7 — always rendered
 * at the end of the statistics tab, names „Erfassung seit …" per active source.
 *
 * <p>Markup (§11.2): {@code section#limits.st-limits} with three {@code h3} blocks — „Erfassung
 * seit" ({@code dl}, one entry per <b>active</b> source: usage from
 * {@code UsageStats.collectingSince}, booking attributes, booking flow, drop-off scenarios,
 * capacity scan), „Darstellungsregeln" ({@code ul}, the {@code RULE_*} texts and the privacy note)
 * and „Messgrenzen" ({@code ul}, the {@code LIMIT_*} texts of the active sources — with the
 * booking attributes also {@code LIMIT_BOOKING_BATCH}, the daily shuffled flush). A missing date
 * renders „—", never 0 (BR-19). / Je aktiver Quelle; fehlendes Datum als „—".
 *
 * <p>„Active" is read from the {@link StatisticsPage} fields alone (§10.1): usage
 * {@code usage != null}; booking attributes {@code insights.bookingsEnabled()}; booking flow
 * {@code insights.funnelEnabled()}; drop-off scenarios {@code insights.funnel().dropoff() != null}
 * (N1, AC-35); capacity {@code capacity != null}. / Aktiv allein nach den Feldern.
 *
 * <p>Stateless renderer, view model in, HTML string out; every dynamic value passes through
 * {@link ReportHtmlGenerator#esc(String)}. / Zustandsloser Renderer.
 */
@Component
public class LimitsSectionRenderer {

    // ---- visible labels, German literals (§17) / sichtbare Beschriftungen ----

    /** §17 {@code LIMITS_HEADING}. */
    static final String LIMITS_HEADING = "Messgrenzen und Datenschutz";
    /** §17 {@code LIMITS_SUB_*} — collection start / Erfassung seit. */
    static final String LIMITS_SUB_SINCE = "Erfassung seit";
    /** §17 {@code LIMITS_SUB_*} — display rules / Darstellungsregeln. */
    static final String LIMITS_SUB_RULES = "Darstellungsregeln";
    /** §17 {@code LIMITS_SUB_*} — measurement limits / Messgrenzen. */
    static final String LIMITS_SUB_LIMITS = "Messgrenzen";

    /** §17 {@code SOURCE_*} — REQ-037 usage. */
    static final String SOURCE_USAGE = "Nutzung (Besuche, Geräteklassen)";
    /** §17 {@code SOURCE_*} — K2 booking attributes. */
    static final String SOURCE_BOOKINGS = "Buchungsmerkmale und Leistungsaufrufe";
    /** §17 {@code SOURCE_*} — K3 funnel. */
    static final String SOURCE_FUNNEL = "Buchungsstrecke";
    /** §17 {@code SOURCE_*} — N1 drop-off scenarios. */
    static final String SOURCE_DROPOFF = "Absprungszenarien";
    /** §17 {@code SOURCE_*} — K1 capacity scan. */
    static final String SOURCE_CAPACITY = "Kapazitäts-Scan";

    /** §17 {@code RULE_SUPPRESSED}. */
    static final String RULE_SUPPRESSED = "Buchungsmerkmale und Buchungsstrecke: Werte unter 5 "
        + "erscheinen als „< 5“; 0 bleibt 0. Kapazitätswerte (Betriebsdaten) und die "
        + "Nutzungsstatistik aus REQ-037 sind davon ausgenommen.";
    /** §17 {@code RULE_SHARE}. */
    static final String RULE_SHARE = "Anteile in % erst ab 20 Fällen, sonst „zu wenig Daten (n < 20)“.";
    /** §17 {@code RULE_WITHHELD}. */
    static final String RULE_WITHHELD = "„k. A.“: zusätzlich ausgeblendet, damit sich ein "
        + "ausgeblendeter Wert nicht aus einer Summe oder einem Anteil zurückrechnen lässt.";
    /** §17 {@code RULE_NO_DATA}. */
    static final String RULE_NO_DATA = "„—“: keine Daten (z. B. vor dem Erfassungsbeginn) – nie „0“.";
    /** §17 {@code RULE_LOCATION}. */
    static final String RULE_LOCATION = "Werte je Standort nur für abgeschlossene Kalendermonate; "
        + "keine Werte je Standort und Tag, Stunde oder Gerät.";
    /** §17 {@code PRIVACY_NOTE}. */
    static final String PRIVACY_NOTE = "Nur Aggregate. Keine Cookies, kein Browserspeicher, keine "
        + "IP-Adressen, kein roher User-Agent; keine Auswertung nach Marke, Modell, Kunde, "
        + "Serviceberater oder Wiederkehr; Sitzung und Gerät werden nie mit einem Standort verknüpft.";

    /** §17 {@code LIMIT_CAPACITY}. */
    static final String LIMIT_CAPACITY = "Kapazität: SoftNet liefert gerankte Vorschläge, keine "
        + "Gesamtkapazität; im Datumsmodus waren es nur ca. 5–7 Vorschläge pro Tag. Ist ein Vorlauf "
        + "konfiguriert, heißt „morgen nicht frei“ unter Umständen „online nicht buchbar“, nicht "
        + "„voll“. Gemessen wird nur die Referenz-Leistung; Feiertage zählen als Werktage.";
    /** §17 {@code LIMIT_ONLINE_ONLY}. */
    static final String LIMIT_ONLINE_ONLY = "Nur Online: Telefon- und Filialbuchungen sind "
        + "unsichtbar. Ein „Penner“ kann offline stark sein.";
    /** §17 {@code LIMIT_SMALL_NUMBERS}. */
    static final String LIMIT_SMALL_NUMBERS = "Kleine Fallzahlen: Bei rund 10 Online-Buchungen pro "
        + "Tag sind das rund 11 je Standort und Monat. Viele Zellen zeigen daher „< 5“ oder „zu "
        + "wenig Daten“.";
    /** §17 {@code LIMIT_SERVICE_VIEWS}. */
    static final String LIMIT_SERVICE_VIEWS = "Leistungsaufrufe sind Seitenaufrufe, keine Besucher.";
    /** §17 {@code LIMIT_GROUPS}. */
    static final String LIMIT_GROUPS = "Servicegruppen: Standorte ohne SoftNet-Tags (heute die beiden "
        + "Leverkusener Standorte) erscheinen als „nicht zuordenbar“.";
    /** §17 {@code LIMIT_SLOTS}. */
    static final String LIMIT_SLOTS = "Terminzeiten spiegeln Angebot und Nachfrage zugleich.";
    /**
     * §17 {@code LIMIT_BOOKING_BATCH} — the daily, shuffled flush of the booking attributes
     * (§10.2) / Tagesflush der Buchungsmerkmale.
     */
    static final String LIMIT_BOOKING_BATCH = "Buchungsmerkmale werden nicht im Moment der Buchung "
        + "geschrieben, sondern im Speicher gesammelt und einmal täglich (23:59:59 Uhr) gebündelt und "
        + "gemischt protokolliert – so lässt sich keine Statistikzeile einer einzelnen Buchung zuordnen. "
        + "Bei einem Absturz der Anwendung (nicht bei Neustart oder Deploy) fehlen die Buchungsmerkmale "
        + "des laufenden Tages.";
    /** §17 {@code LIMIT_FUNNEL}. */
    static final String LIMIT_FUNNEL = "Buchungsstrecke: Marke, Fahrzeugdaten und Übersicht sind "
        + "nicht sichtbar; Terminwahl und Werkstattkalender sind nicht unterscheidbar; ein Reload "
        + "zählt als Abbruch plus neuer Besuch; eine Sitzung über Mitternacht zählt an beiden Tagen.";
    /** §17 {@code LIMIT_DROPOFF} (N1). */
    static final String LIMIT_DROPOFF = "Absprungszenarien: Ein Reload beginnt eine neue Sitzung – "
        + "die alte zählt als Abbruch, meist „ohne erkennbaren Anlass“. Ein Netzabbruch am "
        + "Sitzungsende bleibt unsichtbar; erkannt werden nur Lücken mitten in der Sitzung. Ein "
        + "Neustart oder Deploy leert den Termin-Zwischenspeicher und erzeugt „Termin abgelaufen“. "
        + "Die Trefferzahl nennt, was der Server geliefert hat – die Anzeige wählt höchstens 4 aus. "
        + "Eine Sitzung über Mitternacht zählt am ersten Tag als Abbruch.";
    /** §17 {@code LIMIT_HISTORY}. */
    static final String LIMIT_HISTORY = "Historie: Daten gibt es erst ab dem Erfassungsbeginn der "
        + "jeweiligen Quelle; davor wird nie rückwirkend gerechnet. Danach ausgefallene Nächte "
        + "werden bis zu 25 Tage nachgeholt.";
    /** REQ-043: the load figure „Anfragen" of the usage section / Last-Kennzahl. */
    static final String LIMIT_REQUESTS = "Anfragen: jeder Terminsuch-Aufruf, inklusive Fehler – ein "
        + "Kunde sucht meist mehrfach (andere Filiale, anderer Tag), sie zeigen die Last, nicht die Zahl "
        + "der Kunden. Die Fehler bei Terminsuchen und Buchungen sind Sitzungen, in denen jeder Versuch "
        + "scheiterte; gezählt seit REQ-043 und beim Deploy so weit nachgerechnet, wie Log Analytics "
        + "zurückreicht (30 Tage) – ältere Tage zeigen „Fehler nicht erfasst\".";
    /** §17 {@code LIMIT_TIRE_SEASON}. */
    static final String LIMIT_TIRE_SEASON = "Radwechsel ist nur in der Saison buchbar.";

    /** Display date / Anzeigedatum, z. B. {@code 01.10.2026}. */
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** A limit label (before the first colon) is emphasised when it is this short at most. */
    private static final int MAX_LABEL_LENGTH = 30;

    /** Renders the section, {@code ""} when there is nothing to render / rendert die Sektion. */
    public String render(StatisticsPage page) {
        if (page == null) {
            return "";
        }
        UsageStats usage = page.usage();
        InsightsViewModel insights = page.insights();
        CapacityViewModel capacity = page.capacity();
        boolean bookings = insights != null && insights.bookingsEnabled();
        boolean funnel = insights != null && insights.funnelEnabled();
        boolean dropoff = funnel && insights.funnel() != null && insights.funnel().dropoff() != null;

        StringBuilder h = new StringBuilder(4096);
        h.append("<section id=\"limits\" class=\"st-limits\" aria-labelledby=\"limits-h\">")
            .append("<h2 id=\"limits-h\">").append(esc(LIMITS_HEADING)).append("</h2>");

        // Erfassung seit — one entry per active source (AC-29, BR-19).
        List<String[]> since = new ArrayList<>(5);
        if (usage != null) {
            since.add(new String[]{SOURCE_USAGE, day(usage.collectingSince())});
        }
        if (bookings) {
            since.add(new String[]{SOURCE_BOOKINGS, day(insights.collectingSince())});
        }
        if (funnel) {
            since.add(new String[]{SOURCE_FUNNEL, day(insights.funnelCollectingSince())});
        }
        if (dropoff) {
            since.add(new String[]{SOURCE_DROPOFF, day(insights.funnel().dropoff().collectingSince())});
        }
        if (capacity != null) {
            since.add(new String[]{SOURCE_CAPACITY, day(capacity.collectingSince())});
        }
        h.append("<h3 id=\"limits-since-h\">").append(esc(LIMITS_SUB_SINCE)).append("</h3>");
        if (!since.isEmpty()) {
            h.append("<dl aria-labelledby=\"limits-since-h\">");
            for (String[] entry : since) {
                h.append("<dt>").append(esc(entry[0])).append("</dt><dd>").append(entry[1]).append("</dd>");
            }
            h.append("</dl>");
        }

        // Darstellungsregeln — always all of them.
        h.append("<h3>").append(esc(LIMITS_SUB_RULES)).append("</h3><ul>");
        for (String rule : List.of(RULE_SUPPRESSED, RULE_SHARE, RULE_WITHHELD, RULE_NO_DATA,
            RULE_LOCATION, PRIVACY_NOTE)) {
            h.append("<li>").append(esc(rule)).append("</li>");
        }
        h.append("</ul>");

        // Messgrenzen — the limits of the active sources, history always.
        List<String> limits = new ArrayList<>(11);
        if (capacity != null) {
            limits.add(LIMIT_CAPACITY);
        }
        if (bookings) {
            limits.add(LIMIT_ONLINE_ONLY);
            limits.add(LIMIT_SMALL_NUMBERS);
            limits.add(LIMIT_SERVICE_VIEWS);
            limits.add(LIMIT_GROUPS);
            limits.add(LIMIT_SLOTS);
            limits.add(LIMIT_BOOKING_BATCH);
        }
        if (funnel) {
            limits.add(LIMIT_FUNNEL);
        }
        if (dropoff) {
            limits.add(LIMIT_DROPOFF);
        }
        if (usage != null) {
            limits.add(LIMIT_REQUESTS);
        }
        limits.add(LIMIT_HISTORY);
        if (bookings) {
            limits.add(LIMIT_TIRE_SEASON);
        }
        h.append("<h3>").append(esc(LIMITS_SUB_LIMITS)).append("</h3><ul>");
        for (String limit : limits) {
            h.append("<li>").append(limitItem(limit)).append("</li>");
        }
        h.append("</ul>");

        h.append("</section>");
        return h.toString();
    }

    /** The escaped date or „—" (BR-19) / Datum oder Strich. */
    private static String day(LocalDate date) {
        return date == null ? StatFormat.DASH : esc(DAY.format(date));
    }

    /**
     * A limit with its short label („Kapazität:") emphasised / Messgrenze mit hervorgehobenem
     * Kurzlabel. Texts without such a label render plain. / Ohne Label unverändert.
     */
    private static String limitItem(String text) {
        int colon = text.indexOf(": ");
        if (colon > 0 && colon <= MAX_LABEL_LENGTH) {
            return "<b>" + esc(text.substring(0, colon + 1)) + "</b>" + esc(text.substring(colon + 1));
        }
        return esc(text);
    }
}
