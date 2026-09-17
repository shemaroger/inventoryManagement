package com.company.tai.analytics.dto;

import java.math.BigDecimal;

public record ProfitByProductLineDto(
        Long productId,
        String productName,
        String productSku,
        String categoryName,
        BigDecimal quantitySold,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal margin,
        BigDecimal marginPercent
) {}
