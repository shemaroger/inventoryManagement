package com.company.tai.ai.dto;

import java.math.BigDecimal;
import java.util.List;

public record SeasonalPredictionDto(
        Long productId,
        String productName,
        String targetPeriod,
        BigDecimal suggestedQuantity,
        List<Integer> basedOnYears,
        String confidence,
        String explanation,
        String dataSource,
        List<YearlyQuantityDto> yearlyBreakdown
) {}
