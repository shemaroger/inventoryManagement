package com.company.tai.ai.service;

import com.company.tai.ai.dto.ForecastDto;
import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.inventory.entity.AdjustmentType;
import com.company.tai.inventory.entity.Product;
import com.company.tai.inventory.entity.StockAdjustment;
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
 * Deterministic demand forecasting — a least-squares linear regression fitted fresh from
 * {@code stock_adjustments} history on every call. No LLM, no external API, no stored model:
 * the "training" is simply refitting the regression against whatever history currently exists,
 * so accuracy improves automatically as more adjustment data accumulates over time.
 */
@Service
@RequiredArgsConstructor
public class ForecastService {

    private static final int LOOKBACK_DAYS = 90;
    private static final int LOW_CONFIDENCE_DAYS = 14;
    private static final int HIGH_CONFIDENCE_DAYS = 45;

    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final StockItemRepository stockItemRepository;
    private final ProductRepository productRepository;

    public ForecastDto forecast(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        Instant cutoff = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
        List<StockAdjustment> decreases = stockAdjustmentRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .filter(a -> a.getAdjustmentType() == AdjustmentType.DECREASE)
                .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(cutoff))
                .toList();

        BigDecimal currentStock = stockItemRepository.findByProductId(productId).stream()
                .map(si -> si.getQuantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (decreases.isEmpty()) {
            return new ForecastDto(
                    productId, product.getName(), currentStock, product.getReorderLevel(),
                    BigDecimal.ZERO, null, null, "low",
                    "No DECREASE stock adjustments recorded in the last " + LOOKBACK_DAYS + " days — not enough history to forecast consumption."
            );
        }

        // Aggregate to one consumption total per calendar day, then fit consumed-so-far
        // (cumulative) against day-index via least squares — the slope is the fitted average
        // daily consumption rate.
        Map<LocalDate, BigDecimal> byDay = new TreeMap<>();
        for (StockAdjustment a : decreases) {
            LocalDate day = a.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            byDay.merge(day, a.getQuantity(), BigDecimal::add);
        }

        LocalDate firstDay = byDay.keySet().iterator().next();
        List<double[]> points = new ArrayList<>(); // [dayIndex, cumulativeConsumed]
        double cumulative = 0;
        for (var entry : byDay.entrySet()) {
            long dayIndex = ChronoUnit.DAYS.between(firstDay, entry.getKey());
            cumulative += entry.getValue().doubleValue();
            points.add(new double[]{dayIndex, cumulative});
        }

        double slope = leastSquaresSlope(points);
        BigDecimal avgDailyConsumption = BigDecimal.valueOf(Math.max(slope, 0))
                .setScale(3, RoundingMode.HALF_UP);

        int distinctDaysOfHistory = byDay.size();
        String confidence = distinctDaysOfHistory < LOW_CONFIDENCE_DAYS ? "low"
                : distinctDaysOfHistory < HIGH_CONFIDENCE_DAYS ? "medium" : "high";

        if (avgDailyConsumption.compareTo(BigDecimal.ZERO) <= 0) {
            return new ForecastDto(
                    productId, product.getName(), currentStock, product.getReorderLevel(),
                    BigDecimal.ZERO, null, null, confidence,
                    "Consumption trend is flat or negative over the lookback window — no reorder timeline can be projected."
            );
        }

        BigDecimal buffer = currentStock.subtract(product.getReorderLevel());
        int daysUntilReorderNeeded = buffer.compareTo(BigDecimal.ZERO) <= 0
                ? 0
                : buffer.divide(avgDailyConsumption, 0, RoundingMode.FLOOR).intValue();

        int daysUntilStockout = currentStock.divide(avgDailyConsumption, 0, RoundingMode.FLOOR).intValue();
        LocalDate projectedStockoutDate = LocalDate.now().plusDays(daysUntilStockout);

        String explanation = String.format(
                "Fitted from %d day(s) of DECREASE history in the last %d days: ~%s units/day. At %s in stock (reorder level %s), reorder is needed in ~%d day(s).",
                distinctDaysOfHistory, LOOKBACK_DAYS, avgDailyConsumption, currentStock, product.getReorderLevel(), daysUntilReorderNeeded
        );

        return new ForecastDto(
                productId, product.getName(), currentStock, product.getReorderLevel(),
                avgDailyConsumption, daysUntilReorderNeeded, projectedStockoutDate, confidence, explanation
        );
    }

    /** Simple least-squares slope: slope = covariance(x,y) / variance(x). */
    private double leastSquaresSlope(List<double[]> points) {
        if (points.size() < 2) {
            // Only one data point — the "slope" is just that day's total consumption.
            return points.isEmpty() ? 0 : points.get(0)[1];
        }
        double meanX = points.stream().mapToDouble(p -> p[0]).average().orElse(0);
        double meanY = points.stream().mapToDouble(p -> p[1]).average().orElse(0);

        double numerator = 0;
        double denominator = 0;
        for (double[] p : points) {
            numerator += (p[0] - meanX) * (p[1] - meanY);
            denominator += (p[0] - meanX) * (p[0] - meanX);
        }
        return denominator == 0 ? 0 : numerator / denominator;
    }
}
