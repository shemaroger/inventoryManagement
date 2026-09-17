package com.company.tai.accounting.dto;

import java.math.BigDecimal;

public record JournalEntryLineDto(
        Long id,
        Long accountId,
        String accountCode,
        String accountName,
        BigDecimal debitAmount,
        BigDecimal creditAmount
) {}
