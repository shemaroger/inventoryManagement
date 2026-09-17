package com.company.tai.analytics.dto;

import java.math.BigDecimal;

public record ProfitByCategoryLineDto(
        Long categoryId,
        String categoryName,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal margin,
        BigDecimal marginPercent
) {}
