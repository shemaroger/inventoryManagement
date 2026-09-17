package com.company.tai.purchasing.dto;

import java.math.BigDecimal;

public record PurchaseOrderLineDto(
        Long id,
        Long productId,
        String productName,
        String productSku,
        BigDecimal quantityOrdered,
        BigDecimal quantityReceived,
        BigDecimal unitCost
) {}
