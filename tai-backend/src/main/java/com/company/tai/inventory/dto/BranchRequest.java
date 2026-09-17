package com.company.tai.inventory.dto;

import jakarta.validation.constraints.NotBlank;

public record BranchRequest(@NotBlank String name, String address) {}
