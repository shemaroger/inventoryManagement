package com.company.tai.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DailySalesReportDto(
        LocalDate date,
        List<DailySalesEntryDto> cashSales,
        List<DailySalesEntryDto> creditSales,
        BigDecimal cashTotal,
        BigDecimal creditTotal,
        BigDecimal grandTotal
) {}
