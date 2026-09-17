package com.company.tai.inventory.dto;

import java.math.BigDecimal;

public record ProductDto(
        Long id,
        String sku,
        String barcode,
        String name,
        String description,
        Long categoryId,
        String categoryName,
        Long brandId,
        String brandName,
        Long unitId,
        String unitName,
        BigDecimal costPrice,
        BigDecimal sellingPrice,
        BigDecimal reorderLevel,
        BigDecimal totalStock,
        boolean active,
        String imageUrl
) {}
