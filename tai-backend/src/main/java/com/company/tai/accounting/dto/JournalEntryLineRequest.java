package com.company.tai.accounting.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record JournalEntryLineRequest(
        @NotNull Long accountId,
        @NotNull @PositiveOrZero BigDecimal debitAmount,
        @NotNull @PositiveOrZero BigDecimal creditAmount
) {}
