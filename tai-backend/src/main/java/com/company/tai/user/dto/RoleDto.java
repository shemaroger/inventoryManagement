package com.company.tai.user.dto;

import java.util.Set;

public record RoleDto(Long id, String name, String description, Set<String> permissions) {}
