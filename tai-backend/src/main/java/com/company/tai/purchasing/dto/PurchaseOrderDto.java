package com.company.tai.purchasing.dto;

import com.company.tai.purchasing.entity.PurchaseOrderStatus;
import com.company.tai.purchasing.entity.PurchasePaymentType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PurchaseOrderDto(
        Long id,
        Long supplierId,
        String supplierName,
        Long warehouseId,
        String warehouseName,
        PurchaseOrderStatus status,
        PurchasePaymentType paymentType,
        LocalDate orderDate,
        List<PurchaseOrderLineDto> lines,
        String notes,
        String createdByName,
        Instant createdAt,
        Instant updatedAt
) {}
