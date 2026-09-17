package com.company.tai.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CustomerRequest(
        @NotBlank String name,
        String contactPerson,
        String phone,
        String email,
        String address,
        @NotNull @PositiveOrZero BigDecimal creditLimit,
        String customerCategory
) {}
