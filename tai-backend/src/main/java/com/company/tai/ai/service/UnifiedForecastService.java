package com.company.tai.ai.service;

import com.company.tai.ai.dto.*;
import com.company.tai.common.exception.BusinessRuleException;
import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.inventory.entity.AdjustmentType;
import com.company.tai.inventory.entity.Product;
import com.company.tai.inventory.entity.StockAdjustment;
import com.company.tai.inventory.entity.StockItem;
import com.company.tai.inventory.repository.ProductRepository;
import com.company.tai.inventory.repository.StockAdjustmentRepository;
import com.company.tai.inventory.repository.StockItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Combines the trend engine (week/month) and the seasonal engine (season) behind a single
 * response shape, so the frontend doesn't need horizon-specific rendering logic. All math is
 * plain deterministic statistics — no LLM anywhere in this service, per this project's
 * "no external API" constraint; {@code explanation} is template-built from the computed
 * numbers, same as the two engines it composes.
 */
@Service
@RequiredArgsConstructor
public class UnifiedForecastService {

    // Lookback windows — deliberately separate per horizon and easy to retune.
    private static final int WEEK_LOOKBACK_DAYS = 56;   // 8 weeks
    private static final int MONTH_LOOKBACK_DAYS = 180; // ~6 months

    // Trend confidence thresholds, in distinct days of history within the lookback window.
    private static final int TREND_HIGH_CONFIDENCE_DAYS = 42; // 6 weeks
    private static final int TREND_MEDIUM_CONFIDENCE_DAYS = 14; // 2 weeks

    private static final String TREND_DATA_SOURCE =
            "Based on DECREASE-type stock adjustments (no Sales module exists yet) — real sales data would be a more accurate demand signal once available.";

    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final StockItemRepository stockItemRepository;
    private final ProductRepository productRepository;
    private final SeasonalDemandService seasonalDemandService;

    public UnifiedForecastDto forecast(Long productId, String horizon, String targetPeriod) {
        return switch (horizon) {
            case "week" -> trendForecast(productId, "week", WEEK_LOOKBACK_DAYS, 7);
            case "month" -> trendForecast(productId, "month", MONTH_LOOKBACK_DAYS, 30);
            case "season" -> seasonForecast(productId, targetPeriod);
            default -> throw new BusinessRuleException("horizon must be 'week', 'month', or 'season'");
        };
    }

    private UnifiedForecastDto trendForecast(Long productId, String horizon, int lookbackDays, int projectionDays) {
        Product product = findProduct(productId);

        Instant cutoff = Instant.now().minus(lookbackDays, ChronoUnit.DAYS);
        List<StockAdjustment> decreases = stockAdjustmentRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .filter(a -> a.getAdjustmentType() == AdjustmentType.DECREASE)
                .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(cutoff))
                .toList();

        String periodLabel = "week".equals(horizon) ? "next week" : "next month";

        if (decreases.isEmpty()) {
            return new UnifiedForecastDto(
                    productId, product.getName(), horizon, periodLabel, null, "trend",
                    new ForecastBasedOnDto(0, null, null, List.of()),
                    "insufficient_data",
                    "No DECREASE stock adjustments recorded in the last " + lookbackDays + " days — not enough history to project " + periodLabel + "'s demand.",
                    TREND_DATA_SOURCE
            );
        }

        TreeMap<LocalDate, BigDecimal> byDay = new TreeMap<>();
        for (StockAdjustment a : decreases) {
            LocalDate day = a.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            byDay.merge(day, a.getQuantity(), BigDecimal::add);
        }

        LocalDate firstDay = byDay.firstKey();
        LocalDate lastDay = byDay.lastKey();
        List<double[]> points = new ArrayList<>();
        double cumulative = 0;
        for (var entry : byDay.entrySet()) {
            long dayIndex = ChronoUnit.DAYS.between(firstDay, entry.getKey());
            cumulative += entry.getValue().doubleValue();
            points.add(new double[]{dayIndex, cumulative});
        }

        double slope = leastSquaresSlope(points);
        BigDecimal avgDailyConsumption = BigDecimal.valueOf(Math.max(slope, 0)).setScale(3, RoundingMode.HALF_UP);

        int distinctDays = byDay.size();
        String confidence = distinctDays == 0 ? "insufficient_data"
                : distinctDays < TREND_MEDIUM_CONFIDENCE_DAYS ? "low"
                : distinctDays < TREND_HIGH_CONFIDENCE_DAYS ? "medium" : "high";

        BigDecimal suggestedQuantity = avgDailyConsumption.multiply(BigDecimal.valueOf(projectionDays))
                .setScale(0, RoundingMode.HALF_UP);

        String explanation = String.format(
                "Selling ~%s units/day over the last %d day(s) of recorded history (fitted trend). Projected demand for %s: ~%s units.",
                avgDailyConsumption, lookbackDays, periodLabel, suggestedQuantity
        );

        ForecastBasedOnDto basedOn = new ForecastBasedOnDto(distinctDays, firstDay.toString(), lastDay.toString(), List.of());

        return new UnifiedForecastDto(
                productId, product.getName(), horizon, periodLabel, suggestedQuantity, "trend",
                basedOn, confidence, explanation, TREND_DATA_SOURCE
        );
    }

    private UnifiedForecastDto seasonForecast(Long productId, String targetPeriod) {
        if (targetPeriod == null || targetPeriod.isBlank()) {
            throw new BusinessRuleException("targetPeriod is required for horizon=season (a month name/number, or Q1-Q4)");
        }

        String period;
        int bucket;
        String normalized = targetPeriod.trim();
        if (normalized.matches("(?i)Q[1-4]")) {
            period = "quarter";
            bucket = Integer.parseInt(normalized.substring(1));
        } else if (normalized.matches("\\d{1,2}")) {
            period = "month";
            bucket = Integer.parseInt(normalized);
            if (bucket < 1 || bucket > 12) throw new BusinessRuleException("Month must be between 1 and 12");
        } else {
            period = "month";
            bucket = parseMonthName(normalized);
        }

        SeasonalPredictionDto seasonal = seasonalDemandService.predict(productId, period, bucket);
        List<StockAdjustment> matching = seasonalDemandService.matchingHistory(productId, period, bucket);

        ForecastBasedOnDto basedOn;
        if (matching.isEmpty()) {
            basedOn = new ForecastBasedOnDto(0, null, null, List.of());
        } else {
            List<LocalDate> dates = matching.stream()
                    .map(a -> a.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate())
                    .sorted()
                    .toList();
            basedOn = new ForecastBasedOnDto(
                    matching.size(), dates.get(0).toString(), dates.get(dates.size() - 1).toString(),
                    seasonal.basedOnYears()
            );
        }

        return new UnifiedForecastDto(
                productId, seasonal.productName(), "season", seasonal.targetPeriod(), seasonal.suggestedQuantity(),
                "seasonal_average", basedOn, seasonal.confidence(), seasonal.explanation(), seasonal.dataSource()
        );
    }

    /** Runs the trend forecast across every currently low-stock item, sorted soonest-stockout-first. */
    public List<AtRiskItemDto> atRisk(Long warehouseId, String horizon) {
        if (!"week".equals(horizon) && !"month".equals(horizon)) {
            throw new BusinessRuleException("horizon must be 'week' or 'month' for at-risk suggestions");
        }
        int lookbackDays = "week".equals(horizon) ? WEEK_LOOKBACK_DAYS : MONTH_LOOKBACK_DAYS;
        int projectionDays = "week".equals(horizon) ? 7 : 30;

        List<StockItem> lowStock = stockItemRepository.findLowStock();
        if (warehouseId != null) {
            lowStock = lowStock.stream().filter(si -> si.getWarehouse().getId().equals(warehouseId)).toList();
        }

        List<AtRiskItemDto> results = new ArrayList<>();
        for (StockItem item : lowStock) {
            UnifiedForecastDto forecast = trendForecast(item.getProduct().getId(), horizon, lookbackDays, projectionDays);

            Integer daysUntilStockout = null;
            if (forecast.suggestedQuantity() != null && forecast.suggestedQuantity().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal avgDaily = forecast.suggestedQuantity().divide(BigDecimal.valueOf(projectionDays), 6, RoundingMode.HALF_UP);
                if (avgDaily.compareTo(BigDecimal.ZERO) > 0) {
                    daysUntilStockout = item.getQuantity().divide(avgDaily, 0, RoundingMode.FLOOR).intValue();
                }
            }

            results.add(new AtRiskItemDto(
                    item.getProduct().getId(), item.getProduct().getName(),
                    item.getWarehouse().getId(), item.getWarehouse().getName(),
                    item.getQuantity(), item.getProduct().getReorderLevel(),
                    forecast.suggestedQuantity(), daysUntilStockout, forecast.confidence()
            ));
        }

        // Soonest projected stockout first; items with no usable projection sort last.
        return results.stream()
                .sorted(Comparator.comparing(r -> r.daysUntilStockout() == null ? Integer.MAX_VALUE : r.daysUntilStockout()))
                .toList();
    }

    private int parseMonthName(String name) {
        String[] months = {"january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december"};
        for (int i = 0; i < months.length; i++) {
            if (months[i].startsWith(name.toLowerCase())) return i + 1;
        }
        throw new BusinessRuleException("Could not parse '" + name + "' as a month name, month number, or quarter (Q1-Q4)");
    }

    private double leastSquaresSlope(List<double[]> points) {
        if (points.size() < 2) return points.isEmpty() ? 0 : points.get(0)[1];
        double meanX = points.stream().mapToDouble(p -> p[0]).average().orElse(0);
        double meanY = points.stream().mapToDouble(p -> p[1]).average().orElse(0);
        double numerator = 0, denominator = 0;
        for (double[] p : points) {
            numerator += (p[0] - meanX) * (p[1] - meanY);
            denominator += (p[0] - meanX) * (p[0] - meanX);
        }
        return denominator == 0 ? 0 : numerator / denominator;
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
    }
}
