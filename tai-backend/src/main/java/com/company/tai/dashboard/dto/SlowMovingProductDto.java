package com.company.tai.dashboard.dto;

import java.math.BigDecimal;

public record SlowMovingProductDto(
        Long productId,
        String productName,
        String productSku,
        BigDecimal currentStock,
        BigDecimal quantitySoldInPeriod
) {}
