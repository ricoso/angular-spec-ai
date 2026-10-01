package com.autohaus.backend.usage.insights;

import com.autohaus.backend.common.logging.JobTrace;
import com.autohaus.backend.common.util.Sleeper;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Assigns the operations of one booking to service groups (REQ-038 BR-11, E9) / ordnet die
 * Operationen einer Buchung Servicegruppen zu.
 *
 * <p><b>Rule per operation, in this order (BR-11) / Regel je Operation:</b>
 * <ol>
 *   <li>name contains „hol" (case-insensitive, same keyword as {@code OperationIdMapper}'s
 *       {@code pickup-delivery}) → {@link ServiceGroup#PICKUP} — before the tag rule, because
 *       „Hol &amp; Bringservice" hangs on all seven tags / vor der Tag-Regel;</li>
 *   <li>exactly one tag → group of the tag's {@code rank} 0–6 ({@link ServiceGroup#ofRank});</li>
 *   <li>two or more tags → {@link ServiceGroup#EXTRAS};</li>
 *   <li>no tag, a tag with an unknown rank or an operation id the group does not know →
 *       {@link ServiceGroup#UNKNOWN} („nicht zuordenbar").</li>
 * </ol>
 * A booking in a {@code tireChange} group always counts additionally as
 * {@link ServiceGroup#TIRE_CHANGE}. Every group appears at most once per booking; combinations are
 * never stored. A booking without any readable operation is {@code unknown} (or only
 * {@code tire-change} for a {@code tireChange} booking) — it still counts once. / Jede Gruppe
 * höchstens einmal je Buchung; ohne Operation „nicht zuordenbar".
 *
 * <p><b>Catalogue per groupId, cached {@value #CACHE_TTL_HOURS} h / Katalog je groupId:</b>
 * {@code GET /api/planning/tags} and {@code GET /api/planning/operations} with the
 * {@code x-nrg-group} header — at most two SoftNet calls per group occurring in the bookings.
 *
 * <p><b>Failures (E9) / Fehlerfälle:</b>
 * <ul>
 *   <li><b>Permanent refusal</b> — HTTP 401/403, an {@code AccessContextError} or any other
 *       non-retryable 4xx (known for the two Leverkusen tireChange groups, REQ-035): the
 *       operations of <b>this</b> group become {@code unknown}, one {@code WARN usage insights:
 *       group <groupId> not readable (403)} per cache period; the day is still written. /
 *       Dauerhafte Verweigerung: nur diese Group „nicht zuordenbar".</li>
 *   <li><b>Transient</b> — 5xx, 408/429, timeout, network or token failure: one retry after
 *       {@value #RETRY_DELAY_SECONDS} s; if it fails again {@link CatalogUnavailableException} is
 *       thrown so the <b>whole day</b> is not written and the next night catches it up — no
 *       silent inflation of {@code unknown}. / Vorübergehend: ein Retry, dann fällt der Tag aus.</li>
 * </ul>
 *
 * <p>SoftNet errors are logged by {@link SoftnetApiClient} as {@code SOFTNET_ERROR}; the rollup
 * runs under MDC {@code traceId=job.insightsrollup} so they never reach the Bugs tab (BR-15). If a
 * caller has no trace id yet, the catalogue load sets that marker itself for the duration of the
 * calls. / Läuft unter dem Job-Marker, damit Fehler nicht im Bugs-Tab erscheinen.
 */
@Component
public class ServiceGroupClassifier {

    private static final Logger log = LoggerFactory.getLogger(ServiceGroupClassifier.class);

    /** Hours a group catalogue stays cached / Cache-Dauer in Stunden. */
    public static final int CACHE_TTL_HOURS = 6;

    /** Cache lifetime of one group catalogue (§16) / Lebensdauer eines Katalog-Eintrags. */
    public static final Duration CACHE_TTL = Duration.ofHours(CACHE_TTL_HOURS);

    /** Seconds before the single retry of a transient failure (E9) / Wartezeit vor dem Retry. */
    public static final int RETRY_DELAY_SECONDS = 30;

    /** Delay before the single retry (E9) / Wartezeit vor dem einzigen Wiederholungsversuch. */
    public static final Duration RETRY_DELAY = Duration.ofSeconds(RETRY_DELAY_SECONDS);

    /**
     * Name fragment of „Hol &amp; Bringservice" — identical to the {@code pickup-delivery} keyword
     * of {@code OperationIdMapper} (§19 Risiken) / Schlüsselwort für Hol &amp; Bring.
     */
    public static final String PICKUP_KEYWORD = "hol";

    /** Variant key of the tire-change group (§10.2) / Varianten-Schlüssel der Räderwechsel-Group. */
    public static final String TIRE_CHANGE_VARIANT = "tireChange";

    /** SoftNet tag endpoint, read with the group header (like {@code JobTypesService}). */
    static final String TAGS_ENDPOINT = "/api/planning/tags";

    /** SoftNet error class of a refused group (REQ-035 spec.md:34) / Fehlerklasse „kein Zugriff". */
    static final String ACCESS_CONTEXT_ERROR = "AccessContextError";

    private final SoftnetApiClient softnetApi;
    private final Clock clock;
    private final Sleeper sleeper;
    private final Map<String, CachedCatalog> cache = new ConcurrentHashMap<>();

    @Autowired
    public ServiceGroupClassifier(SoftnetApiClient softnetApi) {
        this(softnetApi, Clock.systemUTC(), Sleeper.THREAD);
    }

    /**
     * Visible for testing / für Tests: pins the cache clock and replaces the 30-second wait. /
     * Fixiert die Cache-Uhr und ersetzt die 30-Sekunden-Pause.
     */
    ServiceGroupClassifier(SoftnetApiClient softnetApi, Clock clock, Sleeper sleeper) {
        this.softnetApi = softnetApi;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /**
     * Service groups of one booking (BR-11) / Servicegruppen einer Buchung.
     *
     * @param groupId the resolved SoftNet group (24 hex, from {@code setup.json}) / aufgelöste Group
     * @param operationIds the booked operation ids, already whitelisted / gebuchte Operationen
     * @param variant {@code default} or {@code tireChange} / Variante der Group
     * @return every group at most once, in {@link ServiceGroup} order; never empty / nie leer
     * @throws CatalogUnavailableException transient SoftNet failure after the retry (E9) — the
     *     caller must not write the day / vorübergehender Fehler nach dem Retry
     */
    public Set<ServiceGroup> classify(String groupId, List<String> operationIds, String variant) {
        EnumSet<ServiceGroup> groups = EnumSet.noneOf(ServiceGroup.class);
        Collection<String> ops = operationIds == null ? List.of() : new LinkedHashSet<>(operationIds);
        if (!ops.isEmpty()) {
            GroupCatalog catalog = groupId == null || groupId.isBlank()
                ? GroupCatalog.UNREADABLE : catalog(groupId);
            for (String operationId : ops) {
                groups.add(catalog.classify(operationId));
            }
        }
        if (TIRE_CHANGE_VARIANT.equals(variant)) {
            groups.add(ServiceGroup.TIRE_CHANGE);
        }
        if (groups.isEmpty()) {
            groups.add(ServiceGroup.UNKNOWN);
        }
        return Collections.unmodifiableSet(groups);
    }

    /** Drops every cached catalogue / leert den Cache (tests, manual reloads). */
    void clearCache() {
        cache.clear();
    }

    // ---- catalogue loading / Katalog laden ----

    private GroupCatalog catalog(String groupId) {
        Instant now = clock.instant();
        CachedCatalog cached = cache.get(groupId);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.catalog();
        }
        GroupCatalog loaded = loadWithRetry(groupId);
        cache.put(groupId, new CachedCatalog(loaded, now.plus(CACHE_TTL)));
        return loaded;
    }

    /**
     * Loads tags and operations once, retries a transient failure once after
     * {@value #RETRY_DELAY_SECONDS} s (E9) / lädt Tags und Operationen, ein Retry bei
     * vorübergehendem Fehler.
     */
    private GroupCatalog loadWithRetry(String groupId) {
        String previousTraceId = JobTrace.enterIfAbsent(InsightsScheduler.TRACE_ID);
        try {
            try {
                return load(groupId);
            } catch (RuntimeException first) {
                Integer status = permanentStatus(first);
                if (status != null) {
                    return refused(groupId, status);
                }
                log.warn("usage insights: group {} catalogue not loadable ({}) — retrying in {} s (E9)",
                    groupId, first.getClass().getSimpleName(), RETRY_DELAY_SECONDS);
            }
            waitBeforeRetry(groupId);
            try {
                return load(groupId);
            } catch (RuntimeException second) {
                Integer status = permanentStatus(second);
                if (status != null) {
                    return refused(groupId, status);
                }
                log.warn("usage insights: group {} catalogue still not loadable ({}) — the day is "
                    + "not written and caught up next night (E9)", groupId,
                    second.getClass().getSimpleName());
                throw new CatalogUnavailableException(
                    "Service-group catalogue of group " + groupId + " not loadable", second);
            }
        } finally {
            JobTrace.restore(previousTraceId);
        }
    }

    private GroupCatalog load(String groupId) {
        List<JsonNode> tags = softnetApi.getList(TAGS_ENDPOINT, groupId);
        List<JsonNode> operations = softnetApi.getOperations(groupId);
        return GroupCatalog.of(tags, operations);
    }

    private GroupCatalog refused(String groupId, int status) {
        log.warn("usage insights: group {} not readable ({}) — its operations count as unknown (E9)",
            groupId, status);
        return GroupCatalog.UNREADABLE;
    }

    private void waitBeforeRetry(String groupId) {
        try {
            sleeper.sleep(RETRY_DELAY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CatalogUnavailableException(
                "Interrupted while waiting to retry the catalogue of group " + groupId, e);
        }
    }

    /**
     * HTTP status of a <b>permanent</b> refusal, {@code null} for a transient failure / Status einer
     * dauerhaften Verweigerung, sonst {@code null}.
     *
     * <p>Permanent: 401, 403, an {@code AccessContextError} body, every other 4xx except 408/429
     * (retrying a malformed or unknown group changes nothing). Transient: 5xx, 408, 429 and every
     * non-HTTP failure (timeout, network, token). / Dauerhaft: 401/403/AccessContextError/4xx;
     * vorübergehend: 5xx/408/429/Netzwerk.
     */
    static Integer permanentStatus(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof RestClientResponseException response) {
                int status = response.getStatusCode().value();
                String body = response.getResponseBodyAsString();
                if (body != null && body.contains(ACCESS_CONTEXT_ERROR)) {
                    return status;
                }
                if (status == 401 || status == 403) {
                    return status;
                }
                boolean retryable = status >= 500 || status == 408 || status == 429;
                return retryable ? null : status;
            }
        }
        return null;
    }

    // ---- types / Typen ----

    /**
     * Thrown when a group catalogue stays unloadable after the retry (E9) — the caller does not
     * write the day. / Katalog auch nach dem Retry nicht ladbar; der Tag wird nicht geschrieben.
     */
    public static class CatalogUnavailableException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public CatalogUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record CachedCatalog(GroupCatalog catalog, Instant expiresAt) {}

    /** One operation of the catalogue / eine Operation des Katalogs. */
    private record Operation(String name, Set<String> tagIds) {}

    /**
     * Tags and operations of one group / Tags und Operationen einer Group. {@link #UNREADABLE}
     * classifies everything as {@code unknown} (permanent refusal, E9).
     */
    private record GroupCatalog(Map<String, Operation> operations, Map<String, Integer> tagRanks) {

        static final GroupCatalog UNREADABLE = new GroupCatalog(Map.of(), Map.of());

        static GroupCatalog of(List<JsonNode> tags, List<JsonNode> operations) {
            Map<String, Integer> ranks = new HashMap<>();
            for (JsonNode tag : tags == null ? List.<JsonNode>of() : tags) {
                String id = tag.path("id").asText("");
                if (!id.isBlank()) {
                    ranks.put(id, tag.path("rank").asInt(-1));
                }
            }
            Map<String, Operation> ops = new HashMap<>();
            for (JsonNode op : operations == null ? List.<JsonNode>of() : operations) {
                String id = op.path("id").asText("");
                if (id.isBlank()) {
                    continue;
                }
                Set<String> tagIds = new LinkedHashSet<>();
                JsonNode tagNodes = op.path("tags");
                if (tagNodes.isArray()) {
                    for (JsonNode tag : tagNodes) {
                        String tagId = tag.isTextual() ? tag.asText() : tag.path("id").asText("");
                        if (!tagId.isBlank()) {
                            tagIds.add(tagId);
                        }
                    }
                }
                ops.put(id, new Operation(op.path("name").asText(""), tagIds));
            }
            return new GroupCatalog(Map.copyOf(ops), Map.copyOf(ranks));
        }

        /** BR-11 for one operation / BR-11 für eine Operation. */
        ServiceGroup classify(String operationId) {
            Operation operation = operationId == null ? null : operations.get(operationId);
            if (operation == null) {
                return ServiceGroup.UNKNOWN;
            }
            if (operation.name().toLowerCase(Locale.ROOT).contains(PICKUP_KEYWORD)) {
                return ServiceGroup.PICKUP;
            }
            if (operation.tagIds().size() >= 2) {
                return ServiceGroup.EXTRAS;
            }
            if (operation.tagIds().size() == 1) {
                Integer rank = tagRanks.get(operation.tagIds().iterator().next());
                return rank == null ? ServiceGroup.UNKNOWN : ServiceGroup.ofRank(rank);
            }
            return ServiceGroup.UNKNOWN;
        }
    }
}
