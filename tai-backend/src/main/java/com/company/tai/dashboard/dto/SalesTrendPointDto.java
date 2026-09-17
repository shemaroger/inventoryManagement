package com.company.tai.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesTrendPointDto(LocalDate date, BigDecimal total) {}
