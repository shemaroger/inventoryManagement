package com.company.tai.reports.dto;

import com.company.tai.analytics.dto.SupplierPerformanceLineDto;

import java.math.BigDecimal;
import java.util.List;

public record OwnerReportPurchasingDto(
        BigDecimal totalPurchases,
        List<SupplierPerformanceLineDto> topSuppliers,
        // Total owed to suppliers right now across all purchase orders — not period-scoped.
        BigDecimal totalPayables
) {}
