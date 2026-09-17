package com.company.tai.sales.controller;

import com.company.tai.common.dto.ApiResponse;
import com.company.tai.sales.dto.ConfirmSaleResult;
import com.company.tai.sales.dto.CreateSaleRequest;
import com.company.tai.sales.dto.SaleDto;
import com.company.tai.sales.entity.SaleStatus;
import com.company.tai.sales.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @GetMapping
    public ApiResponse<Page<SaleDto>> search(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) SaleStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(saleService.search(customerId, status, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<SaleDto> getById(@PathVariable Long id) {
        return ApiResponse.ok(saleService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<SaleDto> create(@Valid @RequestBody CreateSaleRequest request) {
        return ApiResponse.ok("Quotation created", saleService.create(request));
    }

    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<ConfirmSaleResult> confirm(@PathVariable Long id) {
        return ApiResponse.ok("Sale confirmed", saleService.confirm(id));
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<SaleDto> complete(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean override) {
        return ApiResponse.ok("Sale completed", saleService.complete(id, override));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<SaleDto> cancel(@PathVariable Long id) {
        return ApiResponse.ok("Sale cancelled", saleService.cancel(id));
    }
}
