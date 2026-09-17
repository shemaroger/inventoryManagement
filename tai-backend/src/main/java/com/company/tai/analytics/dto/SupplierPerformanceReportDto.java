package com.company.tai.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record SupplierPerformanceReportDto(LocalDate startDate, LocalDate endDate, List<SupplierPerformanceLineDto> lines) {}
