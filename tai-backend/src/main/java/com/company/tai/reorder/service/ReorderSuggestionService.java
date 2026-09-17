package com.company.tai.reorder.service;

import com.company.tai.common.exception.BusinessRuleException;
import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.purchasing.dto.CreatePurchaseOrderRequest;
import com.company.tai.purchasing.dto.PurchaseOrderLineRequest;
import com.company.tai.purchasing.entity.PurchaseOrder;
import com.company.tai.purchasing.repository.PurchaseOrderRepository;
import com.company.tai.purchasing.service.PurchaseOrderService;
import com.company.tai.reorder.dto.CreatePoFromSuggestionRequest;
import com.company.tai.reorder.dto.DismissSuggestionRequest;
import com.company.tai.reorder.dto.ReorderSuggestionDto;
import com.company.tai.reorder.entity.ReorderSuggestion;
import com.company.tai.reorder.entity.ReorderSuggestionStatus;
import com.company.tai.reorder.repository.ReorderSuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReorderSuggestionService {

    // Severity bucket thresholds for the frontend badge — shared scale regardless of whether
    // the score came from the trend formula or the ratio fallback (see ReorderEvaluationService).
    private static final BigDecimal MEDIUM_THRESHOLD = BigDecimal.valueOf(20);
    private static final BigDecimal HIGH_THRESHOLD = BigDecimal.valueOf(60);

    private final ReorderSuggestionRepository reorderSuggestionRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderService purchaseOrderService;

    public Page<ReorderSuggestionDto> search(ReorderSuggestionStatus status, Long warehouseId, Pageable pageable) {
        return reorderSuggestionRepository.search(status, warehouseId, pageable).map(this::toDto);
    }

    public long countOpen() {
        return reorderSuggestionRepository.countByStatus(ReorderSuggestionStatus.OPEN);
    }

    public List<ReorderSuggestionDto> topUrgent() {
        return reorderSuggestionRepository.findTop5ByStatusOrderByUrgencyScoreDesc(ReorderSuggestionStatus.OPEN)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public ReorderSuggestionDto dismiss(Long id, DismissSuggestionRequest request) {
        ReorderSuggestion suggestion = findOrThrow(id);
        if (suggestion.getStatus() != ReorderSuggestionStatus.OPEN) {
            throw new BusinessRuleException("Only an OPEN suggestion can be dismissed (current status: " + suggestion.getStatus() + ")");
        }
        suggestion.setStatus(ReorderSuggestionStatus.DISMISSED);
        suggestion.setDismissReason(request.reason());
        suggestion.setResolvedAt(Instant.now());
        return toDto(suggestion);
    }

    @Transactional
    public ReorderSuggestionDto createPurchaseOrder(Long id, CreatePoFromSuggestionRequest request) {
        ReorderSuggestion suggestion = findOrThrow(id);
        if (suggestion.getStatus() != ReorderSuggestionStatus.OPEN) {
            throw new BusinessRuleException("Only an OPEN suggestion can be converted to a purchase order (current status: " + suggestion.getStatus() + ")");
        }

        BigDecimal quantity = request.quantity() != null ? request.quantity() : suggestion.getSuggestedQuantity();
        BigDecimal unitCost = request.unitCost() != null ? request.unitCost() : suggestion.getProduct().getCostPrice();

        var poRequest = new CreatePurchaseOrderRequest(
                request.supplierId(),
                suggestion.getWarehouse().getId(),
                null,
                "Auto-generated from reorder suggestion #" + suggestion.getId(),
                null,
                List.of(new PurchaseOrderLineRequest(suggestion.getProduct().getId(), quantity, unitCost))
        );
        var createdPo = purchaseOrderService.create(poRequest);
        PurchaseOrder poEntity = purchaseOrderRepository.findById(createdPo.id())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found with id: " + createdPo.id()));

        suggestion.setStatus(ReorderSuggestionStatus.ORDERED);
        suggestion.setPurchaseOrder(poEntity);
        suggestion.setResolvedAt(Instant.now());

        return toDto(suggestion);
    }

    private ReorderSuggestion findOrThrow(Long id) {
        return reorderSuggestionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reorder suggestion not found with id: " + id));
    }

    private String urgencyLevel(BigDecimal score) {
        if (score.compareTo(HIGH_THRESHOLD) >= 0) return "high";
        if (score.compareTo(MEDIUM_THRESHOLD) >= 0) return "medium";
        return "low";
    }

    private ReorderSuggestionDto toDto(ReorderSuggestion s) {
        return new ReorderSuggestionDto(
                s.getId(),
                s.getProduct().getId(), s.getProduct().getName(), s.getProduct().getSku(),
                s.getWarehouse().getId(), s.getWarehouse().getName(),
                s.getCurrentQuantity(), s.getReorderLevel(), s.getSuggestedQuantity(),
                s.getUrgencyScore(), urgencyLevel(s.getUrgencyScore()),
                s.getProjectedStockoutDate(), s.getStatus().name(),
                s.getPurchaseOrder() != null ? s.getPurchaseOrder().getId() : null,
                s.getDismissReason(), s.getCreatedAt(), s.getUpdatedAt(), s.getResolvedAt()
        );
    }
}
