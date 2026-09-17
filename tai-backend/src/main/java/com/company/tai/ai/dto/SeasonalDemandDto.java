package com.company.tai.ai.dto;

import java.util.List;

public record SeasonalDemandDto(
        Long productId,
        String productName,
        String period,
        String dataSource,
        List<SeasonalBucketDto> buckets
) {}
