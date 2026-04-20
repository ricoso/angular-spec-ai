package com.autohaus.backend.customers;

import com.autohaus.backend.customers.dto.CreateCustomerRequest;
import com.autohaus.backend.customers.dto.CustomerResponseDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomersController {

    private final CustomersService customersService;

    public CustomersController(CustomersService customersService) {
        this.customersService = customersService;
    }

    @PostMapping
    public CustomerResponseDto createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        return customersService.createCustomer(request);
    }

    @GetMapping("/search")
    public List<CustomerResponseDto> searchCustomers(
            @RequestParam(required = false) String licensePlate,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String email) {
        return customersService.searchCustomers(licensePlate, lastName, email);
    }
}
