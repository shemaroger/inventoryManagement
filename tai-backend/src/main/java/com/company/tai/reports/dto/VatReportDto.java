package com.company.tai.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VatReportDto(
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal outputVat,
        BigDecimal inputVat,
        BigDecimal netVatPayable
) {}
