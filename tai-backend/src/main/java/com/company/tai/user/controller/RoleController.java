package com.company.tai.user.controller;

import com.company.tai.common.dto.ApiResponse;
import com.company.tai.user.dto.AssignPermissionsRequest;
import com.company.tai.user.dto.RoleDto;
import com.company.tai.user.dto.RoleRequest;
import com.company.tai.user.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_MANAGE')")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public ApiResponse<List<RoleDto>> listRoles() {
        return ApiResponse.ok(roleService.listRoles());
    }

    @GetMapping("/{id}")
    public ApiResponse<RoleDto> getRole(@PathVariable Long id) {
        return ApiResponse.ok(roleService.getRole(id));
    }

    @PostMapping
    public ApiResponse<RoleDto> createRole(@Valid @RequestBody RoleRequest request) {
        return ApiResponse.ok("Role created", roleService.createRole(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoleDto> updateRole(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        return ApiResponse.ok("Role updated", roleService.updateRole(id, request));
    }

    @PutMapping("/{id}/permissions")
    public ApiResponse<RoleDto> assignPermissions(@PathVariable Long id, @Valid @RequestBody AssignPermissionsRequest request) {
        return ApiResponse.ok("Permissions assigned", roleService.assignPermissions(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ApiResponse.ok("Role deleted", null);
    }
}
