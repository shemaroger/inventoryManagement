package com.company.tai.reorder.service;

import com.company.tai.ai.dto.ForecastDto;
import com.company.tai.ai.service.ForecastService;
import com.company.tai.inventory.entity.Product;
import com.company.tai.inventory.entity.StockItem;
import com.company.tai.inventory.repository.ProductRepository;
import com.company.tai.inventory.repository.StockItemRepository;
import com.company.tai.reorder.dto.ReorderRefreshResultDto;
import com.company.tai.reorder.entity.ReorderSuggestion;
import com.company.tai.reorder.entity.ReorderSuggestionStatus;
import com.company.tai.reorder.repository.ReorderSuggestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * Continuously watches every active product/warehouse combination against its reorder level.
 * Uses AI Demand Forecasting's trend projection (ForecastService, deployed) for urgency ranking
 * and suggested quantity when it can — an explicit numeric fallback otherwise. No LLM, no
 * judgment calls: everything here is arithmetic against configurable, documented constants.
 */
@Service
public class ReorderEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(ReorderEvaluationService.class);

    // Fallback heuristic when no usable consumption trend exists: bring stock back up to this
    // multiple of the reorder level (default 2.0, configurable via app.reorder.level-multiplier).

    // When a trend IS available, cover this many days of projected consumption.
    private static final int REORDER_COVERAGE_DAYS = 30;

    private final ProductRepository productRepository;
    private final StockItemRepository stockItemRepository;
    private final ReorderSuggestionRepository reorderSuggestionRepository;
    private final ForecastService forecastService;
    private final BigDecimal reorderLevelMultiplier;

    public ReorderEvaluationService(
            ProductRepository productRepository,
            StockItemRepository stockItemRepository,
            ReorderSuggestionRepository reorderSuggestionRepository,
            ForecastService forecastService,
            @Value("${app.reorder.level-multiplier:2.0}") double reorderLevelMultiplier) {
        this.productRepository = productRepository;
        this.stockItemRepository = stockItemRepository;
        this.reorderSuggestionRepository = reorderSuggestionRepository;
        this.forecastService = forecastService;
        this.reorderLevelMultiplier = BigDecimal.valueOf(reorderLevelMultiplier);
    }

    // Interval configurable via app.reorder.schedule-cron in application.yaml — default every 6 hours.
    @Scheduled(cron = "${app.reorder.schedule-cron:0 0 */6 * * *}")
    public void runScheduledEvaluation() {
        var result = runEvaluation();
        log.info("Reorder evaluation: {} evaluated, {} created, {} updated, {} auto-resolved",
                result.evaluated(), result.created(), result.updated(), result.resolved());
    }

    @Transactional
    public ReorderRefreshResultDto runEvaluation() {
        int evaluated = 0, created = 0, updated = 0, resolved = 0;

        List<Product> activeProducts = productRepository.findAll().stream()
                .filter(Product::isActive)
                .filter(p -> p.getReorderLevel() != null && p.getReorderLevel().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        for (Product product : activeProducts) {
            List<StockItem> stockItems = stockItemRepository.findByProductId(product.getId());
            for (StockItem item : stockItems) {
                evaluated++;
                var existing = reorderSuggestionRepository.findByProductIdAndWarehouseIdAndStatus(
                        product.getId(), item.getWarehouse().getId(), ReorderSuggestionStatus.OPEN);

                boolean isLow = item.getQuantity().compareTo(product.getReorderLevel()) <= 0;

                if (!isLow) {
                    if (existing.isPresent()) {
                        // Stock recovered before anyone acted on it (e.g. a manual reorder
                        // outside the system) — don't leave a stale suggestion open.
                        ReorderSuggestion suggestion = existing.get();
                        suggestion.setStatus(ReorderSuggestionStatus.RESOLVED);
                        suggestion.setResolvedAt(Instant.now());
                        resolved++;
                    }
                    continue;
                }

                ForecastDto forecast = safeForecast(product.getId());
                boolean hasUsableTrend = forecast != null
                        && !"insufficient_data".equals(forecast.confidence())
                        && forecast.avgDailyConsumption() != null
                        && forecast.avgDailyConsumption().compareTo(BigDecimal.ZERO) > 0;

                BigDecimal suggestedQuantity = computeSuggestedQuantity(product, item, forecast, hasUsableTrend);
                BigDecimal urgencyScore = computeUrgencyScore(product, item, forecast, hasUsableTrend);

                if (existing.isPresent()) {
                    ReorderSuggestion suggestion = existing.get();
                    suggestion.setCurrentQuantity(item.getQuantity());
                    suggestion.setReorderLevel(product.getReorderLevel());
                    suggestion.setSuggestedQuantity(suggestedQuantity);
                    suggestion.setUrgencyScore(urgencyScore);
                    suggestion.setProjectedStockoutDate(hasUsableTrend ? forecast.projectedStockoutDate() : null);
                    updated++;
                } else {
                    ReorderSuggestion suggestion = ReorderSuggestion.builder()
                            .product(product)
                            .warehouse(item.getWarehouse())
                            .currentQuantity(item.getQuantity())
                            .reorderLevel(product.getReorderLevel())
                            .suggestedQuantity(suggestedQuantity)
                            .urgencyScore(urgencyScore)
                            .projectedStockoutDate(hasUsableTrend ? forecast.projectedStockoutDate() : null)
                            .status(ReorderSuggestionStatus.OPEN)
                            .build();
                    reorderSuggestionRepository.save(suggestion);
                    created++;
                }
            }
        }

        return new ReorderRefreshResultDto(evaluated, created, updated, resolved);
    }

    private ForecastDto safeForecast(Long productId) {
        try {
            return forecastService.forecast(productId);
        } catch (Exception e) {
            // Forecasting is a nice-to-have here — never let it block reorder evaluation itself.
            log.warn("Forecast lookup failed for product {}, falling back to ratio heuristic: {}", productId, e.getMessage());
            return null;
        }
    }

    private BigDecimal computeSuggestedQuantity(Product product, StockItem item, ForecastDto forecast, boolean hasUsableTrend) {
        if (hasUsableTrend) {
            BigDecimal coverage = forecast.avgDailyConsumption().multiply(BigDecimal.valueOf(REORDER_COVERAGE_DAYS));
            BigDecimal shortfall = product.getReorderLevel().subtract(item.getQuantity()).max(BigDecimal.ZERO);
            BigDecimal suggested = coverage.add(shortfall).setScale(0, RoundingMode.CEILING);
            return suggested.max(BigDecimal.ONE);
        }
        // Fallback heuristic: bring stock back up to `reorderLevelMultiplier` x reorder level.
        BigDecimal target = product.getReorderLevel().multiply(reorderLevelMultiplier);
        return target.subtract(item.getQuantity()).max(BigDecimal.ONE).setScale(0, RoundingMode.CEILING);
    }

    private BigDecimal computeUrgencyScore(Product product, StockItem item, ForecastDto forecast, boolean hasUsableTrend) {
        if (hasUsableTrend && forecast.daysUntilReorderNeeded() != null) {
            // Sooner projected stockout = higher score. Not calibrated 1:1 against the fallback
            // scale below (that's fine — both only need to rank correctly within their own method).
            return BigDecimal.valueOf(1000.0 / (forecast.daysUntilReorderNeeded() + 1)).setScale(2, RoundingMode.HALF_UP);
        }
        // Fallback: how far below reorder level, as a percentage-style ratio.
        if (product.getReorderLevel().compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        BigDecimal ratio = item.getQuantity().divide(product.getReorderLevel(), 4, RoundingMode.HALF_UP);
        BigDecimal deficit = BigDecimal.ONE.subtract(ratio).max(BigDecimal.ZERO);
        return deficit.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }
}
