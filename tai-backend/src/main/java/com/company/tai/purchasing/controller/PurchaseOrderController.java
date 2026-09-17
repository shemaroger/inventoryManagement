package com.company.tai.purchasing.controller;

import com.company.tai.common.dto.ApiResponse;
import com.company.tai.purchasing.dto.CreatePurchaseOrderRequest;
import com.company.tai.purchasing.dto.PurchaseOrderDto;
import com.company.tai.purchasing.dto.ReceivePurchaseOrderRequest;
import com.company.tai.purchasing.entity.PurchaseOrderStatus;
import com.company.tai.purchasing.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    public ApiResponse<Page<PurchaseOrderDto>> search(
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(purchaseOrderService.search(supplierId, status, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<PurchaseOrderDto> getById(@PathVariable Long id) {
        return ApiResponse.ok(purchaseOrderService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<PurchaseOrderDto> create(@Valid @RequestBody CreatePurchaseOrderRequest request) {
        return ApiResponse.ok("Purchase order created", purchaseOrderService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<PurchaseOrderDto> update(@PathVariable Long id, @Valid @RequestBody CreatePurchaseOrderRequest request) {
        return ApiResponse.ok("Purchase order updated", purchaseOrderService.update(id, request));
    }

    @PatchMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<PurchaseOrderDto> submit(@PathVariable Long id) {
        return ApiResponse.ok("Purchase order submitted", purchaseOrderService.submit(id));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<PurchaseOrderDto> cancel(@PathVariable Long id) {
        return ApiResponse.ok("Purchase order cancelled", purchaseOrderService.cancel(id));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public ApiResponse<PurchaseOrderDto> receive(@PathVariable Long id, @Valid @RequestBody ReceivePurchaseOrderRequest request) {
        return ApiResponse.ok("Goods received", purchaseOrderService.receive(id, request));
    }
}
