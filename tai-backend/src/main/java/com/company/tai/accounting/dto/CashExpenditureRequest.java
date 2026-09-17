package com.company.tai.accounting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashExpenditureRequest(
        @NotNull LocalDate date,
        @NotBlank String description,
        @NotNull Long expenseAccountId,
        @NotNull @Positive BigDecimal amount,
        // "CASH" or "BANK" — which asset account the money actually came out of.
        @NotBlank String paymentSource
) {}
