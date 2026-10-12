package com.autohaus.backend.usage.insights;

import com.autohaus.backend.common.logging.JobTrace;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.config.setup.BranchNotFoundException;
import com.autohaus.backend.config.setup.BranchSetup;
import com.autohaus.backend.config.setup.GroupEntry;
import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.usage.UsageZone;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Collects the anonymous booking events and writes them once a day as shuffled
 * {@code USAGE_BOOKING {json}} lines (REQ-038 K2, §10.2, BR-6) / sammelt die anonymen
 * Buchungsereignisse und schreibt sie einmal täglich gemischt.
 *
 * <p><b>Decoupled from the booking (PO decision „ich will nie wissen, wer") / von der Buchung
 * entkoppelt:</b> {@link #logBooking} is called by {@code AppointmentsService} only after a
 * <b>successful</b> SoftNet booking and only while {@code usage.insights.enabled} (AC-13). It
 * validates and serialises the event but writes <b>no</b> log line — it only appends the JSON to a
 * thread-safe in-memory buffer (at most {@value #MAX_BUFFERED_EVENTS} events, further ones are
 * dropped with one {@code WARN} without values). {@link #flush} runs on the scheduler thread at
 * {@value #FLUSH_CRON} in {@code usage.zone} and on shutdown ({@code @PreDestroy}, so a restart or
 * deploy loses nothing); it shuffles the batch with {@link SecureRandom} and writes each event
 * under MDC {@code traceId=}{@value #TRACE_ID}. The line therefore shares neither the trace id nor
 * the millisecond nor the thread with {@code BOOKING_CREATED} (appointment id) and can no longer be
 * linked to one booking in the raw logs. Only a crash (no {@code @PreDestroy}) loses the events of
 * the running day. / Kein Log im Request; der Tagesflush mischt und schreibt unter dem Job-Marker,
 * ohne Trace-ID, Millisekunde oder Thread der Buchung. Nur ein Absturz verliert den laufenden Tag.
 *
 * <p>The marker {@value #TRACE_ID} lies outside the {@code X-Request-Id} whitelist of
 * {@code RequestIdFilter} (the dot), so no client request can produce the log prefix KQL 1 is
 * anchored on (§12.8). / Der Punkt liegt außerhalb der Whitelist — das Präfix ist nicht fälschbar.
 *
 * <p>The line is built exclusively from whitelist values:
 * <ul>
 *   <li>{@code day} — Berlin calendar day of the booking moment ({@link Clock}), ISO, no time; the
 *       rollup assigns the line by this field, not by its log timestamp;</li>
 *   <li>{@code branch} — {@code ^[a-f0-9]{24}$} <b>and</b> present in {@code setup.json}; otherwise
 *       no event at all and a {@code WARN} without values (E11 spirit);</li>
 *   <li>{@code group} — the resolved groupId, {@code ^[a-f0-9]{24}$}; otherwise no event;</li>
 *   <li>{@code variant} — variant of the {@code setup.json} group entry of this branch whose
 *       groupId equals the resolved one. {@code SetupService.resolveGroupId} falls back to the
 *       default group when no tireChange group exists, so the logged variant is always the one of
 *       the group actually booked (§5.5, TC-15);</li>
 *   <li>{@code ops} — {@code rawSnap.wishlist.operations} (plain string or {@code {"id": …}}),
 *       24 hex only, duplicates dropped, at most {@value UsageBookingEvent#MAX_OPS};</li>
 *   <li>{@code bookingDaypart} — the booking moment ({@link Clock}) in the configured zone;</li>
 *   <li>{@code slotWeekday}/{@code slotDaypart}/{@code leadTime} — {@code rawSnap.checkInTime}
 *       (UTC ISO) in the configured zone, {@code unknown} when missing or unparsable (E12);
 *       the lead time counts Berlin calendar days from booking day to slot day (BR-12).</li>
 * </ul>
 * / Die Zeile entsteht ausschließlich aus Whitelist-Werten; unbekannte Standorte oder Groups
 * erzeugen gar kein Ereignis, ungültige Operationen werden verworfen.
 *
 * <p><b>Never contained (BR-24):</b> appointment id, snap id / {@code internal}, slot date, any time
 * of day, brand, model, device, {@code sessionId}, {@code checkInResource}, customer data, free
 * text. The JSON is produced by an own Jackson mapper from {@link UsageBookingEvent} with a fixed
 * field order, so no global mapper setting (e.g. {@code non_null}) can reshape the line the KQL
 * parser relies on (§12.8 KQL 1). / Eigener Mapper mit fester Feldreihenfolge, unabhängig von der
 * globalen Jackson-Konfiguration.
 *
 * <p>Any {@link RuntimeException} of {@link #logBooking} is caught by the caller in its own
 * {@code try/catch}, so the booking response never changes (E10). / Fehler fängt der Aufrufer.
 */
@Component
public class UsageEventLogger {

    private static final Logger log = LoggerFactory.getLogger(UsageEventLogger.class);

    /** Marker in front of the JSON; KQL 1 filters on it (§12.8) / Marker vor dem JSON. */
    public static final String MARKER = "USAGE_BOOKING";

    /**
     * MDC marker of the daily flush — the log prefix KQL 1 is anchored on (§12.8, BR-15) /
     * MDC-Marker des Tagesflushs, auf dessen Präfix KQL 1 verankert ist.
     */
    public static final String TRACE_ID = "job.usagebooking";

    /** Flush time, every day 23:59:59 in {@code usage.zone} / Flush-Zeitpunkt, täglich 23:59:59. */
    public static final String FLUSH_CRON = "59 59 23 * * *";

    /**
     * Upper bound of buffered events (≈ 1 000 × the expected daily volume) — protects the heap;
     * further events of the period are dropped. / Obergrenze des Puffers; darüber wird verworfen.
     */
    public static final int MAX_BUFFERED_EVENTS = 10_000;

    /** Variant key of the default group (§10.2) / Varianten-Schlüssel der Default-Group. */
    public static final String VARIANT_DEFAULT = "default";

    /** Variant key of the tire-change group (§10.2, REQ-035) / Varianten-Schlüssel Radwechsel. */
    public static final String VARIANT_TIRE_CHANGE = "tireChange";

    /** SoftNet ObjectId: 24 lowercase hex / SoftNet-ObjectId, 24 Hex-Zeichen klein. */
    private static final Pattern HEX_24 = Pattern.compile("^[a-f0-9]{24}$");

    /**
     * Own mapper, independent of the global {@code ObjectMapper} bean (§10.2). The mix-in pins the
     * field order of the record; the enums serialise via their {@code @JsonValue} key. / Eigener
     * Mapper; der Mix-in fixiert die Feldreihenfolge, Enums erscheinen als Schlüssel.
     */
    private static final ObjectMapper LINE_MAPPER = new ObjectMapper()
        .addMixIn(UsageBookingEvent.class, FixedFieldOrder.class);

    private final SetupService setupService;
    private final Clock clock;
    private final ZoneId zone;

    /** Shuffles each batch before it is written / mischt jeden Stapel vor dem Schreiben. */
    private final Random shuffler;

    /** Guards {@link #buffer} and {@link #droppedEvents} / schützt Puffer und Zähler. */
    private final Object bufferLock = new Object();

    /** Serialised events since the last flush — never logged in the request / gepufferte Zeilen. */
    private List<String> buffer = new ArrayList<>();

    /** Events dropped at {@link #MAX_BUFFERED_EVENTS} since the last flush / verworfene Ereignisse. */
    private int droppedEvents;

    @Autowired
    public UsageEventLogger(SetupService setupService, UsageStatsProperties usageProperties) {
        this(setupService, Clock.system(UsageZone.resolve(usageProperties)), new SecureRandom());
    }

    /**
     * Visible for testing / für Tests: pins "now"; the clock's zone is the bucketing zone. /
     * Fixiert die Zeit; die Zone der Clock ist die Zone der Buckets.
     */
    UsageEventLogger(SetupService setupService, Clock clock) {
        this(setupService, clock, new SecureRandom());
    }

    /** Visible for testing / für Tests: additionally pins the shuffle / fixiert auch das Mischen. */
    UsageEventLogger(SetupService setupService, Clock clock, Random shuffler) {
        this.setupService = setupService;
        this.clock = clock;
        this.zone = clock.getZone();
        this.shuffler = shuffler;
    }

    /**
     * Buffers exactly one whitelisted event — or none when branch or group are not whitelisted
     * (then one {@code WARN} without values). Writes <b>no</b> {@code USAGE_BOOKING} line; that
     * happens in {@link #flush}. / Puffert genau ein Ereignis oder keines; schreibt keine Zeile.
     *
     * @param branchId branch of the booking ({@code dto.branchId()}) / Standort der Buchung
     * @param groupId groupId resolved by {@code SetupService.resolveGroupId} / aufgelöste Group
     * @param rawSnap the pristine SoftNet snap from the cache / unveränderter SoftNet-Snap
     * @param mobility whether a rental car was booked — snap requested with {@code replacementPreference}
     *     and reserved by SoftNet ({@code replacementResource}, REQ-039 BR-M12) / Mietwagen gebucht
     */
    public void logBooking(String branchId, String groupId, JsonNode rawSnap, boolean mobility) {
        buildEvent(branchId, groupId, rawSnap, mobility).ifPresent(event -> enqueue(toJson(event)));
    }

    /**
     * Daily flush at {@value #FLUSH_CRON} in {@code usage.zone} on the scheduler thread / täglicher
     * Flush auf dem Scheduler-Thread.
     */
    @Scheduled(cron = FLUSH_CRON, zone = "${usage.zone:Europe/Berlin}")
    public void scheduledFlush() {
        flush();
    }

    /**
     * Flush on a graceful shutdown (restart, deploy, scale-in) — only a crash loses the running
     * day / Flush beim geordneten Herunterfahren; nur ein Absturz verliert den laufenden Tag.
     */
    @PreDestroy
    public void flushOnShutdown() {
        flush();
    }

    /**
     * Empties the buffer and writes one INFO line {@code USAGE_BOOKING {json}} per event, in
     * shuffled order and under MDC {@code traceId=}{@value #TRACE_ID}; the previous MDC value is
     * restored (removed on the scheduler thread). Events dropped at the buffer limit are reported
     * as a count only. / Leert den Puffer, schreibt gemischt unter dem Job-Marker und stellt die
     * MDC wieder her; verworfene Ereignisse nur als Anzahl.
     *
     * @return the number of lines written / Anzahl geschriebener Zeilen
     */
    public int flush() {
        List<String> batch;
        int dropped;
        synchronized (bufferLock) {
            batch = buffer;
            dropped = droppedEvents;
            buffer = new ArrayList<>();
            droppedEvents = 0;
        }
        if (!batch.isEmpty()) {
            Collections.shuffle(batch, shuffler);
            String previousTraceId = JobTrace.enter(TRACE_ID);
            try {
                for (String json : batch) {
                    log.info("{} {}", MARKER, json);
                }
            } finally {
                JobTrace.restore(previousTraceId);
            }
        }
        if (dropped > 0) {
            // A count only — no value of any booking / nur die Anzahl, keine Werte.
            log.warn("{} flush: {} event(s) dropped at the buffer limit of {}", MARKER, dropped,
                MAX_BUFFERED_EVENTS);
        }
        return batch.size();
    }

    /** Events currently buffered (visible for testing) / aktuell gepufferte Ereignisse. */
    int bufferedEvents() {
        synchronized (bufferLock) {
            return buffer.size();
        }
    }

    /**
     * Appends one serialised event; at {@link #MAX_BUFFERED_EVENTS} it is dropped and the first
     * drop of the period raises one {@code WARN} without values. / Hängt an oder verwirft.
     */
    private void enqueue(String json) {
        boolean firstDrop;
        synchronized (bufferLock) {
            if (buffer.size() < MAX_BUFFERED_EVENTS) {
                buffer.add(json);
                return;
            }
            firstDrop = droppedEvents == 0;
            droppedEvents++;
        }
        if (firstDrop) {
            log.warn("{} buffer full ({} events) — further bookings are not recorded until the next flush",
                MARKER, MAX_BUFFERED_EVENTS);
        }
    }

    /**
     * Builds the whitelisted event, or empty when branch or group fail the whitelist (visible for
     * testing). / Baut das Ereignis aus Whitelist-Werten; leer bei unbekanntem Standort/Group.
     */
    Optional<UsageBookingEvent> buildEvent(String branchId, String groupId, JsonNode rawSnap,
                                           boolean mobility) {
        if (!isHex24(branchId)) {
            log.warn("{} skipped: branch id not whitelisted", MARKER);
            return Optional.empty();
        }
        BranchSetup branch = findBranch(branchId);
        if (branch == null) {
            log.warn("{} skipped: branch not in setup", MARKER);
            return Optional.empty();
        }
        if (!isHex24(groupId)) {
            log.warn("{} skipped: group id not whitelisted", MARKER);
            return Optional.empty();
        }

        JsonNode snap = rawSnap == null ? MissingNode.getInstance() : rawSnap;
        Instant now = clock.instant();
        Instant slot = parseSlotInstant(snap.path("checkInTime"));
        LocalDate bookingDay = LocalDate.ofInstant(now, zone);
        LocalDate slotDay = slot == null ? null : LocalDate.ofInstant(slot, zone);

        return Optional.of(new UsageBookingEvent(
            UsageBookingEvent.FORMAT_VERSION,
            DateTimeFormatter.ISO_LOCAL_DATE.format(bookingDay),
            branchId,
            groupId,
            variantOf(branch, groupId),
            operationIds(snap.path("wishlist").path("operations")),
            BookingDaypart.of(now, zone),
            SlotWeekday.of(slot, zone),
            SlotDaypart.of(slot, zone),
            LeadTimeBucket.of(bookingDay, slotDay),
            mobility));
    }

    /**
     * Serialises the event with the fixed field order of §10.2 (visible for testing) / serialisiert
     * mit fester Feldreihenfolge.
     */
    static String toJson(UsageBookingEvent event) {
        try {
            return LINE_MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // No values in the message — the caller logs the class name only (E10).
            // Keine Werte in der Meldung; der Aufrufer loggt nur den Klassennamen.
            throw new IllegalStateException("USAGE_BOOKING serialisation failed");
        }
    }

    // ---- whitelist helpers / Whitelist-Hilfen ----

    private BranchSetup findBranch(String branchId) {
        try {
            return setupService.findByBranchId(branchId);
        } catch (BranchNotFoundException e) {
            return null;
        }
    }

    /**
     * Variant of the group entry matching the resolved group; {@code default} when no entry
     * matches or the match is ambiguous (fallback-safe, §5.5). / Variante des passenden
     * Group-Eintrags, sonst {@code default}.
     */
    private static String variantOf(BranchSetup branch, String groupId) {
        boolean tireChange = false;
        List<GroupEntry> groups = branch.groups() == null ? List.of() : branch.groups();
        for (GroupEntry group : groups) {
            if (group == null || !groupId.equals(group.groupId())) {
                continue;
            }
            if (!VARIANT_TIRE_CHANGE.equals(group.variant())) {
                return VARIANT_DEFAULT;
            }
            tireChange = true;
        }
        return tireChange ? VARIANT_TIRE_CHANGE : VARIANT_DEFAULT;
    }

    /**
     * Operation ids of the wishlist: string entries or objects with {@code id}; only 24 hex, in
     * order, without duplicates, at most {@value UsageBookingEvent#MAX_OPS}. / Nur gültige IDs,
     * höchstens 20.
     */
    private static List<String> operationIds(JsonNode operations) {
        if (!operations.isArray()) {
            return List.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode entry : operations) {
            if (ids.size() >= UsageBookingEvent.MAX_OPS) {
                break;
            }
            JsonNode idNode = entry.isObject() ? entry.path("id") : entry;
            if (idNode.isTextual() && isHex24(idNode.asText())) {
                ids.add(idNode.asText());
            }
        }
        return List.copyOf(ids);
    }

    /**
     * {@code checkInTime} as instant — ISO-8601 with {@code Z} or an explicit offset
     * ({@link Instant#parse} accepts both); {@code null} when missing, not textual or unparsable
     * (E12). / Slot-Zeit oder {@code null}.
     */
    private static Instant parseSlotInstant(JsonNode checkInTime) {
        if (!checkInTime.isTextual() || checkInTime.asText().isBlank()) {
            return null;
        }
        try {
            return Instant.parse(checkInTime.asText().trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean isHex24(String value) {
        return value != null && HEX_24.matcher(value).matches();
    }

    /**
     * Jackson mix-in fixing the field order of the line (§10.2) — the record itself stays free of
     * serialisation concerns. / Mix-in für die feste Feldreihenfolge.
     */
    @JsonPropertyOrder({"v", "day", "branch", "group", "variant", "ops", "bookingDaypart", "slotWeekday",
        "slotDaypart", "leadTime", "mobility"})
    private abstract static class FixedFieldOrder {
    }
}
