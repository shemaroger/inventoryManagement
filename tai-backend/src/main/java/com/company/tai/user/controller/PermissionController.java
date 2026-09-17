package com.company.tai.user.controller;

import com.company.tai.common.dto.ApiResponse;
import com.company.tai.user.dto.PermissionDto;
import com.company.tai.user.dto.PermissionRequest;
import com.company.tai.user.service.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_MANAGE')")
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    public ApiResponse<List<PermissionDto>> listPermissions() {
        return ApiResponse.ok(permissionService.listPermissions());
    }

    @PostMapping
    public ApiResponse<PermissionDto> createPermission(@Valid @RequestBody PermissionRequest request) {
        return ApiResponse.ok("Permission created", permissionService.createPermission(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PermissionDto> updatePermission(@PathVariable Long id, @Valid @RequestBody PermissionRequest request) {
        return ApiResponse.ok("Permission updated", permissionService.updatePermission(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePermission(@PathVariable Long id) {
        permissionService.deletePermission(id);
        return ApiResponse.ok("Permission deleted", null);
    }
}
