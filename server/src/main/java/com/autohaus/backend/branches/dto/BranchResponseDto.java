package com.autohaus.backend.branches.dto;

import java.util.List;

public record BranchResponseDto(String id, String name, String description, List<String> brands) {}
