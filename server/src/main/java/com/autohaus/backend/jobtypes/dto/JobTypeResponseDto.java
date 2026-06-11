package com.autohaus.backend.jobtypes.dto;

import java.util.List;

/**
 * REQ-025 (a): JobType-Response erweitert um optionale Master-Data-Felder
 * (hint, category, categoryName, iconUrl). Frontend nutzt sie für graceful
 * degradation — wenn der SoftNet-Eintrag das Feld nicht liefert, wird es
 * im Frontend ausgeblendet (z. B. kein Hint-Card).
 */
public record JobTypeResponseDto(
    String id,
    String name,
    String description,
    String hint,
    String category,
    String categoryName,
    String iconUrl,
    List<OperationDto> operations
) {
    public record OperationDto(String id, String name) {}
}
