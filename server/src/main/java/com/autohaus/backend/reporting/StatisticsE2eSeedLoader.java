package com.autohaus.backend.reporting;

import com.autohaus.backend.capacity.CapacityBlobClient;
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
import com.autohaus.backend.usage.UsageZone;
import com.autohaus.backend.usage.insights.BranchCounts;
import com.autohaus.backend.usage.insights.InsightsBlobClient;
import com.autohaus.backend.usage.insights.InsightsHistory;
import com.autohaus.backend.usage.insights.InsightsStore;
import com.autohaus.backend.usage.insights.InsightsViewModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Seeds the statistics tab with fixture data for E2E runs and screenshots (REQ-038 §14.1, §14.2,
 * TC-41–TC-44) / befüllt den Statistik-Tab für E2E-Läufe und Screenshots mit Fixture-Daten.
 *
 * <p>Locally there is neither Blob Storage nor Log Analytics, so without this loader S0–S4 and S6
 * could only ever show „Historie derzeit nicht verfügbar" (E4). In the {@code e2e-seed} profile it
 * reads {@code playwright/fixtures/req038/<set>/usage-insights.json} and
 * {@code capacity-daily.json}, replaces the relative date tokens, parses them with the
 * <b>same</b> tolerant parsers as production ({@link InsightsBlobClient#parse},
 * {@link CapacityBlobClient#parse}), builds the view models with the real switches and puts them
 * into {@link InsightsStore} and {@link CapacityStore}. / Lokal gibt es weder Blob noch Log
 * Analytics; das Profil lädt die Fixtures über dieselben Parser in die Stores.
 *
 * <p><b>Guards / Schutz:</b> the start is refused ({@link IllegalStateException}) while
 * {@code USAGE_STORAGE_ACCOUNT} is set — fantasy numbers must never appear next to real ones (§19
 * risks) — and for an unknown {@code E2E_SEED_SET} ({@code full} | {@code thin} | {@code fail},
 * default {@code full}). An unusable fixture also stops the start: a green E2E run on an empty
 * page would prove nothing. {@code UsageStatsStore} (REQ-037) is deliberately <b>not</b> touched —
 * the REQ-037 spec expects empty periods ({@code .us-empty == 2}). / Start verweigert bei gesetztem
 * Storage-Account, unbekanntem Set oder unbrauchbarer Fixture; die REQ-037-Statistik bleibt leer.
 *
 * <p><b>Date tokens / Datums-Tokens</b> (keys, values and inside timestamps such as
 * {@code "@wd-1T20:31:12Z"}), all relative to the Berlin calendar day of the start:
 * <ul>
 *   <li>{@code @day}, {@code @day-N}, {@code @day+N} → ISO date today ± N days</li>
 *   <li>{@code @wd-N}, {@code @wd+N} → Mo–Sa workday index: {@code @wd-0} is the first Mo–Sa day
 *       ≥ today, {@code @wd-1} the last Mo–Sa day before today (scan days, target days)</li>
 *   <li>{@code @wk-N-DD}, {@code @wk+N-DD} → weekday {@code DD} ({@code MO}…{@code SU}) of the
 *       ISO week N weeks before/after the current one — keeps every day row on its weekday, so the
 *       weekday-dependent counters ({@code offHours}) stay consistent with the global rows</li>
 *   <li>{@code @month}, {@code @month-N} → {@code yyyy-MM}; {@code @year}, {@code @year-N} →
 *       {@code yyyy}</li>
 * </ul>
 * Week tokens overshoot on purpose, therefore day rows from today on and before the collecting
 * start are dropped after parsing (a real blob never holds them, BR-19), as are scans from today
 * on. An optional fixture field {@code "e2eSeed": {"lastDay": "@day-2"}} — ignored by the
 * production parser — ends the day rows earlier to reproduce a pending rollup (E3). / Wochen-Tokens
 * schießen über; Tage ab heute bzw. vor dem Erfassungsbeginn werden verworfen.
 *
 * <p>The fixtures are generated deterministically by {@code scripts/gen-req038-fixtures.mjs}, which
 * checks every counter invariant before writing — regenerate instead of editing them by hand. /
 * Die Fixtures erzeugt {@code scripts/gen-req038-fixtures.mjs}; nicht von Hand bearbeiten.
 *
 * <p><b>Order / Reihenfolge:</b> seeding runs on {@link ApplicationReadyEvent} with the lowest
 * precedence, i.e. after the startup runs of {@code InsightsScheduler} and
 * {@code CapacityScanScheduler}, which would otherwise publish their „Historie derzeit nicht
 * verfügbar" views. Listeners with equal order are called in bean registration order, so the
 * readiness event that Spring Boot publishes right after {@code ApplicationReadyEvent} re-applies
 * the seed once if a startup listener still replaced it. / Läuft nach den Startläufen der
 * Scheduler; das Readiness-Event setzt den Seed notfalls einmal erneut.
 */
@Component
@Profile(StatisticsE2eSeedLoader.PROFILE)
public class StatisticsE2eSeedLoader {

    private static final Logger log = LoggerFactory.getLogger(StatisticsE2eSeedLoader.class);

    /** Spring profile that activates the loader (§14.2) / aktivierendes Profil. */
    public static final String PROFILE = "e2e-seed";

    /** Seed set used when {@code E2E_SEED_SET} is empty / Standard-Set. */
    public static final String DEFAULT_SET = "full";

    /** Valid values of {@code E2E_SEED_SET} / gültige Seed-Sets. */
    public static final List<String> SEED_SETS = List.of("full", "thin", "fail");

    /** Fixture file of the insights history (§10.6) / Fixture der Insights-Historie. */
    public static final String INSIGHTS_FILE = InsightsBlobClient.DEFAULT_BLOB;

    /** Fixture file of the capacity history (§10.6) / Fixture der Kapazitäts-Historie. */
    public static final String CAPACITY_FILE = CapacityBlobClient.DEFAULT_BLOB;

    /** Fixture root relative to the repository root / Fixture-Wurzel relativ zum Repo. */
    static final String FIXTURE_SUBDIR = "playwright/fixtures/req038";

    /**
     * Parent directories searched for {@link #FIXTURE_SUBDIR} — {@code bootRun} runs in
     * {@code server/}, the repository root is one level up / durchsuchte Elternverzeichnisse.
     */
    static final int MAX_PARENT_LEVELS = 3;

    private static final Pattern TOKEN = Pattern.compile(
        "@(?:(day|wd|month|year)([+-]\\d{1,4})?|wk([+-]\\d{1,4})-(MO|TU|WE|TH|FR|SA|SU))");

    /** Anything that still looks like a token after the replacement is malformed / fehlerhaft. */
    private static final Pattern LEFTOVER_TOKEN = Pattern.compile("@(?:day|wd|wk|month|year)");

    private static final Map<String, DayOfWeek> WEEKDAY_KEYS = Map.of(
        "MO", DayOfWeek.MONDAY, "TU", DayOfWeek.TUESDAY, "WE", DayOfWeek.WEDNESDAY,
        "TH", DayOfWeek.THURSDAY, "FR", DayOfWeek.FRIDAY, "SA", DayOfWeek.SATURDAY,
        "SU", DayOfWeek.SUNDAY);

    private static final ObjectMapper DIRECTIVE_MAPPER = new ObjectMapper();

    private final String seedSet;
    private final Path setDirectory;
    private final boolean bookingsEnabled;
    private final boolean funnelEnabled;
    private final boolean dropoffEnabled;
    private final boolean capacityEnabled;
    private final boolean showSlotCount;
    private final Supplier<Map<String, String>> branchNames;
    private final InsightsStore insightsStore;
    private final CapacityStore capacityStore;
    private final Clock clock;
    private final Parsers parsers;

    private final AtomicBoolean reapplied = new AtomicBoolean(false);
    private volatile InsightsViewModel seededInsights;
    private volatile InsightsHistory seededInsightsHistory;
    private volatile CapacityViewModel seededCapacity;
    private volatile CapacityHistory seededCapacityHistory;

    /**
     * Spring constructor / Spring-Konstruktor. Validates everything up front, so a refused start
     * happens before the web server accepts a single request. / Prüft vor dem ersten Request.
     *
     * @param seedSet {@code E2E_SEED_SET} ({@code full} | {@code thin} | {@code fail})
     * @param seedDir {@code E2E_SEED_DIR} — optional directory that holds the set folders; empty =
     *     search {@value #FIXTURE_SUBDIR} from the working directory upwards
     */
    @Autowired
    public StatisticsE2eSeedLoader(@Value("${e2e.seed.set:" + DEFAULT_SET + "}") String seedSet,
                                   @Value("${e2e.seed.dir:}") String seedDir,
                                   UsageStatsProperties usageProperties,
                                   UsageInsightsProperties insightsProperties,
                                   UsageFunnelProperties funnelProperties,
                                   UsageDropoffProperties dropoffProperties,
                                   CapacityProperties capacityProperties,
                                   SetupService setupService,
                                   InsightsStore insightsStore,
                                   CapacityStore capacityStore) {
        this(seedSet, seedDir, Path.of(System.getProperty("user.dir", ".")), usageProperties,
            insightsProperties, funnelProperties, dropoffProperties, capacityProperties,
            () -> BranchNames.byId(setupService), insightsStore, capacityStore,
            Clock.system(UsageZone.resolve(usageProperties)), Parsers.SHARED);
    }

    /**
     * Visible for testing / für Tests: pins the working directory, the branch names, "now" and
     * the parsers. / Fixiert Arbeitsverzeichnis, Standortnamen, Zeit und Parser.
     */
    StatisticsE2eSeedLoader(String seedSet, String seedDir, Path workingDirectory,
                            UsageStatsProperties usageProperties,
                            UsageInsightsProperties insightsProperties,
                            UsageFunnelProperties funnelProperties,
                            UsageDropoffProperties dropoffProperties,
                            CapacityProperties capacityProperties,
                            Supplier<Map<String, String>> branchNames,
                            InsightsStore insightsStore, CapacityStore capacityStore,
                            Clock clock, Parsers parsers) {
        String account = usageProperties == null || usageProperties.storage() == null
            ? null : usageProperties.storage().account();
        if (account != null && !account.isBlank()) {
            // §19 risks: the seed profile must never run next to real statistics data.
            throw refuse("USAGE_STORAGE_ACCOUNT is set — the seed profile is for local E2E runs "
                + "only and never runs against a real storage account");
        }
        this.seedSet = normalizeSeedSet(seedSet);
        Path fixtureRoot = resolveFixtureRoot(seedDir, workingDirectory);
        this.setDirectory = fixtureRoot.resolve(this.seedSet);
        if (!Files.isDirectory(setDirectory)) {
            throw refuse("seed set directory " + setDirectory + " does not exist");
        }
        boolean usageEnabled = usageProperties != null && usageProperties.enabled();
        this.bookingsEnabled = insightsProperties != null && insightsProperties.enabled();
        // §5.1: the funnel only works together with usage.enabled, drop-off only inside the funnel.
        this.funnelEnabled = usageEnabled && funnelProperties != null && funnelProperties.enabled();
        this.dropoffEnabled = funnelEnabled && dropoffProperties != null && dropoffProperties.enabled();
        this.capacityEnabled = capacityProperties != null && capacityProperties.enabled();
        this.showSlotCount = capacityProperties != null && capacityProperties.showSlotCount();
        this.branchNames = branchNames;
        this.insightsStore = insightsStore;
        this.capacityStore = capacityStore;
        this.clock = clock;
        this.parsers = parsers;
        log.warn("REQ-038 e2e-seed profile active — the statistics tab shows FIXTURE data from {} "
            + "(never enable this profile in production)", setDirectory);
    }

    // ---- events / Ereignisse ----

    /**
     * Seeds after all startup runs (lowest precedence) / befüllt nach allen Startläufen. An
     * exception here aborts the start on purpose (unusable fixture). / Ein Fehler bricht den Start
     * bewusst ab.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void onApplicationReady() {
        seed();
    }

    /**
     * Re-applies the seed once when the application starts accepting traffic — Spring Boot
     * publishes this right after {@link ApplicationReadyEvent}, so it also wins against a startup
     * listener that happened to run after the seed despite the ordering. / Setzt den Seed einmal
     * erneut, falls ein Startlauf ihn trotz Reihenfolge überschrieben hat.
     */
    @EventListener
    public void onReadinessChange(AvailabilityChangeEvent<ReadinessState> event) {
        if (event.getState() == ReadinessState.ACCEPTING_TRAFFIC && reapplied.compareAndSet(false, true)) {
            reapplyIfReplaced();
        }
    }

    // ---- seeding / Befüllen ----

    /**
     * Loads the fixtures of the configured set into the stores / lädt die Fixtures in die Stores.
     * Only sources that are switched on are seeded — a switched-off source keeps its store empty,
     * exactly like production (BR-4). / Nur aktive Quellen werden befüllt.
     *
     * @throws IllegalStateException when a fixture is missing or unusable / bei fehlender oder
     *     unbrauchbarer Fixture
     */
    public synchronized void seed() {
        LocalDate today = LocalDate.now(clock);
        Map<String, String> names = branchNames == null ? Map.of() : branchNames.get();
        if (!bookingsEnabled && !funnelEnabled && !capacityEnabled) {
            log.warn("REQ-038 e2e-seed: all statistics sources are switched off — nothing seeded");
            return;
        }
        if (bookingsEnabled || funnelEnabled) {
            InsightsHistory history = loadInsights(today);
            InsightsViewModel view = InsightsViewModel.build(history, names, today,
                bookingsEnabled, funnelEnabled, dropoffEnabled);
            seededInsightsHistory = history;
            seededInsights = view;
            insightsStore.update(view, history);
            log.info("REQ-038 e2e-seed '{}': insights seeded — {} day(s) {}…{} (bookings={}, "
                + "funnel={}, dropoff={})", seedSet, history.days().size(), firstKey(history.days()),
                lastKey(history.days()), bookingsEnabled, funnelEnabled, dropoffEnabled);
        } else {
            log.info("REQ-038 e2e-seed '{}': insights sources off — {} not loaded", seedSet,
                INSIGHTS_FILE);
        }
        if (capacityEnabled) {
            CapacityHistory history = loadCapacity(today);
            CapacityViewModel view = CapacityViewModel.build(history, names, showSlotCount, today,
                null);
            seededCapacityHistory = history;
            seededCapacity = view;
            capacityStore.update(view, history);
            log.info("REQ-038 e2e-seed '{}': capacity seeded — {} scan(s) {}…{}", seedSet,
                history.scans().size(), firstKey(history.scans()), lastKey(history.scans()));
        } else {
            log.info("REQ-038 e2e-seed '{}': capacity scan off — {} not loaded", seedSet,
                CAPACITY_FILE);
        }
    }

    /** Puts the seeded views back if someone replaced them / setzt die Seed-Sichten erneut. */
    synchronized void reapplyIfReplaced() {
        InsightsViewModel insights = seededInsights;
        if (insights != null && insightsStore.current() != insights) {
            insightsStore.update(insights, seededInsightsHistory);
            log.info("REQ-038 e2e-seed: insights view re-applied after a startup listener replaced it");
        }
        CapacityViewModel capacity = seededCapacity;
        if (capacity != null && capacityStore.current() != capacity) {
            capacityStore.update(capacity, seededCapacityHistory);
            log.info("REQ-038 e2e-seed: capacity view re-applied after a startup listener replaced it");
        }
    }

    private InsightsHistory loadInsights(LocalDate today) {
        Path file = setDirectory.resolve(INSIGHTS_FILE);
        String json = resolveTokens(read(file), today);
        InsightsBlobClient.Loaded loaded = parseFixture(() -> parsers.insights().apply(json), file);
        if (loaded == null || !loaded.usable() || loaded.history() == null) {
            throw refuse("fixture " + file + " is unusable (E6) — see the ERROR line of the parser");
        }
        return trimInsights(loaded.history(), lastDay(json, today, file), today);
    }

    private CapacityHistory loadCapacity(LocalDate today) {
        Path file = setDirectory.resolve(CAPACITY_FILE);
        String json = resolveTokens(read(file), today);
        CapacityBlobClient.Loaded loaded = parseFixture(() -> parsers.capacity().apply(json), file);
        if (loaded == null || !loaded.usable() || loaded.history() == null) {
            throw refuse("fixture " + file + " is unusable (E6) — see the ERROR line of the parser");
        }
        return trimCapacity(loaded.history(), today);
    }

    // ---- trimming / Zuschneiden ----

    /**
     * Keeps the day rows within [collecting start, lastDay] and only closed months / behält nur
     * Tage im Erfassungsfenster und nur abgeschlossene Monate.
     */
    static InsightsHistory trimInsights(InsightsHistory history, LocalDate lastDay, LocalDate today) {
        LocalDate from = earliest(history.collectingSince(), history.funnelCollectingSince());
        SortedMap<YearMonth, Map<String, BranchCounts>> months = new TreeMap<>();
        if (history.branchMonths() != null) {
            months.putAll(history.branchMonths().headMap(YearMonth.from(today)));
        }
        return new InsightsHistory(history.schemaVersion(), history.updatedAt(),
            history.collectingSince(), history.funnelCollectingSince(),
            history.dropoffCollectingSince(),
            window(history.days(), from, lastDay),
            window(history.branchDays(), from, lastDay),
            Collections.unmodifiableSortedMap(months));
    }

    /** Keeps the scans before today / behält die Scans vor heute. */
    static CapacityHistory trimCapacity(CapacityHistory history, LocalDate today) {
        return new CapacityHistory(history.schemaVersion(), history.referenceService(),
            window(history.scans(), null, today.minusDays(1)));
    }

    /** Copy of {@code source} restricted to [from, to] (both inclusive, {@code from} optional). */
    static <V> SortedMap<LocalDate, V> window(SortedMap<LocalDate, V> source, LocalDate from,
                                             LocalDate to) {
        TreeMap<LocalDate, V> copy = new TreeMap<>();
        if (source != null) {
            source.forEach((day, value) -> {
                if ((from == null || !day.isBefore(from)) && !day.isAfter(to)) {
                    copy.put(day, value);
                }
            });
        }
        return Collections.unmodifiableSortedMap(copy);
    }

    /**
     * Last day row to keep: yesterday, or the earlier {@code e2eSeed.lastDay} of the fixture /
     * letzter Tag: gestern bzw. die frühere Direktive der Fixture.
     */
    static LocalDate lastDay(String resolvedJson, LocalDate today, Path file) {
        LocalDate yesterday = today.minusDays(1);
        JsonNode directive;
        try {
            directive = DIRECTIVE_MAPPER.readTree(resolvedJson).path("e2eSeed").path("lastDay");
        } catch (JsonProcessingException e) {
            throw refuse("fixture " + file + " is not valid JSON: " + e.getOriginalMessage());
        }
        if (directive.isMissingNode() || directive.isNull()) {
            return yesterday;
        }
        try {
            LocalDate requested = LocalDate.parse(directive.asText());
            return requested.isBefore(yesterday) ? requested : yesterday;
        } catch (DateTimeParseException e) {
            throw refuse("fixture " + file + ": e2eSeed.lastDay is not an ISO date");
        }
    }

    // ---- tokens / Datums-Tokens ----

    /**
     * Replaces every relative date token (see the class comment) / ersetzt alle Datums-Tokens.
     *
     * @throws IllegalStateException for a malformed token / bei fehlerhaftem Token
     */
    static String resolveTokens(String text, LocalDate today) {
        Matcher matcher = TOKEN.matcher(text);
        StringBuilder resolved = new StringBuilder(text.length());
        while (matcher.find()) {
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(resolveToken(matcher, today)));
        }
        matcher.appendTail(resolved);
        String result = resolved.toString();
        Matcher leftover = LEFTOVER_TOKEN.matcher(result);
        if (leftover.find()) {
            int end = Math.min(result.length(), leftover.start() + 16);
            throw refuse("malformed date token '" + result.substring(leftover.start(), end) + "…'");
        }
        return result;
    }

    private static String resolveToken(Matcher matcher, LocalDate today) {
        String unit = matcher.group(1);
        if (unit == null) {
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            DayOfWeek weekday = WEEKDAY_KEYS.get(matcher.group(4));
            return monday.plusWeeks(Integer.parseInt(matcher.group(3)))
                .plusDays(weekday.getValue() - 1L).toString();
        }
        int offset = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        return switch (unit) {
            case "day" -> today.plusDays(offset).toString();
            case "wd" -> workday(today, offset).toString();
            case "month" -> YearMonth.from(today).plusMonths(offset).toString();
            default -> Integer.toString(today.getYear() + offset);
        };
    }

    /**
     * Mo–Sa workday by index (BR-26: working days Mon–Sat) / Werktag Mo–Sa nach Index. Index 0 is
     * the first workday on or after today, negative indexes count back, positive ones forward. /
     * Index 0 = erster Werktag ab heute.
     */
    static LocalDate workday(LocalDate today, int index) {
        LocalDate day = today.getDayOfWeek() == DayOfWeek.SUNDAY ? today.plusDays(1) : today;
        int step = Integer.signum(index);
        for (int remaining = Math.abs(index); remaining > 0; ) {
            day = day.plusDays(step);
            if (day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                remaining--;
            }
        }
        return day;
    }

    // ---- configuration / Konfiguration ----

    /** Trimmed, lower-case set name; empty → {@value #DEFAULT_SET} / normalisierter Set-Name. */
    static String normalizeSeedSet(String seedSet) {
        String normalized = seedSet == null || seedSet.isBlank()
            ? DEFAULT_SET : seedSet.trim().toLowerCase(Locale.ROOT);
        if (!SEED_SETS.contains(normalized)) {
            throw refuse("unknown E2E_SEED_SET '" + seedSet + "' — expected one of "
                + String.join(" | ", SEED_SETS));
        }
        return normalized;
    }

    /**
     * The directory that holds the set folders / Verzeichnis mit den Set-Ordnern: {@code seedDir}
     * when given (absolute or relative to the working directory), else {@value #FIXTURE_SUBDIR} in
     * the working directory or one of its {@value #MAX_PARENT_LEVELS} parents.
     */
    static Path resolveFixtureRoot(String seedDir, Path workingDirectory) {
        Path base = workingDirectory.toAbsolutePath().normalize();
        if (seedDir != null && !seedDir.isBlank()) {
            Path explicit = base.resolve(seedDir.trim()).normalize();
            if (!Files.isDirectory(explicit)) {
                throw refuse("E2E_SEED_DIR " + explicit + " is not a directory");
            }
            return explicit;
        }
        Path directory = base;
        for (int level = 0; level <= MAX_PARENT_LEVELS && directory != null; level++) {
            Path candidate = directory.resolve(FIXTURE_SUBDIR);
            if (Files.isDirectory(candidate)) {
                return candidate.normalize();
            }
            directory = directory.getParent();
        }
        throw refuse("fixtures not found — looked for " + FIXTURE_SUBDIR + " in " + base
            + " and up to " + MAX_PARENT_LEVELS + " parent directories; set E2E_SEED_DIR");
    }

    /** The active seed set / aktives Seed-Set. */
    String seedSet() {
        return seedSet;
    }

    /** The directory the fixtures are read from / Quellverzeichnis der Fixtures. */
    Path setDirectory() {
        return setDirectory;
    }

    // ---- helpers / Hilfsmethoden ----

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw refuse("fixture " + file + " not readable: " + e.getMessage());
        }
    }

    private static <T> T parseFixture(Supplier<T> parse, Path file) {
        try {
            return parse.get();
        } catch (RuntimeException e) {
            throw new IllegalStateException("REQ-038 e2e-seed: parsing " + file + " failed: "
                + e.getMessage(), e);
        }
    }

    private static LocalDate earliest(LocalDate first, LocalDate second) {
        if (first == null) {
            return second;
        }
        return second == null || first.isBefore(second) ? first : second;
    }

    private static Object firstKey(SortedMap<LocalDate, ?> map) {
        return map.isEmpty() ? "—" : map.firstKey();
    }

    private static Object lastKey(SortedMap<LocalDate, ?> map) {
        return map.isEmpty() ? "—" : map.lastKey();
    }

    private static IllegalStateException refuse(String message) {
        return new IllegalStateException("REQ-038 e2e-seed: " + message);
    }

    /**
     * The fixture parsers — production uses the shared tolerant blob parsers (§14.1), tests may
     * stub them / die Fixture-Parser; produktiv die gemeinsamen toleranten Blob-Parser.
     *
     * @param insights parser of {@code usage-insights.json}
     * @param capacity parser of {@code capacity-daily.json}
     */
    record Parsers(Function<String, InsightsBlobClient.Loaded> insights,
                   Function<String, CapacityBlobClient.Loaded> capacity) {

        /** The production parsers / die produktiven Parser. */
        static final Parsers SHARED = new Parsers(
            json -> InsightsBlobClient.parse(json, null),
            json -> CapacityBlobClient.parse(json, null));
    }
}
