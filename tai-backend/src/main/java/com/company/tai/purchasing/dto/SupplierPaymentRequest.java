package com.company.tai.purchasing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierPaymentRequest(
        @NotNull @Positive BigDecimal amount,
        LocalDate paymentDate,
        String notes
) {}
