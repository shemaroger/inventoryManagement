package com.company.tai.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ForecastDto(
        Long productId,
        String productName,
        BigDecimal currentStock,
        BigDecimal reorderLevel,
        BigDecimal avgDailyConsumption,
        Integer daysUntilReorderNeeded,
        LocalDate projectedStockoutDate,
        String confidence,
        String explanation
) {}
