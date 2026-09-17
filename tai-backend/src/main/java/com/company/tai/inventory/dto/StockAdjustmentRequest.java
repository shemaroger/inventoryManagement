package com.company.tai.inventory.dto;

import com.company.tai.inventory.entity.AdjustmentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record StockAdjustmentRequest(
        @NotNull Long productId,
        @NotNull Long warehouseId,
        @NotNull AdjustmentType adjustmentType,
        @NotNull @Positive BigDecimal quantity,
        String reason
) {}
