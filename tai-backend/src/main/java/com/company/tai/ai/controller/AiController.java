package com.company.tai.ai.controller;

import com.company.tai.ai.dto.*;
import com.company.tai.ai.service.AiQueryService;
import com.company.tai.ai.service.ForecastService;
import com.company.tai.ai.service.PurchaseOrderCopilotService;
import com.company.tai.ai.service.SeasonalDemandService;
import com.company.tai.ai.service.UnifiedForecastService;
import com.company.tai.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiQueryService aiQueryService;
    private final ForecastService forecastService;
    private final PurchaseOrderCopilotService purchaseOrderCopilotService;
    private final SeasonalDemandService seasonalDemandService;
    private final UnifiedForecastService unifiedForecastService;

    @PostMapping("/query")
    public ApiResponse<AiQueryResponse> query(@Valid @RequestBody AiQueryRequest request) {
        return ApiResponse.ok(aiQueryService.answer(request.question()));
    }

    @GetMapping("/forecast/{productId}")
    public ApiResponse<ForecastDto> forecast(@PathVariable Long productId) {
        return ApiResponse.ok(forecastService.forecast(productId));
    }

    @PostMapping("/suggest-purchase-order")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<PurchaseOrderSuggestion> suggestPurchaseOrder(@RequestBody(required = false) SuggestPurchaseOrderRequest request) {
        SuggestPurchaseOrderRequest effective = request != null ? request : new SuggestPurchaseOrderRequest(null, null);
        return ApiResponse.ok(purchaseOrderCopilotService.suggest(effective));
    }

    @GetMapping("/seasonal-demand")
    public ApiResponse<SeasonalDemandDto> seasonalDemand(
            @RequestParam Long productId,
            @RequestParam(defaultValue = "month") String period) {
        return ApiResponse.ok(seasonalDemandService.aggregate(productId, period));
    }

    @GetMapping("/seasonal-demand/predict")
    public ApiResponse<SeasonalPredictionDto> predictSeasonalDemand(
            @RequestParam Long productId,
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false) Integer targetMonth,
            @RequestParam(required = false) Integer targetQuarter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {

        int bucket = seasonalDemandService.resolveTargetBucket(period, targetMonth, targetQuarter, targetDate);
        return ApiResponse.ok(seasonalDemandService.predict(productId, period, bucket));
    }

    @GetMapping("/forecast")
    public ApiResponse<UnifiedForecastDto> unifiedForecast(
            @RequestParam Long productId,
            @RequestParam String horizon,
            @RequestParam(required = false) String targetPeriod) {
        return ApiResponse.ok(unifiedForecastService.forecast(productId, horizon, targetPeriod));
    }

    @GetMapping("/forecast/at-risk")
    public ApiResponse<List<AtRiskItemDto>> atRisk(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "week") String horizon) {
        return ApiResponse.ok(unifiedForecastService.atRisk(warehouseId, horizon));
    }

    @GetMapping("/query-examples")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<List<AiQueryExampleDto>> listQueryExamples() {
        return ApiResponse.ok(aiQueryService.listExamples());
    }

    @PostMapping("/query-examples")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<AiQueryExampleDto> addQueryExample(@Valid @RequestBody AiQueryExampleRequest request) {
        return ApiResponse.ok("Training example added", aiQueryService.addExample(request));
    }
}
