package com.company.tai.reorder.controller;

import com.company.tai.common.dto.ApiResponse;
import com.company.tai.reorder.dto.*;
import com.company.tai.reorder.entity.ReorderSuggestionStatus;
import com.company.tai.reorder.service.ReorderEvaluationService;
import com.company.tai.reorder.service.ReorderSuggestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reorder-suggestions")
@RequiredArgsConstructor
public class ReorderSuggestionController {

    private final ReorderSuggestionService reorderSuggestionService;
    private final ReorderEvaluationService reorderEvaluationService;

    @GetMapping
    public ApiResponse<Page<ReorderSuggestionDto>> search(
            @RequestParam(required = false) ReorderSuggestionStatus status,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(reorderSuggestionService.search(status, warehouseId, pageable));
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.ok(Map.of(
                "openCount", reorderSuggestionService.countOpen(),
                "topUrgent", reorderSuggestionService.topUrgent()
        ));
    }

    @PatchMapping("/{id}/dismiss")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<ReorderSuggestionDto> dismiss(@PathVariable Long id, @RequestBody(required = false) DismissSuggestionRequest request) {
        DismissSuggestionRequest effective = request != null ? request : new DismissSuggestionRequest(null);
        return ApiResponse.ok("Suggestion dismissed", reorderSuggestionService.dismiss(id, effective));
    }

    @PostMapping("/{id}/create-po")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<ReorderSuggestionDto> createPurchaseOrder(@PathVariable Long id, @Valid @RequestBody CreatePoFromSuggestionRequest request) {
        return ApiResponse.ok("Purchase order created", reorderSuggestionService.createPurchaseOrder(id, request));
    }

    @PostMapping("/refresh")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<ReorderRefreshResultDto> refresh() {
        return ApiResponse.ok("Reorder evaluation complete", reorderEvaluationService.runEvaluation());
    }
}
