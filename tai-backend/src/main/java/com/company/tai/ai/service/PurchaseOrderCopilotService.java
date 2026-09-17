package com.company.tai.ai.service;

import com.company.tai.ai.dto.ForecastDto;
import com.company.tai.ai.dto.PurchaseOrderSuggestion;
import com.company.tai.ai.dto.PurchaseOrderSuggestionLine;
import com.company.tai.ai.dto.SuggestPurchaseOrderRequest;
import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.inventory.entity.StockItem;
import com.company.tai.inventory.entity.Warehouse;
import com.company.tai.inventory.repository.StockItemRepository;
import com.company.tai.inventory.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Drafts a purchase order for a human to review — never creates a real PurchaseOrder record.
 * Quantities come entirely from ForecastService's deterministic regression, never from an LLM;
 * the per-line "reasoning" text is template-generated from those same numbers, not AI prose,
 * per this session's "no external API" constraint.
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderCopilotService {

    private static final int TARGET_COVERAGE_DAYS = 30;

    private final StockItemRepository stockItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final ForecastService forecastService;

    public PurchaseOrderSuggestion suggest(SuggestPurchaseOrderRequest request) {
        List<StockItem> lowStock = stockItemRepository.findLowStock();

        Warehouse warehouse;
        if (request.warehouseId() != null) {
            warehouse = warehouseRepository.findById(request.warehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found with id: " + request.warehouseId()));
            lowStock = lowStock.stream().filter(si -> si.getWarehouse().getId().equals(warehouse.getId())).toList();
        } else {
            // No warehouse specified — a PO can only target one warehouse, so pick whichever
            // warehouse has the most low-stock items right now and tell the user which one,
            // since they can still change it in the create-PO form before submitting.
            Map<Warehouse, Long> countsByWarehouse = lowStock.stream()
                    .collect(Collectors.groupingBy(StockItem::getWarehouse, Collectors.counting()));
            warehouse = countsByWarehouse.entrySet().stream()
                    .max(Comparator.comparingLong(Map.Entry::getValue))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (warehouse == null) {
                return new PurchaseOrderSuggestion(null, null, request.supplierId(), List.of(),
                        "No low-stock items found anywhere — nothing to suggest reordering right now.");
            }
            final Long chosenWarehouseId = warehouse.getId();
            lowStock = lowStock.stream().filter(si -> si.getWarehouse().getId().equals(chosenWarehouseId)).toList();
        }

        List<PurchaseOrderSuggestionLine> lines = lowStock.stream()
                .map(si -> buildLine(si, warehouse))
                .toList();

        String message = lines.isEmpty()
                ? "No low-stock items found in " + warehouse.getName() + " — nothing to suggest reordering."
                : "Suggested " + lines.size() + " line(s) for " + warehouse.getName()
                        + " based on current low stock and consumption trends. Review and adjust before submitting.";

        return new PurchaseOrderSuggestion(warehouse.getId(), warehouse.getName(), request.supplierId(), lines, message);
    }

    private PurchaseOrderSuggestionLine buildLine(StockItem stockItem, Warehouse warehouse) {
        ForecastDto forecast = forecastService.forecast(stockItem.getProduct().getId());

        BigDecimal avgDaily = forecast.avgDailyConsumption();
        BigDecimal suggestedQuantity;
        String reasoning;

        if (avgDaily.compareTo(BigDecimal.ZERO) > 0) {
            // Order enough to cover the target window plus close the current shortfall below
            // reorder level — deterministic arithmetic, no AI involved in the quantity itself.
            BigDecimal targetCoverage = avgDaily.multiply(BigDecimal.valueOf(TARGET_COVERAGE_DAYS));
            BigDecimal shortfall = stockItem.getProduct().getReorderLevel().subtract(stockItem.getQuantity()).max(BigDecimal.ZERO);
            suggestedQuantity = targetCoverage.add(shortfall).setScale(0, RoundingMode.CEILING);
            if (suggestedQuantity.compareTo(BigDecimal.ONE) < 0) suggestedQuantity = BigDecimal.ONE;

            double weekly = avgDaily.doubleValue() * 7;
            reasoning = String.format(
                    "Selling ~%.1f units/week in %s, %s day(s) of stock left (%s confidence). Suggested quantity covers ~%d days.",
                    weekly, warehouse.getName(),
                    forecast.daysUntilReorderNeeded() != null ? forecast.daysUntilReorderNeeded() : "unknown",
                    forecast.confidence(), TARGET_COVERAGE_DAYS
            );
        } else {
            // No usable consumption trend — fall back to simply topping back up to reorder level.
            suggestedQuantity = stockItem.getProduct().getReorderLevel().subtract(stockItem.getQuantity()).max(BigDecimal.ONE)
                    .setScale(0, RoundingMode.CEILING);
            reasoning = "No recent consumption history to project a trend — suggested quantity just restores stock to the reorder level.";
        }

        return new PurchaseOrderSuggestionLine(
                stockItem.getProduct().getId(),
                stockItem.getProduct().getName(),
                stockItem.getProduct().getSku(),
                suggestedQuantity,
                stockItem.getProduct().getCostPrice(),
                reasoning
        );
    }
}
