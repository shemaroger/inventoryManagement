package com.company.tai.ai.dto;

import java.math.BigDecimal;

public record UnifiedForecastDto(
        Long productId,
        String productName,
        String horizon,
        String targetPeriod,
        BigDecimal suggestedQuantity,
        String method,
        ForecastBasedOnDto basedOn,
        String confidence,
        String explanation,
        String dataSource
) {}
