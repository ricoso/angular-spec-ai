package com.autohaus.backend.health;

import com.autohaus.backend.softnet.SoftnetApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

    @Mock
    private SoftnetApiClient softnetApiClient;

    private HealthService healthService;

    @BeforeEach
    void setUp() {
        healthService = new HealthService(softnetApiClient);
    }

    @Test
    void checkHealth_whenSoftnetHealthy_returnsHealthy() {
        when(softnetApiClient.ping()).thenReturn(new SoftnetApiClient.PingResult(true, 100));

        HealthService.HealthStatus status = healthService.checkHealth();
        assertEquals("healthy", status.status());
        assertTrue(status.uptime() >= 0);
        assertNotNull(status.timestamp());
        assertEquals("healthy", status.dependencies().softnet().status());
        assertEquals(100, status.dependencies().softnet().responseTime());
    }

    @Test
    void checkHealth_whenSoftnetUnhealthy_returnsDegraded() {
        when(softnetApiClient.ping()).thenReturn(new SoftnetApiClient.PingResult(false, 5000));

        HealthService.HealthStatus status = healthService.checkHealth();
        assertEquals("degraded", status.status());
        assertEquals("unhealthy", status.dependencies().softnet().status());
    }
}
