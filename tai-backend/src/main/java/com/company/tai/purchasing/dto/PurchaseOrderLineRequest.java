package com.company.tai.purchasing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PurchaseOrderLineRequest(
        @NotNull Long productId,
        @NotNull @Positive BigDecimal quantityOrdered,
        @NotNull BigDecimal unitCost
) {}
