package com.company.tai.inventory.controller;

import com.company.tai.common.dto.ApiResponse;
import com.company.tai.inventory.dto.StockAdjustmentDto;
import com.company.tai.inventory.dto.StockAdjustmentRequest;
import com.company.tai.inventory.dto.StockItemDto;
import com.company.tai.inventory.dto.StockTransferRequest;
import com.company.tai.inventory.dto.StockTransferResult;
import com.company.tai.inventory.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @GetMapping("/warehouse/{warehouseId}")
    public ApiResponse<List<StockItemDto>> byWarehouse(@PathVariable Long warehouseId) {
        return ApiResponse.ok(stockService.getStockForWarehouse(warehouseId));
    }

    @GetMapping("/product/{productId}")
    public ApiResponse<List<StockItemDto>> byProduct(@PathVariable Long productId) {
        return ApiResponse.ok(stockService.getStockForProduct(productId));
    }

    @GetMapping("/low-stock")
    public ApiResponse<List<StockItemDto>> lowStock() {
        return ApiResponse.ok(stockService.getLowStock());
    }

    @GetMapping("/adjustments")
    public ApiResponse<Page<StockAdjustmentDto>> adjustmentHistory(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(stockService.searchAdjustments(productId, warehouseId, pageable));
    }

    @PostMapping("/adjustments")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public ApiResponse<StockItemDto> adjust(@Valid @RequestBody StockAdjustmentRequest request) {
        return ApiResponse.ok("Stock adjustment applied", stockService.applyAdjustment(request));
    }

    @PostMapping("/transfers")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public ApiResponse<StockTransferResult> transfer(@Valid @RequestBody StockTransferRequest request) {
        return ApiResponse.ok("Stock transferred", stockService.transfer(request));
    }
}
