package com.company.tai.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CustomerStatementEntryDto(
        LocalDate date,
        String type,
        String description,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal runningBalance
) {}
