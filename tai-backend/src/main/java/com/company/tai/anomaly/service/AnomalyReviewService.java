package com.company.tai.anomaly.service;

import com.company.tai.anomaly.dto.AnomalyDto;
import com.company.tai.anomaly.dto.AnomalyReviewRequest;
import com.company.tai.anomaly.entity.AnomalyStatus;
import com.company.tai.anomaly.entity.StockAnomaly;
import com.company.tai.anomaly.repository.StockAnomalyRepository;
import com.company.tai.common.exception.BusinessRuleException;
import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.user.entity.User;
import com.company.tai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AnomalyReviewService {

    // Severity bucket thresholds — shared across all anomaly types for a consistent badge
    // color regardless of which detector produced the score.
    private static final BigDecimal MEDIUM_THRESHOLD = BigDecimal.valueOf(5);
    private static final BigDecimal HIGH_THRESHOLD = BigDecimal.valueOf(10);

    private final StockAnomalyRepository stockAnomalyRepository;
    private final UserRepository userRepository;

    public Page<AnomalyDto> search(AnomalyStatus status, String severity, Pageable pageable) {
        BigDecimal minSeverity = null;
        BigDecimal maxSeverity = null;
        if ("low".equals(severity)) {
            maxSeverity = MEDIUM_THRESHOLD;
        } else if ("medium".equals(severity)) {
            minSeverity = MEDIUM_THRESHOLD;
            maxSeverity = HIGH_THRESHOLD;
        } else if ("high".equals(severity)) {
            minSeverity = HIGH_THRESHOLD;
        }
        return stockAnomalyRepository.search(status, minSeverity, maxSeverity, pageable).map(this::toDto);
    }

    @Transactional
    public AnomalyDto review(Long id, AnomalyReviewRequest request) {
        StockAnomaly anomaly = stockAnomalyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Anomaly not found with id: " + id));

        AnomalyStatus newStatus;
        try {
            newStatus = AnomalyStatus.valueOf(request.status());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessRuleException("status must be REVIEWED or DISMISSED");
        }
        if (newStatus == AnomalyStatus.OPEN) {
            throw new BusinessRuleException("status must be REVIEWED or DISMISSED");
        }

        anomaly.setStatus(newStatus);
        anomaly.setReviewNote(request.reviewNote());
        anomaly.setReviewedAt(java.time.Instant.now());
        anomaly.setReviewedBy(currentUser());

        return toDto(anomaly);
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElse(null);
    }

    private String severityLevel(BigDecimal score) {
        if (score.compareTo(HIGH_THRESHOLD) >= 0) return "high";
        if (score.compareTo(MEDIUM_THRESHOLD) >= 0) return "medium";
        return "low";
    }

    private AnomalyDto toDto(StockAnomaly a) {
        var adjustment = a.getAdjustment();
        return new AnomalyDto(
                a.getId(),
                a.getAnomalyType().name(),
                a.getSeverityScore(),
                severityLevel(a.getSeverityScore()),
                a.getStatus().name(),
                a.getProduct().getId(),
                a.getProduct().getName(),
                a.getWarehouse() != null ? a.getWarehouse().getId() : null,
                a.getWarehouse() != null ? a.getWarehouse().getName() : null,
                adjustment != null ? adjustment.getId() : null,
                adjustment != null ? adjustment.getAdjustmentType().name() : null,
                adjustment != null ? adjustment.getQuantity() : null,
                adjustment != null && adjustment.getPerformedBy() != null ? adjustment.getPerformedBy().getFullName() : null,
                adjustment != null ? adjustment.getCreatedAt() : null,
                a.getContextNote(),
                a.getDetectedAt(),
                a.getReviewedBy() != null ? a.getReviewedBy().getFullName() : null,
                a.getReviewedAt(),
                a.getReviewNote()
        );
    }
}
