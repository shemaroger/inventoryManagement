package com.company.tai.anomaly.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AnomalyDto(
        Long id,
        String anomalyType,
        BigDecimal severityScore,
        String severityLevel,
        String status,
        Long productId,
        String productName,
        Long warehouseId,
        String warehouseName,
        Long adjustmentId,
        String adjustmentType,
        BigDecimal adjustmentQuantity,
        String performedByName,
        Instant adjustmentCreatedAt,
        String contextNote,
        Instant detectedAt,
        String reviewedByName,
        Instant reviewedAt,
        String reviewNote
) {}
