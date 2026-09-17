package com.company.tai.analytics.dto;

import java.math.BigDecimal;

public record InventoryTurnoverLineDto(
        Long productId,
        String productName,
        String productSku,
        BigDecimal unitsSold,
        BigDecimal averageStock,
        // null when unitsSold is 0 for the period — a turnover ratio of "0" would misleadingly
        // suggest "very slow" rather than "no sales data to compute one from".
        BigDecimal turnoverRatio,
        BigDecimal daysOfStockOnHand
) {}
