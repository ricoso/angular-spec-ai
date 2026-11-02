package com.autohaus.backend.snaps.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SnapRequestDto(
    @NotBlank String branchId,
    @NotEmpty List<String> jobTypeIds,
    @NotBlank String fromDate,
    @NotBlank String toDate,
    @Pattern(regexp = "^(next-available|date-range)$") String mode,
    // REQ-039 BR-M6: brand id ends up in a WARN log line on mismatch — keep it short and plain.
    @Size(max = 50) @Pattern(regexp = "^[\\p{L}0-9 &.\\-]*$") String brandName,
    String timePreference,
    Boolean replacementCar,
    String variant,
    NotificationsDto notifications
) {
    /** REQ-027: SoftNet notifications channel ({ SMS, SMTP }). */
    public record NotificationsDto(Boolean SMS, Boolean SMTP) {}
}
