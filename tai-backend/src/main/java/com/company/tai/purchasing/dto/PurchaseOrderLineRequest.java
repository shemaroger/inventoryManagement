package com.company.tai.purchasing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record PurchaseOrderLineRequest(
        @NotNull Long productId,
        @NotNull @Positive BigDecimal quantityOrdered,
        @NotNull @PositiveOrZero BigDecimal unitCost
) {}
