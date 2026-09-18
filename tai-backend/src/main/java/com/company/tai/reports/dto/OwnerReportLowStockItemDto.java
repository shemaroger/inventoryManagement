package com.company.tai.reports.dto;

import java.math.BigDecimal;

public record OwnerReportLowStockItemDto(
        Long productId,
        String productName,
        String warehouseName,
        BigDecimal quantity,
        BigDecimal reorderLevel
) {}
