package com.company.tai.user.dto;

import jakarta.validation.constraints.NotBlank;

public record RoleRequest(
        @NotBlank String name,
        String description
) {}
