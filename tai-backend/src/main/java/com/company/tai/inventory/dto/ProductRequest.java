package com.company.tai.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank String sku,
        String barcode,
        @NotBlank String name,
        String description,
        Long categoryId,
        Long brandId,
        Long unitId,
        @NotNull @PositiveOrZero BigDecimal costPrice,
        @NotNull @PositiveOrZero BigDecimal sellingPrice,
        @PositiveOrZero BigDecimal reorderLevel
) {}
