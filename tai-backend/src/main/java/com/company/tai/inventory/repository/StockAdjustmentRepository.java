package com.company.tai.inventory.repository;

import com.company.tai.inventory.entity.AdjustmentType;
import com.company.tai.inventory.entity.StockAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {
    List<StockAdjustment> findByProductIdOrderByCreatedAtDesc(Long productId);
    List<StockAdjustment> findByWarehouseIdOrderByCreatedAtDesc(Long warehouseId);

    @Query("SELECT sa FROM StockAdjustment sa WHERE " +
           "(:productId IS NULL OR sa.product.id = :productId) AND " +
           "(:warehouseId IS NULL OR sa.warehouse.id = :warehouseId) " +
           "ORDER BY sa.createdAt DESC")
    Page<StockAdjustment> search(@Param("productId") Long productId,
                                  @Param("warehouseId") Long warehouseId,
                                  Pageable pageable);

    // Used by the Daily Purchases statutory report: every goods-receipt event (identified by
    // PurchaseOrderService.receive()'s reason string) recorded within a date-time window. The
    // PO id isn't a real column here — only carried in the reason text — so callers parse it
    // back out; a dedicated GRN audit table would duplicate this data for no real benefit yet.
    List<StockAdjustment> findByAdjustmentTypeAndReasonStartingWithAndCreatedAtBetween(
            AdjustmentType adjustmentType, String reasonPrefix, Instant start, Instant end);

    // Used by inventory turnover to reconstruct an approximate opening stock level: current
    // quantity minus the net effect of every adjustment within the period gives the balance at
    // the start of that period, without needing a separate daily stock-snapshot table.
    List<StockAdjustment> findByProductIdAndCreatedAtBetween(Long productId, Instant start, Instant end);
}
