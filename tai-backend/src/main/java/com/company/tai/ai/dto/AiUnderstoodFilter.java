package com.company.tai.ai.dto;

public record AiUnderstoodFilter(
        String intent,
        String categoryName,
        String brandName,
        String warehouseName,
        String searchText,
        double confidence
) {}
