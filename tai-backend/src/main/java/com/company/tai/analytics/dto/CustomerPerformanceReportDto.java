package com.company.tai.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record CustomerPerformanceReportDto(LocalDate startDate, LocalDate endDate, List<CustomerPerformanceLineDto> lines) {}
