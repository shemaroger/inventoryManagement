package com.company.tai.sales.dto;

import com.company.tai.sales.entity.SalePaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalePaymentRequest(
        @NotNull @Positive BigDecimal amount,
        LocalDate paymentDate,
        @NotNull SalePaymentMethod paymentMethod,
        String notes
) {}
