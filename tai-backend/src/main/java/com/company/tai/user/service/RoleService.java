package com.company.tai.user.service;

import com.company.tai.common.exception.BusinessRuleException;
import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.user.dto.AssignPermissionsRequest;
import com.company.tai.user.dto.RoleDto;
import com.company.tai.user.dto.RoleRequest;
import com.company.tai.user.entity.Permission;
import com.company.tai.user.entity.Role;
import com.company.tai.user.repository.PermissionRepository;
import com.company.tai.user.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public List<RoleDto> listRoles() {
        return roleRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public RoleDto getRole(Long id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public RoleDto createRole(RoleRequest request) {
        if (roleRepository.findByName(request.name()).isPresent()) {
            throw new BusinessRuleException("Role already exists: " + request.name());
        }
        Role role = Role.builder()
                .name(request.name())
                .description(request.description())
                .build();
        return toDto(roleRepository.save(role));
    }

    @Transactional
    public RoleDto updateRole(Long id, RoleRequest request) {
        Role role = findOrThrow(id);
        if (!role.getName().equalsIgnoreCase(request.name())) {
            roleRepository.findByName(request.name()).ifPresent(existing -> {
                throw new BusinessRuleException("Role already exists: " + request.name());
            });
        }
        if (Set.of("ADMIN", "MANAGER", "STAFF").contains(role.getName()) && !role.getName().equals(request.name())) {
            throw new BusinessRuleException("Cannot rename a seeded system role: " + role.getName());
        }
        role.setName(request.name());
        role.setDescription(request.description());
        return toDto(role);
    }

    @Transactional
    public RoleDto assignPermissions(Long id, AssignPermissionsRequest request) {
        Role role = findOrThrow(id);
        Set<Permission> permissions = new HashSet<>();
        for (String code : request.permissionCodes()) {
            permissions.add(permissionRepository.findByCode(code)
                    .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + code)));
        }
        role.setPermissions(permissions);
        return toDto(role);
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = findOrThrow(id);
        if (Set.of("ADMIN", "MANAGER", "STAFF").contains(role.getName())) {
            throw new BusinessRuleException("Cannot delete a seeded system role: " + role.getName());
        }
        roleRepository.delete(role);
    }

    private Role findOrThrow(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + id));
    }

    private RoleDto toDto(Role role) {
        return new RoleDto(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getPermissions().stream().map(Permission::getCode).collect(Collectors.toSet())
        );
    }
}
