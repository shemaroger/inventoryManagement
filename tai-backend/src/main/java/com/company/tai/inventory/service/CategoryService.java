package com.company.tai.inventory.service;

import com.company.tai.common.exception.BusinessRuleException;
import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.inventory.dto.CategoryDto;
import com.company.tai.inventory.dto.CategoryRequest;
import com.company.tai.inventory.entity.Category;
import com.company.tai.inventory.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryDto> listAll() {
        return categoryRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public CategoryDto create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new BusinessRuleException("Category already exists: " + request.name());
        }
        Category parent = request.parentId() != null ? findOrThrow(request.parentId()) : null;
        Category category = Category.builder().name(request.name()).parent(parent).build();
        return toDto(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDto update(Long id, CategoryRequest request) {
        Category category = findOrThrow(id);
        category.setName(request.name());
        category.setParent(request.parentId() != null ? findOrThrow(request.parentId()) : null);
        return toDto(category);
    }

    @Transactional
    public void delete(Long id) {
        categoryRepository.delete(findOrThrow(id));
    }

    private Category findOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
    }

    private CategoryDto toDto(Category c) {
        return new CategoryDto(c.getId(), c.getName(), c.getParent() != null ? c.getParent().getId() : null);
    }
}
