package com.company.tai.inventory.service;

import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.inventory.dto.BranchDto;
import com.company.tai.inventory.dto.BranchRequest;
import com.company.tai.inventory.entity.Branch;
import com.company.tai.inventory.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;

    public List<BranchDto> listAll() {
        return branchRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public BranchDto create(BranchRequest request) {
        Branch branch = Branch.builder()
                .name(request.name())
                .address(request.address())
                .active(true)
                .build();

        return toDto(branchRepository.save(branch));
    }

    @Transactional
    public BranchDto update(Long id, BranchRequest request) {
        Branch branch = findOrThrow(id);
        branch.setName(request.name());
        branch.setAddress(request.address());
        return toDto(branch);
    }

    @Transactional
    public void deactivate(Long id) {
        Branch branch = findOrThrow(id);
        branch.setActive(false);
    }

    @Transactional
    public void delete(Long id) {
        // Mirrors WarehouseService.delete: no explicit pre-check for referencing warehouses —
        // the FK constraint on warehouses.branch_id rejects the delete, and
        // GlobalExceptionHandler already maps that DataIntegrityViolationException to a 409.
        branchRepository.delete(findOrThrow(id));
    }

    private Branch findOrThrow(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id: " + id));
    }

    private BranchDto toDto(Branch b) {
        return new BranchDto(b.getId(), b.getName(), b.getAddress(), b.isActive());
    }
}
