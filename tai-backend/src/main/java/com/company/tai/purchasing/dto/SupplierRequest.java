package com.company.tai.purchasing.dto;

import jakarta.validation.constraints.NotBlank;

public record SupplierRequest(
        @NotBlank String name,
        String contactPerson,
        String phone,
        String email,
        String address
) {}
