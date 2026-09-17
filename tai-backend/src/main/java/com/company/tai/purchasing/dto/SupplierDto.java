package com.company.tai.purchasing.dto;

public record SupplierDto(
        Long id,
        String name,
        String contactPerson,
        String phone,
        String email,
        String address,
        boolean active
) {}
