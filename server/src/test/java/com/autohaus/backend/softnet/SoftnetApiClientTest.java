package com.autohaus.backend.softnet;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.autohaus.backend.config.SoftnetProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpServerErrorException;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SoftnetApiClientTest {

    @Mock
    private SoftnetAuthService authService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private SoftnetApiClient client;

    @BeforeEach
    void setUp() {
        SoftnetProperties props = new SoftnetProperties(
            "http://nonexistent.local:9999", "http://auth", "/fp", null, 1000
        );
        client = new SoftnetApiClient(authService, props, objectMapper);
    }

    @Test
    void ping_whenAuthFails_returnsUnhealthy() {
        when(authService.getAccessToken(any())).thenThrow(new RuntimeException("No token"));

        SoftnetApiClient.PingResult result = client.ping();

        assertFalse(result.healthy());
        assertTrue(result.responseTime() >= 0);
    }

    @Test
    void ping_whenConnectionFails_returnsUnhealthy() {
        when(authService.getAccessToken(any())).thenReturn("fake-token");

        SoftnetApiClient.PingResult result = client.ping();

        assertFalse(result.healthy());
        assertTrue(result.responseTime() >= 0);
    }

    @Test
    void pingResult_record_accessors() {
        SoftnetApiClient.PingResult healthy = new SoftnetApiClient.PingResult(true, 123);
        assertTrue(healthy.healthy());
        assertEquals(123, healthy.responseTime());

        SoftnetApiClient.PingResult unhealthy = new SoftnetApiClient.PingResult(false, 456);
        assertFalse(unhealthy.healthy());
        assertEquals(456, unhealthy.responseTime());
    }

    @Test
    void pingResult_record_equals() {
        SoftnetApiClient.PingResult a = new SoftnetApiClient.PingResult(true, 100);
        SoftnetApiClient.PingResult b = new SoftnetApiClient.PingResult(true, 100);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void getGroups_whenAuthFails_throwsRuntimeException() {
        when(authService.getAccessToken(any())).thenThrow(new RuntimeException("No token"));
        assertThrows(RuntimeException.class, () -> client.getGroups());
    }

    @Test
    void getBranches_whenConnectionFails_throwsException() {
        when(authService.getAccessToken(any())).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.getBranches());
    }

    @Test
    void getBranch_whenConnectionFails_throwsException() {
        when(authService.getAccessToken(any())).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.getBranch("some-id"));
    }

    @Test
    void getBrands_whenConnectionFails_throwsException() {
        when(authService.getAccessToken(any())).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.getBrands("group-1"));
    }

    @Test
    void getJobTypes_whenConnectionFails_throwsException() {
        when(authService.getAccessToken(any())).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.getJobTypes("group-1"));
    }

    @Test
    void getOperations_whenConnectionFails_throwsException() {
        when(authService.getAccessToken(any())).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.getOperations("group-1"));
    }

    @Test
    void getMobilityAlternatives_whenConnectionFails_throwsException() {
        when(authService.getAccessToken(any())).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.getMobilityAlternatives("group-1"));
    }

    @Test
    void getGroup_whenAuthFails_throwsRuntimeException() {
        when(authService.getAccessToken(any())).thenThrow(new RuntimeException("No token"));
        assertThrows(RuntimeException.class, () -> client.getGroup("g1"));
    }

    @Test
    void createVaultItem_whenConnectionFails_throwsException() {
        when(authService.getAccessToken(any())).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.createVaultItem(java.util.Map.of("email", "test@test.de"), "group-1"));
    }

    @Test
    void searchVaultItems_emptyQuery_whenConnectionFails_throwsException() {
        when(authService.getAccessToken(any())).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.searchVaultItems(java.util.Map.of(), "group-1"));
    }

    @Test
    void searchVaultItems_withQuery_whenConnectionFails_throwsException() {
        when(authService.getAccessToken(any())).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.searchVaultItems(java.util.Map.of("email", "test@test.de"), "group-1"));
    }

    @Test
    void createCar_whenConnectionFails_throwsException() {
        when(authService.getAccessToken(any())).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.createCar(java.util.Map.of("licensePlate", "B-MS1234"), "group-1"));
    }

    @Test
    void createAppointment_whenConnectionFails_throwsException() {
        when(authService.getAccessToken(any())).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.createAppointment(java.util.Map.of("snapId", "s1"), "group-1"));
    }

    @Test
    void getAppointment_whenConnectionFails_throwsException() {
        when(authService.getAccessToken(any())).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.getAppointment("a1", "group-1"));
    }

    @Test
    void cancelAppointment_whenConnectionFails_throwsException() {
        when(authService.getAccessToken(any())).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.cancelAppointment("a1", "group-1"));
    }

    @Test
    void getSnaps_whenConnectionFails_throwsException() {
        when(authService.getAccessToken(any())).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.getSnaps(java.util.Map.of("branchId", "b1"), "group-1"));
    }

    @Test
    void createAppointment_when502_logsSoftnetErrorWithRequestAndResponseBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/planning/appointments", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"error\":\"upstream boom\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(502, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(SoftnetApiClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            SoftnetProperties props = new SoftnetProperties(
                "http://localhost:" + server.getAddress().getPort(), "http://auth", "/fp", null, 1000
            );
            SoftnetApiClient localClient = new SoftnetApiClient(authService, props, objectMapper);
            when(authService.getAccessToken(any())).thenReturn("fake-token");

            assertThrows(HttpServerErrorException.class,
                () -> localClient.createAppointment(java.util.Map.of("snapId", "s1"), "group-1"));

            String errorLine = appender.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .map(ILoggingEvent::getFormattedMessage)
                .findFirst()
                .orElse("");
            assertTrue(errorLine.contains("SOFTNET_ERROR"), errorLine);
            assertTrue(errorLine.contains("\"snapId\":\"s1\""), errorLine);
            assertTrue(errorLine.contains("upstream boom"), errorLine);
        } finally {
            logger.detachAppender(appender);
            server.stop(0);
        }
    }

    @Test
    void createAppointment_whenError_redactsPiiInRequestBodyLog() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/planning/appointments", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"code\":\"ValidationError\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(502, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(SoftnetApiClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            SoftnetProperties props = new SoftnetProperties(
                "http://localhost:" + server.getAddress().getPort(), "http://auth", "/fp", null, 1000
            );
            SoftnetApiClient localClient = new SoftnetApiClient(authService, props, objectMapper);
            when(authService.getAccessToken(any())).thenReturn("fake-token");

            java.util.Map<String, Object> requestWithPii = java.util.Map.of(
                "snapId", "s1",
                "wishlist", java.util.Map.of(
                    "client", java.util.Map.of("firstName", "Max", "email", "max@example.com"),
                    "car", java.util.Map.of("license", "AB-CD 1234", "VIN", "WVWZZZ1JZXW000001")));

            assertThrows(Exception.class, () -> localClient.createAppointment(requestWithPii, "group-1"));

            String errorLine = appender.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .map(ILoggingEvent::getFormattedMessage)
                .findFirst()
                .orElse("");

            assertTrue(errorLine.contains("SOFTNET_ERROR"), errorLine);
            // PII from the outbound body must NOT appear in the log line.
            assertFalse(errorLine.contains("Max"), errorLine);
            assertFalse(errorLine.contains("max@example.com"), errorLine);
            assertFalse(errorLine.contains("AB-CD 1234"), errorLine);
            assertFalse(errorLine.contains("WVWZZZ1JZXW000001"), errorLine);
            assertTrue(errorLine.contains("***"), errorLine);
            // Technical, non-PII fields survive for analysis.
            assertTrue(errorLine.contains("\"snapId\":\"s1\""), errorLine);
        } finally {
            logger.detachAppender(appender);
            server.stop(0);
        }
    }

    @Test
    void constructor_withNullBaseUrl_doesNotThrow() {
        SoftnetProperties props = new SoftnetProperties(
            null, "http://auth", "/fp", null, 1000
        );
        SoftnetApiClient nullUrlClient = new SoftnetApiClient(authService, props, objectMapper);
        assertNotNull(nullUrlClient);
    }
}
