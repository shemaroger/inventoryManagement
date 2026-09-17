package com.company.tai.purchasing.controller;

import com.company.tai.common.dto.ApiResponse;
import com.company.tai.purchasing.dto.SupplierDto;
import com.company.tai.purchasing.dto.SupplierRequest;
import com.company.tai.purchasing.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    public ApiResponse<List<SupplierDto>> listAll() {
        return ApiResponse.ok(supplierService.listAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<SupplierDto> create(@Valid @RequestBody SupplierRequest request) {
        return ApiResponse.ok("Supplier created", supplierService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<SupplierDto> update(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return ApiResponse.ok("Supplier updated", supplierService.update(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        supplierService.deactivate(id);
        return ApiResponse.ok("Supplier deactivated", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return ApiResponse.ok("Supplier deleted", null);
    }
}
