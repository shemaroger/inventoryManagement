package com.company.tai.sales.dto;

import java.math.BigDecimal;

public record SaleLineDto(
        Long id,
        Long productId,
        String productName,
        String productSku,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal discountPercent,
        BigDecimal lineTotal
) {}
