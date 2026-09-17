package com.company.tai.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// isApproximate is always true today: even though the Chart of Accounts/General Ledger exists,
// JournalService posts one aggregate COGS line per sale (not broken out per product/line), so
// there is no accurate per-product historical cost to read back from the ledger. Cost here is
// each product's *current* costPrice × quantity sold — accurate for products whose cost hasn't
// changed recently, an approximation otherwise.
public record ProfitByProductReportDto(
        LocalDate startDate,
        LocalDate endDate,
        boolean isApproximate,
        List<ProfitByProductLineDto> lines,
        BigDecimal totalRevenue,
        BigDecimal totalCost,
        BigDecimal totalMargin
) {}
