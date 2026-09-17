package com.company.tai.purchasing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplierPaymentDto(
        Long id,
        Long supplierId,
        String supplierName,
        Long purchaseOrderId,
        BigDecimal amount,
        LocalDate paymentDate,
        String notes
) {}
