package com.company.tai.user.service;

import com.company.tai.common.exception.BusinessRuleException;
import com.company.tai.security.JwtService;
import com.company.tai.user.dto.AuthResponse;
import com.company.tai.user.dto.LoginRequest;
import com.company.tai.user.dto.RegisterRequest;
import com.company.tai.user.entity.Role;
import com.company.tai.user.entity.User;
import com.company.tai.user.repository.RoleRepository;
import com.company.tai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException("An account with this email already exists");
        }

        String roleName = (request.roleName() == null || request.roleName().isBlank())
                ? "STAFF"
                : request.roleName().toUpperCase();

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new BusinessRuleException("Unknown role: " + roleName));

        User user = User.builder()
                .fullName(request.fullName())
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .passwordHash(passwordEncoder.encode(request.password()))
                .active(true)
                .roles(Set.of(role))
                .build();

        userRepository.save(user);

        String token = jwtService.generateAccessToken(user);
        return AuthResponse.of(token, user.getId(), user.getFullName(), user.getEmail(), extractRoleNames(user));
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessRuleException("Invalid email or password"));

        String token = jwtService.generateAccessToken(user);
        return AuthResponse.of(token, user.getId(), user.getFullName(), user.getEmail(), extractRoleNames(user));
    }

    private Set<String> extractRoleNames(User user) {
        return user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
    }
}
