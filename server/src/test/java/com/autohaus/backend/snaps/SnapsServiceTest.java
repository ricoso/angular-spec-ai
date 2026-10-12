package com.autohaus.backend.snaps;

import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.snaps.dto.SnapRequestDto;
import com.autohaus.backend.snaps.dto.SnapResponseDto;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

import java.nio.charset.StandardCharsets;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnapsServiceTest {

    @Mock private SoftnetApiClient softnetApi;
    @Mock private SnapCache snapCache;
    @Mock private SetupService setupService;
    @Mock private SoftnetBrandResolver brandResolver;
    private final ObjectMapper om = new ObjectMapper();
    private SnapsService service;

    @BeforeEach
    void setUp() {
        when(setupService.resolveGroupId(anyString(), any())).thenReturn("mock-group-id");
        // Operations API returns empty list — mapper passes through hex IDs as-is,
        // drops frontend keys. Tests below use hex IDs in SnapRequestDto.
        when(softnetApi.getOperations(anyString())).thenReturn(List.of());
        OperationIdMapper mapper = new OperationIdMapper(softnetApi);
        service = new SnapsService(softnetApi, mapper, snapCache, setupService, brandResolver);
    }

    @Test
    void getSnaps_transformsToResponseDtos() {
        ObjectNode snap1 = om.createObjectNode();
        snap1.put("internal", "snap-1");
        snap1.put("checkInTime", "2026-04-15T14:30:00.000Z");
        snap1.put("checkInResource", "Werkstatt A");

        ObjectNode snap2 = om.createObjectNode();
        snap2.put("internal", "snap-2");
        snap2.put("checkInTime", "2026-04-15T09:00:00.000Z");
        snap2.put("checkInResource", "Werkstatt B");

        ObjectNode snap3 = om.createObjectNode();
        snap3.put("internal", "snap-3");
        snap3.put("checkInTime", "2026-04-16T10:00:00.000Z");

        when(softnetApi.getSnaps(any(), eq("mock-group-id"))).thenReturn(List.of(snap1, snap2, snap3));

        SnapRequestDto request = new SnapRequestDto("branch-1", List.of("690000000000000000000001"), "2026-04-15", "2026-04-29", "next-available", "BMW", null, null, null, null);

        List<SnapResponseDto> result = service.getSnaps(request);

        assertEquals(2, result.size()); // 2 dates

        // First date
        assertEquals("2026-04-15", result.get(0).date());
        assertEquals("15.04.2026", result.get(0).displayDate());
        assertEquals(2, result.get(0).slots().size());

        // Second date
        assertEquals("2026-04-16", result.get(1).date());
        assertEquals("16.04.2026", result.get(1).displayDate());
        assertEquals(1, result.get(1).slots().size());

        // Verify snap caching
        verify(snapCache).cacheSnaps(any());
    }

    @Test
    void getSnaps_dateRangeMode() {
        when(softnetApi.getSnaps(any(), eq("mock-group-id"))).thenReturn(List.of());

        SnapRequestDto request = new SnapRequestDto("branch-1", List.of("690000000000000000000002"), "2026-04-15", "2026-04-29", "date-range", null, null, null, null, null);
        List<SnapResponseDto> result = service.getSnaps(request);
        assertTrue(result.isEmpty());
    }

    @Test
    void getSnaps_withTimePreferenceAndReplacementCar() {
        when(softnetApi.getSnaps(any(), eq("mock-group-id"))).thenReturn(List.of());

        SnapRequestDto request = new SnapRequestDto("branch-1", List.of("690000000000000000000001"), "2026-04-15", "2026-04-29", "next-available", "BMW", "AM", true, null, null);
        List<SnapResponseDto> result = service.getSnaps(request);
        assertTrue(result.isEmpty());
        verify(softnetApi).getSnaps(any(), eq("mock-group-id"));
    }

    private static HttpServerErrorException softnet500(String body) {
        return new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
            body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Safety net: SoftNet 500 'No matching rent category' on a rental request yields no slots, not an error")
    void getSnaps_rentalBrandWithoutCategory_returnsEmptyInsteadOf500() {
        when(softnetApi.getSnaps(any(), eq("mock-group-id"))).thenThrow(softnet500(
            "{\"code\":\"InternalServerError\",\"original\":{\"message\":\"No matching rent category for brand Hyundai\"}}"));

        SnapRequestDto request = new SnapRequestDto("branch-1", List.of("690000000000000000000001"),
            "2026-04-15", "2026-04-29", "next-available", "Hyundai", null, true, null, null);

        List<SnapResponseDto> result = service.getSnaps(request);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Safety net is narrow: an unrelated SoftNet 500 is NOT swallowed")
    void getSnaps_otherServerError_isRethrown() {
        when(softnetApi.getSnaps(any(), eq("mock-group-id")))
            .thenThrow(softnet500("{\"code\":\"InternalServerError\",\"message\":\"database down\"}"));

        SnapRequestDto request = new SnapRequestDto("branch-1", List.of("690000000000000000000001"),
            "2026-04-15", "2026-04-29", "next-available", "BMW", null, true, null, null);

        assertThrows(HttpServerErrorException.class, () -> service.getSnaps(request));
    }

    @Test
    @DisplayName("Safety net only applies to the rental path: a non-rental 'No matching rent category' 500 is rethrown")
    void getSnaps_nonRentalNoMatchingRentCategory_isRethrown() {
        when(softnetApi.getSnaps(any(), eq("mock-group-id")))
            .thenThrow(softnet500("{\"original\":{\"message\":\"No matching rent category for brand X\"}}"));

        SnapRequestDto request = new SnapRequestDto("branch-1", List.of("690000000000000000000001"),
            "2026-04-15", "2026-04-29", "next-available", "BMW", null, false, null, null);

        assertThrows(HttpServerErrorException.class, () -> service.getSnaps(request));
    }

    @Test
    void getSnaps_emptyCheckInTime_skipped() {
        ObjectNode snap = om.createObjectNode();
        snap.put("internal", "snap-x");
        snap.put("checkInTime", "");

        when(softnetApi.getSnaps(any(), eq("mock-group-id"))).thenReturn(List.of(snap));

        SnapRequestDto request = new SnapRequestDto("branch-1", List.of("690000000000000000000003"), "2026-04-15", "2026-04-29", "next-available", null, null, null, null, null);
        List<SnapResponseDto> result = service.getSnaps(request);
        assertTrue(result.isEmpty());
    }

    @Test
    void getSnaps_unresolvableServices_failFastWith400InsteadOfCallingSoftnet() {
        // REQ-035: frontend key that matches no operation in the group → mapper yields
        // an empty list. SoftNet would answer an opaque 400 ("Missing operations list!"),
        // so the service must fail fast with a clear message and never call SoftNet.
        SnapRequestDto request = new SnapRequestDto("branch-1", List.of("stored-tires"), "2026-04-15", "2026-04-29", "next-available", "BMW", null, null, null, null);

        var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> service.getSnaps(request));

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(String.valueOf(ex.getReason()).contains("mock-group-id"));
        verify(softnetApi, never()).getSnaps(any(), anyString());
    }

    @Test
    void getSnaps_timeSlotFormat() {
        ObjectNode snap = om.createObjectNode();
        snap.put("internal", "snap-fmt");
        snap.put("checkInTime", "2026-04-15T08:05:00.000Z");
        snap.put("checkInResource", "WS A");

        when(softnetApi.getSnaps(any(), eq("mock-group-id"))).thenReturn(List.of(snap));

        SnapRequestDto request = new SnapRequestDto("branch-1", List.of("690000000000000000000001"), "2026-04-15", "2026-04-29", "next-available", null, null, null, null, null);
        List<SnapResponseDto> result = service.getSnaps(request);

        assertEquals(1, result.size());
        var slot = result.get(0).slots().get(0);
        assertEquals("snap-fmt", slot.id());
        assertEquals("08:05", slot.time());
        assertEquals("08:05 Uhr", slot.displayTime());
        assertTrue(slot.available());
        assertEquals("WS A", slot.resource());
    }

    // ─── REQ-039 Abschnitt 18: rental car availability per slot ───

    private ObjectNode snapAt(String internal, String checkInTime, String replacementResource) {
        ObjectNode snap = om.createObjectNode();
        snap.put("internal", internal);
        snap.put("checkInTime", checkInTime);
        if (replacementResource != null) {
            snap.put("replacementResource", replacementResource);
        } else {
            snap.putNull("replacementResource");
        }
        return snap;
    }

    private SnapRequestDto request(String brand, Boolean replacementCar) {
        return new SnapRequestDto("branch-1", List.of("690000000000000000000001"), "2026-04-15", "2026-04-29",
            "next-available", brand, null, replacementCar, null, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getSnaps_withRentalCar_sendsReplacementPreferenceWithResolvedBrandAndNoCategory() {
        when(brandResolver.resolve("mock-group-id", "audi")).thenReturn("Audi");
        when(softnetApi.getSnaps(any(), eq("mock-group-id"))).thenReturn(List.of());

        service.getSnaps(request("audi", true));

        org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(softnetApi).getSnaps(captor.capture(), eq("mock-group-id"));
        java.util.Map<String, Object> sent = (java.util.Map<String, Object>) captor.getValue();
        assertEquals(Boolean.TRUE, sent.get("replacementPreference"));
        assertEquals("Audi", sent.get("brand"));
        assertEquals(java.util.Map.of("brand", "Audi"), sent.get("car"));
        assertFalse(sent.containsKey("rentCategoryid"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getSnaps_withoutRentalCar_keepsRequestUnchanged() {
        when(softnetApi.getSnaps(any(), eq("mock-group-id"))).thenReturn(List.of());

        service.getSnaps(request("audi", null));

        org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(softnetApi).getSnaps(captor.capture(), eq("mock-group-id"));
        java.util.Map<String, Object> sent = (java.util.Map<String, Object>) captor.getValue();
        assertFalse(sent.containsKey("replacementPreference"));
        assertEquals("audi", sent.get("brand"));
        verifyNoInteractions(brandResolver);
    }

    @Test
    void getSnaps_withRentalCar_offersOnlySlotsWithReservedCarButCachesAll() {
        when(brandResolver.resolve(anyString(), anyString())).thenReturn("Audi");
        List<com.fasterxml.jackson.databind.JsonNode> raw = List.of(
            snapAt("with-car", "2026-04-15T08:00:00.000Z", "Kleinwagen"),
            snapAt("without-car", "2026-04-15T09:00:00.000Z", null),
            snapAt("blank-car", "2026-04-15T10:00:00.000Z", " "),
            snapAt("other-day", "2026-04-16T08:00:00.000Z", "Mittelklasse"));
        when(softnetApi.getSnaps(any(), eq("mock-group-id"))).thenReturn(raw);

        List<SnapResponseDto> result = service.getSnaps(request("audi", true));

        assertEquals(2, result.size());
        assertEquals(List.of("with-car"), result.get(0).slots().stream().map(SnapResponseDto.TimeSlotDto::id).toList());
        assertEquals("other-day", result.get(1).slots().getFirst().id());
        assertTrue(result.stream().flatMap(d -> d.slots().stream()).allMatch(SnapResponseDto.TimeSlotDto::rentalCarIncluded));
        verify(snapCache).cacheSnaps(raw);
    }

    @Test
    void getSnaps_withRentalCar_noReservedCar_returnsEmptyList() {
        when(brandResolver.resolve(anyString(), anyString())).thenReturn("Audi");
        when(softnetApi.getSnaps(any(), eq("mock-group-id")))
            .thenReturn(List.of(snapAt("without-car", "2026-04-15T09:00:00.000Z", null)));

        assertTrue(service.getSnaps(request("audi", true)).isEmpty());
    }

    @Test
    void getSnaps_withoutRentalCar_marksSlotsWithoutRentalCar() {
        when(softnetApi.getSnaps(any(), eq("mock-group-id")))
            .thenReturn(List.of(snapAt("plain", "2026-04-15T09:00:00.000Z", null)));

        List<SnapResponseDto> result = service.getSnaps(request("audi", false));

        assertFalse(result.getFirst().slots().getFirst().rentalCarIncluded());
    }
}
