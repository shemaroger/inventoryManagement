package com.company.tai.anomaly.repository;

import com.company.tai.anomaly.entity.AnomalyStatus;
import com.company.tai.anomaly.entity.AnomalyType;
import com.company.tai.anomaly.entity.StockAnomaly;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface StockAnomalyRepository extends JpaRepository<StockAnomaly, Long> {

    boolean existsByAdjustmentIdAndAnomalyType(Long adjustmentId, AnomalyType anomalyType);

    Optional<StockAnomaly> findByAdjustmentIdAndAnomalyType(Long adjustmentId, AnomalyType anomalyType);

    @Query("SELECT a FROM StockAnomaly a WHERE " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:minSeverity IS NULL OR a.severityScore >= :minSeverity) AND " +
           "(:maxSeverity IS NULL OR a.severityScore < :maxSeverity) " +
           "ORDER BY a.detectedAt DESC")
    Page<StockAnomaly> search(@Param("status") AnomalyStatus status,
                               @Param("minSeverity") BigDecimal minSeverity,
                               @Param("maxSeverity") BigDecimal maxSeverity,
                               Pageable pageable);
}
