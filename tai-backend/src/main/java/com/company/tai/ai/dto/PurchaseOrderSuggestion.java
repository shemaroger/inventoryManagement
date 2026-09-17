package com.company.tai.ai.dto;

import java.util.List;

public record PurchaseOrderSuggestion(
        Long warehouseId,
        String warehouseName,
        Long supplierId,
        List<PurchaseOrderSuggestionLine> suggestedLines,
        String message
) {}
