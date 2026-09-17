package com.company.tai.purchasing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ReceiveLineRequest(
        @NotNull Long lineId,
        @NotNull @Positive BigDecimal quantityReceived
) {}
