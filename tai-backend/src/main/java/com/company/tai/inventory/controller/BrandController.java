package com.company.tai.inventory.controller;

import com.company.tai.common.dto.ApiResponse;
import com.company.tai.inventory.dto.BrandDto;
import com.company.tai.inventory.dto.BrandRequest;
import com.company.tai.inventory.service.BrandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @GetMapping
    public ApiResponse<List<BrandDto>> listAll() {
        return ApiResponse.ok(brandService.listAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<BrandDto> create(@Valid @RequestBody BrandRequest request) {
        return ApiResponse.ok("Brand created", brandService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<BrandDto> update(@PathVariable Long id, @Valid @RequestBody BrandRequest request) {
        return ApiResponse.ok("Brand updated", brandService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        brandService.delete(id);
        return ApiResponse.ok("Brand deleted", null);
    }
}
