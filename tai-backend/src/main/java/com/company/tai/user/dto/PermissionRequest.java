package com.company.tai.user.dto;

import jakarta.validation.constraints.NotBlank;

public record PermissionRequest(
        @NotBlank String code,
        String description
) {}
