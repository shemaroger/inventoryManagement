package com.company.tai.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record InventoryTurnoverReportDto(
        LocalDate startDate,
        LocalDate endDate,
        boolean dataScarce,
        List<InventoryTurnoverLineDto> lines
) {}
