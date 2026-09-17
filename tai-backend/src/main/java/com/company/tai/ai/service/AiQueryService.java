package com.company.tai.ai.service;

import com.company.tai.ai.dto.*;
import com.company.tai.ai.entity.AiQueryExample;
import com.company.tai.ai.repository.AiQueryExampleRepository;
import com.company.tai.inventory.entity.Brand;
import com.company.tai.inventory.entity.Category;
import com.company.tai.inventory.entity.StockItem;
import com.company.tai.inventory.entity.Warehouse;
import com.company.tai.inventory.repository.BrandRepository;
import com.company.tai.inventory.repository.CategoryRepository;
import com.company.tai.inventory.repository.StockItemRepository;
import com.company.tai.inventory.repository.WarehouseRepository;
import com.company.tai.inventory.service.ProductService;
import com.company.tai.inventory.dto.StockItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Translates a natural-language question into a validated, structured filter and executes it
 * against the app's own existing services — never against raw SQL, and the classifier/entity
 * matcher never has database write access or sees anything beyond category/brand/warehouse
 * names it already extracted from this same database.
 */
@Service
@RequiredArgsConstructor
public class AiQueryService {

    private static final double CONFIDENCE_THRESHOLD = 0.55;

    private final IntentClassifierService intentClassifierService;
    private final AiQueryExampleRepository aiQueryExampleRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockItemRepository stockItemRepository;
    private final ProductService productService;

    public AiQueryResponse answer(String question) {
        IntentClassifierService.Prediction prediction = intentClassifierService.classify(question);

        if (!prediction.trained()) {
            return new AiQueryResponse(false,
                    "The assistant hasn't been trained with any example questions yet.", null, null, null);
        }
        if (prediction.confidence() < CONFIDENCE_THRESHOLD) {
            return new AiQueryResponse(false,
                    "I can search by product name, category, brand, warehouse, and stock status — try rephrasing.",
                    null, null, null);
        }

        // Entity extraction is a live, deterministic lookup against real DB rows — never
        // guessed by the classifier. This is also validated against an allowlist implicitly:
        // only names that actually exist in categories/brands/warehouses can ever be matched.
        String lower = question.toLowerCase();
        Category matchedCategory = findContained(categoryRepository.findAll(), Category::getName, lower);
        Brand matchedBrand = findContained(brandRepository.findAll(), Brand::getName, lower);
        Warehouse matchedWarehouse = findContained(warehouseRepository.findAll(), Warehouse::getName, lower);

        String searchText = stripKnownEntities(question, matchedCategory, matchedBrand, matchedWarehouse);

        AiUnderstoodFilter filter = new AiUnderstoodFilter(
                prediction.intent(),
                matchedCategory != null ? matchedCategory.getName() : null,
                matchedBrand != null ? matchedBrand.getName() : null,
                matchedWarehouse != null ? matchedWarehouse.getName() : null,
                searchText,
                prediction.confidence()
        );

        if ("LOW_STOCK_SEARCH".equals(prediction.intent())) {
            return lowStockAnswer(filter);
        }
        return productSearchAnswer(filter);
    }

    private AiQueryResponse productSearchAnswer(AiUnderstoodFilter filter) {
        Long categoryId = filter.categoryName() != null
                ? categoryRepository.findAll().stream().filter(c -> c.getName().equals(filter.categoryName())).findFirst().map(Category::getId).orElse(null)
                : null;
        Long brandId = filter.brandName() != null
                ? brandRepository.findAll().stream().filter(b -> b.getName().equals(filter.brandName())).findFirst().map(Brand::getId).orElse(null)
                : null;

        var page = productService.search(
                filter.searchText() != null && !filter.searchText().isBlank() ? filter.searchText() : null,
                categoryId, brandId, PageRequest.of(0, 50));

        StringBuilder message = new StringBuilder("Showing products");
        if (filter.categoryName() != null) message.append(" in category '").append(filter.categoryName()).append("'");
        if (filter.brandName() != null) message.append(" from brand '").append(filter.brandName()).append("'");
        if (filter.searchText() != null && !filter.searchText().isBlank()) message.append(" matching '").append(filter.searchText()).append("'");
        if (filter.warehouseName() != null) {
            message.append(". Note: product search isn't scoped to a warehouse — '")
                    .append(filter.warehouseName()).append("' was ignored; try a low-stock question to filter by warehouse.");
        }

        return new AiQueryResponse(true, message.toString(), filter, page, null);
    }

    private AiQueryResponse lowStockAnswer(AiUnderstoodFilter filter) {
        List<StockItem> lowStock = stockItemRepository.findLowStock();

        List<StockItemDto> results = lowStock.stream()
                .filter(si -> filter.categoryName() == null
                        || (si.getProduct().getCategory() != null && filter.categoryName().equals(si.getProduct().getCategory().getName())))
                .filter(si -> filter.brandName() == null
                        || (si.getProduct().getBrand() != null && filter.brandName().equals(si.getProduct().getBrand().getName())))
                .filter(si -> filter.warehouseName() == null || filter.warehouseName().equals(si.getWarehouse().getName()))
                .map(si -> new StockItemDto(
                        si.getId(), si.getProduct().getId(), si.getProduct().getName(),
                        si.getWarehouse().getId(), si.getWarehouse().getName(), si.getQuantity()))
                .toList();

        StringBuilder message = new StringBuilder("Showing low-stock items");
        if (filter.categoryName() != null) message.append(" in category '").append(filter.categoryName()).append("'");
        if (filter.brandName() != null) message.append(" from brand '").append(filter.brandName()).append("'");
        if (filter.warehouseName() != null) message.append(" in warehouse '").append(filter.warehouseName()).append("'");

        return new AiQueryResponse(true, message.toString(), filter, null, results);
    }

    private <T> T findContained(List<T> candidates, java.util.function.Function<T, String> nameFn, String lowerQuestion) {
        return candidates.stream()
                .filter(c -> nameFn.apply(c) != null && lowerQuestion.contains(nameFn.apply(c).toLowerCase()))
                .findFirst()
                .orElse(null);
    }

    private String stripKnownEntities(String question, Category category, Brand brand, Warehouse warehouse) {
        String result = question;
        if (category != null) result = result.replaceAll("(?i)" + java.util.regex.Pattern.quote(category.getName()), "");
        if (brand != null) result = result.replaceAll("(?i)" + java.util.regex.Pattern.quote(brand.getName()), "");
        if (warehouse != null) result = result.replaceAll("(?i)" + java.util.regex.Pattern.quote(warehouse.getName()), "");
        // Strip common stop-phrases so what's left is closer to an actual free-text search term.
        result = result.replaceAll("(?i)\\b(show me|show|list|find|search for|search|what|which|all|products?|items?|category|brand|warehouse|in|the|from|by|of|with|named|called|for|do we|does|is|are|we sell|we have|come from)\\b", " ");
        return result.trim().replaceAll("\\s+", " ");
    }

    @Transactional
    public AiQueryExampleDto addExample(AiQueryExampleRequest request) {
        AiQueryExample example = AiQueryExample.builder()
                .questionText(request.questionText())
                .intent(request.intent())
                .build();
        AiQueryExample saved = aiQueryExampleRepository.save(example);
        return new AiQueryExampleDto(saved.getId(), saved.getQuestionText(), saved.getIntent());
    }

    public List<AiQueryExampleDto> listExamples() {
        return aiQueryExampleRepository.findAll().stream()
                .map(e -> new AiQueryExampleDto(e.getId(), e.getQuestionText(), e.getIntent()))
                .toList();
    }
}
