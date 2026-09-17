package com.company.tai.analytics.dto;

import java.math.BigDecimal;

// averageLeadTimeDays is measured from PO creation to full receipt, not true "submission" —
// PurchaseOrder doesn't track a separate submittedAt timestamp, so creation is the closest
// available proxy. isApproximateLeadTime is always true for this reason.
public record SupplierPerformanceLineDto(
        Long supplierId,
        String supplierName,
        BigDecimal totalSpend,
        int purchaseOrderCount,
        BigDecimal averageLeadTimeDays,
        boolean isApproximateLeadTime
) {}
