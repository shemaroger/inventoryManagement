package com.company.tai.analytics.dto;

import java.math.BigDecimal;

public record CustomerPerformanceLineDto(
        Long customerId,
        String customerName,
        BigDecimal totalRevenue,
        int saleCount,
        // null when this customer has no recorded payments in the period to average — most
        // relevant for CREDIT customers; a CASH-only customer will always show null here.
        BigDecimal averagePaymentDelayDays
) {}
