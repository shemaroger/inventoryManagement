package com.company.tai.sales.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record SaleLineRequest(
        @NotNull Long productId,
        @NotNull @Positive BigDecimal quantity,
        // null unitPrice means "use the product's default sellingPrice (or applicable price list once Phase 2 lands)"
        BigDecimal unitPrice,
        @DecimalMin("0") @DecimalMax("100") BigDecimal discountPercent
) {}
