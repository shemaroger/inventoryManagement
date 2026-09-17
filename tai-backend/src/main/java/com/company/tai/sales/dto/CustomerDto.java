package com.company.tai.sales.dto;

import java.math.BigDecimal;

public record CustomerDto(
        Long id,
        String name,
        String contactPerson,
        String phone,
        String email,
        String address,
        BigDecimal creditLimit,
        BigDecimal currentBalance,
        String customerCategory,
        boolean active
) {}
