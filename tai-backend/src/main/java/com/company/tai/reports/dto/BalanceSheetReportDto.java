package com.company.tai.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BalanceSheetReportDto(
        LocalDate asOfDate,
        List<FinancialLineDto> assets,
        List<FinancialLineDto> liabilities,
        List<FinancialLineDto> equity,
        // Net income earned since inception up to asOfDate, folded into equity as a computed
        // line — there is no period-closing process that moves Revenue/Expense balances into
        // Retained Earnings, so this is what keeps the statement mathematically balanced.
        BigDecimal currentEarnings,
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        BigDecimal totalEquity
) {}
