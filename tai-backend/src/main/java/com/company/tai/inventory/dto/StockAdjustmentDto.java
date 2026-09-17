package com.company.tai.inventory.dto;

import com.company.tai.inventory.entity.AdjustmentType;

import java.math.BigDecimal;
import java.time.Instant;

public record StockAdjustmentDto(
        Long id,
        Long productId,
        String productName,
        Long warehouseId,
        String warehouseName,
        AdjustmentType adjustmentType,
        BigDecimal quantity,
        String reason,
        String performedByName,
        Instant createdAt
) {}
