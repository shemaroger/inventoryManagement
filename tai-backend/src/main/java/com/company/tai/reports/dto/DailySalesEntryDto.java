package com.company.tai.reports.dto;

import com.company.tai.sales.entity.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySalesEntryDto(
        Long saleId,
        LocalDate saleDate,
        String customerName,
        PaymentType paymentType,
        BigDecimal amount
) {}
