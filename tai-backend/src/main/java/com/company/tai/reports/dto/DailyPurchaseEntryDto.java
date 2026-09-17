package com.company.tai.reports.dto;

import com.company.tai.purchasing.entity.PurchasePaymentType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyPurchaseEntryDto(
        Long purchaseOrderId,
        LocalDate receivedDate,
        String supplierName,
        PurchasePaymentType paymentType,
        BigDecimal amountReceived
) {}
