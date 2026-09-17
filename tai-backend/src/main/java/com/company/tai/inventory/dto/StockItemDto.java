package com.company.tai.inventory.dto;

import java.math.BigDecimal;

public record StockItemDto(
        Long id,
        Long productId,
        String productName,
        Long warehouseId,
        String warehouseName,
        BigDecimal quantity
) {}
