package com.company.tai.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesAnalyticsReportDto(
        LocalDate startDate,
        LocalDate endDate,
        String groupBy,
        List<SalesAnalyticsPointDto> points,
        BigDecimal grandTotal
) {}
