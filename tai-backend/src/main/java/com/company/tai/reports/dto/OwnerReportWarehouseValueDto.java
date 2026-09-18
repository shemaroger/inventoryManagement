package com.company.tai.reports.dto;

import java.math.BigDecimal;

public record OwnerReportWarehouseValueDto(
        Long warehouseId,
        String warehouseName,
        BigDecimal stockValue
) {}
