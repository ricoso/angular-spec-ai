package com.autohaus.backend.customers;

import com.autohaus.backend.config.setup.SetupService;
import com.autohaus.backend.customers.dto.CreateCustomerRequest;
import com.autohaus.backend.customers.dto.CustomerResponseDto;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomersServiceTest {

    @Mock private SoftnetApiClient softnetApi;
    @Mock private SetupService setupService;
    private final ObjectMapper om = new ObjectMapper();
    private CustomersService service;

    @BeforeEach
    void setUp() {
        when(setupService.resolveGroupId(anyString(), any())).thenReturn("mock-group-id");
        service = new CustomersService(softnetApi, setupService);
    }

    @Test
    void createCustomer_mapsFieldsCorrectly() {
        ObjectNode result = om.createObjectNode();
        result.put("id", "v-1");
        result.put("email", "test@test.de");
        result.put("firstName", "Max");
        result.put("lastName", "Muster");

        when(softnetApi.createVaultItem(any(), eq("mock-group-id"))).thenReturn(result);

        CreateCustomerRequest dto = new CreateCustomerRequest(
            "test@test.de", "mr", "Max", "Muster", "Straße 1", "12345", "Berlin", "+491701234567", "branch-1"
        );

        CustomerResponseDto response = service.createCustomer(dto);
        assertEquals("v-1", response.id());
        assertEquals("test@test.de", response.email());
    }

    @Test
    void createCustomer_mobilePhoneWithLeadingZero_normalizedToPlus49() {
        ObjectNode result = om.createObjectNode();
        result.put("id", "v-3");
        result.put("email", "test@test.de");

        when(softnetApi.createVaultItem(any(), eq("mock-group-id"))).thenReturn(result);

        CreateCustomerRequest dto = new CreateCustomerRequest(
            "test@test.de", "mr", "Max", "Muster", "Straße 1", "12345", "Berlin", "017012345678", "branch-1"
        );

        service.createCustomer(dto);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(softnetApi).createVaultItem(captor.capture(), eq("mock-group-id"));
        assertEquals("+4917012345678", captor.getValue().get("mobilePhone"));
    }

    @Test
    void searchCustomers_returnsResults() {
        ObjectNode r = om.createObjectNode();
        r.put("id", "v-2");
        r.put("email", "found@test.de");

        when(softnetApi.searchVaultItems(any(), eq("mock-group-id"))).thenReturn(List.of(r));

        List<CustomerResponseDto> results = service.searchCustomers(null, null, "found@test.de", "branch-1");
        assertEquals(1, results.size());
        assertEquals("found@test.de", results.get(0).email());
    }
}
