package com.autohaus.backend.appointments;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.autohaus.backend.appointments.dto.AppointmentResponseDto;
import com.autohaus.backend.appointments.dto.CreateAppointmentRequest;
import com.autohaus.backend.common.filter.RequestIdFilter;
import com.autohaus.backend.config.UsageInsightsProperties;
import com.autohaus.backend.config.UsageStatsProperties;
import com.autohaus.backend.config.setup.BranchSetup;
import com.autohaus.backend.config.setup.GroupEntry;
import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.snaps.SnapCache;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.autohaus.backend.usage.insights.UsageEventLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentsServiceTest {

    /** Real 24-char SoftNet rent-category id ("Kleinwagen", Gottfried Schultz Velbert). */
    private static final String RENT_CATEGORY_ID = "682ad3dc6969db66dd27f6c3";

    /** REQ-038: whitelisted 24-hex branch / group of the USAGE_BOOKING tests (§10.2). */
    private static final String HEX_BRANCH = "68f0a1b2c3d4e5f6a7b8c9d0";
    private static final String HEX_GROUP = "6908a1b2c3d4e5f6a7b8c9d1";

    /** REQ-038: trace id of the booking request (RequestIdFilter) / Trace-ID des Buchungs-Requests. */
    private static final String REQUEST_TRACE_ID = "3f2a9c1e-7b44-4d0e-9a51-0c6d2e8f1b77";

    private static final UsageInsightsProperties INSIGHTS_ON =
        new UsageInsightsProperties(true, null, "0 45 3 * * *", "usage-insights.json");
    private static final UsageInsightsProperties INSIGHTS_OFF =
        new UsageInsightsProperties(false, null, "0 45 3 * * *", "usage-insights.json");

    @Mock private SoftnetApiClient softnetApi;
    @Mock private SnapCache snapCache;
    @Mock private SetupService setupService;
    @Mock private UsageEventLogger usageEventLogger;
    private final ObjectMapper om = new ObjectMapper();
    private AppointmentsService service;

    @BeforeEach
    void setUp() {
        lenient().when(setupService.resolveGroupId(anyString(), any())).thenReturn("mock-group-id");
        service = new AppointmentsService(softnetApi, snapCache, om, setupService);
    }

    @Test
    void createAppointment_snapNotFound_throws404() {
        when(snapCache.getRawSnap("missing")).thenReturn(null);

        CreateAppointmentRequest dto = new CreateAppointmentRequest(
            "missing",
            "branch-1",
            null,
            new CreateAppointmentRequest.CustomerDto("test@test.de", "Max", "Muster", "0170", null, "Str 1", "12345", "Berlin", null, null, null),
            new CreateAppointmentRequest.VehicleDto("B-MS1234", 50000, null, null, null, null),
            null, null, null, null
        );

        assertThrows(ResponseStatusException.class, () -> service.createAppointment(dto));
    }

    @Test
    void createAppointment_success() {
        ObjectNode snap = om.createObjectNode();
        snap.put("internal", "snap-1");
        snap.put("checkInTime", "2026-04-15T14:30:00Z");
        ObjectNode branch = om.createObjectNode();
        branch.put("branchLabel", "Filiale A");
        snap.set("branch", branch);

        when(snapCache.getRawSnap("snap-1")).thenReturn(snap);

        ObjectNode result = om.createObjectNode();
        result.put("id", "apt-1");
        result.put("status", "confirmed");
        result.put("checkInTime", "2026-04-15T14:30:00Z");
        ObjectNode branchid = om.createObjectNode();
        branchid.put("branchLabel", "Filiale A");
        result.set("branchid", branchid);

        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(result);

        CreateAppointmentRequest dto = new CreateAppointmentRequest(
            "snap-1",
            "branch-1",
            null,
            new CreateAppointmentRequest.CustomerDto("test@test.de", "Max", "Muster", "0170", null, "Str 1", "12345", "Berlin", null, null, null),
            new CreateAppointmentRequest.VehicleDto("B-MS1234", 50000, "WVWZZZ3CZWE123456", "Golf", "01", "2020"),
            "Bitte Klima prüfen", null, null, null
        );

        AppointmentResponseDto response = service.createAppointment(dto);
        assertEquals("apt-1", response.id());
        assertEquals("confirmed", response.status());
        assertEquals("2026-04-15", response.date());
        assertEquals("14:30", response.time());
        assertEquals("Filiale A", response.branchName());
        assertEquals("Max Muster", response.customerName());
        assertEquals("B-MS1234", response.licensePlate());
    }

    @Test
    void createAppointment_success_logsBookingWithPiiRemovedButLengthKept() {
        ObjectNode snap = baseSnap();
        when(snapCache.getRawSnap("snap-happy")).thenReturn(snap);
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());

        ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AppointmentsService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            CreateAppointmentRequest dto = new CreateAppointmentRequest(
                "snap-happy", "branch-1", null,
                new CreateAppointmentRequest.CustomerDto(
                    "max@example.com", "Max", "Mustermann", "017012345678", null,
                    "Hauptstr 1", "45127", "Essen", "DE", "de", null),
                new CreateAppointmentRequest.VehicleDto(
                    "AB-CD 1234", 45000, "WVWZZZ1JZXW000001", "Golf", "01", "2020"),
                null, null, null, null);

            service.createAppointment(dto);

            String infoLine = appender.list.stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(msg -> msg.contains("BOOKING_CREATED"))
                .findFirst()
                .orElse("");

            assertTrue(infoLine.contains("BOOKING_CREATED"), infoLine);
            assertTrue(infoLine.contains("apt-x"), infoLine);            // technical id present
            // PII removed from the happy-case log ...
            assertFalse(infoLine.contains("max@example.com"), infoLine);
            assertFalse(infoLine.contains("Mustermann"), infoLine);
            assertFalse(infoLine.contains("WVWZZZ1JZXW000001"), infoLine);
            assertFalse(infoLine.contains("AB-CD 1234"), infoLine);
            // ... but the char-count annotation is kept.
            assertTrue(infoLine.contains("***("), infoLine);
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void getAppointment_mapsCustomerAndPlateFromTheSoftnetResult() {
        ObjectNode result = om.createObjectNode();
        result.put("id", "apt-3");
        result.put("checkInTime", "2026-04-15T10:00:00Z");
        result.withObject("userid").put("firstName", "Erika").put("lastName", "Musterfrau");
        result.withObject("carid").put("license", "E-AB 123");
        result.withObject("branch").put("name", "Filiale Y");
        when(softnetApi.getAppointment("apt-3", "mock-group-id")).thenReturn(result);

        AppointmentResponseDto response = service.getAppointment("apt-3", "branch-1", null);

        assertEquals("apt-3", response.id());
        assertEquals("confirmed", response.status());
        assertEquals("2026-04-15", response.date());
        assertEquals("10:00", response.time());
        assertEquals("Filiale Y", response.branchName());
        assertEquals("Erika Musterfrau", response.customerName());
        assertEquals("E-AB 123", response.licensePlate());
    }

    @Test
    void createAppointment_withNotifications_forwardsBothChannels() {
        ObjectNode snap = baseSnap();
        when(snapCache.getRawSnap("snap-notify")).thenReturn(snap);
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());
        CreateAppointmentRequest base = buildDto("snap-notify", null, null);

        service.createAppointment(new CreateAppointmentRequest(base.snapId(), base.branchId(), null,
            base.customer(), base.vehicle(), null, null, null,
            new CreateAppointmentRequest.NotificationsDto(true, null)));

        @SuppressWarnings("unchecked")
        Map<String, Object> notifications = (Map<String, Object>) captureSentSnap().get("notifications");
        assertEquals(Boolean.TRUE, notifications.get("SMS"));
        assertEquals(Boolean.FALSE, notifications.get("SMTP"));
    }

    @Test
    void cancelAppointment_setsStatusToCancelled() {
        ObjectNode result = om.createObjectNode();
        result.put("id", "apt-2");
        result.put("status", "confirmed");
        result.put("checkInTime", "2026-04-15T10:00:00Z");

        when(softnetApi.cancelAppointment("apt-2", "mock-group-id")).thenReturn(result);

        AppointmentResponseDto response = service.cancelAppointment("apt-2", "branch-1", null);
        assertEquals("cancelled", response.status());
    }

    @Test
    void createAppointment_callbackRequestedTrue_setsCallbackPreferenceOnWishlist() {
        ObjectNode snap = baseSnap();
        when(snapCache.getRawSnap("snap-cb")).thenReturn(snap);
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());

        CreateAppointmentRequest dto = buildDto("snap-cb", null, true);
        service.createAppointment(dto);

        Map<String, Object> sent = captureSentSnap();
        @SuppressWarnings("unchecked")
        Map<String, Object> wishlist = (Map<String, Object>) sent.get("wishlist");
        assertEquals(Boolean.TRUE, wishlist.get("callbackPreference"));
    }

    @Test
    void createAppointment_callbackRequestedFalse_doesNotSetCallbackPreference() {
        ObjectNode snap = baseSnap();
        when(snapCache.getRawSnap("snap-nocb")).thenReturn(snap);
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());

        CreateAppointmentRequest dto = buildDto("snap-nocb", null, false);
        service.createAppointment(dto);

        Map<String, Object> sent = captureSentSnap();
        @SuppressWarnings("unchecked")
        Map<String, Object> wishlist = (Map<String, Object>) sent.get("wishlist");
        assertFalse(wishlist.containsKey("callbackPreference"),
            "callbackPreference should not be set when callbackRequested is false");
    }

    @Test
    void createAppointment_callbackRequestedNull_doesNotSetCallbackPreference() {
        ObjectNode snap = baseSnap();
        when(snapCache.getRawSnap("snap-null")).thenReturn(snap);
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());

        CreateAppointmentRequest dto = buildDto("snap-null", null, null);
        service.createAppointment(dto);

        Map<String, Object> sent = captureSentSnap();
        @SuppressWarnings("unchecked")
        Map<String, Object> wishlist = (Map<String, Object>) sent.get("wishlist");
        assertFalse(wishlist.containsKey("callbackPreference"));
    }

    // ─── REQ-039 Abschnitt 18: rental car travels inside the snap, category is only a wish ───

    @Test
    @DisplayName("Hybrid: a rental booking reserves the offered category via rentCategoryid and drops the replacement fields")
    void createAppointment_rentalSnap_reservesOfferedCategoryAndDropsReplacementFields() {
        when(snapCache.getRawSnap("snap-rental")).thenReturn(rentalSnap());
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());
        when(softnetApi.getRentCategories("mock-group-id")).thenReturn(List.of(rentCategory("Kleinwagen")));

        service.createAppointment(buildDto("snap-rental", null, null));

        Map<String, Object> sent = captureSentSnap();
        assertFalse(sent.containsKey("replacementResource"));
        assertFalse(sent.containsKey("replacementStart"));
        assertFalse(sent.containsKey("replacementEnd"));
        @SuppressWarnings("unchecked")
        Map<String, Object> wishlist = (Map<String, Object>) sent.get("wishlist");
        assertFalse(wishlist.containsKey("replacementPreference"));
        assertEquals(Boolean.TRUE, wishlist.get("isFleet"));
        assertEquals("rentObject", wishlist.get("fleetType"));
        assertEquals(RENT_CATEGORY_ID, wishlist.get("rentCategoryid"));
    }

    @Test
    @DisplayName("Hybrid: the category wish is reserved as rentCategoryid and still noted for the dealership")
    void createAppointment_rentalSnapWithWish_reservesWishAndKeepsNote() {
        when(snapCache.getRawSnap("snap-wish")).thenReturn(rentalSnap());
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());
        when(softnetApi.getRentCategories("mock-group-id")).thenReturn(List.of(rentCategory("Mittelklasse")));

        CreateAppointmentRequest base = buildDto("snap-wish", RENT_CATEGORY_ID, null);
        service.createAppointment(new CreateAppointmentRequest(base.snapId(), base.branchId(), null,
            base.customer(), base.vehicle(), "Bitte vorher anrufen", RENT_CATEGORY_ID, null, null));

        @SuppressWarnings("unchecked")
        Map<String, Object> wishlist = (Map<String, Object>) captureSentSnap().get("wishlist");
        assertEquals(RENT_CATEGORY_ID, wishlist.get("rentCategoryid"));
        assertEquals("Mietwagen-Wunsch: Mittelklasse\nBitte vorher anrufen", wishlist.get("note"));
    }

    @Test
    @DisplayName("BR-M5: a wish without rental snap is ignored — no lookup, no note")
    void createAppointment_wishWithoutRentalSnap_isIgnored() {
        when(snapCache.getRawSnap("snap-plain")).thenReturn(baseSnap());
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());

        service.createAppointment(buildDto("snap-plain", RENT_CATEGORY_ID, null));

        @SuppressWarnings("unchecked")
        Map<String, Object> wishlist = (Map<String, Object>) captureSentSnap().get("wishlist");
        assertFalse(wishlist.containsKey("note"));
        assertFalse(wishlist.containsKey("rentCategoryid"));
        verify(softnetApi, never()).getRentCategories(anyString());
    }

    @Test
    @DisplayName("Hybrid: a wrong-length wish is ignored and the booking falls back to the offered category")
    void createAppointment_wishWithWrongLength_fallsBackToOfferedCategory() {
        when(snapCache.getRawSnap("snap-bad-cat")).thenReturn(rentalSnap());
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());
        when(softnetApi.getRentCategories("mock-group-id")).thenReturn(List.of(rentCategory("Kleinwagen")));

        service.createAppointment(buildDto("snap-bad-cat", "cat-abc", null));

        @SuppressWarnings("unchecked")
        Map<String, Object> wishlist = (Map<String, Object>) captureSentSnap().get("wishlist");
        assertEquals(RENT_CATEGORY_ID, wishlist.get("rentCategoryid"));
        assertFalse(wishlist.containsKey("note"));
    }

    @Test
    void createAppointment_wishLookupFails_bookingStillSucceedsWithoutNote() {
        when(snapCache.getRawSnap("snap-lookup-err")).thenReturn(rentalSnap());
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());
        when(softnetApi.getRentCategories("mock-group-id"))
            .thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));

        AppointmentResponseDto response = service.createAppointment(buildDto("snap-lookup-err", RENT_CATEGORY_ID, null));

        assertEquals("apt-x", response.id());
        @SuppressWarnings("unchecked")
        Map<String, Object> wishlist = (Map<String, Object>) captureSentSnap().get("wishlist");
        assertFalse(wishlist.containsKey("note"));
    }

    @Test
    void composeNote_longCustomerNote_isShortenedButWishIsKept() {
        String note = AppointmentsService.composeNote("Oberklasse", "x".repeat(2500));

        assertEquals(AppointmentsService.NOTE_MAX_LENGTH, note.length());
        assertTrue(note.startsWith("Mietwagen-Wunsch: Oberklasse\n"), note.substring(0, 40));
    }

    @Test
    void composeNote_withoutWish_keepsCustomerNoteAndBlankBecomesNull() {
        assertEquals("Hallo", AppointmentsService.composeNote(null, "Hallo"));
        assertNull(AppointmentsService.composeNote(null, "  "));
        assertEquals("Mietwagen-Wunsch: Kleinwagen", AppointmentsService.composeNote("Kleinwagen", null));
    }

    // === REQ-043 B-2: OTV marker on wheel/tire change bookings ===

    @Test
    void composeNote_otvMarker_isAlwaysTheLastLine() {
        String marker = AppointmentsService.OTV_MARKER;
        assertEquals(marker, AppointmentsService.composeNote(null, null, true));
        assertEquals(marker, AppointmentsService.composeNote(null, "  ", true));
        assertEquals("Hallo\n" + marker, AppointmentsService.composeNote(null, "Hallo", true));
        assertEquals("Mietwagen-Wunsch: Kleinwagen\nHallo\n" + marker,
            AppointmentsService.composeNote("Kleinwagen", "Hallo", true));
        assertEquals("Hallo", AppointmentsService.composeNote(null, "Hallo", false));
    }

    @Test
    void composeNote_otvMarker_longNoteIsShortenedButWishAndMarkerAreKept() {
        String note = AppointmentsService.composeNote("Oberklasse", "x".repeat(2500), true);

        assertEquals(AppointmentsService.NOTE_MAX_LENGTH, note.length());
        assertTrue(note.startsWith("Mietwagen-Wunsch: Oberklasse\n"), note.substring(0, 40));
        assertTrue(note.endsWith("\n" + AppointmentsService.OTV_MARKER));
    }

    @Test
    void createAppointment_boxenstoppVariant_appendsOtvMarkerToCustomerNote() {
        when(snapCache.getRawSnap("snap-tire")).thenReturn(baseSnap());
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());

        service.createAppointment(tireDto("snap-tire", "tireChange", "Bitte Felgen prüfen", null));

        assertEquals("Bitte Felgen prüfen\n" + AppointmentsService.OTV_MARKER, sentNote());
    }

    @Test
    void createAppointment_tireChangeServiceInNormalFlow_appendsOtvMarker() {
        when(snapCache.getRawSnap("snap-tire")).thenReturn(baseSnap());
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());

        service.createAppointment(tireDto("snap-tire", null, null, true));

        assertEquals(AppointmentsService.OTV_MARKER, sentNote());
    }

    @Test
    void createAppointment_withoutTireChange_hasNoOtvMarker() {
        when(snapCache.getRawSnap("snap-plain")).thenReturn(baseSnap());
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());

        service.createAppointment(tireDto("snap-plain", "default", "Bitte Klima prüfen", false));

        assertEquals("Bitte Klima prüfen", sentNote());
    }

    // === REQ-043 B-1: license plate goes to SoftNet trimmed ===

    @Test
    @SuppressWarnings("unchecked")
    void createAppointment_licensePlate_isSentTrimmed() {
        when(snapCache.getRawSnap("snap-plate")).thenReturn(baseSnap());
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());

        service.createAppointment(new CreateAppointmentRequest(
            "snap-plate", "branch-1", null,
            new CreateAppointmentRequest.CustomerDto(
                "test@test.de", "Petra", "Voß", "0170", null,
                "Wuppertaler Str. 12", "42103", "Wuppertal", "DE", "de", null),
            new CreateAppointmentRequest.VehicleDto("  W-TEST-99 ", 45000, null, null, null, null),
            null, null, null, null));

        Map<String, Object> wishlist = (Map<String, Object>) captureSentSnap().get("wishlist");
        Map<String, Object> car = (Map<String, Object>) wishlist.get("car");
        assertEquals("W-TEST-99", car.get("license"));
    }

    @Test
    void normalizeVin_trimsAndUpperCases_blankMissingOrPlaceholderIsNull() {
        assertEquals("WAUZZZ8V0JA000001", AppointmentsService.normalizeVin(" wauzzz8v0ja000001 "));
        assertEquals("WVWZZZ1JZXW000001", AppointmentsService.normalizeVin("WVWZZZ1JZXW000001"));
        // REQ-045: no "UNKNOWN" any more — an unknown VIN is null, the caller omits the field.
        assertNull(AppointmentsService.normalizeVin(null));
        assertNull(AppointmentsService.normalizeVin("   "));
        assertNull(AppointmentsService.normalizeVin("UNKNOWN"));
        assertNull(AppointmentsService.normalizeVin(" unknown "));
    }

    // === REQ-045: an unknown VIN / first registration is omitted; the model is always sent ===

    @Test
    void createAppointment_unknownVehicleData_omitsVinAndFirstRegistration_sendsModelFallback() {
        when(snapCache.getRawSnap("snap-unknown")).thenReturn(baseSnap());
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());

        service.createAppointment(vehicleDto("snap-unknown",
            new CreateAppointmentRequest.VehicleDto("W-TEST-99", 45000, null, null, null, null)));

        Map<String, Object> car = sentCar();
        assertEquals("W-TEST-99", car.get("license"));
        assertEquals(45000, car.get("currentMileage"));
        for (String field : List.of("VIN", "firstRegMonth", "firstRegYear")) {
            assertFalse(car.containsKey(field), field + " must be omitted, but was " + car.get(field));
        }
        // REQ-045 5.3: SoftNet requires a model at some branches — it is never omitted.
        assertEquals(AppointmentsService.MODEL_UNKNOWN, car.get("model"));
        // REQ-045 leaves the brand untouched: still an empty string when the snap carries none.
        assertEquals("", car.get("brand"));
    }

    @Test
    void createAppointment_blankOrPlaceholderVehicleData_omitsVinAndFirstRegistration_sendsModelFallback() {
        when(snapCache.getRawSnap("snap-blank")).thenReturn(baseSnap());
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());

        service.createAppointment(vehicleDto("snap-blank",
            new CreateAppointmentRequest.VehicleDto("W-TEST-99", 45000, "   ", "n/a", " ", "N/A")));

        Map<String, Object> car = sentCar();
        for (String field : List.of("VIN", "firstRegMonth", "firstRegYear")) {
            assertFalse(car.containsKey(field), field + " must be omitted, but was " + car.get(field));
        }
        assertEquals(AppointmentsService.MODEL_UNKNOWN, car.get("model"));
    }

    @Test
    void createAppointment_knownVehicleData_isForwardedTrimmed() {
        when(snapCache.getRawSnap("snap-known")).thenReturn(baseSnap());
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());

        service.createAppointment(vehicleDto("snap-known",
            new CreateAppointmentRequest.VehicleDto("W-TEST-99", 45000, " wvwzzz3czwe123456 ", " Golf ", "03", "2023")));

        Map<String, Object> car = sentCar();
        assertEquals("WVWZZZ3CZWE123456", car.get("VIN"));
        assertEquals("Golf", car.get("model"));
        assertEquals("03", car.get("firstRegMonth"));
        assertEquals("2023", car.get("firstRegYear"));
    }

    @Test
    void createAppointment_placeholdersInheritedFromTheSnap_vinRemoved_modelFallsBack() {
        ObjectNode snap = baseSnap();
        snap.withObject("wishlist").withObject("car")
            .put("brand", "Audi").put("model", "").put("VIN", "UNKNOWN");
        when(snapCache.getRawSnap("snap-inherited")).thenReturn(snap);
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());

        service.createAppointment(vehicleDto("snap-inherited",
            new CreateAppointmentRequest.VehicleDto("W-TEST-99", 45000, null, null, null, null)));

        Map<String, Object> car = sentCar();
        assertEquals("Audi", car.get("brand"));
        // REQ-015: SoftNet rejects an empty model ("Mandatory field model not set") — falls back instead.
        assertEquals(AppointmentsService.MODEL_UNKNOWN, car.get("model"));
        assertFalse(car.containsKey("VIN"), "inherited placeholder VIN must be removed");
    }

    @Test
    void bookingModel_isNeverEmpty_customerThenSnapThenFallback() {
        assertEquals("Golf", AppointmentsService.bookingModel(" Golf ", "A4 Avant"));
        assertEquals("A4 Avant", AppointmentsService.bookingModel(null, " A4 Avant "));
        assertEquals("A4 Avant", AppointmentsService.bookingModel("Unknown", "A4 Avant"));
        assertEquals(AppointmentsService.MODEL_UNKNOWN, AppointmentsService.bookingModel(null, ""));
        assertEquals(AppointmentsService.MODEL_UNKNOWN, AppointmentsService.bookingModel("  ", null));
        assertEquals(AppointmentsService.MODEL_UNKNOWN, AppointmentsService.bookingModel("n/a", "-"));
    }

    @Test
    void createAppointment_emptyDtoModel_keepsTheRealModelOfTheSnap() {
        ObjectNode snap = baseSnap();
        snap.withObject("wishlist").withObject("car").put("model", "A4 Avant");
        when(snapCache.getRawSnap("snap-model")).thenReturn(snap);
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());

        service.createAppointment(vehicleDto("snap-model",
            new CreateAppointmentRequest.VehicleDto("W-TEST-99", 45000, null, null, null, null)));

        assertEquals("A4 Avant", sentCar().get("model"));
    }

    @Test
    void createAppointment_dtoModel_winsOverTheSnapModel() {
        ObjectNode snap = baseSnap();
        snap.withObject("wishlist").withObject("car").put("model", "A4 Avant");
        when(snapCache.getRawSnap("snap-model-dto")).thenReturn(snap);
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());

        service.createAppointment(vehicleDto("snap-model-dto",
            new CreateAppointmentRequest.VehicleDto("W-TEST-99", 45000, null, "Golf", null, null)));

        assertEquals("Golf", sentCar().get("model"));
    }

    @Test
    @DisplayName("Hybrid: a booking counts as rental once a concrete rentCategoryid is reserved")
    void isRentalCarBooking_requiresReservedRentCategory() {
        ObjectNode reserved = baseSnap();
        reserved.withObject("wishlist").put("rentCategoryid", RENT_CATEGORY_ID);
        assertTrue(AppointmentsService.isRentalCarBooking(reserved));
        assertFalse(AppointmentsService.isRentalCarBooking(baseSnap()));
        // A snap that only carries replacementPreference (not yet booked/reserved) is not a reservation.
        assertFalse(AppointmentsService.isRentalCarBooking(rentalSnap()));
    }

    @Test
    void createAppointment_logsMobilityFields_soASuccessfulBookingIsTraceable() {
        when(snapCache.getRawSnap("snap-mob-log")).thenReturn(rentalSnap());
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());
        when(softnetApi.getRentCategories("mock-group-id")).thenReturn(List.of(rentCategory("Kleinwagen")));

        ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AppointmentsService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            service.createAppointment(buildDto("snap-mob-log", null, null));

            String infoLine = bookingLogLine(appender);
            assertTrue(infoLine.contains("\"isFleet\":true"), infoLine);
            assertTrue(infoLine.contains("\"rentCategoryid\":\"" + RENT_CATEGORY_ID + "\""), infoLine);
            assertFalse(infoLine.contains("replacementPreference"), infoLine);
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void createAppointment_withoutMobility_logsEmptyMobilityObject() {
        ObjectNode snap = baseSnap();
        when(snapCache.getRawSnap("snap-mob-empty")).thenReturn(snap);
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());

        ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AppointmentsService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            service.createAppointment(buildDto("snap-mob-empty", null, null));

            assertTrue(bookingLogLine(appender).contains("mobility={}"), bookingLogLine(appender));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void createAppointment_mobileWithLeadingZero_normalizedToPlus49InClientMap() {
        ObjectNode snap = baseSnap();
        when(snapCache.getRawSnap("snap-phone")).thenReturn(snap);
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());

        CreateAppointmentRequest dto = new CreateAppointmentRequest(
            "snap-phone", "branch-1", null,
            new CreateAppointmentRequest.CustomerDto(
                "test@test.de", "Petra", "Voß", "017012345678", null,
                "Wuppertaler Str. 12", "42103", "Wuppertal", "DE", "de", null),
            new CreateAppointmentRequest.VehicleDto("W-TEST-99", 45000, null, "A4", "03", "2023"),
            null, null, null, null
        );
        service.createAppointment(dto);

        Map<String, Object> client = captureSentClient();
        assertEquals("+4917012345678", client.get("mobile"));
        assertEquals("+4917012345678", client.get("phone"));
    }

    @Test
    void createAppointment_mobileAlreadyInternational_unchangedInClientMap() {
        ObjectNode snap = baseSnap();
        when(snapCache.getRawSnap("snap-intl")).thenReturn(snap);
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());

        CreateAppointmentRequest dto = new CreateAppointmentRequest(
            "snap-intl", "branch-1", null,
            new CreateAppointmentRequest.CustomerDto(
                "test@test.de", "Petra", "Voß", "+491701234567", "+4989123456",
                "Wuppertaler Str. 12", "42103", "Wuppertal", "DE", "de", null),
            new CreateAppointmentRequest.VehicleDto("W-TEST-99", 45000, null, "A4", "03", "2023"),
            null, null, null, null
        );
        service.createAppointment(dto);

        Map<String, Object> client = captureSentClient();
        assertEquals("+491701234567", client.get("mobile"));
        assertEquals("+4989123456", client.get("phone"));
    }

    @Test
    void createAppointment_whenSoftnetFails_logsUnpatchedRawSnapAndRethrows() {
        ObjectNode snap = baseSnap();
        when(snapCache.getRawSnap("snap-err")).thenReturn(snap);
        when(softnetApi.createAppointment(any(), eq("mock-group-id")))
            .thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));

        ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AppointmentsService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            assertThrows(HttpServerErrorException.class,
                () -> service.createAppointment(buildDto("snap-err", null, null)));

            String errorLine = appender.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .map(ILoggingEvent::getFormattedMessage)
                .findFirst()
                .orElse("");
            assertTrue(errorLine.contains("SOFTNET_ERROR"), errorLine);
            assertTrue(errorLine.contains("snap-err"), errorLine);
            assertTrue(errorLine.contains("Filiale X"), errorLine);
            assertFalse(errorLine.contains("test@test.de"),
                "raw snap in log must not contain patched customer data: " + errorLine);
        } finally {
            logger.detachAppender(appender);
        }
    }

    // ─── REQ-038 K2: USAGE_BOOKING (AC-13, BR-6, E10, TC-14) ───

    @Test
    @DisplayName("AC-13: a successful booking hands branch, group, pristine snap and mobility to the event buffer")
    void createAppointment_insightsEnabled_logsUsageBookingWithPristineSnap() {
        ObjectNode snap = baseSnap();
        ObjectNode rental = rentalSnap();
        when(snapCache.getRawSnap("snap-usage")).thenReturn(snap);
        when(snapCache.getRawSnap("snap-usage-rental")).thenReturn(rental);
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());
        when(softnetApi.getRentCategories("mock-group-id")).thenReturn(List.of(rentCategory("Kleinwagen")));
        AppointmentsService withInsights = serviceWithInsights(INSIGHTS_ON);

        // BR-M12 (hybrid): mobility=true once a concrete rentCategoryid was reserved for the booking.
        withInsights.createAppointment(buildDto("snap-usage-rental", null, null));
        withInsights.createAppointment(buildDto("snap-usage", RENT_CATEGORY_ID, null));

        // same(snap): the cached SoftNet snap itself — the service patches a deep copy only.
        verify(usageEventLogger).logBooking(eq("branch-1"), eq("mock-group-id"), same(rental), eq(true));
        verify(usageEventLogger).logBooking(eq("branch-1"), eq("mock-group-id"), same(snap), eq(false));
        assertFalse(snap.path("wishlist").has("client"), "the logger never sees patched customer data");
    }

    @Test
    @DisplayName("AC-13: no USAGE_BOOKING line in the booking request — exactly one after the flush, under job.usagebooking")
    void createAppointment_insightsEnabled_writesNoLineInTheRequestAndOneAfterTheFlush() {
        ObjectNode snap = baseSnap();
        snap.put("checkInTime", "2026-10-24T07:30:00Z");
        snap.withObject("wishlist").putArray("operations").add("6908a1b2c3d4e5f6a7b8c9e2");
        snap.withObject("wishlist").put("replacementPreference", true);
        snap.put("replacementResource", "Kleinwagen");
        when(snapCache.getRawSnap("snap-line")).thenReturn(snap);
        when(setupService.resolveGroupId(HEX_BRANCH, null)).thenReturn(HEX_GROUP);
        when(setupService.findByBranchId(HEX_BRANCH)).thenReturn(new BranchSetup(HEX_BRANCH, "Filiale X",
            null, List.of(new GroupEntry("default", HEX_GROUP, "Filiale X Werkstatt"))));
        when(softnetApi.createAppointment(any(), eq(HEX_GROUP))).thenReturn(baseResult());
        // The Spring constructor (zone from usage.zone) — the assertions below do not depend on "now".
        UsageEventLogger realLogger = new UsageEventLogger(setupService,
            new UsageStatsProperties(true, "-", "-", "Europe/Berlin", null, 24, 60, null));
        AppointmentsService withInsights = new AppointmentsService(softnetApi, snapCache, om, setupService,
            realLogger, INSIGHTS_ON);

        // Captures MDC and thread at logging time (LoggingEvent reads both lazily).
        ListAppender<ILoggingEvent> appender = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        appender.start();
        for (Class<?> type : List.of(AppointmentsService.class, UsageEventLogger.class)) {
            ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(type)).addAppender(appender);
        }
        MDC.put(RequestIdFilter.TRACE_ID_MDC_KEY, REQUEST_TRACE_ID);
        try {
            CreateAppointmentRequest base = buildDto("snap-line", RENT_CATEGORY_ID, null);
            withInsights.createAppointment(new CreateAppointmentRequest(base.snapId(), HEX_BRANCH, null,
                base.customer(), base.vehicle(), null, RENT_CATEGORY_ID, null, null));
            MDC.remove(RequestIdFilter.TRACE_ID_MDC_KEY);   // end of the request / Request-Ende

            List<String> inRequest = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertTrue(indexOfPrefix(inRequest, "BOOKING_CREATED") >= 0, inRequest.toString());
            assertTrue(inRequest.stream().noneMatch(m -> m.contains("USAGE_BOOKING")),
                "no USAGE_BOOKING line next to BOOKING_CREATED: " + inRequest);

            assertEquals(1, realLogger.flush());

            List<ILoggingEvent> usageEvents = appender.list.stream()
                .filter(e -> e.getFormattedMessage().startsWith("USAGE_BOOKING {")).toList();
            assertEquals(1, usageEvents.size(), inRequest.toString());
            ILoggingEvent usage = usageEvents.getFirst();
            ILoggingEvent created = appender.list.stream()
                .filter(e -> e.getFormattedMessage().startsWith("BOOKING_CREATED")).findFirst().orElseThrow();
            assertEquals(REQUEST_TRACE_ID, created.getMDCPropertyMap().get(RequestIdFilter.TRACE_ID_MDC_KEY));
            assertEquals(UsageEventLogger.TRACE_ID, usage.getMDCPropertyMap().get(RequestIdFilter.TRACE_ID_MDC_KEY),
                "the line carries the job marker, not the trace id of the booking");
            String line = usage.getFormattedMessage();
            assertTrue(line.startsWith("USAGE_BOOKING {\"v\":2,\"day\":\""), line);
            assertTrue(line.contains("\"branch\":\"" + HEX_BRANCH + "\""), line);
            assertTrue(line.contains("\"mobility\":true"), line);
            assertFalse(line.contains("apt-x"), "no appointment id: " + line);
            assertFalse(line.contains("snap-x"), "no snap id: " + line);
            assertFalse(line.contains("@"), "no customer data: " + line);
            assertFalse(line.contains(REQUEST_TRACE_ID), line);
        } finally {
            MDC.remove(RequestIdFilter.TRACE_ID_MDC_KEY);
            detachAppender(appender, AppointmentsService.class, UsageEventLogger.class);
        }
    }

    @Test
    @DisplayName("TC-14 (a): with usage.insights.enabled=false no line is written")
    void createAppointment_insightsDisabled_writesNoUsageBookingLine() {
        ObjectNode snap = baseSnap();
        when(snapCache.getRawSnap("snap-off")).thenReturn(snap);
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());

        serviceWithInsights(INSIGHTS_OFF).createAppointment(buildDto("snap-off", null, null));
        new AppointmentsService(softnetApi, snapCache, om, setupService, usageEventLogger, null)
            .createAppointment(buildDto("snap-off", null, null));
        new AppointmentsService(softnetApi, snapCache, om, setupService, null, INSIGHTS_ON)
            .createAppointment(buildDto("snap-off", null, null));

        verify(usageEventLogger, never()).logBooking(any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("TC-14 (b): a failed SoftNet booking writes no line and logs SOFTNET_ERROR as before")
    void createAppointment_softnetFails_writesNoUsageBookingLine() {
        ObjectNode snap = baseSnap();
        when(snapCache.getRawSnap("snap-fail")).thenReturn(snap);
        when(softnetApi.createAppointment(any(), eq("mock-group-id")))
            .thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));
        AppointmentsService withInsights = serviceWithInsights(INSIGHTS_ON);

        ListAppender<ILoggingEvent> appender = attachAppender(AppointmentsService.class);
        try {
            assertThrows(HttpServerErrorException.class,
                () -> withInsights.createAppointment(buildDto("snap-fail", null, null)));

            verify(usageEventLogger, never()).logBooking(any(), any(), any(), anyBoolean());
            assertTrue(appender.list.stream().anyMatch(e -> e.getLevel() == Level.ERROR
                && e.getFormattedMessage().contains("SOFTNET_ERROR")), "SOFTNET_ERROR as before");
            assertTrue(appender.list.stream().noneMatch(e -> e.getFormattedMessage().contains("USAGE_BOOKING")));
        } finally {
            detachAppender(appender, AppointmentsService.class);
        }
    }

    @Test
    @DisplayName("TC-14 (c): a failing logger never changes the response and never raises SOFTNET_ERROR")
    void createAppointment_usageLoggerThrows_keepsResponseAndWarnsWithoutValues() {
        ObjectNode snap = baseSnap();
        when(snapCache.getRawSnap("snap-boom")).thenReturn(snap);
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());
        doThrow(new IllegalStateException("secret branch-1 snap-boom test@test.de"))
            .when(usageEventLogger).logBooking(any(), any(), any(), anyBoolean());
        AppointmentResponseDto withoutLogger = service.createAppointment(buildDto("snap-boom", RENT_CATEGORY_ID, null));
        AppointmentsService withInsights = serviceWithInsights(INSIGHTS_ON);

        ListAppender<ILoggingEvent> appender = attachAppender(AppointmentsService.class);
        try {
            AppointmentResponseDto response = withInsights.createAppointment(buildDto("snap-boom", RENT_CATEGORY_ID, null));

            assertEquals(withoutLogger, response, "the booking response is identical to the one without logger");
            List<String> warnings = appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
            assertEquals(List.of("USAGE_BOOKING skipped: IllegalStateException"), warnings);
            assertTrue(appender.list.stream().noneMatch(e -> e.getLevel() == Level.ERROR), "no SOFTNET_ERROR");
            assertTrue(appender.list.stream().noneMatch(e -> e.getFormattedMessage().contains("SOFTNET_ERROR")));
            assertTrue(appender.list.stream().noneMatch(e -> e.getFormattedMessage().contains("secret")),
                "neither the message text nor values are logged");
            assertTrue(appender.list.stream().allMatch(e -> e.getThrowableProxy() == null),
                "no stack trace that could carry values");
        } finally {
            detachAppender(appender, AppointmentsService.class);
        }
    }

    // ─── helpers ───

    private AppointmentsService serviceWithInsights(UsageInsightsProperties properties) {
        return new AppointmentsService(softnetApi, snapCache, om, setupService, usageEventLogger, properties);
    }

    private static ListAppender<ILoggingEvent> attachAppender(Class<?>... loggers) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        for (Class<?> type : loggers) {
            ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(type)).addAppender(appender);
        }
        return appender;
    }

    private static void detachAppender(ListAppender<ILoggingEvent> appender, Class<?>... loggers) {
        for (Class<?> type : loggers) {
            ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(type)).detachAppender(appender);
        }
        appender.stop();
    }

    private static int indexOfPrefix(List<String> messages, String prefix) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private ObjectNode baseSnap() {
        ObjectNode snap = om.createObjectNode();
        snap.put("internal", "snap-x");
        snap.put("checkInTime", "2026-05-10T09:00:00Z");
        ObjectNode branch = om.createObjectNode();
        branch.put("branchLabel", "Filiale X");
        snap.set("branch", branch);
        return snap;
    }

    /** REQ-039: snap requested with replacementPreference, SoftNet reserved a car. */
    private ObjectNode rentalSnap() {
        ObjectNode snap = baseSnap();
        snap.withObject("wishlist").put("replacementPreference", true);
        snap.put("replacementResource", "Kleinwagen");
        snap.put("replacementStart", "2026-05-10T09:00:00Z");
        snap.put("replacementEnd", "2026-05-10T11:45:00Z");
        return snap;
    }

    /** A SoftNet rent category ({@code id}, {@code name}) as returned by {@code getRentCategories}. */
    private ObjectNode rentCategory(String name) {
        ObjectNode category = om.createObjectNode();
        category.put("id", RENT_CATEGORY_ID);
        category.put("name", name);
        return category;
    }

    private ObjectNode baseResult() {
        ObjectNode result = om.createObjectNode();
        result.put("id", "apt-x");
        result.put("status", "confirmed");
        result.put("checkInTime", "2026-05-10T09:00:00Z");
        ObjectNode branchid = om.createObjectNode();
        branchid.put("branchLabel", "Filiale X");
        result.set("branchid", branchid);
        return result;
    }

    private CreateAppointmentRequest buildDto(String snapId, String rentCategoryId, Boolean callbackRequested) {
        return new CreateAppointmentRequest(
            snapId,
            "branch-1",
            null,
            new CreateAppointmentRequest.CustomerDto(
                "test@test.de", "Petra", "Voß", "0170", null,
                "Wuppertaler Str. 12", "42103", "Wuppertal", "DE", "de", null),
            new CreateAppointmentRequest.VehicleDto("W-TEST-99", 45000, null, "A4", "03", "2023"),
            null,
            rentCategoryId,
            callbackRequested,
            null
        );
    }

    private String bookingLogLine(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
            .filter(event -> event.getLevel() == Level.INFO)
            .map(ILoggingEvent::getFormattedMessage)
            .filter(msg -> msg.contains("BOOKING_CREATED"))
            .findFirst()
            .orElse("");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureSentSnap() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(softnetApi).createAppointment(captor.capture(), eq("mock-group-id"));
        return captor.getValue();
    }

    /** REQ-043: booking request with variant, customer note and the tire-change flag. */
    private CreateAppointmentRequest tireDto(String snapId, String variant, String note, Boolean tireChangeBooking) {
        return new CreateAppointmentRequest(
            snapId, "branch-1", variant,
            new CreateAppointmentRequest.CustomerDto(
                "test@test.de", "Petra", "Voß", "0170", null,
                "Wuppertaler Str. 12", "42103", "Wuppertal", "DE", "de", null),
            new CreateAppointmentRequest.VehicleDto("W-TEST-99", 45000, null, "A4", "03", "2023"),
            note, null, null, null, tireChangeBooking);
    }

    /** REQ-045: booking request with the given vehicle data, otherwise like {@link #buildDto}. */
    private CreateAppointmentRequest vehicleDto(String snapId, CreateAppointmentRequest.VehicleDto vehicle) {
        return new CreateAppointmentRequest(
            snapId, "branch-1", null,
            new CreateAppointmentRequest.CustomerDto(
                "test@test.de", "Petra", "Voß", "0170", null,
                "Wuppertaler Str. 12", "42103", "Wuppertal", "DE", "de", null),
            vehicle, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sentCar() {
        Map<String, Object> wishlist = (Map<String, Object>) captureSentSnap().get("wishlist");
        return (Map<String, Object>) wishlist.get("car");
    }

    @SuppressWarnings("unchecked")
    private String sentNote() {
        Map<String, Object> wishlist = (Map<String, Object>) captureSentSnap().get("wishlist");
        return (String) wishlist.get("note");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureSentClient() {
        Map<String, Object> sent = captureSentSnap();
        Map<String, Object> wishlist = (Map<String, Object>) sent.get("wishlist");
        return (Map<String, Object>) wishlist.get("client");
    }
}
