package com.autohaus.backend.snaps.dto;

import java.util.List;

public record SnapResponseDto(String date, String displayDate, String dayAbbreviation, List<TimeSlotDto> slots) {
    public record TimeSlotDto(String id, String time, String displayTime, boolean available, String resource) {}
}
