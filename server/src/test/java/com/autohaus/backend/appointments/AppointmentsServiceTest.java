package com.autohaus.backend.appointments;

import com.autohaus.backend.appointments.dto.AppointmentResponseDto;
import com.autohaus.backend.appointments.dto.CreateAppointmentRequest;
import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.snaps.SnapCache;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
            null, null, null
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
            "Bitte Klima prüfen", null, null
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
    void cancelAppointment_setsStatusToCancelled() {
        ObjectNode result = om.createObjectNode();
        result.put("id", "apt-2");
        result.put("status", "confirmed");
        result.put("checkInTime", "2026-04-15T10:00:00Z");

        when(softnetApi.cancelAppointment("apt-2", "mock-group-id")).thenReturn(result);

        AppointmentResponseDto response = service.cancelAppointment("apt-2", "branch-1", null);
        assertEquals("cancelled", response.status());
    }
}
