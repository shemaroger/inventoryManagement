package com.company.tai.purchasing.dto;

import com.company.tai.purchasing.entity.PurchasePaymentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreatePurchaseOrderRequest(
        @NotNull Long supplierId,
        @NotNull Long warehouseId,
        LocalDate orderDate,
        String notes,
        // Null defaults to CREDIT in PurchaseOrderService — most supplier terms in this business
        // are on-account, and this keeps older callers (e.g. ReorderSuggestionService) working
        // without forcing every call site to specify it explicitly.
        PurchasePaymentType paymentType,
        @NotEmpty @Valid List<PurchaseOrderLineRequest> lines
) {}
