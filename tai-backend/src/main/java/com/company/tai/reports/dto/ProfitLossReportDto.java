package com.company.tai.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProfitLossReportDto(
        LocalDate startDate,
        LocalDate endDate,
        List<FinancialLineDto> revenue,
        List<FinancialLineDto> expenses,
        BigDecimal totalRevenue,
        BigDecimal totalExpenses,
        BigDecimal netIncome
) {}
