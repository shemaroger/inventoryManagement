package com.company.tai.inventory.controller;

import com.company.tai.common.dto.ApiResponse;
import com.company.tai.inventory.dto.BranchDto;
import com.company.tai.inventory.dto.BranchRequest;
import com.company.tai.inventory.service.BranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    public ApiResponse<List<BranchDto>> listAll() {
        return ApiResponse.ok(branchService.listAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<BranchDto> create(@Valid @RequestBody BranchRequest request) {
        return ApiResponse.ok("Branch created", branchService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<BranchDto> update(@PathVariable Long id, @Valid @RequestBody BranchRequest request) {
        return ApiResponse.ok("Branch updated", branchService.update(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        branchService.deactivate(id);
        return ApiResponse.ok("Branch deactivated", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        branchService.delete(id);
        return ApiResponse.ok("Branch deleted", null);
    }
}
