package com.company.tai.reports.dto;

import com.company.tai.analytics.dto.CustomerPerformanceLineDto;
import com.company.tai.analytics.dto.ProfitByProductLineDto;

import java.math.BigDecimal;
import java.util.List;

public record OwnerReportSalesDto(
        BigDecimal totalSales,
        int saleCount,
        List<ProfitByProductLineDto> topProducts,
        List<CustomerPerformanceLineDto> topCustomers,
        // Total customer balances outstanding right now, across all customers — not period-scoped.
        BigDecimal totalReceivables
) {}
