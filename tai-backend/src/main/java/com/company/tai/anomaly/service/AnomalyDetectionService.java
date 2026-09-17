package com.company.tai.anomaly.service;

import com.company.tai.anomaly.entity.AnomalyType;
import com.company.tai.anomaly.entity.StockAnomaly;
import com.company.tai.anomaly.repository.StockAnomalyRepository;
import com.company.tai.inventory.entity.AdjustmentType;
import com.company.tai.inventory.entity.StockAdjustment;
import com.company.tai.inventory.repository.StockAdjustmentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Statistical outlier detection over StockAdjustment history — no LLM, no ML model, so every
 * flag can be explained to a non-technical admin in terms of "this number vs. that average."
 * This intentionally detects statistical patterns only, never intent: a legitimate bulk order,
 * a promotion, a data-entry typo, and an actual loss can all produce the same numeric outlier,
 * and nothing here claims to know which one occurred — see AnomalyController/frontend copy for
 * the framing rules this drives.
 *
 * Reconciliation-mismatch detection (Sale vs. stock-decrease comparison) is deliberately not
 * implemented — there is no Sales module in this codebase to reconcile against yet.
 */
@Service
@RequiredArgsConstructor
public class AnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);

    // Quantity-outlier tuning.
    private static final int QUANTITY_MIN_SAMPLE_SIZE = 5;
    private static final double LOWER_PRIORITY_TYPE_MULTIPLIER = 1.5; // INCREASE/RECOUNT need a bigger deviation to flag
    private static final Set<AdjustmentType> LOWER_PRIORITY_TYPES = Set.of(AdjustmentType.INCREASE, AdjustmentType.RECOUNT);

    // Frequency-outlier tuning.
    private static final int FREQUENCY_LOOKBACK_DAYS = 90;
    private static final int FREQUENCY_MIN_BASELINE_DAYS = 5; // distinct prior days needed before a baseline is trusted
    private static final double FREQUENCY_STD_DEV_THRESHOLD = 3.0;

    // Timing-outlier: Mapleco's standard business hours (confirmed with the user), fixed rule,
    // not statistical — no baseline sample size required.
    private static final int BUSINESS_HOUR_START = 8;
    private static final int BUSINESS_HOUR_END = 18;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Africa/Kigali");
    private static final BigDecimal TIMING_SEVERITY_SCORE = BigDecimal.valueOf(4);

    @Value("${app.anomaly.quantity-std-dev-threshold:3.0}")
    private double quantityStdDevThreshold;

    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final StockAnomalyRepository stockAnomalyRepository;

    @Scheduled(cron = "0 0 2 * * *") // nightly at 02:00 — avoids slowing down the real-time adjustment flow
    public void runNightlyDetection() {
        var result = runDetection();
        log.info("Nightly anomaly detection: scanned {} adjustments, created {} anomalies", result.adjustmentsScanned(), result.anomaliesCreated());
    }

    @Transactional
    public com.company.tai.anomaly.dto.DetectionRunResultDto runDetection() {
        List<StockAdjustment> all = stockAdjustmentRepository.findAll();
        int created = 0;
        created += detectQuantityOutliers(all);
        created += detectFrequencyOutliers(all);
        created += detectTimingOutliers(all);
        return new com.company.tai.anomaly.dto.DetectionRunResultDto(all.size(), created);
    }

    private int detectQuantityOutliers(List<StockAdjustment> all) {
        int created = 0;
        Map<String, List<StockAdjustment>> byProductAndType = all.stream()
                .collect(Collectors.groupingBy(a -> a.getProduct().getId() + "|" + a.getAdjustmentType()));

        for (StockAdjustment a : all) {
            if (a.getAdjustmentType() == AdjustmentType.TRANSFER_IN || a.getAdjustmentType() == AdjustmentType.TRANSFER_OUT) continue;
            if (stockAnomalyRepository.existsByAdjustmentIdAndAnomalyType(a.getId(), AnomalyType.QUANTITY_OUTLIER)) continue;

            String key = a.getProduct().getId() + "|" + a.getAdjustmentType();
            List<StockAdjustment> peers = byProductAndType.get(key).stream()
                    .filter(p -> !p.getId().equals(a.getId()))
                    .toList();
            if (peers.size() < QUANTITY_MIN_SAMPLE_SIZE) continue; // no trustworthy baseline yet — honest skip, not a false flag

            double mean = peers.stream().mapToDouble(p -> p.getQuantity().doubleValue()).average().orElse(0);
            double variance = peers.stream().mapToDouble(p -> Math.pow(p.getQuantity().doubleValue() - mean, 2)).average().orElse(0);
            double stdDev = Math.sqrt(variance);
            if (stdDev == 0) continue; // identical historical values — no meaningful z-score

            double z = (a.getQuantity().doubleValue() - mean) / stdDev;
            double threshold = quantityStdDevThreshold * (LOWER_PRIORITY_TYPES.contains(a.getAdjustmentType()) ? LOWER_PRIORITY_TYPE_MULTIPLIER : 1.0);

            if (Math.abs(z) > threshold) {
                String note = String.format(
                        "Usual %s adjustment for this product: ~%.1f units (n=%d prior). This one: %s units.",
                        a.getAdjustmentType(), mean, peers.size(), a.getQuantity().stripTrailingZeros().toPlainString()
                );
                save(a, AnomalyType.QUANTITY_OUTLIER, BigDecimal.valueOf(Math.abs(z)).setScale(2, RoundingMode.HALF_UP), note);
                created++;
            }
        }
        return created;
    }

    private int detectFrequencyOutliers(List<StockAdjustment> all) {
        int created = 0;
        var cutoff = ZonedDateTime.now(BUSINESS_ZONE).minusDays(FREQUENCY_LOOKBACK_DAYS).toInstant();

        // (productId, warehouseId, performedById) -> date -> adjustments that day
        Map<String, Map<LocalDate, List<StockAdjustment>>> grouped = new HashMap<>();
        for (StockAdjustment a : all) {
            if (a.getAdjustmentType() != AdjustmentType.DECREASE) continue;
            if (a.getPerformedBy() == null || a.getCreatedAt() == null || a.getCreatedAt().isBefore(cutoff)) continue;

            String key = a.getProduct().getId() + "|" + a.getWarehouse().getId() + "|" + a.getPerformedBy().getId();
            LocalDate day = a.getCreatedAt().atZone(BUSINESS_ZONE).toLocalDate();
            grouped.computeIfAbsent(key, k -> new TreeMap<>()).computeIfAbsent(day, d -> new ArrayList<>()).add(a);
        }

        for (var entry : grouped.entrySet()) {
            Map<LocalDate, List<StockAdjustment>> byDay = entry.getValue();
            if (byDay.size() < FREQUENCY_MIN_BASELINE_DAYS + 1) continue; // need history plus the day being evaluated

            for (LocalDate day : byDay.keySet()) {
                List<StockAdjustment> todaysAdjustments = byDay.get(day);
                StockAdjustment representative = todaysAdjustments.get(todaysAdjustments.size() - 1);
                if (stockAnomalyRepository.existsByAdjustmentIdAndAnomalyType(representative.getId(), AnomalyType.FREQUENCY_OUTLIER)) continue;

                List<Integer> otherDayCounts = byDay.entrySet().stream()
                        .filter(e -> !e.getKey().equals(day))
                        .map(e -> e.getValue().size())
                        .toList();
                if (otherDayCounts.size() < FREQUENCY_MIN_BASELINE_DAYS) continue;

                double mean = otherDayCounts.stream().mapToInt(Integer::intValue).average().orElse(0);
                double variance = otherDayCounts.stream().mapToDouble(c -> Math.pow(c - mean, 2)).average().orElse(0);
                double stdDev = Math.sqrt(variance);
                int todayCount = todaysAdjustments.size();

                boolean flagged;
                double severity;
                if (stdDev == 0) {
                    // No variation historically at all — flag only if today is meaningfully more than the constant baseline.
                    flagged = mean > 0 && todayCount > mean * 3;
                    severity = flagged ? todayCount / Math.max(mean, 1) : 0;
                } else {
                    double z = (todayCount - mean) / stdDev;
                    flagged = z > FREQUENCY_STD_DEV_THRESHOLD;
                    severity = Math.max(z, 0);
                }

                if (flagged) {
                    String note = String.format(
                            "Unusual frequency: %d adjustment(s) by this user for this product/warehouse on %s (usual: ~%.1f/day based on %d prior day(s)).",
                            todayCount, day, mean, otherDayCounts.size()
                    );
                    save(representative, AnomalyType.FREQUENCY_OUTLIER, BigDecimal.valueOf(severity).setScale(2, RoundingMode.HALF_UP), note);
                    created++;
                }
            }
        }
        return created;
    }

    private int detectTimingOutliers(List<StockAdjustment> all) {
        int created = 0;
        for (StockAdjustment a : all) {
            if (a.getCreatedAt() == null) continue;
            if (stockAnomalyRepository.existsByAdjustmentIdAndAnomalyType(a.getId(), AnomalyType.TIMING_OUTLIER)) continue;

            ZonedDateTime local = a.getCreatedAt().atZone(BUSINESS_ZONE);
            boolean outsideHours = local.getHour() < BUSINESS_HOUR_START || local.getHour() >= BUSINESS_HOUR_END;
            boolean isSunday = local.getDayOfWeek() == DayOfWeek.SUNDAY;

            if (outsideHours || isSunday) {
                String note = String.format(
                        "Recorded at %s (%s), outside standard business hours (%02d:00-%02d:00, Mon-Sat).",
                        local.toLocalTime(), local.getDayOfWeek(), BUSINESS_HOUR_START, BUSINESS_HOUR_END
                );
                save(a, AnomalyType.TIMING_OUTLIER, TIMING_SEVERITY_SCORE, note);
                created++;
            }
        }
        return created;
    }

    private void save(StockAdjustment adjustment, AnomalyType type, BigDecimal severity, String note) {
        StockAnomaly anomaly = StockAnomaly.builder()
                .adjustment(adjustment)
                .product(adjustment.getProduct())
                .warehouse(adjustment.getWarehouse())
                .anomalyType(type)
                .severityScore(severity)
                .contextNote(note)
                .build();
        stockAnomalyRepository.save(anomaly);
    }
}
