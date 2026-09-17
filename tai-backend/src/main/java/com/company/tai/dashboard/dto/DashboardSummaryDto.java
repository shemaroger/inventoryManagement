package com.company.tai.dashboard.dto;

import java.math.BigDecimal;

// Money fields are nullable and set to null (not zero) when the caller's role shouldn't see
// them (STAFF) — the frontend must treat null as "not shown to you", never render it as 0.
public record DashboardSummaryDto(
        BigDecimal todaySalesTotal,
        Integer todaySalesCount,
        BigDecimal monthSalesTotal,
        long totalActiveProducts,
        BigDecimal totalStockValue,
        int lowStockCount,
        long openReorderCount,
        BigDecimal grossMarginThisMonth
) {}
