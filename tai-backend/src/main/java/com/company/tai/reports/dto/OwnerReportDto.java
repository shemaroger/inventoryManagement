package com.company.tai.reports.dto;

import java.time.LocalDate;

public record OwnerReportDto(
        LocalDate startDate,
        LocalDate endDate,
        OwnerReportFinancialDto financial,
        OwnerReportSalesDto sales,
        OwnerReportInventoryDto inventory,
        OwnerReportPurchasingDto purchasing
) {}
