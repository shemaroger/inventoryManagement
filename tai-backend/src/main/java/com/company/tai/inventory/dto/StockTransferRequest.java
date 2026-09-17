package com.company.tai.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record StockTransferRequest(
        @NotNull Long productId,
        @NotNull Long sourceWarehouseId,
        @NotNull Long destinationWarehouseId,
        @NotNull @Positive BigDecimal quantity,
        String reason
) {}
