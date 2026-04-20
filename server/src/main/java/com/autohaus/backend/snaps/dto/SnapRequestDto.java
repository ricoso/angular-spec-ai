package com.autohaus.backend.snaps.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record SnapRequestDto(
    @NotBlank String branchId,
    @NotEmpty List<String> jobTypeIds,
    @NotBlank String fromDate,
    @NotBlank String toDate,
    @Pattern(regexp = "^(next-available|date-range)$") String mode,
    String brandName,
    String timePreference,
    Boolean replacementCar
) {}
