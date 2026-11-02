package com.autohaus.backend.health;

import com.autohaus.backend.softnet.SoftnetApiClient;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class HealthService {

    private final long startTime = System.currentTimeMillis();
    private final SoftnetApiClient softnetApiClient;

    public HealthService(SoftnetApiClient softnetApiClient) {
        this.softnetApiClient = softnetApiClient;
    }

    public HealthStatus checkHealth() {
        SoftnetApiClient.PingResult ping = softnetApiClient.ping();
        long uptime = (System.currentTimeMillis() - startTime) / 1000;

        return new HealthStatus(
            ping.healthy() ? "healthy" : "degraded",
            uptime,
            Instant.now().toString(),
            new HealthStatus.Dependencies(new HealthStatus.SoftnetStatus(
                ping.healthy() ? "healthy" : "unhealthy",
                ping.responseTime()
            ))
        );
    }

    public record HealthStatus(
        String status,
        long uptime,
        String timestamp,
        Dependencies dependencies
    ) {
        public record Dependencies(SoftnetStatus softnet) {}
        public record SoftnetStatus(String status, long responseTime) {}
    }
}
