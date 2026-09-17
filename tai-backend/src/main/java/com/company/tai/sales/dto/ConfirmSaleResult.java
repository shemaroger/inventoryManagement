package com.company.tai.sales.dto;

import java.util.List;

public record ConfirmSaleResult(SaleDto sale, List<String> stockWarnings) {}
