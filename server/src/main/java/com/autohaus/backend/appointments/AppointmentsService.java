package com.autohaus.backend.appointments;

import com.autohaus.backend.appointments.dto.AppointmentResponseDto;
import com.autohaus.backend.appointments.dto.CreateAppointmentRequest;
import com.autohaus.backend.common.logging.LogSanitizer;
import com.autohaus.backend.common.util.PhoneNumberNormalizer;
import com.autohaus.backend.config.UsageInsightsProperties;
import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.snaps.SnapCache;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.autohaus.backend.usage.insights.UsageEventLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AppointmentsService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentsService.class);

    /** SoftNet spec: rent category ids are exactly 24 chars. */
    private static final int RENT_CATEGORY_ID_LENGTH = 24;

    /** Reserved rental object fields logged in {@code BOOKING_CREATED} (no PII). */
    private static final List<String> MOBILITY_LOG_FIELDS = List.of("isFleet", "fleetType", "rentCategoryid");

    /** SoftNet spec: wishlist.note holds at most 2000 chars. */
    static final int NOTE_MAX_LENGTH = 2000;

    /**
     * REQ-039 BR-M5: fixed German prefix for the dealership — the category is only a wish, the
     * dealership confirms it. Not a UI text, so not part of the i18n catalogue.
     */
    static final String RENTAL_WISH_PREFIX = "Mietwagen-Wunsch: ";

    /** SoftNet placeholder when the customer leaves the optional VIN empty (unchanged since REQ-009). */
    static final String VIN_UNKNOWN = "UNKNOWN";

    private final SoftnetApiClient softnetApi;
    private final SnapCache snapCache;
    private final ObjectMapper objectMapper;
    private final SetupService setupService;

    /**
     * REQ-038 K2: collector of the anonymous booking events, {@code null} = off. It only buffers;
     * the {@code USAGE_BOOKING} lines are written by its daily shuffled flush, decoupled from this
     * request. / Sammelt nur; die Zeilen schreibt der tägliche, gemischte Flush.
     */
    private final UsageEventLogger usageEventLogger;

    /**
     * REQ-038 kill switch {@code usage.insights.enabled} (AC-13, AC-31): with {@code false} no
     * booking event is buffered and hence no {@code USAGE_BOOKING} line is ever written — nothing
     * is collected "just in case". / Kill-Switch: bei {@code false} wird nichts gepuffert.
     */
    private final boolean usageInsightsEnabled;

    /**
     * Pre-REQ-038 constructor, kept for existing callers and tests / Konstruktor wie vor REQ-038:
     * the booking insights stay off. / Die Buchungs-Insights bleiben aus.
     */
    public AppointmentsService(SoftnetApiClient softnetApi, SnapCache snapCache, ObjectMapper objectMapper,
                               SetupService setupService) {
        this(softnetApi, snapCache, objectMapper, setupService, null, null);
    }

    @Autowired
    public AppointmentsService(SoftnetApiClient softnetApi, SnapCache snapCache, ObjectMapper objectMapper,
                               SetupService setupService, UsageEventLogger usageEventLogger,
                               UsageInsightsProperties usageInsightsProperties) {
        this.softnetApi = softnetApi;
        this.snapCache = snapCache;
        this.objectMapper = objectMapper;
        this.setupService = setupService;
        this.usageEventLogger = usageEventLogger;
        this.usageInsightsEnabled = usageEventLogger != null
            && usageInsightsProperties != null && usageInsightsProperties.enabled();
    }

    public AppointmentResponseDto createAppointment(CreateAppointmentRequest dto) {
        JsonNode rawSnap = snapCache.getRawSnap(dto.snapId());
        if (rawSnap == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Snap \"" + dto.snapId() + "\" not found in cache. The appointment slot may have expired. Please select a new time slot.");
        }

        String groupId = setupService.resolveGroupId(dto.branchId(), dto.variant());

        // Patch a copy so the pristine SoftNet snap stays available for error logging.
        ObjectNode snap = ((ObjectNode) rawSnap).deepCopy();
        patchSnapWithBookingData(snap, dto, rentalWishName(dto, groupId, rawSnap),
            resolveRentCategoryId(dto, groupId, rawSnap));

        String branchLabel = "";
        JsonNode branchNode = snap.path("branch");
        if (!branchNode.isMissingNode()) {
            branchLabel = branchNode.path("branchLabel").asText(
                branchNode.path("name").asText(branchNode.path("id").asText("")));
        }
        log.debug("Creating appointment for snap {}, branch {}", dto.snapId(), branchLabel);

        Map<String, Object> snapMap = objectMapper.convertValue(snap, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        try {
            JsonNode result = softnetApi.createAppointment(snapMap, groupId);
            // REQ-034: happy-case observability — a successful booking is now logged too, but with
            // PII removed. Values are replaced by their char count ("***(12)"), so a booking stays
            // traceable end-to-end via the trace ID without leaking name/e-mail/phone/plate/VIN.
            log.info("BOOKING_CREATED appointment={} branch={} | client={} | car={} | mobility={}",
                result.path("id").asText(""), branchLabel,
                LogSanitizer.redact(snap.path("wishlist").path("client").toString()),
                LogSanitizer.redact(snap.path("wishlist").path("car").toString()),
                mobilityLogFields(snap));
            logUsageBooking(dto, groupId, rawSnap, snap);
            return mapAppointmentResponse(result, dto);
        } catch (RuntimeException e) {
            log.error("SOFTNET_ERROR appointment creation failed for snap {} | rawSnap={}",
                dto.snapId(), LogSanitizer.redactAndTruncate(rawSnap.toString()));
            throw e;
        }
    }

    public AppointmentResponseDto getAppointment(String appointmentId, String branchId, String variant) {
        String groupId = setupService.resolveGroupId(branchId, variant);
        JsonNode result = softnetApi.getAppointment(appointmentId, groupId);
        return mapAppointmentResponse(result, null);
    }

    public AppointmentResponseDto cancelAppointment(String appointmentId, String branchId, String variant) {
        String groupId = setupService.resolveGroupId(branchId, variant);
        JsonNode result = softnetApi.cancelAppointment(appointmentId, groupId);
        AppointmentResponseDto mapped = mapAppointmentResponse(result, null);
        return new AppointmentResponseDto(mapped.id(), "cancelled", mapped.date(), mapped.time(),
            mapped.branchName(), mapped.services(), mapped.customerName(), mapped.licensePlate());
    }

    /**
     * REQ-038 K2 (AC-13, BR-6): hands the booking to the anonymous event buffer after a successful
     * booking / übergibt die Buchung nach Erfolg an den anonymen Ereignispuffer.
     *
     * <p>Only in the success branch, after {@code BOOKING_CREATED}, and only while
     * {@code usage.insights.enabled}. {@link UsageEventLogger#logBooking} validates and buffers the
     * event but writes <b>no</b> line in this request: the {@code USAGE_BOOKING} line is written by
     * the daily, shuffled flush under MDC {@code job.usagebooking}, so it shares neither trace id,
     * millisecond nor thread with {@code BOOKING_CREATED} (PO decision: never know who). The call
     * sits in its <b>own</b> {@code try/catch}: without it the outer {@code catch} would log a
     * {@code SOFTNET_ERROR} and report the successful booking as a failure (E10). The warning names
     * the exception class only — no message text, no values (REQ-034 BR-6). / Nur Puffern, keine
     * Zeile im Request; eigener {@code try/catch}, die Buchungsantwort bleibt unverändert, die
     * Warnung trägt keine Werte.
     */
    private void logUsageBooking(CreateAppointmentRequest dto, String groupId, JsonNode rawSnap, ObjectNode snap) {
        if (!usageInsightsEnabled) {
            return;
        }
        try {
            usageEventLogger.logBooking(dto.branchId(), groupId, rawSnap, isRentalCarBooking(snap));
        } catch (RuntimeException e) {
            log.warn("USAGE_BOOKING skipped: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * Mobility part of the booking log — the reserved rental object (isFleet/fleetType/rentCategoryid),
     * no PII, no redaction. Stays an empty object for bookings without a rental car, keeping the line
     * greppable. Hybrid (2026-10-12): back to the fields that are actually sent to SoftNet.
     */
    private String mobilityLogFields(JsonNode snap) {
        ObjectNode mobility = objectMapper.createObjectNode();
        JsonNode wishlist = snap.path("wishlist");
        for (String field : MOBILITY_LOG_FIELDS) {
            JsonNode value = wishlist.path(field);
            if (!value.isMissingNode()) {
                mobility.set(field, value);
            }
        }
        return mobility.toString();
    }

    /**
     * REQ-039: the optional VIN is forwarded trimmed and upper-cased — the same canonical spelling
     * the frontend sends — so SoftNet and the log masking (LogSanitizer) see one form. Missing or
     * blank stays {@link #VIN_UNKNOWN} as before.
     */
    static String normalizeVin(String vin) {
        if (vin == null || vin.isBlank()) {
            return VIN_UNKNOWN;
        }
        return vin.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * BR-M12 (hybrid 2026-10-12): a booking counts as a rental-car booking once a concrete rental
     * object was reserved ({@code wishlist.rentCategoryid} via the proven REQ-036 fleet path).
     */
    static boolean isRentalCarBooking(JsonNode snap) {
        return snap.path("wishlist").hasNonNull("rentCategoryid");
    }

    /**
     * REQ-039 BR-M5: resolves the optional category wish to its display name. Only relevant for a
     * snap requested with a rental car; any lookup problem drops the wish instead of failing the
     * booking (the category is confirmed by the dealership anyway).
     */
    private String rentalWishName(CreateAppointmentRequest dto, String groupId, JsonNode rawSnap) {
        String wishId = dto.rentCategoryId();
        if (wishId == null || wishId.isBlank()
                || !rawSnap.path("wishlist").path("replacementPreference").asBoolean(false)) {
            return null;
        }
        if (wishId.length() != RENT_CATEGORY_ID_LENGTH) {
            log.warn("Ignoring rent category wish: expected {} chars, got {}", RENT_CATEGORY_ID_LENGTH, wishId.length());
            return null;
        }
        try {
            return softnetApi.getRentCategories(groupId).stream()
                .filter(category -> wishId.equals(category.path("id").asText()))
                .map(category -> category.path("name").asText(""))
                .filter(name -> !name.isBlank())
                .findFirst()
                .orElse(null);
        } catch (RuntimeException e) {
            log.warn("Rent category wish dropped, lookup failed: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Hybrid (2026-10-12): the concrete rent category reserved for a rental booking via the proven
     * REQ-036 fleet path. The customer's wish if set, otherwise the category SoftNet already offered
     * for the slot ({@code replacementResource}, matched by name), otherwise the first category of the
     * group. Returns {@code null} for a non-rental booking or when no category can be resolved — then
     * nothing is reserved and the snap is booked as is (same as the current replacementPreference-only
     * behaviour, so this never fails a booking).
     */
    private String resolveRentCategoryId(CreateAppointmentRequest dto, String groupId, JsonNode rawSnap) {
        if (!rawSnap.path("wishlist").path("replacementPreference").asBoolean(false)) {
            return null;
        }
        String wishId = dto.rentCategoryId();
        if (wishId != null && wishId.length() == RENT_CATEGORY_ID_LENGTH) {
            return wishId;
        }
        String offered = rawSnap.path("replacementResource").asText("");
        try {
            List<JsonNode> categories = softnetApi.getRentCategories(groupId);
            String byName = categories.stream()
                .filter(category -> !offered.isBlank()
                    && offered.equalsIgnoreCase(category.path("name").asText("")))
                .map(category -> category.path("id").asText(""))
                .filter(id -> id.length() == RENT_CATEGORY_ID_LENGTH)
                .findFirst()
                .orElse(null);
            if (byName != null) {
                return byName;
            }
            return categories.stream()
                .map(category -> category.path("id").asText(""))
                .filter(id -> id.length() == RENT_CATEGORY_ID_LENGTH)
                .findFirst()
                .orElse(null);
        } catch (RuntimeException e) {
            log.warn("Rent category resolution failed, booking without a reserved category: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * REQ-039 BR-M5: the wish goes in front of the customer note; the customer note is shortened
     * if the total would exceed SoftNet's 2000-char limit — never the wish.
     */
    static String composeNote(String rentalWishName, String customerNote) {
        String note = customerNote != null && !customerNote.isBlank() ? customerNote : null;
        if (rentalWishName == null) {
            return note;
        }
        String wish = RENTAL_WISH_PREFIX + rentalWishName;
        if (note == null) {
            return wish;
        }
        int room = NOTE_MAX_LENGTH - wish.length() - 1;
        return room <= 0 ? wish : wish + "\n" + (note.length() > room ? note.substring(0, room) : note);
    }

    private void patchSnapWithBookingData(ObjectNode snap, CreateAppointmentRequest dto, String rentalWishName,
                                          String rentCategoryId) {
        ObjectNode wishlist = snap.has("wishlist") && snap.get("wishlist").isObject()
            ? (ObjectNode) snap.get("wishlist")
            : objectMapper.createObjectNode();
        snap.set("wishlist", wishlist);

        ObjectNode client = objectMapper.createObjectNode();
        client.put("email", dto.customer().email());
        client.put("lastName", dto.customer().lastName());
        client.put("firstName", dto.customer().firstName());
        client.put("mobile", PhoneNumberNormalizer.toSoftnetFormat(dto.customer().mobile()));
        client.put("phone", PhoneNumberNormalizer.toSoftnetFormat(
            dto.customer().phone() != null ? dto.customer().phone() : dto.customer().mobile()));
        client.put("address", dto.customer().address());
        client.put("zipCode", dto.customer().zipCode());
        client.put("city", dto.customer().city());
        client.put("country", dto.customer().country() != null ? dto.customer().country() : "DE");
        client.put("language", dto.customer().language() != null ? dto.customer().language() : "de");
        wishlist.set("client", client);

        ObjectNode existingCar = wishlist.has("car") && wishlist.get("car").isObject()
            ? (ObjectNode) wishlist.get("car")
            : objectMapper.createObjectNode();

        ObjectNode car = existingCar.deepCopy();
        if (!car.has("brand") || car.get("brand").asText("").isEmpty()) {
            car.put("brand", "");
        }
        car.put("license", dto.vehicle().licensePlate());
        car.put("VIN", normalizeVin(dto.vehicle().vin()));
        car.put("currentMileage", dto.vehicle().mileage());
        car.put("firstRegMonth", dto.vehicle().firstRegMonth() != null ? dto.vehicle().firstRegMonth() : "01");
        car.put("firstRegYear", dto.vehicle().firstRegYear() != null ? dto.vehicle().firstRegYear() : "2020");
        car.put("model", dto.vehicle().model() != null ? dto.vehicle().model()
            : (existingCar.has("model") ? existingCar.get("model").asText("Unknown") : "Unknown"));
        wishlist.set("car", car);

        String note = composeNote(rentalWishName, dto.note());
        if (note != null) {
            wishlist.put("note", note);
        }

        // Hybrid (2026-10-12): a rental booking reserves a concrete rental object via the proven
        // REQ-036 fleet path (isFleet/fleetType/rentCategoryid) — the mechanism that reliably
        // reserved cars before REQ-039. The REQ-039 replacement* passthrough is removed so the
        // payload sent to SoftNet matches that proven booking exactly. The filter (snap request with
        // replacementCar) stays and only decides which slots are shown.
        if (rentCategoryId != null) {
            wishlist.put("isFleet", true);
            wishlist.put("fleetType", "rentObject");
            wishlist.put("rentCategoryid", rentCategoryId);
            wishlist.remove("replacementPreference");
            snap.remove("replacementResource");
            snap.remove("replacementStart");
            snap.remove("replacementEnd");
        }

        if (Boolean.TRUE.equals(dto.callbackRequested())) {
            wishlist.put("callbackPreference", true);
        }

        // REQ-027: forward notification channel ({ SMS, SMTP }) to SoftNet.
        if (dto.notifications() != null) {
            ObjectNode notifications = objectMapper.createObjectNode();
            notifications.put("SMS", Boolean.TRUE.equals(dto.notifications().SMS()));
            notifications.put("SMTP", Boolean.TRUE.equals(dto.notifications().SMTP()));
            snap.set("notifications", notifications);
        }
    }

    private AppointmentResponseDto mapAppointmentResponse(JsonNode result, CreateAppointmentRequest dto) {
        String branchName = "";
        JsonNode branchid = result.path("branchid");
        if (!branchid.isMissingNode() && branchid.has("branchLabel")) {
            branchName = branchid.path("branchLabel").asText("");
        }
        if (branchName.isEmpty()) {
            JsonNode branch = result.path("branch");
            branchName = branch.path("branchLabel").asText(branch.path("name").asText(""));
        }

        String status = result.path("status").asText(result.path("statusCode").asText("confirmed"));
        String checkInTime = result.path("checkInTime").asText("");
        String date = checkInTime.contains("T") ? checkInTime.split("T")[0] : "";
        String time = "";
        if (checkInTime.contains("T")) {
            String timePart = checkInTime.split("T")[1];
            time = timePart.length() >= 5 ? timePart.substring(0, 5) : timePart;
        }

        String customerName;
        String licensePlate;
        if (dto != null) {
            customerName = dto.customer().firstName() + " " + dto.customer().lastName();
            licensePlate = dto.vehicle().licensePlate();
        } else {
            JsonNode userid = result.path("userid");
            String fn = userid.path("firstName").asText("");
            String ln = userid.path("lastName").asText("");
            customerName = (fn + " " + ln).trim();
            licensePlate = result.path("carid").path("license").asText("");
        }

        return new AppointmentResponseDto(
            result.path("id").asText(),
            status, date, time, branchName,
            List.of(),
            customerName, licensePlate
        );
    }
}
