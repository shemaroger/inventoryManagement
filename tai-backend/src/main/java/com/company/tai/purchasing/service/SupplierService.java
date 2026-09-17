package com.company.tai.purchasing.service;

import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.purchasing.dto.SupplierDto;
import com.company.tai.purchasing.dto.SupplierRequest;
import com.company.tai.purchasing.entity.Supplier;
import com.company.tai.purchasing.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Mirrors WarehouseService exactly (same simple-entity pattern: create/update/deactivate/delete),
// since Warehouse is the closest existing precedent and ended up with both deactivate and a
// hard delete.
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public List<SupplierDto> listAll() {
        return supplierRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public SupplierDto create(SupplierRequest request) {
        Supplier supplier = Supplier.builder()
                .name(request.name())
                .contactPerson(request.contactPerson())
                .phone(request.phone())
                .email(request.email())
                .address(request.address())
                .active(true)
                .build();
        return toDto(supplierRepository.save(supplier));
    }

    @Transactional
    public SupplierDto update(Long id, SupplierRequest request) {
        Supplier supplier = findOrThrow(id);
        supplier.setName(request.name());
        supplier.setContactPerson(request.contactPerson());
        supplier.setPhone(request.phone());
        supplier.setEmail(request.email());
        supplier.setAddress(request.address());
        return toDto(supplier);
    }

    @Transactional
    public void deactivate(Long id) {
        findOrThrow(id).setActive(false);
    }

    @Transactional
    public void delete(Long id) {
        supplierRepository.delete(findOrThrow(id));
    }

    Supplier findOrThrow(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with id: " + id));
    }

    private SupplierDto toDto(Supplier s) {
        return new SupplierDto(s.getId(), s.getName(), s.getContactPerson(), s.getPhone(), s.getEmail(), s.getAddress(), s.isActive());
    }
}
