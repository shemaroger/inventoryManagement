package com.company.tai.user.service;

import com.company.tai.common.exception.BusinessRuleException;
import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.user.dto.PermissionDto;
import com.company.tai.user.dto.PermissionRequest;
import com.company.tai.user.entity.Permission;
import com.company.tai.user.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public List<PermissionDto> listPermissions() {
        return permissionRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public PermissionDto createPermission(PermissionRequest request) {
        if (permissionRepository.existsByCode(request.code())) {
            throw new BusinessRuleException("Permission code already exists: " + request.code());
        }
        Permission permission = Permission.builder()
                .code(request.code())
                .description(request.description())
                .build();
        return toDto(permissionRepository.save(permission));
    }

    @Transactional
    public PermissionDto updatePermission(Long id, PermissionRequest request) {
        Permission permission = findOrThrow(id);
        if (!permission.getCode().equals(request.code()) && permissionRepository.existsByCode(request.code())) {
            throw new BusinessRuleException("Permission code already exists: " + request.code());
        }
        permission.setCode(request.code());
        permission.setDescription(request.description());
        return toDto(permission);
    }

    @Transactional
    public void deletePermission(Long id) {
        Permission permission = findOrThrow(id);
        permissionRepository.delete(permission);
    }

    private Permission findOrThrow(Long id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found with id: " + id));
    }

    private PermissionDto toDto(Permission permission) {
        return new PermissionDto(permission.getId(), permission.getCode(), permission.getDescription());
    }
}
