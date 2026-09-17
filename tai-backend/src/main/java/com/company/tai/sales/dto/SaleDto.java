package com.company.tai.sales.dto;

import com.company.tai.sales.entity.PaymentType;
import com.company.tai.sales.entity.SaleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SaleDto(
        Long id,
        Long customerId,
        String customerName,
        Long warehouseId,
        String warehouseName,
        SaleStatus status,
        PaymentType paymentType,
        LocalDate saleDate,
        List<SaleLineDto> lines,
        BigDecimal subtotal,
        BigDecimal vatAmount,
        BigDecimal totalAmount,
        String notes,
        String createdByName,
        Instant createdAt,
        Instant updatedAt
) {}
