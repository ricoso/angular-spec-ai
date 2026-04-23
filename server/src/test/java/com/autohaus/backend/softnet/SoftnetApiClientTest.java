package com.autohaus.backend.softnet;

import com.autohaus.backend.config.SoftnetProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
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
            "http://nonexistent.local:9999", "http://auth", "/fp", 1000
        );
        client = new SoftnetApiClient(authService, props, objectMapper);
    }

    @Test
    void ping_whenAuthFails_returnsUnhealthy() {
        when(authService.getAccessToken()).thenThrow(new RuntimeException("No token"));

        SoftnetApiClient.PingResult result = client.ping();

        assertFalse(result.healthy());
        assertTrue(result.responseTime() >= 0);
    }

    @Test
    void ping_whenConnectionFails_returnsUnhealthy() {
        when(authService.getAccessToken()).thenReturn("fake-token");

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
        when(authService.getAccessToken()).thenThrow(new RuntimeException("No token"));
        assertThrows(RuntimeException.class, () -> client.getGroups());
    }

    @Test
    void getBranches_whenConnectionFails_throwsException() {
        when(authService.getAccessToken()).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.getBranches());
    }

    @Test
    void getBranch_whenConnectionFails_throwsException() {
        when(authService.getAccessToken()).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.getBranch("some-id"));
    }

    @Test
    void getBrands_whenConnectionFails_throwsException() {
        when(authService.getAccessToken()).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.getBrands("group-1"));
    }

    @Test
    void getJobTypes_whenConnectionFails_throwsException() {
        when(authService.getAccessToken()).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.getJobTypes("group-1"));
    }

    @Test
    void getOperations_whenConnectionFails_throwsException() {
        when(authService.getAccessToken()).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.getOperations("group-1"));
    }

    @Test
    void getMobilityAlternatives_whenConnectionFails_throwsException() {
        when(authService.getAccessToken()).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.getMobilityAlternatives("group-1"));
    }

    @Test
    void getGroup_whenAuthFails_throwsRuntimeException() {
        when(authService.getAccessToken()).thenThrow(new RuntimeException("No token"));
        assertThrows(RuntimeException.class, () -> client.getGroup("g1"));
    }

    @Test
    void createVaultItem_whenConnectionFails_throwsException() {
        when(authService.getAccessToken()).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.createVaultItem(java.util.Map.of("email", "test@test.de"), "group-1"));
    }

    @Test
    void searchVaultItems_emptyQuery_whenConnectionFails_throwsException() {
        when(authService.getAccessToken()).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.searchVaultItems(java.util.Map.of(), "group-1"));
    }

    @Test
    void searchVaultItems_withQuery_whenConnectionFails_throwsException() {
        when(authService.getAccessToken()).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.searchVaultItems(java.util.Map.of("email", "test@test.de"), "group-1"));
    }

    @Test
    void createCar_whenConnectionFails_throwsException() {
        when(authService.getAccessToken()).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.createCar(java.util.Map.of("licensePlate", "B-MS1234"), "group-1"));
    }

    @Test
    void createAppointment_whenConnectionFails_throwsException() {
        when(authService.getAccessToken()).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.createAppointment(java.util.Map.of("snapId", "s1"), "group-1"));
    }

    @Test
    void getAppointment_whenConnectionFails_throwsException() {
        when(authService.getAccessToken()).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.getAppointment("a1", "group-1"));
    }

    @Test
    void cancelAppointment_whenConnectionFails_throwsException() {
        when(authService.getAccessToken()).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.cancelAppointment("a1", "group-1"));
    }

    @Test
    void getSnaps_whenConnectionFails_throwsException() {
        when(authService.getAccessToken()).thenReturn("fake-token");
        assertThrows(Exception.class, () -> client.getSnaps(java.util.Map.of("branchId", "b1"), "group-1"));
    }

    @Test
    void constructor_withNullBaseUrl_doesNotThrow() {
        SoftnetProperties props = new SoftnetProperties(
            null, "http://auth", "/fp", 1000
        );
        SoftnetApiClient nullUrlClient = new SoftnetApiClient(authService, props, objectMapper);
        assertNotNull(nullUrlClient);
    }
}
