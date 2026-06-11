package com.autohaus.backend.jobtypes.dto;

/**
 * REQ-025 (b) Iter. 3: Service-Kategorie aus dem SoftNet-Tags-Endpoint.
 * Cards auf der Services-Page entsprechen 1:1 diesen DTOs.
 */
public record CategoryDto(
    String id,
    String name,
    int rank
) {}
