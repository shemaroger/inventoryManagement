package com.company.tai.ai.dto;

import java.util.List;

public record ForecastBasedOnDto(
        int dataPoints,
        String dateRangeStart,
        String dateRangeEnd,
        List<Integer> yearsIncluded
) {}
