package com.company.tai.sales.controller;

import com.company.tai.common.dto.ApiResponse;
import com.company.tai.sales.dto.SalePaymentDto;
import com.company.tai.sales.dto.SalePaymentRequest;
import com.company.tai.sales.service.SalePaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sales/{saleId}/payments")
@RequiredArgsConstructor
public class SalePaymentController {

    private final SalePaymentService salePaymentService;

    @GetMapping
    public ApiResponse<List<SalePaymentDto>> list(@PathVariable Long saleId) {
        return ApiResponse.ok(salePaymentService.listForSale(saleId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<SalePaymentDto> record(@PathVariable Long saleId, @Valid @RequestBody SalePaymentRequest request) {
        return ApiResponse.ok("Payment recorded", salePaymentService.recordForSale(saleId, request));
    }
}
