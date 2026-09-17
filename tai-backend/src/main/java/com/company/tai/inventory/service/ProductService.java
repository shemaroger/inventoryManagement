package com.company.tai.inventory.service;

import com.company.tai.common.exception.BusinessRuleException;
import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.inventory.dto.ProductDto;
import com.company.tai.inventory.dto.ProductRequest;
import com.company.tai.inventory.entity.*;
import com.company.tai.inventory.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductService {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024; // 5MB

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final UnitRepository unitRepository;
    private final StockItemRepository stockItemRepository;
    private final Path uploadBasePath;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            BrandRepository brandRepository,
            UnitRepository unitRepository,
            StockItemRepository stockItemRepository,
            @Value("${app.upload.base-path}") String uploadBasePath) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.unitRepository = unitRepository;
        this.stockItemRepository = stockItemRepository;
        this.uploadBasePath = Path.of(uploadBasePath);
    }

    public Page<ProductDto> search(String search, Long categoryId, Long brandId, Pageable pageable) {
        return productRepository.search(search, categoryId, brandId, pageable).map(this::toDto);
    }

    public ProductDto getById(Long id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public ProductDto create(ProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new BusinessRuleException("A product with SKU '" + request.sku() + "' already exists");
        }
        if (request.barcode() != null && !request.barcode().isBlank() && productRepository.existsByBarcode(request.barcode())) {
            throw new BusinessRuleException("A product with barcode '" + request.barcode() + "' already exists");
        }

        Product product = Product.builder()
                .sku(request.sku())
                .barcode(request.barcode())
                .name(request.name())
                .description(request.description())
                .category(request.categoryId() != null ? findCategory(request.categoryId()) : null)
                .brand(request.brandId() != null ? findBrand(request.brandId()) : null)
                .unit(request.unitId() != null ? findUnit(request.unitId()) : null)
                .costPrice(request.costPrice())
                .sellingPrice(request.sellingPrice())
                .reorderLevel(request.reorderLevel() != null ? request.reorderLevel() : BigDecimal.ZERO)
                .active(true)
                .build();

        return toDto(productRepository.save(product));
    }

    @Transactional
    public ProductDto update(Long id, ProductRequest request) {
        Product product = findOrThrow(id);

        product.setSku(request.sku());
        product.setBarcode(request.barcode());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setCategory(request.categoryId() != null ? findCategory(request.categoryId()) : null);
        product.setBrand(request.brandId() != null ? findBrand(request.brandId()) : null);
        product.setUnit(request.unitId() != null ? findUnit(request.unitId()) : null);
        product.setCostPrice(request.costPrice());
        product.setSellingPrice(request.sellingPrice());
        product.setReorderLevel(request.reorderLevel() != null ? request.reorderLevel() : BigDecimal.ZERO);

        return toDto(product);
    }

    @Transactional
    public void deactivate(Long id) {
        Product product = findOrThrow(id);
        product.setActive(false);
    }

    private Product findOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
    }

    private Brand findBrand(Long id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + id));
    }

    private Unit findUnit(Long id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Unit not found with id: " + id));
    }

    @Transactional
    public ProductDto storeImage(Long id, MultipartFile file) {
        Product product = findOrThrow(id);

        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("No file provided");
        }
        if (!ALLOWED_IMAGE_TYPES.contains(file.getContentType())) {
            throw new BusinessRuleException("Unsupported image type: " + file.getContentType() + " (allowed: jpg, png, webp)");
        }
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new BusinessRuleException("Image exceeds the 5MB size limit");
        }

        String extension = switch (file.getContentType()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> ".webp";
        };
        String filename = "product-" + id + "-" + UUID.randomUUID() + extension;

        try {
            Files.createDirectories(uploadBasePath);
            Path target = uploadBasePath.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            // Clean up the previous image file, if any, now that the new one is stored.
            if (product.getImagePath() != null) {
                Files.deleteIfExists(uploadBasePath.resolve(product.getImagePath()));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to store product image", e);
        }

        product.setImagePath(filename);
        return toDto(product);
    }

    public Resource loadImage(Long id) {
        Product product = findOrThrow(id);
        if (product.getImagePath() == null) {
            throw new ResourceNotFoundException("Product " + id + " has no image");
        }
        Path imagePath = uploadBasePath.resolve(product.getImagePath());
        if (!Files.exists(imagePath)) {
            throw new ResourceNotFoundException("Image file is missing for product " + id);
        }
        return new FileSystemResource(imagePath);
    }

    public String imageContentType(Long id) {
        String path = findOrThrow(id).getImagePath();
        if (path == null) return null;
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private ProductDto toDto(Product p) {
        BigDecimal totalStock = stockItemRepository.findByProductId(p.getId()).stream()
                .map(StockItem::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ProductDto(
                p.getId(), p.getSku(), p.getBarcode(), p.getName(), p.getDescription(),
                p.getCategory() != null ? p.getCategory().getId() : null,
                p.getCategory() != null ? p.getCategory().getName() : null,
                p.getBrand() != null ? p.getBrand().getId() : null,
                p.getBrand() != null ? p.getBrand().getName() : null,
                p.getUnit() != null ? p.getUnit().getId() : null,
                p.getUnit() != null ? p.getUnit().getName() : null,
                p.getCostPrice(), p.getSellingPrice(), p.getReorderLevel(),
                totalStock, p.isActive(),
                p.getImagePath() != null ? "/api/products/" + p.getId() + "/image" : null
        );
    }
}
