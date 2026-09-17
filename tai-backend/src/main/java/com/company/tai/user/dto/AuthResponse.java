package com.company.tai.user.dto;

import java.util.Set;

public record AuthResponse(
        String accessToken,
        String tokenType,
        Long userId,
        String fullName,
        String email,
        Set<String> roles
) {
    public static AuthResponse of(String accessToken, Long userId, String fullName, String email, Set<String> roles) {
        return new AuthResponse(accessToken, "Bearer", userId, fullName, email, roles);
    }
}
