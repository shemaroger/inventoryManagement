package com.company.tai.ai.service;

import com.company.tai.ai.dto.*;
import com.company.tai.common.exception.BusinessRuleException;
import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.inventory.entity.AdjustmentType;
import com.company.tai.inventory.entity.Product;
import com.company.tai.inventory.entity.StockAdjustment;
import com.company.tai.inventory.repository.ProductRepository;
import com.company.tai.inventory.repository.StockAdjustmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Seasonal demand aggregation and forward projection — plain descriptive statistics
 * (grouping/averaging in Java over the loaded history), no ML library and no LLM. There is no
 * Sales module in this codebase yet, so this always falls back to DECREASE-type
 * StockAdjustment records as a demand proxy; every response says so explicitly via
 * `dataSource`, since damage/loss/recount adjustments are excluded (only DECREASE, matching
 * the same convention as ForecastService) but this is still not as clean a signal as real
 * completed sales would be.
 */
@Service
@RequiredArgsConstructor
public class SeasonalDemandService {

    private static final String DATA_SOURCE_NOTE =
            "Based on DECREASE-type stock adjustments (no Sales module exists yet) — real sales data would be a more accurate demand signal once available.";

    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final ProductRepository productRepository;

    public SeasonalDemandDto aggregate(Long productId, String period) {
        Product product = findProduct(productId);
        validatePeriod(period);

        List<StockAdjustment> history = decreaseHistory(productId);

        // bucketNumber -> year -> quantity
        Map<Integer, Map<Integer, BigDecimal>> buckets = new TreeMap<>();
        for (StockAdjustment a : history) {
            var dateTime = a.getCreatedAt().atZone(ZoneOffset.UTC);
            int year = dateTime.getYear();
            int bucketNumber = "quarter".equals(period)
                    ? (dateTime.getMonthValue() - 1) / 3 + 1
                    : dateTime.getMonthValue();

            buckets.computeIfAbsent(bucketNumber, k -> new HashMap<>())
                    .merge(year, a.getQuantity(), BigDecimal::add);
        }

        int bucketCount = "quarter".equals(period) ? 4 : 12;
        List<SeasonalBucketDto> bucketDtos = new ArrayList<>();
        for (int b = 1; b <= bucketCount; b++) {
            Map<Integer, BigDecimal> yearData = buckets.getOrDefault(b, Map.of());
            BigDecimal total = yearData.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            int yearsCount = yearData.size();
            BigDecimal avgPerYear = yearsCount > 0
                    ? total.divide(BigDecimal.valueOf(yearsCount), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            List<Integer> years = yearData.keySet().stream().sorted().toList();

            bucketDtos.add(new SeasonalBucketDto(b, bucketLabel(b, period), total, yearsCount, avgPerYear, years));
        }

        return new SeasonalDemandDto(productId, product.getName(), period, DATA_SOURCE_NOTE, bucketDtos);
    }

    public SeasonalPredictionDto predict(Long productId, String period, int targetBucket) {
        Product product = findProduct(productId);
        validatePeriod(period);

        List<StockAdjustment> history = decreaseHistory(productId);

        Map<Integer, BigDecimal> yearTotals = new TreeMap<>();
        for (StockAdjustment a : history) {
            var dateTime = a.getCreatedAt().atZone(ZoneOffset.UTC);
            int bucketNumber = "quarter".equals(period)
                    ? (dateTime.getMonthValue() - 1) / 3 + 1
                    : dateTime.getMonthValue();
            if (bucketNumber != targetBucket) continue;
            yearTotals.merge(dateTime.getYear(), a.getQuantity(), BigDecimal::add);
        }

        String targetLabel = bucketLabel(targetBucket, period);
        List<YearlyQuantityDto> breakdown = yearTotals.entrySet().stream()
                .map(e -> new YearlyQuantityDto(e.getKey(), e.getValue()))
                .toList();

        if (yearTotals.isEmpty()) {
            return new SeasonalPredictionDto(
                    productId, product.getName(), targetLabel, null, List.of(), "insufficient_data",
                    "No historical DECREASE adjustments found for " + targetLabel + " in any prior year — nothing to base a projection on yet.",
                    DATA_SOURCE_NOTE, breakdown
            );
        }

        BigDecimal total = yearTotals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal suggested = total.divide(BigDecimal.valueOf(yearTotals.size()), 0, RoundingMode.HALF_UP);

        int yearsCount = yearTotals.size();
        String confidence = yearsCount == 1 ? "low" : yearsCount == 2 ? "medium" : "high";

        String numbersList = yearTotals.entrySet().stream()
                .map(e -> e.getKey() + " (" + e.getValue().stripTrailingZeros().toPlainString() + " units)")
                .collect(Collectors.joining(", "));
        String explanation = "Based on average consumption in " + targetLabel + " across "
                + yearsCount + " prior year(s): " + numbersList + ".";

        return new SeasonalPredictionDto(
                productId, product.getName(), targetLabel, suggested,
                yearTotals.keySet().stream().toList(), confidence, explanation, DATA_SOURCE_NOTE, breakdown
        );
    }

    /** Raw matching history for a bucket — used by UnifiedForecastService to build its basedOn detail. */
    public List<StockAdjustment> matchingHistory(Long productId, String period, int targetBucket) {
        return decreaseHistory(productId).stream()
                .filter(a -> {
                    var dt = a.getCreatedAt().atZone(ZoneOffset.UTC);
                    int b = "quarter".equals(period) ? (dt.getMonthValue() - 1) / 3 + 1 : dt.getMonthValue();
                    return b == targetBucket;
                })
                .toList();
    }

    public int resolveTargetBucket(String period, Integer targetMonth, Integer targetQuarter, java.time.LocalDate targetDate) {
        if ("quarter".equals(period)) {
            if (targetQuarter != null) return targetQuarter;
            if (targetDate != null) return (targetDate.getMonthValue() - 1) / 3 + 1;
            throw new BusinessRuleException("targetQuarter or targetDate is required when period=quarter");
        } else {
            if (targetMonth != null) return targetMonth;
            if (targetDate != null) return targetDate.getMonthValue();
            throw new BusinessRuleException("targetMonth or targetDate is required when period=month");
        }
    }

    private List<StockAdjustment> decreaseHistory(Long productId) {
        return stockAdjustmentRepository.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .filter(a -> a.getAdjustmentType() == AdjustmentType.DECREASE)
                .filter(a -> a.getCreatedAt() != null)
                .toList();
    }

    private void validatePeriod(String period) {
        if (!"month".equals(period) && !"quarter".equals(period)) {
            throw new BusinessRuleException("period must be 'month' or 'quarter'");
        }
    }

    private String bucketLabel(int bucket, String period) {
        if ("quarter".equals(period)) return "Q" + bucket;
        return Month.of(bucket).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
    }
}
