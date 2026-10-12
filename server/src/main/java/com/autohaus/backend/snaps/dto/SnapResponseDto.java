package com.autohaus.backend.snaps.dto;

import java.util.List;

public record SnapResponseDto(String date, String displayDate, String dayAbbreviation, List<TimeSlotDto> slots) {
    /** REQ-039 AC-M10: {@code rentalCarIncluded} = SoftNet reserved a rental car for this slot. */
    public record TimeSlotDto(String id, String time, String displayTime, boolean available, String resource,
                              boolean rentalCarIncluded) {}
}
