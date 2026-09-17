package com.company.tai.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(
        @NotBlank String fullName,
        String phoneNumber,
        @NotBlank String roleName
) {}
