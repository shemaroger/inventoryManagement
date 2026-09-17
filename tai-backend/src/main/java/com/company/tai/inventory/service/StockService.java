package com.company.tai.inventory.service;

import com.company.tai.common.exception.BusinessRuleException;
import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.inventory.dto.StockAdjustmentDto;
import com.company.tai.inventory.dto.StockAdjustmentRequest;
import com.company.tai.inventory.dto.StockItemDto;
import com.company.tai.inventory.dto.StockTransferRequest;
import com.company.tai.inventory.dto.StockTransferResult;
import com.company.tai.inventory.entity.*;
import com.company.tai.inventory.repository.*;
import com.company.tai.user.entity.User;
import com.company.tai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StockService {

    private static final Set<AdjustmentType> INCREASING_TYPES =
            Set.of(AdjustmentType.INCREASE, AdjustmentType.TRANSFER_IN, AdjustmentType.RECOUNT);

    private final StockItemRepository stockItemRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRepository userRepository;

    public List<StockItemDto> getStockForWarehouse(Long warehouseId) {
        return stockItemRepository.findByWarehouseId(warehouseId).stream().map(this::toDto).toList();
    }

    public List<StockItemDto> getStockForProduct(Long productId) {
        return stockItemRepository.findByProductId(productId).stream().map(this::toDto).toList();
    }

    public List<StockItemDto> getLowStock() {
        return stockItemRepository.findLowStock().stream().map(this::toDto).toList();
    }

    public Page<StockAdjustmentDto> searchAdjustments(Long productId, Long warehouseId, Pageable pageable) {
        return stockAdjustmentRepository.search(productId, warehouseId, pageable).map(this::toAdjustmentDto);
    }

    @Transactional
    public StockItemDto applyAdjustment(StockAdjustmentRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.productId()));
        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + request.warehouseId()));

        StockItem stockItem = recordAdjustment(product, warehouse, request.adjustmentType(), request.quantity(), request.reason());
        return toDto(stockItem);
    }

    @Transactional
    public StockTransferResult transfer(StockTransferRequest request) {
        if (request.sourceWarehouseId().equals(request.destinationWarehouseId())) {
            throw new BusinessRuleException("Source and destination warehouse must be different");
        }

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.productId()));
        Warehouse source = warehouseRepository.findById(request.sourceWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + request.sourceWarehouseId()));
        Warehouse destination = warehouseRepository.findById(request.destinationWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + request.destinationWarehouseId()));

        StockItem sourceItem = recordAdjustment(product, source, AdjustmentType.TRANSFER_OUT, request.quantity(), request.reason());
        StockItem destinationItem = recordAdjustment(product, destination, AdjustmentType.TRANSFER_IN, request.quantity(), request.reason());

        return new StockTransferResult(toDto(sourceItem), toDto(destinationItem));
    }

    // Shared by single adjustments and transfers (a transfer is just a paired OUT+IN,
    // both going through this same insufficient-stock check and audit-record write,
    // inside one @Transactional method so both sides commit or neither does).
    private StockItem recordAdjustment(Product product, Warehouse warehouse, AdjustmentType type, BigDecimal quantity, String reason) {
        StockItem stockItem = stockItemRepository.findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                .orElseGet(() -> StockItem.builder()
                        .product(product)
                        .warehouse(warehouse)
                        .quantity(BigDecimal.ZERO)
                        .build());

        BigDecimal newQuantity = INCREASING_TYPES.contains(type)
                ? stockItem.getQuantity().add(quantity)
                : stockItem.getQuantity().subtract(quantity);

        if (newQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException(
                    "Insufficient stock: cannot reduce quantity below zero for product " + product.getSku()
                            + " in warehouse " + warehouse.getName());
        }

        stockItem.setQuantity(newQuantity);
        stockItemRepository.save(stockItem);

        StockAdjustment adjustment = StockAdjustment.builder()
                .product(product)
                .warehouse(warehouse)
                .adjustmentType(type)
                .quantity(quantity)
                .reason(reason)
                .performedBy(currentUser())
                .build();
        stockAdjustmentRepository.save(adjustment);

        return stockItem;
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElse(null);
    }

    private StockItemDto toDto(StockItem si) {
        return new StockItemDto(
                si.getId(),
                si.getProduct().getId(), si.getProduct().getName(),
                si.getWarehouse().getId(), si.getWarehouse().getName(),
                si.getQuantity()
        );
    }

    private StockAdjustmentDto toAdjustmentDto(StockAdjustment sa) {
        return new StockAdjustmentDto(
                sa.getId(),
                sa.getProduct().getId(), sa.getProduct().getName(),
                sa.getWarehouse().getId(), sa.getWarehouse().getName(),
                sa.getAdjustmentType(), sa.getQuantity(), sa.getReason(),
                sa.getPerformedBy() != null ? sa.getPerformedBy().getFullName() : null,
                sa.getCreatedAt()
        );
    }
}
