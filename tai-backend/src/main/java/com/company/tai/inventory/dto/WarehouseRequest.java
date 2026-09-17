package com.company.tai.inventory.dto;

import jakarta.validation.constraints.NotBlank;

public record WarehouseRequest(@NotBlank String name, String location, Long branchId) {}
