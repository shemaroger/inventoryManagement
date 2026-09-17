package com.company.tai.ai.dto;

import java.math.BigDecimal;
import java.util.List;

public record SeasonalBucketDto(
        int bucket,
        String bucketLabel,
        BigDecimal totalQuantity,
        int yearsCount,
        BigDecimal avgPerYear,
        List<Integer> years
) {}
