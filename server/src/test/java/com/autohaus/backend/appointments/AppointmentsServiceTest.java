package com.autohaus.backend.appointments;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.autohaus.backend.appointments.dto.AppointmentResponseDto;
import com.autohaus.backend.appointments.dto.CreateAppointmentRequest;
import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.snaps.SnapCache;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentsServiceTest {

    @Mock private SoftnetApiClient softnetApi;
    @Mock private SnapCache snapCache;
    @Mock private SetupService setupService;
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

    @Test
    void createAppointment_withRentCategory_setsFleetFields() {
        ObjectNode snap = baseSnap();
        when(snapCache.getRawSnap("snap-fleet")).thenReturn(snap);
        when(softnetApi.createAppointment(any(), eq("mock-group-id"))).thenReturn(baseResult());

        CreateAppointmentRequest dto = buildDto("snap-fleet", "cat-abc", null);
        service.createAppointment(dto);

        Map<String, Object> sent = captureSentSnap();
        @SuppressWarnings("unchecked")
        Map<String, Object> wishlist = (Map<String, Object>) sent.get("wishlist");
        assertEquals(Boolean.TRUE, wishlist.get("isFleet"));
        assertEquals("rentObject", wishlist.get("fleetType"));
        assertEquals("cat-abc", wishlist.get("fleetCategoryid"));
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

    // ─── helpers ───

    private ObjectNode baseSnap() {
        ObjectNode snap = om.createObjectNode();
        snap.put("internal", "snap-x");
        snap.put("checkInTime", "2026-05-10T09:00:00Z");
        ObjectNode branch = om.createObjectNode();
        branch.put("branchLabel", "Filiale X");
        snap.set("branch", branch);
        return snap;
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureSentSnap() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(softnetApi).createAppointment(captor.capture(), eq("mock-group-id"));
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureSentClient() {
        Map<String, Object> sent = captureSentSnap();
        Map<String, Object> wishlist = (Map<String, Object>) sent.get("wishlist");
        return (Map<String, Object>) wishlist.get("client");
    }
}
