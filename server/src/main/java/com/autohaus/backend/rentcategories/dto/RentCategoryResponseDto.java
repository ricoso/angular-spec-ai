package com.autohaus.backend.rentcategories.dto;

import java.util.List;

public record RentCategoryResponseDto(
    String id,
    String groupId,
    String name,
    String description,
    List<String> brands,
    List<String> branches
) {}
