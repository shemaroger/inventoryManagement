package com.company.tai.user.dto;

import java.util.Set;

public record UserDto(
        Long id,
        String fullName,
        String email,
        String phoneNumber,
        boolean active,
        Set<String> roles
) {}
