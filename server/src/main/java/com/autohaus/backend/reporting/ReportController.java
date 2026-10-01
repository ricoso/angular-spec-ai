package com.autohaus.backend.reporting;

import com.autohaus.backend.capacity.CapacityHistory;
import com.autohaus.backend.capacity.CapacityStore;
import com.autohaus.backend.capacity.CapacityViewModel;
import com.autohaus.backend.config.CapacityProperties;
import com.autohaus.backend.config.UsageDropoffProperties;
import com.autohaus.backend.config.UsageFunnelProperties;
import com.autohaus.backend.config.UsageInsightsProperties;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.config.setup.BranchNames;
import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.usage.UsageStats;
import com.autohaus.backend.usage.UsageStatsScheduler;
import com.autohaus.backend.usage.UsageStatsStore;
import com.autohaus.backend.usage.UsageZone;
import com.autohaus.backend.usage.insights.InsightsHistory;
import com.autohaus.backend.usage.insights.InsightsStore;
import com.autohaus.backend.usage.insights.InsightsViewModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.ServletRequestPathUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Serves the internal status page as HTML (REQ-030, REQ-037, REQ-038), behind Basic Auth (see
 * {@code SecurityConfig}, {@code /internal/**}).
 *
 * <p><b>REQ-038 — two tabs as real pages (BR-1, §12):</b> {@code GET /internal/report} (and
 * {@code /internal/report/}) renders the default tab „Statistik" directly with 200 — never a
 * redirect; {@code /internal/report/statistics} the same content, {@code /internal/report/bugs}
 * the stored error-report fragment byte-identical (or the hint shown in its place). Every other
 * {@code GET /internal/report/**} answers 404 {@code text/html} through
 * {@link #catchAll(HttpServletRequest)} — never the Angular {@code index.html} of the SPA fallback
 * (E7); literal mappings win by pattern specificity. A literal GET path whose content negotiation
 * failed (an {@code Accept} without its media type) also lands in the catch-all and answers
 * {@code 406} there, as before REQ-038 — never a misleading 404 (§12.4). {@code GET} on the
 * POST-only {@code /refresh} is an unknown GET path and answers 404 (AC-5, BR-1). / Tabs als echte
 * Pfade, unbekannte Unterpfade 404, fehlgeschlagene Content-Negotiation 406.
 *
 * <p><b>Kill-switch matrix (§5.1, BR-4):</b> the statistics tab exists iff
 * {@code usage.enabled ∨ usage.insights.enabled ∨ capacity.enabled}; the funnel requires
 * {@code usage.enabled} and does not count on its own. Without the tab {@code /internal/report}
 * renders the bugs content without navigation and {@code /statistics} answers 404. General rule:
 * {@code insights.json} 200 ⇔ {@code usage.insights.enabled ∨ (usage.enabled ∧
 * usage.funnel.enabled)}; {@code capacity.json} 200 ⇔ {@code capacity.enabled}; {@code usage.json}
 * unchanged. A switched-off source is passed as {@code null} and its section is omitted without
 * replacement. / Kill-Switch-Matrix; abgeschaltete Quellen sind {@code null}.
 *
 * <p><b>No backend in the request path (AC-7, BR-5):</b> every endpoint reads the in-memory
 * snapshots of {@link UsageStatsStore}, {@link InsightsStore} and {@link CapacityStore} only — no
 * Log Analytics query, no blob access, no SoftNet call. Those run exclusively in the schedulers.
 * While a store holds no view yet (never loaded, E4) the active source is shown with
 * „Historie derzeit nicht verfügbar" instead of numbers — built from an empty history in memory,
 * never zeros and never 5xx (AC-32). / Nur In-Memory-Snapshots; ohne Stand ein Warnhinweis.
 */
@RestController
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    /**
     * Own mapper for {@code usage.json} (REQ-037 §12.2) / eigener Mapper für {@code usage.json}.
     *
     * <p>Two reasons it cannot be the injected bean: it is a bare {@code new ObjectMapper()}
     * without {@code JavaTimeModule} ({@code config/WebConfig}), and the application sets
     * {@code spring.jackson.default-property-inclusion: non_null} globally — which would silently
     * drop {@code searchRate}/{@code bookingRate}/{@code closeRate}, {@code liveAsOf},
     * {@code collectingSince} and {@code lastRollupDay} although §12.2 shows them explicitly as
     * {@code null}. {@code ALWAYS} keeps the contract stable for monitoring consumers; the global
     * setting stays untouched so the existing API responses do not change. / Nullwerte müssen
     * erhalten bleiben, deshalb ein eigener Mapper.
     *
     * <p>REQ-038 serializes {@code insights.json} and {@code capacity.json} with the same mapper
     * (§12.5/§12.6): suppressed cells keep their explicit {@code null}s. / Auch für die neuen
     * JSON-Endpunkte.
     */
    private static final ObjectMapper USAGE_MAPPER = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .serializationInclusion(JsonInclude.Include.ALWAYS)
        .build();

    /**
     * Hint shown instead of the report sections while no report exists (REQ-030 wording kept). It
     * is a <b>fragment</b>; since REQ-038 it is shown on the bugs tab only (BR-3). / Bisheriger
     * Hinweistext, jetzt nur im Bugs-Tab.
     */
    private static final String NO_REPORT_HINT =
        "<section class=\"nr\"><h2>Fehler-Report</h2>"
            + "<p class=\"empty\">Es liegt noch kein Report vor. Das Feature erstellt automatisch "
            + "einen Report, sobald die Secrets <code>LLM_API_KEY</code> und <code>LLM_MODEL</code> gesetzt "
            + "sind und der nächtliche Lauf (bzw. der Start) erfolgt ist.</p></section>";

    private static final String NO_LLM_MESSAGE = "Report-Feature nicht konfiguriert (LLM_API_KEY / LLM_MODEL fehlen).";

    /** {@code text/html; charset=UTF-8} (§12.1) — umlauts must not depend on converter defaults. */
    private static final MediaType TEXT_HTML_UTF8 = new MediaType("text", "html", StandardCharsets.UTF_8);

    // ---- paths (§12) / Pfade ----

    static final String PATH_REPORT = "/internal/report";
    static final String PATH_REPORT_SLASH = "/internal/report/";
    static final String PATH_STATISTICS = "/internal/report/statistics";
    static final String PATH_BUGS = "/internal/report/bugs";
    static final String PATH_USAGE_JSON = "/internal/report/usage.json";
    static final String PATH_INSIGHTS_JSON = "/internal/report/insights.json";
    static final String PATH_CAPACITY_JSON = "/internal/report/capacity.json";
    static final String PATH_REFRESH = "/internal/report/refresh";
    static final String PATH_CATCH_ALL = "/internal/report/**";

    /**
     * Every literal {@code GET} mapping of this controller (§12.4) / alle wörtlichen GET-Pfade. A
     * request for one of them reaches the catch-all only when its {@code produces} condition failed
     * — the only condition these mappings carry — so the catch-all answers {@code 406} instead of
     * 404. {@link #PATH_REFRESH} is deliberately absent: it is POST-only, a GET is unknown (AC-5).
     */
    static final Set<String> LITERAL_GET_PATHS = Set.of(PATH_REPORT, PATH_REPORT_SLASH,
        PATH_STATISTICS, PATH_BUGS, PATH_USAGE_JSON, PATH_INSIGHTS_JSON, PATH_CAPACITY_JSON);

    private final ReportStore store;
    private final ReportHtmlGenerator htmlGenerator;
    private final UsageStatsStore usageStatsStore;
    private final UsageStatsProperties usageProperties;
    private final ObjectProvider<ErrorReportScheduler> scheduler;
    private final ObjectProvider<UsageStatsScheduler> usageScheduler;
    private final InsightsStore insightsStore;
    private final CapacityStore capacityStore;
    private final BriefingCalculator briefingCalculator;
    private final SetupService setupService;
    private final UsageInsightsProperties insightsProperties;
    private final UsageFunnelProperties funnelProperties;
    private final UsageDropoffProperties dropoffProperties;
    private final CapacityProperties capacityProperties;
    private final Clock clock;

    /**
     * Spring wiring — the only constructor (REQ-038 §14.2) / Spring-Verdrahtung.
     *
     * @param store the error-report fragment (REQ-030)
     * @param htmlGenerator the page renderer
     * @param usageStatsStore the REQ-037 snapshot
     * @param usageProperties {@code usage.*} (kill switch {@code usage.enabled}, zone)
     * @param scheduler the error-report scheduler, absent without the LLM toggle
     * @param usageScheduler the usage scheduler, absent with {@code usage.enabled=false}
     * @param insightsStore the precomputed K2/K3 view (REQ-038)
     * @param capacityStore the precomputed K1 view (REQ-038)
     * @param briefingCalculator the S0 impulses (BR-21)
     * @param setupService branch names from {@code setup.json} (only for the „no history" view)
     * @param insightsProperties {@code usage.insights.*}
     * @param funnelProperties {@code usage.funnel.*}
     * @param dropoffProperties {@code usage.dropoff.*}
     * @param capacityProperties {@code capacity.*}
     * @param clock optional {@link Clock} bean (tests pin „today"); default is the system clock —
     *     the Berlin calendar day is always derived with {@code usage.zone} / Uhr-Quelle
     */
    @Autowired
    public ReportController(ReportStore store,
                            ReportHtmlGenerator htmlGenerator,
                            UsageStatsStore usageStatsStore,
                            UsageStatsProperties usageProperties,
                            ObjectProvider<ErrorReportScheduler> scheduler,
                            ObjectProvider<UsageStatsScheduler> usageScheduler,
                            InsightsStore insightsStore,
                            CapacityStore capacityStore,
                            BriefingCalculator briefingCalculator,
                            SetupService setupService,
                            UsageInsightsProperties insightsProperties,
                            UsageFunnelProperties funnelProperties,
                            UsageDropoffProperties dropoffProperties,
                            CapacityProperties capacityProperties,
                            ObjectProvider<Clock> clock) {
        this.store = store;
        this.htmlGenerator = htmlGenerator;
        this.usageStatsStore = usageStatsStore;
        this.usageProperties = usageProperties;
        this.scheduler = scheduler;
        this.usageScheduler = usageScheduler;
        this.insightsStore = insightsStore;
        this.capacityStore = capacityStore;
        this.briefingCalculator = briefingCalculator;
        this.setupService = setupService;
        this.insightsProperties = insightsProperties;
        this.funnelProperties = funnelProperties;
        this.dropoffProperties = dropoffProperties;
        this.capacityProperties = capacityProperties;
        Clock injected = clock != null ? clock.getIfUnique() : null;
        this.clock = injected != null ? injected : Clock.systemUTC();
    }

    /**
     * Default tab (§12.1, AC-1): the statistics tab directly with 200 — no redirect — or, while
     * the statistics tab does not exist (§5.1 row 8), the bugs content <b>without</b> navigation.
     * Reads in-memory state only (AC-7). / Standard-Tab bzw. Bugs-Inhalt ohne Navigation.
     */
    @GetMapping(value = {PATH_REPORT, PATH_REPORT_SLASH}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> report() {
        if (!statisticsAvailable()) {
            return html(HttpStatus.OK, htmlGenerator.renderBugsPage(bugsFragment(), false));
        }
        return html(HttpStatus.OK, htmlGenerator.renderStatisticsPage(statisticsPage()));
    }

    /** Statistics tab (§12.2); 404 while it does not exist (§5.1) / Statistik-Tab. */
    @GetMapping(value = PATH_STATISTICS, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> statistics() {
        if (!statisticsAvailable()) {
            return notFoundPage();
        }
        return html(HttpStatus.OK, htmlGenerator.renderStatisticsPage(statisticsPage()));
    }

    /**
     * Bugs tab (§12.3) — always exists; the fragment byte-identical or the previous hint (BR-3),
     * navigation only while the statistics tab exists. / Bugs-Tab, existiert immer.
     */
    @GetMapping(value = PATH_BUGS, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> bugs() {
        return html(HttpStatus.OK, htmlGenerator.renderBugsPage(bugsFragment(), statisticsAvailable()));
    }

    /**
     * Machine-readable aggregate for verification/monitoring (§12.2, AC-16) — the same snapshot the
     * page renders, serialized directly from {@link UsageStats}. {@code 404} when the feature is
     * switched off (AC-22). / Dasselbe Aggregat als JSON.
     */
    @GetMapping(value = PATH_USAGE_JSON, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> usageJson() {
        if (!usageEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        try {
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(USAGE_MAPPER.writeValueAsString(usageStatsStore.current()));
        } catch (JsonProcessingException e) {
            log.error("REQ-037: usage snapshot not serializable: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"usage stats not serializable\"}");
        }
    }

    /**
     * The already suppressed insights view as JSON plus {@code briefing} (§12.5, AC-25) — the same
     * values the page shows. {@code 404} unless {@code usage.insights.enabled ∨ (usage.enabled ∧
     * usage.funnel.enabled)} (§5.1). / Dieselbe unterdrückte Sicht wie die Seite.
     */
    @GetMapping(value = PATH_INSIGHTS_JSON, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> insightsJson() {
        if (!insightsActive()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        LocalDate today = today();
        InsightsViewModel insights = currentInsights(today);
        CapacityViewModel capacity = capacityEnabled() ? currentCapacity(today) : null;
        Briefing briefing = briefing(insights, capacity, today);
        try {
            ObjectNode node = USAGE_MAPPER.valueToTree(insights);
            JsonNode briefingNode = briefing == null ? NullNode.getInstance() : USAGE_MAPPER.valueToTree(briefing);
            node.set("briefing", briefingNode);
            return json(USAGE_MAPPER.writeValueAsString(node));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.error("REQ-038: insights view not serializable: {}", e.getClass().getSimpleName());
            return jsonError("insights view not serializable");
        }
    }

    /**
     * The precomputed capacity view as JSON (§12.6, AC-25); {@code 404} with
     * {@code capacity.enabled=false}. / Die Kapazitäts-Sicht als JSON.
     */
    @GetMapping(value = PATH_CAPACITY_JSON, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> capacityJson() {
        if (!capacityEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        try {
            return json(USAGE_MAPPER.writeValueAsString(currentCapacity(today())));
        } catch (JsonProcessingException e) {
            log.error("REQ-038: capacity view not serializable: {}", e.getClass().getSimpleName());
            return jsonError("capacity view not serializable");
        }
    }

    /**
     * Catch-all for every other {@code GET /internal/report/**} (§12.4, E7, AC-5) / fängt alle
     * übrigen GET-Pfade ab.
     *
     * <ul>
     *   <li><b>Unknown path</b> — including {@code GET /refresh} (POST-only, AC-5): 404
     *       {@code text/html} with links to the existing tabs. The requested path is never part of
     *       the response (no reflected markup). / Unbekannter Pfad: 404 ohne Spiegelung.</li>
     *   <li><b>Literal path</b> ({@link #LITERAL_GET_PATHS}) — it only gets here when content
     *       negotiation failed (e.g. {@code Accept: text/html} on {@code usage.json}): {@code 406}
     *       without a body, exactly the status these paths answered before the catch-all existed.
     *       / Wörtlicher Pfad mit unpassendem Accept: 406 wie vor REQ-038.</li>
     * </ul>
     *
     * <p>Without credentials the security chain answers 401 first (E8). / Ohne Login 401.
     *
     * @param request the request, only for its path / nur für den Pfad
     * @return 406 for a literal path, else the 404 page / 406 bzw. die 404-Seite
     */
    @GetMapping(PATH_CATCH_ALL)
    public ResponseEntity<String> catchAll(HttpServletRequest request) {
        if (LITERAL_GET_PATHS.contains(lookupPath(request))) {
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
        }
        return notFoundPage();
    }

    /**
     * Manual refresh (§12.3): updates the statistics whenever the {@link UsageStatsScheduler} bean
     * exists ({@code usage.enabled=true}) and the error report whenever its own scheduler bean
     * exists. Only when <b>both</b> are absent the endpoint still answers {@code 503}.
     *
     * <p>Throttled server-side to one run per {@code usage.refresh-min-interval-seconds} (AC-21):
     * the second call inside that window gets {@code 429} <b>before</b> any KQL or blob request is
     * issued — {@code /internal/**} is publicly reachable and every run costs 3-9 Log Analytics
     * queries. The throttle clock is stamped by the scheduler itself, so a cron run seconds earlier
     * blocks a manual call too. / Gedrosselt, ohne Backend-Abfrage bei Ablehnung.
     *
     * <p>REQ-038 BR-16: unchanged — it triggers neither the capacity scan nor the insights rollup.
     */
    @PostMapping(value = PATH_REFRESH, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> refresh() {
        UsageStatsScheduler activeUsage = usageScheduler.getIfAvailable();
        ErrorReportScheduler activeReport = scheduler.getIfAvailable();

        if (activeUsage == null && activeReport == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(NO_LLM_MESSAGE);
        }

        List<String> messages = new ArrayList<>(2);
        if (activeUsage != null) {
            if (!activeUsage.refreshIfAllowed()) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Zu früh — frühestens " + refreshMinIntervalSeconds() + " s nach dem letzten Lauf.");
            }
            messages.add("Statistik neu erzeugt.");
        }
        if (activeReport != null) {
            activeReport.generate();
            messages.add("Report neu erzeugt.");
        } else {
            messages.add(NO_LLM_MESSAGE);
        }
        return ResponseEntity.ok(String.join(" ", messages));
    }

    // ---- kill-switch matrix (§5.1) / Kill-Switch-Matrix ----

    /**
     * Whether the statistics tab exists (§5.1, AC-6): {@code usage ∨ insights ∨ capacity} — the
     * funnel requires {@code usage} and does not count on its own. / Ob der Statistik-Tab existiert.
     */
    boolean statisticsAvailable() {
        return usageEnabled() || insightsEnabled() || capacityEnabled();
    }

    /** Kill switch {@code usage.enabled} (BR-13) — defensive against a missing properties bean. */
    private boolean usageEnabled() {
        return usageProperties != null && usageProperties.enabled();
    }

    /** Kill switch K2 {@code usage.insights.enabled}. */
    private boolean insightsEnabled() {
        return insightsProperties != null && insightsProperties.enabled();
    }

    /** The funnel only works together with {@code usage.enabled} (§5.1 rows 6–8: „beliebig"). */
    private boolean funnelActive() {
        return usageEnabled() && funnelProperties != null && funnelProperties.enabled();
    }

    /** Drop-off scenarios are subordinate to an active funnel (N1, §5.1 general rule). */
    private boolean dropoffActive() {
        return funnelActive() && dropoffProperties != null && dropoffProperties.enabled();
    }

    /** {@code insights.json} and the insights view exist (§5.1 general rule). */
    private boolean insightsActive() {
        return insightsEnabled() || funnelActive();
    }

    /** Kill switch K1 {@code capacity.enabled}. */
    private boolean capacityEnabled() {
        return capacityProperties != null && capacityProperties.enabled();
    }

    private int refreshMinIntervalSeconds() {
        return usageProperties != null ? usageProperties.refreshMinIntervalSeconds() : 0;
    }

    // ---- composition from the in-memory snapshots / Komposition aus dem Speicher ----

    /** Render input of the statistics tab (§10.1) — {@code null} members are switched off. */
    private StatisticsPage statisticsPage() {
        LocalDate today = today();
        UsageStats usage = usageEnabled() ? usageStatsStore.current() : null;
        InsightsViewModel insights = insightsActive() ? currentInsights(today) : null;
        CapacityViewModel capacity = capacityEnabled() ? currentCapacity(today) : null;
        return new StatisticsPage(usage, insights, capacity, briefing(insights, capacity, today));
    }

    /**
     * S0 over the booking part of the insights view and the capacity view (BR-21); a funnel-only
     * view carries no booking data and therefore counts as „off" (§5.1 row 5: no S0). /
     * Ein reiner Trichter-View zählt für S0 als „aus".
     */
    private Briefing briefing(InsightsViewModel insights, CapacityViewModel capacity, LocalDate today) {
        InsightsViewModel bookings = insights != null && insights.bookingsEnabled() ? insights : null;
        if (bookings == null && capacity == null) {
            return null;
        }
        return briefingCalculator.compute(bookings, capacity, today);
    }

    /**
     * The insights view to show / die anzuzeigende Insights-Sicht.
     *
     * <ul>
     *   <li>No view stored yet (never loaded, E4): a view over an empty history carrying
     *       {@code WARN_HISTORY_UNAVAILABLE} — numbers never appear as zeros (AC-32).</li>
     *   <li>A stored view built under other switches (e.g. a stale seed): rebuilt from the stored
     *       history under the current switches — the switch decides, not the data (BR-4). Pure
     *       in-memory computation, no I/O (BR-5).</li>
     * </ul>
     */
    private InsightsViewModel currentInsights(LocalDate today) {
        boolean bookings = insightsEnabled();
        boolean funnel = funnelActive();
        boolean dropoff = dropoffActive();
        // One read: view and history of the same publication (§14.1) / ein konsistenter Stand.
        InsightsStore.Snapshot stored = insightsStore.snapshot();
        InsightsViewModel current = stored.viewModel();
        if (current == null) {
            return InsightsViewModel.build(InsightsHistory.empty(), branchNames(), today,
                    bookings, funnel, dropoff)
                .withAdditionalWarnings(List.of(InsightsViewModel.WARN_HISTORY_UNAVAILABLE));
        }
        // Drop-off data of a switched-off N1 source must never leak either (AC-35); a stored view
        // without the sub-section while the switch is on leaks nothing and is kept.
        boolean storedDropoff = current.funnel() != null && current.funnel().dropoff() != null;
        if (current.bookingsEnabled() != bookings || current.funnelEnabled() != funnel
            || (storedDropoff && !dropoff)) {
            return InsightsViewModel.build(stored.history(), branchNames(), today,
                bookings, funnel, dropoff);
        }
        return current;
    }

    /**
     * The capacity view to show; without a stored view one over an empty history with
     * {@code WARN_HISTORY_UNAVAILABLE} (E4, AC-32). / Die anzuzeigende Kapazitäts-Sicht.
     */
    private CapacityViewModel currentCapacity(LocalDate today) {
        CapacityViewModel current = capacityStore.snapshot().viewModel();
        if (current != null) {
            return current;
        }
        boolean showSlotCount = capacityProperties != null && capacityProperties.showSlotCount();
        return CapacityViewModel.build(CapacityHistory.empty(), branchNames(), showSlotCount, today, null)
            .withAdditionalWarnings(List.of(CapacityViewModel.WARN_HISTORY_UNAVAILABLE));
    }

    /** Display names keyed by branchId from {@code setup.json} (in memory) / Anzeigenamen. */
    private Map<String, String> branchNames() {
        try {
            return BranchNames.byId(setupService);
        } catch (RuntimeException e) {
            log.warn("REQ-038: branch names unavailable: {}", e.getClass().getSimpleName());
            return Map.of();
        }
    }

    private String bugsFragment() {
        return store.hasReport() ? store.fragment() : NO_REPORT_HINT;
    }

    /** 404 {@code text/html} with the tab links, never the requested path (E7) / die 404-Seite. */
    private ResponseEntity<String> notFoundPage() {
        return html(HttpStatus.NOT_FOUND, htmlGenerator.renderNotFoundPage(statisticsAvailable()));
    }

    /**
     * The path as the mapping matched it / der Pfad, wie ihn das Mapping verglichen hat: within the
     * application, each segment decoded and without {@code ;} parameters — the view of Spring's
     * {@code PathPattern} literal match, so {@code usage%2Ejson} or {@code usage.json;x=1} count as
     * {@code usage.json} here exactly as they do there. / Wie der PathPattern-Vergleich.
     */
    static String lookupPath(HttpServletRequest request) {
        RequestPath path = ServletRequestPathUtils.hasParsedRequestPath(request)
            ? ServletRequestPathUtils.getParsedRequestPath(request)
            : RequestPath.parse(request.getRequestURI(), request.getContextPath());
        StringBuilder value = new StringBuilder();
        for (PathContainer.Element element : path.pathWithinApplication().elements()) {
            value.append(element instanceof PathContainer.PathSegment segment
                ? segment.valueToMatch() : element.value());
        }
        return value.toString();
    }

    /** The Berlin calendar day of the request (BR-12) / Berliner Kalendertag des Abrufs. */
    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), UsageZone.resolve(usageProperties));
    }

    private static ResponseEntity<String> html(HttpStatus status, String body) {
        return ResponseEntity.status(status).contentType(TEXT_HTML_UTF8).body(body);
    }

    private static ResponseEntity<String> json(String body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static ResponseEntity<String> jsonError(String message) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"error\":\"" + message + "\"}");
    }
}
