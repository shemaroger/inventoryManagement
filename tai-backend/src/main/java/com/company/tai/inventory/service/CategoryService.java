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
        if (request.parentId() != null) {
            if (request.parentId().equals(id)) {
                throw new BusinessRuleException("A category cannot be its own parent");
            }
            Category parent = findOrThrow(request.parentId());
            assertNoCycle(id, parent);
            category.setParent(parent);
        } else {
            category.setParent(null);
        }
        return toDto(category);
    }

    // Walks the candidate parent's ancestor chain to make sure `id` doesn't appear in it —
    // otherwise setting `parent` on `id` would create a cycle (e.g. A -> B -> A).
    private void assertNoCycle(Long id, Category candidateParent) {
        Category current = candidateParent;
        while (current != null) {
            if (current.getId().equals(id)) {
                throw new BusinessRuleException("Cannot set parent: this would create a circular category hierarchy");
            }
            current = current.getParent();
        }
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
