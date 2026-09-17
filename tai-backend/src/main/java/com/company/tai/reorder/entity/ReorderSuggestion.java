package com.company.tai.reorder.entity;

import com.company.tai.inventory.entity.Product;
import com.company.tai.inventory.entity.Warehouse;
import com.company.tai.purchasing.entity.PurchaseOrder;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "reorder_suggestions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReorderSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Column(name = "current_quantity", nullable = false)
    private BigDecimal currentQuantity;

    @Column(name = "reorder_level", nullable = false)
    private BigDecimal reorderLevel;

    @Column(name = "suggested_quantity", nullable = false)
    private BigDecimal suggestedQuantity;

    @Column(name = "urgency_score", nullable = false)
    private BigDecimal urgencyScore;

    @Column(name = "projected_stockout_date")
    private LocalDate projectedStockoutDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReorderSuggestionStatus status = ReorderSuggestionStatus.OPEN;

    @ManyToOne
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    @Column(name = "dismiss_reason")
    private String dismissReason;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
