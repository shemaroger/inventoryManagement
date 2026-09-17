package com.company.tai.inventory.dto;

public record WarehouseDto(Long id, String name, String location, Long branchId, String branchName, boolean active) {}
