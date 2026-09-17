package com.company.tai.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DailyPurchaseRangeReportDto(
        LocalDate startDate,
        LocalDate endDate,
        List<DailyPurchaseEntryDto> cashPurchases,
        List<DailyPurchaseEntryDto> creditPurchases,
        BigDecimal cashTotal,
        BigDecimal creditTotal,
        BigDecimal grandTotal
) {}
