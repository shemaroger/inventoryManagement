package com.company.tai.sales.dto;

import com.company.tai.sales.entity.SalePaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalePaymentDto(
        Long id,
        Long saleId,
        BigDecimal amount,
        LocalDate paymentDate,
        SalePaymentMethod paymentMethod,
        String notes
) {}
