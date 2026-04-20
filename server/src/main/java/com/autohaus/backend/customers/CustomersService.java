package com.autohaus.backend.customers;

import com.autohaus.backend.customers.dto.CreateCustomerRequest;
import com.autohaus.backend.customers.dto.CustomerResponseDto;
import com.autohaus.backend.softnet.SoftnetApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CustomersService {

    private final SoftnetApiClient softnetApi;

    public CustomersService(SoftnetApiClient softnetApi) {
        this.softnetApi = softnetApi;
    }

    public CustomerResponseDto createCustomer(CreateCustomerRequest dto) {
        Map<String, Object> data = new HashMap<>();
        data.put("email", dto.email());
        data.put("firstName", dto.firstName());
        data.put("lastName", dto.lastName());
        data.put("salutation", dto.salutation());
        data.put("street", dto.street());
        data.put("zip", dto.postalCode());
        data.put("city", dto.city());
        data.put("mobilePhone", dto.mobilePhone());

        JsonNode result = softnetApi.createVaultItem(data);
        return new CustomerResponseDto(
            result.path("id").asText(),
            result.path("email").asText(null),
            result.path("firstName").asText(null),
            result.path("lastName").asText(null)
        );
    }

    public List<CustomerResponseDto> searchCustomers(String licensePlate, String lastName, String email) {
        Map<String, String> query = new HashMap<>();
        if (licensePlate != null && !licensePlate.isBlank()) query.put("licensePlate", licensePlate);
        if (lastName != null && !lastName.isBlank()) query.put("lastName", lastName);
        if (email != null && !email.isBlank()) query.put("email", email);

        List<JsonNode> results = softnetApi.searchVaultItems(query);
        return results.stream()
            .map(r -> new CustomerResponseDto(
                r.path("id").asText(),
                r.path("email").asText(null),
                r.path("firstName").asText(null),
                r.path("lastName").asText(null)
            ))
            .toList();
    }
}
