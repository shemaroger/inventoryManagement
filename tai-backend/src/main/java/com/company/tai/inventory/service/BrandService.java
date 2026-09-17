package com.company.tai.inventory.service;

import com.company.tai.common.exception.BusinessRuleException;
import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.inventory.dto.BrandDto;
import com.company.tai.inventory.dto.BrandRequest;
import com.company.tai.inventory.entity.Brand;
import com.company.tai.inventory.repository.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    public List<BrandDto> listAll() {
        return brandRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public BrandDto create(BrandRequest request) {
        if (brandRepository.existsByName(request.name())) {
            throw new BusinessRuleException("Brand already exists: " + request.name());
        }
        return toDto(brandRepository.save(Brand.builder().name(request.name()).build()));
    }

    @Transactional
    public BrandDto update(Long id, BrandRequest request) {
        Brand brand = findOrThrow(id);
        brand.setName(request.name());
        return toDto(brand);
    }

    @Transactional
    public void delete(Long id) {
        brandRepository.delete(findOrThrow(id));
    }

    private Brand findOrThrow(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + id));
    }

    private BrandDto toDto(Brand b) {
        return new BrandDto(b.getId(), b.getName());
    }
}
