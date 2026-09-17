package com.company.tai.reorder.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ReorderSuggestionDto(
        Long id,
        Long productId,
        String productName,
        String productSku,
        Long warehouseId,
        String warehouseName,
        BigDecimal currentQuantity,
        BigDecimal reorderLevel,
        BigDecimal suggestedQuantity,
        BigDecimal urgencyScore,
        String urgencyLevel,
        LocalDate projectedStockoutDate,
        String status,
        Long purchaseOrderId,
        String dismissReason,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt
) {}
