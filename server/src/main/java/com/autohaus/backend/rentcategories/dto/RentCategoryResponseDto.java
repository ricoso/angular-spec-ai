package com.autohaus.backend.rentcategories.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * REQ-025 (f): Rent-Category-Response erweitert um optionale Preisfelder.
 * Frontend rendert den Mietwagen-Preisblock nur, wenn `price` gesetzt ist
 * (graceful degradation).
 *
 * - currency: ISO-4217 (z. B. "EUR")
 * - priceUnit: "day" | "hour"
 */
public record RentCategoryResponseDto(
    String id,
    String groupId,
    String name,
    String description,
    BigDecimal price,
    String currency,
    String priceUnit,
    List<String> brands,
    List<String> branches
) {}
