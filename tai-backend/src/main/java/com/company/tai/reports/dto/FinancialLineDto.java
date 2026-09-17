package com.company.tai.reports.dto;

import java.math.BigDecimal;

public record FinancialLineDto(
        String accountCode,
        String accountName,
        BigDecimal amount
) {}
