package com.company.tai.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DailyPurchaseReportDto(
        LocalDate date,
        List<DailyPurchaseEntryDto> cashPurchases,
        List<DailyPurchaseEntryDto> creditPurchases,
        BigDecimal cashTotal,
        BigDecimal creditTotal,
        BigDecimal grandTotal
) {}
