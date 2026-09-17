package com.company.tai.anomaly.entity;

import com.company.tai.inventory.entity.Product;
import com.company.tai.inventory.entity.StockAdjustment;
import com.company.tai.inventory.entity.Warehouse;
import com.company.tai.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "stock_anomalies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockAnomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "adjustment_id")
    private StockAdjustment adjustment;

    // No Sales module exists yet in this codebase — reserved for when reconciliation-mismatch
    // detection (comparing Sale quantities against stock decreases) becomes possible.
    @Column(name = "sale_id")
    private Long saleId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(name = "anomaly_type", nullable = false)
    private AnomalyType anomalyType;

    @Column(name = "severity_score", nullable = false)
    private BigDecimal severityScore;

    @Column(name = "context_note")
    private String contextNote;

    @Column(name = "detected_at")
    private Instant detectedAt;

    @ManyToOne
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note")
    private String reviewNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AnomalyStatus status = AnomalyStatus.OPEN;

    @PrePersist
    void onCreate() {
        detectedAt = Instant.now();
    }
}
