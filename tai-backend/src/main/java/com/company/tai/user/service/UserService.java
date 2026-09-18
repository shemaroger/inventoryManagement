package com.company.tai.user.service;

import com.company.tai.common.exception.BusinessRuleException;
import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.user.dto.CreateUserRequest;
import com.company.tai.user.dto.UpdateUserRequest;
import com.company.tai.user.dto.UserDto;
import com.company.tai.user.entity.Role;
import com.company.tai.user.entity.User;
import com.company.tai.user.repository.RoleRepository;
import com.company.tai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserDto createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException("Email already in use: " + request.email());
        }
        Role role = roleRepository.findByName(normalizeRoleName(request.roleName()))
                .orElseThrow(() -> new BusinessRuleException("Unknown role: " + request.roleName()));

        User user = User.builder()
                .fullName(request.fullName())
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .passwordHash(passwordEncoder.encode(request.password()))
                .active(true)
                .roles(new HashSet<>(List.of(role)))
                .build();

        return toDto(userRepository.save(user));
    }

    @Transactional
    public UserDto updateUser(Long id, UpdateUserRequest request) {
        User user = findOrThrow(id);
        Role role = roleRepository.findByName(normalizeRoleName(request.roleName()))
                .orElseThrow(() -> new BusinessRuleException("Unknown role: " + request.roleName()));

        if (isCurrentUser(user) && !"ADMIN".equals(role.getName()) && isLastAdmin(user)) {
            throw new BusinessRuleException("You cannot remove your own ADMIN role: you are the last administrator");
        }

        user.setFullName(request.fullName());
        user.setPhoneNumber(request.phoneNumber());
        user.setRoles(new HashSet<>(List.of(role)));

        return toDto(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = findOrThrow(id);
        if (isCurrentUser(user)) {
            throw new BusinessRuleException("You cannot delete your own account");
        }
        if (isLastAdmin(user)) {
            throw new BusinessRuleException("Cannot delete the last remaining ADMIN user");
        }
        userRepository.delete(user);
    }

    public List<UserDto> listUsers() {
        return userRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public UserDto getUser(Long id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public UserDto setActive(Long id, boolean active) {
        User user = findOrThrow(id);
        if (!active) {
            if (isCurrentUser(user)) {
                throw new BusinessRuleException("You cannot deactivate your own account");
            }
            if (isLastAdmin(user)) {
                throw new BusinessRuleException("Cannot deactivate the last remaining ADMIN user");
            }
        }
        user.setActive(active);
        return toDto(user);
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    private String normalizeRoleName(String roleName) {
        return roleName == null ? null : roleName.trim().toUpperCase();
    }

    private boolean isCurrentUser(User user) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getName() != null && auth.getName().equalsIgnoreCase(user.getEmail());
    }

    private boolean isLastAdmin(User user) {
        boolean isAdmin = user.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getName()));
        if (!isAdmin) {
            return false;
        }
        long activeAdminCount = userRepository.findAll().stream()
                .filter(User::isActive)
                .filter(u -> u.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getName())))
                .count();
        return activeAdminCount <= 1;
    }

    private UserDto toDto(User user) {
        return new UserDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.isActive(),
                user.getRoles().stream().map(Role::getName).collect(Collectors.toSet())
        );
    }
}
