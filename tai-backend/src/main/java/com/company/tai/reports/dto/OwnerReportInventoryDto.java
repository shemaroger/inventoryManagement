package com.company.tai.reports.dto;

import java.math.BigDecimal;
import java.util.List;

public record OwnerReportInventoryDto(
        BigDecimal totalStockValue,
        int lowStockCount,
        List<OwnerReportLowStockItemDto> lowStockItems,
        List<OwnerReportWarehouseValueDto> stockByWarehouse
) {}
