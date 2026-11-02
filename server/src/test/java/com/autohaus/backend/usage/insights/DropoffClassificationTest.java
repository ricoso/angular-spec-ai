package com.autohaus.backend.usage.insights;

import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.reporting.LogAnalyticsClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Primary drop-off scenario per session (REQ-038 TC-60, AC-33, BR-27, N1) / primäres
 * Absprungszenario je Sitzung.
 *
 * <p>The classification itself runs in Log Analytics ({@link InsightsAggregator#DROPOFF_KQL}), so
 * this test works in two halves (§13.6 „KQL-Fixture + Java-Whitelist"):
 * <ol>
 *   <li><b>Priority order in the KQL:</b> the {@code case(...)} of the {@code Scenario} column is
 *       parsed out of the real constant; its order must be the catalogue order of §8 (= the
 *       declaration order of {@link DropoffScenario}), and every condition must be exactly the
 *       catalogue rule. The fixture sessions (a)–(g) are then classified by walking <b>that parsed
 *       order</b> with a Java mirror of each condition and of the per-session {@code summarize} —
 *       a reordered or edited {@code case} fails here. / Die Reihenfolge stammt aus der echten
 *       Konstante.</li>
 *   <li><b>Java processing:</b> the resulting {@code sessions by Scenario, MaxStep, Device} rows are
 *       fed through the real {@link InsightsAggregator}: exactly one scenario per session, the
 *       invariant Σ scenarios = erreicht(1) − erreicht(5), {@code booked} ⇔ MaxStep 5. / Die
 *       Ergebniszeilen laufen durch den echten Aggregator.</li>
 * </ol>
 */
class DropoffClassificationTest {

    /** Catalogue order of §8 (A-1 … A-12); A-0 is the {@code strcat} fallback. */
    private static final List<String> CATALOGUE = List.of(
        "booked", "server-error", "access-denied", "slot-expired", "slot-conflict", "booking-rejected",
        "search-rejected", "error-then-quit", "requests-lost", "no-slots", "few-slots", "slow-response",
        "many-searches");

    /**
     * Java mirror of every KQL condition, keyed by its exact text / Java-Spiegel jeder Bedingung.
     * A changed condition text finds no mirror and fails the test.
     */
    private static final Map<String, Predicate<Session>> CONDITIONS = Map.ofEntries(
        Map.entry("MaxStep == 5", s -> s.maxStep == 5),
        Map.entry("LastStatus >= 500", s -> s.lastStatus >= 500),
        Map.entry("LastStatus in (403, 429)", s -> s.lastStatus == 403 || s.lastStatus == 429),
        Map.entry("Expired > 0", s -> s.expired > 0),
        Map.entry("Conflicts > 0", s -> s.conflicts > 0),
        Map.entry("LastKind == 'book' and LastStatus between (400 .. 499)",
            s -> s.lastKind.equals("book") && s.lastStatus >= 400 && s.lastStatus <= 499),
        Map.entry("LastKind == 'search' and LastStatus between (400 .. 499)",
            s -> s.lastKind.equals("search") && s.lastStatus >= 400 && s.lastStatus <= 499),
        Map.entry("Errors > 0", s -> s.errors > 0),
        Map.entry("MaxSeq - MinSeq + 1 > Seen", s -> s.maxSeq - s.minSeq + 1 > s.seen),
        Map.entry("LastSearchSeq > 0 and LastHits == 'none'", s -> s.lastSearchSeq > 0 && "none".equals(s.lastHits)),
        Map.entry("LastSearchSeq > 0 and LastHits == 'few'", s -> s.lastSearchSeq > 0 && "few".equals(s.lastHits)),
        Map.entry("LastDurMs >= " + DropoffScenario.SLOW_RESPONSE_MS,
            s -> s.lastDurMs >= DropoffScenario.SLOW_RESPONSE_MS),
        Map.entry("Searches >= " + DropoffScenario.MANY_SEARCHES_MIN,
            s -> s.searches >= DropoffScenario.MANY_SEARCHES_MIN));

    // ---- half 1: the case(...) of DROPOFF_KQL ----

    @Test
    @DisplayName("TC-60: the scenario case in DROPOFF_KQL follows the catalogue order of §8")
    void theKqlCaseFollowsTheCatalogueOrder() {
        List<Rule> rules = parseScenarioCase();

        assertEquals(CATALOGUE, rules.stream().map(Rule::key).toList());
        List<String> enumOrder = new ArrayList<>(Arrays.stream(DropoffScenario.values())
            .map(DropoffScenario::key).filter(key -> !key.startsWith("quiet-")).toList());
        enumOrder.add(0, "booked");
        assertEquals(enumOrder, CATALOGUE, "DropoffScenario is declared in catalogue order");
        assertTrue(InsightsAggregator.DROPOFF_KQL.contains("strcat('quiet-', tostring(MaxStep)))"),
            "A-0.k fallback after the last rule");
        for (Rule rule : rules) {
            assertTrue(CONDITIONS.containsKey(rule.condition()),
                "no mirror for the KQL condition '" + rule.condition() + "' of " + rule.key());
        }
    }

    @Test
    @DisplayName("TC-60: thresholds in the KQL equal SLOW_RESPONSE_MS and MANY_SEARCHES_MIN (§19 Frage 10)")
    void theKqlThresholdsMatchTheConstants() {
        assertTrue(InsightsAggregator.DROPOFF_KQL.contains("LastDurMs >= " + DropoffScenario.SLOW_RESPONSE_MS + ","));
        assertTrue(InsightsAggregator.DROPOFF_KQL.contains("Searches >= " + DropoffScenario.MANY_SEARCHES_MIN + ","));
        assertTrue(InsightsAggregator.DROPOFF_KQL.contains("\"hits\":\"(none|few|many)\""),
            "the hits whitelist of §10.8");
    }

    @Test
    @DisplayName("TC-60: fixtures (a)–(g) each get exactly the catalogue scenario")
    void classifiesTheFixtureSessions() {
        List<Rule> rules = parseScenarioCase();

        assertEquals(new Result("no-slots", 4), classify(rules, FIXTURE_A), "(a) last search 200 hits=none");
        assertEquals(new Result("slot-conflict", 4), classify(rules, FIXTURE_B), "(b) booking 410, then search 200");
        assertEquals(new Result("slot-expired", 4), classify(rules, FIXTURE_C),
            "(c) booking 404 twice → slot-expired, not booking-rejected");
        assertEquals(new Result("requests-lost", 3), classify(rules, FIXTURE_D), "(d) counter 1, 2, 5");
        assertEquals(new Result("booked", 5), classify(rules, FIXTURE_E), "(e) 5xx, then booking 201");
        assertEquals(new Result("slow-response", 2), classify(rules, FIXTURE_F), "(f) last request ok after 9 000 ms");
        assertEquals(new Result("many-searches", 4), classify(rules, FIXTURE_G),
            "(g) 5 successful searches, hits=many — no-slots/few-slots only for none/few");
    }

    @Test
    @DisplayName("TC-60: the remaining catalogue rows and the quiet fallback")
    void classifiesTheRemainingCatalogueRows() {
        List<Rule> rules = parseScenarioCase();

        assertEquals(new Result("server-error", 3), classify(rules, List.of(
            start(1), services(2), notes(3), req(4, "search", 502, 100, ""))));
        assertEquals(new Result("access-denied", 2), classify(rules, List.of(
            start(1), services(2), req(3, "notes", 429, 5, ""))));
        assertEquals(new Result("booking-rejected", 4), classify(rules, List.of(
            start(1), services(2), notes(3), search(4, "many"), req(5, "book", 422, 300, ""))));
        assertEquals(new Result("search-rejected", 3), classify(rules, List.of(
            start(1), services(2), notes(3), req(4, "search", 400, 30, ""))));
        assertEquals(new Result("error-then-quit", 2), classify(rules, List.of(
            start(1), req(2, "services", 500, 30, ""), services(3))));
        assertEquals(new Result("few-slots", 4), classify(rules, List.of(
            start(1), services(2), notes(3), search(4, "few"))));
        assertEquals(new Result("quiet-1", 1), classify(rules, List.of(start(1))));
        assertEquals(new Result("quiet-4", 4), classify(rules, List.of(
            start(1), services(2), notes(3), search(4, "many"))));
    }

    // ---- half 2: Java processing of the result rows ----

    @Test
    @DisplayName("TC-60/AC-33: one scenario per session, Σ = erreicht(1) − erreicht(5), booked ⇔ MaxStep 5")
    void foldsTheClassifiedSessionsIntoAConsistentDay() {
        List<Rule> rules = parseScenarioCase();
        Map<List<Object>, Long> grouped = new LinkedHashMap<>();
        List<List<Request>> sessions = List.of(FIXTURE_A, FIXTURE_B, FIXTURE_C, FIXTURE_D, FIXTURE_E,
            FIXTURE_F, FIXTURE_G, List.of(start(1)), List.of(start(1)));
        String[] devices = {"mobile", "desktop", "mobile", "tablet", "mobile", "unknown", "desktop", "mobile", "mobile"};
        for (int i = 0; i < sessions.size(); i++) {
            Result result = classify(rules, sessions.get(i));
            grouped.merge(List.of(result.scenario(), result.maxStep(), devices[i]), 1L, Long::sum);
        }
        List<List<Object>> rows = new ArrayList<>();
        grouped.forEach((key, count) -> rows.add(List.of(key.get(0), key.get(1), key.get(2), count)));

        FunnelDay funnel = aggregate(rows);

        FunnelCounts all = funnel.all();
        assertEquals(9L, all.visits());
        assertEquals(1L, all.booking());
        DropoffDay dropoff = funnel.dropoff();
        long stepSum = dropoff.byScenarioStep().values().stream()
            .flatMap(stages -> stages.values().stream()).mapToLong(Long::longValue).sum();
        long deviceSum = dropoff.byScenarioDevice().values().stream()
            .flatMap(perDevice -> perDevice.values().stream()).mapToLong(Long::longValue).sum();
        assertEquals(all.visits() - all.booking(), stepSum, "Σ scenarios = erreicht(1) − erreicht(5) (BR-27)");
        assertEquals(stepSum, deviceSum, "both margins carry every drop-off exactly once");
        assertEquals(1L, dropoff.byScenarioStep().get("no-slots").get("4"),
            "(a) at stage 4: " + dropoff.byScenarioStep());
        assertEquals(1L, dropoff.byScenarioStep().get("slot-conflict").get("4"), "(b)");
        assertEquals(1L, dropoff.byScenarioStep().get("slot-expired").get("4"), "(c)");
        assertEquals(1L, dropoff.byScenarioStep().get("slow-response").get("2"), "(f)");
        assertEquals(1L, dropoff.byScenarioStep().get("many-searches").get("4"), "(g)");
        assertEquals(1L, dropoff.byScenarioStep().get("requests-lost").get("3"));
        assertEquals(2L, dropoff.byScenarioStep().get("quiet-1").get("1"));
        assertEquals(2L, dropoff.byScenarioDevice().get("quiet-1").get("mobile"));
        assertFalse(dropoff.byScenarioStep().containsKey("booked"), "(e) booked carries no scenario");
    }

    @Test
    @DisplayName("TC-60/E11: a row claiming booked below stage 5 is discarded, the invariant holds")
    void discardsAnInconsistentBookedRow() {
        FunnelDay funnel = aggregate(List.of(
            List.of("booked", 5, "mobile", 2L),
            List.of("booked", 4, "mobile", 3L),
            List.of("quiet-2", 2, "desktop", 4L)));

        assertEquals(6L, funnel.all().visits());
        assertEquals(2L, funnel.all().booking());
        long stepSum = funnel.dropoff().byScenarioStep().values().stream()
            .flatMap(stages -> stages.values().stream()).mapToLong(Long::longValue).sum();
        assertEquals(funnel.all().visits() - funnel.all().booking(), stepSum);
    }

    // ---- fixtures (§13.6 TC-60) ----

    /** (a) last line: search 200 with {@code hits=none}. */
    private static final List<Request> FIXTURE_A = List.of(start(1), services(2), notes(3), search(4, "none"));
    /** (b) booking 410, afterwards a search 200. */
    private static final List<Request> FIXTURE_B = List.of(start(1), services(2), notes(3), search(4, "many"),
        req(5, "book", 410, 400, ""), search(6, "none"));
    /** (c) booking 404, the same booking again 404. */
    private static final List<Request> FIXTURE_C = List.of(start(1), services(2), notes(3), search(4, "many"),
        req(5, "book", 404, 200, ""), req(6, "book", 404, 180, ""));
    /** (d) correlation counter 1, 2, 5. */
    private static final List<Request> FIXTURE_D = List.of(start(1), services(2), notes(5));
    /** (e) 5xx, afterwards booking 201. */
    private static final List<Request> FIXTURE_E = List.of(req(1, "start", 503, 40, ""), services(2), notes(3),
        search(4, "many"), req(5, "book", 201, 900, ""));
    /** (f) last request ok with 9 000 ms. */
    private static final List<Request> FIXTURE_F = List.of(start(1), req(2, "services", 200, 9_000, ""));
    /** (g) five successful searches, the last ok with {@code hits=many}. */
    private static final List<Request> FIXTURE_G = List.of(start(1), services(2), notes(3), search(4, "many"),
        search(5, "many"), search(6, "many"), search(7, "many"), search(8, "many"));

    private static Request start(int seq) {
        return req(seq, "start", 200, 20, "");
    }

    private static Request services(int seq) {
        return req(seq, "services", 200, 50, "");
    }

    private static Request notes(int seq) {
        return req(seq, "notes", 200, 30, "");
    }

    private static Request search(int seq, String hits) {
        return req(seq, "search", 200, 800, hits);
    }

    private static Request req(int seq, String kind, int status, int durMs, String hits) {
        return new Request(seq, kind, status, durMs, hits);
    }

    // ---- KQL mirror / KQL-Spiegel ----

    /** One access line after the {@code project} / eine Access-Zeile nach dem {@code project}. */
    private record Request(int seq, String kind, int status, int durMs, String hits) {
        int step() {
            if (status >= 400) {
                return 1;
            }
            return switch (kind) {
                case "book" -> 5;
                case "search" -> 4;
                case "notes" -> 3;
                case "services" -> 2;
                default -> 1;
            };
        }

        int searchSeq() {
            return kind.equals("search") && status < 400 ? seq : -1;
        }
    }

    /** The per-session {@code summarize} of DROPOFF_KQL / das {@code summarize} je Sitzung. */
    private static final class Session {
        int maxStep;
        int seen;
        int minSeq;
        int maxSeq;
        int lastStatus;
        String lastKind;
        int lastDurMs;
        int lastSearchSeq;
        String lastHits;
        int searches;
        int expired;
        int conflicts;
        int errors;

        static Session of(List<Request> requests) {
            Session s = new Session();
            s.maxStep = requests.stream().mapToInt(Request::step).max().orElseThrow();
            s.seen = requests.size();
            s.minSeq = requests.stream().mapToInt(Request::seq).min().orElseThrow();
            s.maxSeq = requests.stream().mapToInt(Request::seq).max().orElseThrow();
            Request last = requests.stream().max(Comparator.comparingInt(Request::seq)).orElseThrow();
            s.lastStatus = last.status();
            s.lastKind = last.kind();
            s.lastDurMs = last.durMs();
            Request lastSearch = requests.stream().max(Comparator.comparingInt(Request::searchSeq)).orElseThrow();
            s.lastSearchSeq = lastSearch.searchSeq();
            s.lastHits = lastSearch.hits();
            s.searches = (int) requests.stream().filter(r -> r.searchSeq() > 0).count();
            s.expired = (int) requests.stream().filter(r -> r.kind().equals("book") && r.status() == 404).count();
            s.conflicts = (int) requests.stream().filter(r -> r.kind().equals("book") && r.status() == 410).count();
            s.errors = (int) requests.stream()
                .filter(r -> r.status() >= 500 || r.status() == 403 || r.status() == 429).count();
            return s;
        }
    }

    private record Rule(String condition, String key) {}

    private record Result(String scenario, int maxStep) {}

    /** First matching rule in the parsed KQL order wins; A-0.k otherwise / erste Regel gewinnt. */
    private static Result classify(List<Rule> rules, List<Request> requests) {
        Session session = Session.of(requests);
        for (Rule rule : rules) {
            Predicate<Session> condition = Objects.requireNonNull(CONDITIONS.get(rule.condition()), rule.condition());
            if (condition.test(session)) {
                return new Result(rule.key(), session.maxStep);
            }
        }
        return new Result("quiet-" + session.maxStep, session.maxStep);
    }

    /** Parses {@code | extend Scenario = case(...)} out of the real constant / aus der echten Konstante. */
    private static List<Rule> parseScenarioCase() {
        String kql = InsightsAggregator.DROPOFF_KQL;
        int start = kql.indexOf("| extend Scenario = case(");
        int end = kql.indexOf("strcat('quiet-'", start);
        assertTrue(start > 0 && end > start, kql);
        Matcher matcher = Pattern.compile("^\\s*(.+?),\\s*'([a-z-]+)',\\s*$", Pattern.MULTILINE)
            .matcher(kql.substring(start, end));
        List<Rule> rules = new ArrayList<>();
        while (matcher.find()) {
            rules.add(new Rule(matcher.group(1).trim(), matcher.group(2)));
        }
        return rules;
    }

    /** Runs result rows through the real aggregator (drop-off variant) / durch den echten Aggregator. */
    private static FunnelDay aggregate(List<List<Object>> rows) {
        LogAnalyticsClient logClient = mock(LogAnalyticsClient.class);
        when(logClient.query(anyString())).thenReturn(InsightsAggregatorTest.response(rows));
        InsightsAggregator aggregator = new InsightsAggregator(logClient, mock(SetupService.class),
            mock(ServiceGroupClassifier.class), InsightsAggregatorTest.properties("Europe/Berlin"));
        return aggregator.aggregateDay(LocalDate.of(2026, 10, 22), false, true, true).orElseThrow()
            .global().funnel();
    }
}
