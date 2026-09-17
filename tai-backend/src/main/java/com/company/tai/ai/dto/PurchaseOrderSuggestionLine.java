package com.company.tai.ai.dto;

import java.math.BigDecimal;

public record PurchaseOrderSuggestionLine(
        Long productId,
        String productName,
        String productSku,
        BigDecimal suggestedQuantity,
        BigDecimal unitCost,
        String reasoning
) {}
