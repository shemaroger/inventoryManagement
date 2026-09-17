package com.company.tai.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProfitByCategoryReportDto(
        LocalDate startDate,
        LocalDate endDate,
        boolean isApproximate,
        List<ProfitByCategoryLineDto> lines,
        BigDecimal totalRevenue,
        BigDecimal totalCost,
        BigDecimal totalMargin
) {}
