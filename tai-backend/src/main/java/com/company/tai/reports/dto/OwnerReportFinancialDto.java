package com.company.tai.reports.dto;

import java.math.BigDecimal;

public record OwnerReportFinancialDto(
        BigDecimal totalRevenue,
        BigDecimal totalExpenses,
        BigDecimal netIncome,
        BigDecimal grossMargin,
        BigDecimal vatPayable,
        // Current balance of the Cash account, as of now — not scoped to the report period,
        // since "how much cash do I have right now" is what an owner actually wants to know.
        BigDecimal cashBalance
) {}
