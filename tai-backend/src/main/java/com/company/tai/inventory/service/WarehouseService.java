package com.company.tai.inventory.service;

import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.inventory.dto.WarehouseDto;
import com.company.tai.inventory.dto.WarehouseRequest;
import com.company.tai.inventory.entity.Branch;
import com.company.tai.inventory.entity.Warehouse;
import com.company.tai.inventory.repository.BranchRepository;
import com.company.tai.inventory.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final BranchRepository branchRepository;

    public List<WarehouseDto> listAll() {
        return warehouseRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public WarehouseDto create(WarehouseRequest request) {
        Branch branch = request.branchId() != null
                ? branchRepository.findById(request.branchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id: " + request.branchId()))
                : null;

        Warehouse warehouse = Warehouse.builder()
                .name(request.name())
                .location(request.location())
                .branch(branch)
                .active(true)
                .build();

        return toDto(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseDto update(Long id, WarehouseRequest request) {
        Warehouse warehouse = findOrThrow(id);
        warehouse.setName(request.name());
        warehouse.setLocation(request.location());
        if (request.branchId() != null) {
            warehouse.setBranch(branchRepository.findById(request.branchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id: " + request.branchId())));
        }
        return toDto(warehouse);
    }

    @Transactional
    public void deactivate(Long id) {
        Warehouse warehouse = findOrThrow(id);
        warehouse.setActive(false);
    }

    @Transactional
    public void delete(Long id) {
        // No explicit pre-check for existing stock/adjustments referencing this warehouse —
        // the FK constraints on stock_items/stock_adjustments will reject the delete, and
        // GlobalExceptionHandler already maps that DataIntegrityViolationException to a 409.
        warehouseRepository.delete(findOrThrow(id));
    }

    private Warehouse findOrThrow(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + id));
    }

    private WarehouseDto toDto(Warehouse w) {
        return new WarehouseDto(
                w.getId(), w.getName(), w.getLocation(),
                w.getBranch() != null ? w.getBranch().getId() : null,
                w.getBranch() != null ? w.getBranch().getName() : null,
                w.isActive()
        );
    }
}
