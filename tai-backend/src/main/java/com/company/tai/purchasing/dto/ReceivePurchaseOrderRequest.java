package com.company.tai.purchasing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReceivePurchaseOrderRequest(
        @NotEmpty @Valid List<ReceiveLineRequest> lines
) {}
