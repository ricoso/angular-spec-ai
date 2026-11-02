package com.autohaus.backend.common.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    void of_createsWithData() {
        ApiResponse<String> response = ApiResponse.of("test-data", "req-123");
        assertEquals("test-data", response.data());
        assertNotNull(response.meta());
        assertEquals("req-123", response.meta().requestId());
        assertNotNull(response.meta().timestamp());
    }

    @Test
    void of_withListData() {
        ApiResponse<java.util.List<String>> response = ApiResponse.of(java.util.List.of("a", "b"), "req-456");
        assertEquals(2, response.data().size());
    }
}
