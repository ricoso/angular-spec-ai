package com.autohaus.backend.customers;

import com.autohaus.backend.customers.dto.CreateCustomerRequest;
import com.autohaus.backend.customers.dto.CustomerResponseDto;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomersServiceTest {

    @Mock private SoftnetApiClient softnetApi;
    private final ObjectMapper om = new ObjectMapper();
    private CustomersService service;

    @BeforeEach
    void setUp() {
        service = new CustomersService(softnetApi);
    }

    @Test
    void createCustomer_mapsFieldsCorrectly() {
        ObjectNode result = om.createObjectNode();
        result.put("id", "v-1");
        result.put("email", "test@test.de");
        result.put("firstName", "Max");
        result.put("lastName", "Muster");

        when(softnetApi.createVaultItem(any())).thenReturn(result);

        CreateCustomerRequest dto = new CreateCustomerRequest(
            "test@test.de", "mr", "Max", "Muster", "Straße 1", "12345", "Berlin", "+491701234567"
        );

        CustomerResponseDto response = service.createCustomer(dto);
        assertEquals("v-1", response.id());
        assertEquals("test@test.de", response.email());
    }

    @Test
    void searchCustomers_returnsResults() {
        ObjectNode r = om.createObjectNode();
        r.put("id", "v-2");
        r.put("email", "found@test.de");

        when(softnetApi.searchVaultItems(any())).thenReturn(List.of(r));

        List<CustomerResponseDto> results = service.searchCustomers(null, null, "found@test.de");
        assertEquals(1, results.size());
        assertEquals("found@test.de", results.get(0).email());
    }
}
