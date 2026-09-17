package com.company.tai.purchasing.controller;

import com.company.tai.common.dto.ApiResponse;
import com.company.tai.purchasing.dto.SupplierPaymentDto;
import com.company.tai.purchasing.dto.SupplierPaymentRequest;
import com.company.tai.purchasing.service.SupplierPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-orders/{poId}/payments")
@RequiredArgsConstructor
public class SupplierPaymentController {

    private final SupplierPaymentService supplierPaymentService;

    @GetMapping
    public ApiResponse<List<SupplierPaymentDto>> list(@PathVariable Long poId) {
        return ApiResponse.ok(supplierPaymentService.listForPurchaseOrder(poId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<SupplierPaymentDto> record(@PathVariable Long poId, @Valid @RequestBody SupplierPaymentRequest request) {
        return ApiResponse.ok("Payment recorded", supplierPaymentService.recordForPurchaseOrder(poId, request));
    }
}
