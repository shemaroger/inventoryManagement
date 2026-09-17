package com.company.tai.inventory.repository;

import com.company.tai.inventory.entity.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StockItemRepository extends JpaRepository<StockItem, Long> {
    Optional<StockItem> findByProductIdAndWarehouseId(Long productId, Long warehouseId);
    List<StockItem> findByProductId(Long productId);
    List<StockItem> findByWarehouseId(Long warehouseId);

    @Query("SELECT si FROM StockItem si WHERE si.quantity <= si.product.reorderLevel")
    List<StockItem> findLowStock();
}
