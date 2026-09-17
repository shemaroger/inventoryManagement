package com.company.tai.user.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record AssignPermissionsRequest(
        @NotNull Set<String> permissionCodes
) {}
