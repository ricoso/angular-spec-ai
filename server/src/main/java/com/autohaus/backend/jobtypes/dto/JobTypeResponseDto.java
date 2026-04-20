package com.autohaus.backend.jobtypes.dto;

import java.util.List;

public record JobTypeResponseDto(String id, String name, String description, List<OperationDto> operations) {
    public record OperationDto(String id, String name) {}
}
