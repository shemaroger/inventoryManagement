package com.company.tai.inventory.controller;

import com.company.tai.common.dto.ApiResponse;
import com.company.tai.inventory.dto.WarehouseDto;
import com.company.tai.inventory.dto.WarehouseRequest;
import com.company.tai.inventory.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    public ApiResponse<List<WarehouseDto>> listAll() {
        return ApiResponse.ok(warehouseService.listAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<WarehouseDto> create(@Valid @RequestBody WarehouseRequest request) {
        return ApiResponse.ok("Warehouse created", warehouseService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<WarehouseDto> update(@PathVariable Long id, @Valid @RequestBody WarehouseRequest request) {
        return ApiResponse.ok("Warehouse updated", warehouseService.update(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        warehouseService.deactivate(id);
        return ApiResponse.ok("Warehouse deactivated", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        warehouseService.delete(id);
        return ApiResponse.ok("Warehouse deleted", null);
    }
}
