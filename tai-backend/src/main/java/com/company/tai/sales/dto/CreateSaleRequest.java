package com.company.tai.sales.dto;

import com.company.tai.sales.entity.PaymentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreateSaleRequest(
        @NotNull Long customerId,
        @NotNull Long warehouseId,
        LocalDate saleDate,
        @NotNull PaymentType paymentType,
        String notes,
        @NotEmpty @Valid List<SaleLineRequest> lines
) {}
