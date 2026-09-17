package com.company.tai.ai.dto;

import java.math.BigDecimal;

public record AtRiskItemDto(
        Long productId,
        String productName,
        Long warehouseId,
        String warehouseName,
        BigDecimal currentStock,
        BigDecimal reorderLevel,
        BigDecimal projectedDemand,
        Integer daysUntilStockout,
        String confidence
) {}
