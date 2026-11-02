package com.autohaus.backend.customers.dto;

import jakarta.validation.constraints.*;

public record CreateCustomerRequest(
    @Email String email,
    @Pattern(regexp = "^(mr|ms)$") String salutation,
    @NotBlank @Size(min = 2, max = 50) @Pattern(regexp = "^[a-zA-ZäöüÄÖÜß\\s\\-']+$") String firstName,
    @NotBlank @Size(min = 2, max = 50) @Pattern(regexp = "^[a-zA-ZäöüÄÖÜß\\s\\-']+$") String lastName,
    @NotBlank @Size(max = 100) String street,
    @NotBlank @Pattern(regexp = "^\\d{4,5}$") String postalCode,
    @NotBlank @Size(min = 2, max = 50) @Pattern(regexp = "^[a-zA-ZäöüÄÖÜß\\s\\-]+$") String city,
    @NotBlank @Pattern(regexp = "^\\+?[\\d\\s\\-/()]{6,20}$") String mobilePhone,
    @NotBlank String branchId
) {}
