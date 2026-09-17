package com.company.tai.reorder.repository;

import com.company.tai.reorder.entity.ReorderSuggestion;
import com.company.tai.reorder.entity.ReorderSuggestionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReorderSuggestionRepository extends JpaRepository<ReorderSuggestion, Long> {

    Optional<ReorderSuggestion> findByProductIdAndWarehouseIdAndStatus(Long productId, Long warehouseId, ReorderSuggestionStatus status);

    long countByStatus(ReorderSuggestionStatus status);

    List<ReorderSuggestion> findTop5ByStatusOrderByUrgencyScoreDesc(ReorderSuggestionStatus status);

    @Query("SELECT r FROM ReorderSuggestion r WHERE " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:warehouseId IS NULL OR r.warehouse.id = :warehouseId) " +
           "ORDER BY r.urgencyScore DESC")
    Page<ReorderSuggestion> search(@Param("status") ReorderSuggestionStatus status,
                                    @Param("warehouseId") Long warehouseId,
                                    Pageable pageable);
}
