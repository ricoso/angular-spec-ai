package com.autohaus.backend.jobtypes.dto;

import java.util.List;

/**
 * REQ-025 (b) Iter. 3: Operation = einzelner Service aus dem SoftNet-Operations-Endpoint.
 * Sub-Optionen einer Card entsprechen Operations gefiltert nach `tagIds`.
 * `description` wird auf der Services-Page direkt unter dem Service-Namen angezeigt.
 */
public record OperationDto(
    String id,
    String name,
    String description,
    List<String> tagIds,
    Double order,
    String imageId
) {}
