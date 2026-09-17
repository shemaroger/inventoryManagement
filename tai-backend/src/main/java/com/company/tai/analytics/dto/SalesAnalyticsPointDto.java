package com.company.tai.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesAnalyticsPointDto(LocalDate bucketStart, String bucketLabel, BigDecimal total, int saleCount) {}
