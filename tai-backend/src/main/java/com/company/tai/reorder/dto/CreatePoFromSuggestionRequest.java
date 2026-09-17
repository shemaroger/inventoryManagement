package com.company.tai.reorder.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePoFromSuggestionRequest(
        @NotNull Long supplierId,
        BigDecimal quantity,
        BigDecimal unitCost
) {}
