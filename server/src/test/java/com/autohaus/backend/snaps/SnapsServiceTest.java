package com.autohaus.backend.snaps;

import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.snaps.dto.SnapRequestDto;
import com.autohaus.backend.snaps.dto.SnapResponseDto;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private final ObjectMapper om = new ObjectMapper();
    private SnapsService service;

    @BeforeEach
    void setUp() {
        when(setupService.resolveGroupId(anyString(), any())).thenReturn("mock-group-id");
        // Operations API returns empty list — mapper passes through hex IDs as-is,
        // drops frontend keys. Tests below use hex IDs in SnapRequestDto.
        when(softnetApi.getOperations(anyString())).thenReturn(List.of());
        OperationIdMapper mapper = new OperationIdMapper(softnetApi);
        service = new SnapsService(softnetApi, mapper, snapCache, setupService);
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

        SnapRequestDto request = new SnapRequestDto("branch-1", List.of("inspection"), "2026-04-15", "2026-04-29", "next-available", "BMW", null, null, null, null);

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

        SnapRequestDto request = new SnapRequestDto("branch-1", List.of("oil-change"), "2026-04-15", "2026-04-29", "date-range", null, null, null, null, null);
        List<SnapResponseDto> result = service.getSnaps(request);
        assertTrue(result.isEmpty());
    }

    @Test
    void getSnaps_withTimePreferenceAndReplacementCar() {
        when(softnetApi.getSnaps(any(), eq("mock-group-id"))).thenReturn(List.of());

        SnapRequestDto request = new SnapRequestDto("branch-1", List.of("inspection"), "2026-04-15", "2026-04-29", "next-available", "BMW", "AM", true, null, null);
        List<SnapResponseDto> result = service.getSnaps(request);
        assertTrue(result.isEmpty());
        verify(softnetApi).getSnaps(any(), eq("mock-group-id"));
    }

    @Test
    void getSnaps_emptyCheckInTime_skipped() {
        ObjectNode snap = om.createObjectNode();
        snap.put("internal", "snap-x");
        snap.put("checkInTime", "");

        when(softnetApi.getSnaps(any(), eq("mock-group-id"))).thenReturn(List.of(snap));

        SnapRequestDto request = new SnapRequestDto("branch-1", List.of("tuv"), "2026-04-15", "2026-04-29", "next-available", null, null, null, null, null);
        List<SnapResponseDto> result = service.getSnaps(request);
        assertTrue(result.isEmpty());
    }

    @Test
    void getSnaps_timeSlotFormat() {
        ObjectNode snap = om.createObjectNode();
        snap.put("internal", "snap-fmt");
        snap.put("checkInTime", "2026-04-15T08:05:00.000Z");
        snap.put("checkInResource", "WS A");

        when(softnetApi.getSnaps(any(), eq("mock-group-id"))).thenReturn(List.of(snap));

        SnapRequestDto request = new SnapRequestDto("branch-1", List.of("inspection"), "2026-04-15", "2026-04-29", "next-available", null, null, null, null, null);
        List<SnapResponseDto> result = service.getSnaps(request);

        assertEquals(1, result.size());
        var slot = result.get(0).slots().get(0);
        assertEquals("snap-fmt", slot.id());
        assertEquals("08:05", slot.time());
        assertEquals("08:05 Uhr", slot.displayTime());
        assertTrue(slot.available());
        assertEquals("WS A", slot.resource());
    }
}
