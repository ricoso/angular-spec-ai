package com.autohaus.backend.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, ApiResponseMeta meta) {
    public record ApiResponseMeta(String timestamp, String requestId) {}

    public static <T> ApiResponse<T> of(T data, String requestId) {
        return new ApiResponse<>(data, new ApiResponseMeta(
            java.time.Instant.now().toString(),
            requestId
        ));
    }
}
