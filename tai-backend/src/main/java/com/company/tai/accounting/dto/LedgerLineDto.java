package com.company.tai.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LedgerLineDto(
        Long lineId,
        LocalDate date,
        String description,
        String reference,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal netAmount,
        boolean reconciled,
        BigDecimal runningBalance
) {}
