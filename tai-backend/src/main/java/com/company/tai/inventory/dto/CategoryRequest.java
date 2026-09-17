package com.company.tai.inventory.dto;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(@NotBlank String name, Long parentId) {}
