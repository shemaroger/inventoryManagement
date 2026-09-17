package com.company.tai.analytics.service;

import com.company.tai.analytics.dto.*;
import com.company.tai.common.tax.VatConstants;
import com.company.tai.inventory.entity.AdjustmentType;
import com.company.tai.inventory.entity.Category;
import com.company.tai.inventory.entity.Product;
import com.company.tai.inventory.entity.StockAdjustment;
import com.company.tai.inventory.repository.StockAdjustmentRepository;
import com.company.tai.inventory.repository.StockItemRepository;
import com.company.tai.purchasing.entity.PurchaseOrder;
import com.company.tai.purchasing.entity.PurchaseOrderLine;
import com.company.tai.purchasing.entity.PurchaseOrderStatus;
import com.company.tai.purchasing.repository.PurchaseOrderRepository;
import com.company.tai.sales.entity.*;
import com.company.tai.sales.repository.SalePaymentRepository;
import com.company.tai.sales.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

// Deliberate, date-ranged deep-dive reporting for month-end style review — distinct from
// DashboardService, which serves a fixed "last 30 days" at-a-glance view. Both read the same
// underlying Sales/Purchasing/Inventory data but for different purposes, so they are not merged.
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final SaleRepository saleRepository;
    private final SalePaymentRepository salePaymentRepository;
    private final StockItemRepository stockItemRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    @Transactional(readOnly = true)
    public SalesAnalyticsReportDto salesAnalysis(LocalDate startDate, LocalDate endDate, String groupBy) {
        List<Sale> sales = saleRepository.findByStatusAndSaleDateBetweenOrderBySaleDateAscIdAsc(SaleStatus.COMPLETED, startDate, endDate);

        Map<LocalDate, List<Sale>> byBucketStart = new TreeMap<>();
        for (Sale sale : sales) {
            LocalDate bucketStart = bucketStart(sale.getSaleDate(), groupBy);
            byBucketStart.computeIfAbsent(bucketStart, k -> new ArrayList<>()).add(sale);
        }

        List<SalesAnalyticsPointDto> points = byBucketStart.entrySet().stream()
                .map(e -> new SalesAnalyticsPointDto(
                        e.getKey(),
                        bucketLabel(e.getKey(), groupBy),
                        e.getValue().stream().map(this::grandTotal).reduce(BigDecimal.ZERO, BigDecimal::add),
                        e.getValue().size()
                ))
                .sorted(Comparator.comparing(SalesAnalyticsPointDto::bucketStart))
                .toList();

        BigDecimal grandTotal = points.stream().map(SalesAnalyticsPointDto::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SalesAnalyticsReportDto(startDate, endDate, groupBy, points, grandTotal);
    }

    @Transactional(readOnly = true)
    public ProfitByProductReportDto profitByProduct(LocalDate startDate, LocalDate endDate) {
        List<ProfitByProductLineDto> lines = new ArrayList<>(aggregateProductProfit(startDate, endDate).values());
        lines.sort(Comparator.comparing(ProfitByProductLineDto::margin).reversed());

        BigDecimal totalRevenue = lines.stream().map(ProfitByProductLineDto::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCost = lines.stream().map(ProfitByProductLineDto::cost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalMargin = totalRevenue.subtract(totalCost);

        return new ProfitByProductReportDto(startDate, endDate, true, lines, totalRevenue, totalCost, totalMargin);
    }

    @Transactional(readOnly = true)
    public ProfitByCategoryReportDto profitByCategory(LocalDate startDate, LocalDate endDate) {
        Collection<ProfitByProductLineDto> productLines = aggregateProductProfit(startDate, endDate).values();

        record Accum(Long categoryId, String categoryName, BigDecimal revenue, BigDecimal cost) {}
        Map<Long, Accum> byCategory = new LinkedHashMap<>();
        for (ProfitByProductLineDto p : productLines) {
            Long key = p.categoryName() == null ? -1L : (long) p.categoryName().hashCode();
            Accum existing = byCategory.get(key);
            if (existing == null) {
                byCategory.put(key, new Accum(key, p.categoryName() != null ? p.categoryName() : "Uncategorized", p.revenue(), p.cost()));
            } else {
                byCategory.put(key, new Accum(key, existing.categoryName(), existing.revenue().add(p.revenue()), existing.cost().add(p.cost())));
            }
        }

        List<ProfitByCategoryLineDto> lines = byCategory.values().stream()
                .map(a -> {
                    BigDecimal margin = a.revenue().subtract(a.cost());
                    BigDecimal marginPercent = a.revenue().compareTo(BigDecimal.ZERO) > 0
                            ? margin.divide(a.revenue(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                            : BigDecimal.ZERO;
                    return new ProfitByCategoryLineDto(a.categoryId(), a.categoryName(), a.revenue(), a.cost(), margin, marginPercent);
                })
                .sorted(Comparator.comparing(ProfitByCategoryLineDto::margin).reversed())
                .toList();

        BigDecimal totalRevenue = lines.stream().map(ProfitByCategoryLineDto::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCost = lines.stream().map(ProfitByCategoryLineDto::cost).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ProfitByCategoryReportDto(startDate, endDate, true, lines, totalRevenue, totalCost, totalRevenue.subtract(totalCost));
    }

    // Average stock is approximated as (opening + closing) / 2, where opening is reconstructed
    // by working current quantity backwards through every adjustment recorded in the period —
    // there's no daily stock-snapshot table to read a true average from.
    @Transactional(readOnly = true)
    public InventoryTurnoverReportDto inventoryTurnover(LocalDate startDate, LocalDate endDate) {
        boolean dataScarce = ChronoUnit.DAYS.between(startDate, endDate) < 30;
        long periodDays = Math.max(1, ChronoUnit.DAYS.between(startDate, endDate) + 1);

        Map<Long, Object[]> soldByProduct = new HashMap<>(); // productId -> [Product, qtySold]
        for (Sale sale : saleRepository.findByStatusAndSaleDateBetweenOrderBySaleDateAscIdAsc(SaleStatus.COMPLETED, startDate, endDate)) {
            for (SaleLine line : sale.getLines()) {
                soldByProduct.merge(line.getProduct().getId(), new Object[]{line.getProduct(), line.getQuantity()},
                        (existing, added) -> new Object[]{existing[0], ((BigDecimal) existing[1]).add((BigDecimal) added[1])});
            }
        }

        Map<Long, BigDecimal> closingStockByProduct = stockItemRepository.findAll().stream()
                .collect(Collectors.groupingBy(si -> si.getProduct().getId(),
                        Collectors.reducing(BigDecimal.ZERO, si -> si.getQuantity(), BigDecimal::add)));

        var start = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        var end = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<InventoryTurnoverLineDto> lines = new ArrayList<>();
        for (Long productId : new HashSet<>(closingStockByProduct.keySet())) {
            BigDecimal closing = closingStockByProduct.getOrDefault(productId, BigDecimal.ZERO);
            List<StockAdjustment> periodAdjustments = stockAdjustmentRepository.findByProductIdAndCreatedAtBetween(productId, start, end);
            BigDecimal netDelta = periodAdjustments.stream()
                    .map(this::signedQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal opening = closing.subtract(netDelta);
            BigDecimal average = opening.add(closing).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);

            Object[] soldEntry = soldByProduct.get(productId);
            Product product = soldEntry != null ? (Product) soldEntry[0]
                    : periodAdjustments.stream().findFirst().map(StockAdjustment::getProduct).orElse(null);
            if (product == null) continue;
            BigDecimal unitsSold = soldEntry != null ? (BigDecimal) soldEntry[1] : BigDecimal.ZERO;

            BigDecimal turnoverRatio = null;
            BigDecimal daysOfStockOnHand = null;
            if (unitsSold.compareTo(BigDecimal.ZERO) > 0 && average.compareTo(BigDecimal.ZERO) > 0) {
                turnoverRatio = unitsSold.divide(average, 4, RoundingMode.HALF_UP);
                BigDecimal dailySales = unitsSold.divide(BigDecimal.valueOf(periodDays), 6, RoundingMode.HALF_UP);
                daysOfStockOnHand = average.divide(dailySales, 2, RoundingMode.HALF_UP);
            }

            lines.add(new InventoryTurnoverLineDto(productId, product.getName(), product.getSku(), unitsSold, average, turnoverRatio, daysOfStockOnHand));
        }

        lines.sort(Comparator.comparing(InventoryTurnoverLineDto::unitsSold).reversed());
        return new InventoryTurnoverReportDto(startDate, endDate, dataScarce, lines);
    }

    @Transactional(readOnly = true)
    public CustomerPerformanceReportDto customerPerformance(LocalDate startDate, LocalDate endDate) {
        List<Sale> sales = saleRepository.findByStatusAndSaleDateBetweenOrderBySaleDateAscIdAsc(SaleStatus.COMPLETED, startDate, endDate);

        record Accum(Customer customer, BigDecimal revenue, int count) {}
        Map<Long, Accum> byCustomer = new LinkedHashMap<>();
        Map<Long, Long> saleIdToCustomerId = new HashMap<>();
        for (Sale sale : sales) {
            Long custId = sale.getCustomer().getId();
            saleIdToCustomerId.put(sale.getId(), custId);
            Accum existing = byCustomer.get(custId);
            BigDecimal total = grandTotal(sale);
            byCustomer.put(custId, existing == null
                    ? new Accum(sale.getCustomer(), total, 1)
                    : new Accum(existing.customer(), existing.revenue().add(total), existing.count() + 1));
        }

        Map<Long, List<Long>> customerToSaleIds = new HashMap<>();
        saleIdToCustomerId.forEach((saleId, custId) -> customerToSaleIds.computeIfAbsent(custId, k -> new ArrayList<>()).add(saleId));
        Map<Long, LocalDate> saleDateById = sales.stream().collect(Collectors.toMap(Sale::getId, Sale::getSaleDate));

        List<CustomerPerformanceLineDto> lines = new ArrayList<>();
        for (Accum accum : byCustomer.values()) {
            List<Long> saleIds = customerToSaleIds.getOrDefault(accum.customer().getId(), List.of());
            List<SalePayment> payments = saleIds.isEmpty() ? List.of() : salePaymentRepository.findBySaleIdInOrderByPaymentDateAsc(saleIds);

            BigDecimal avgDelay = null;
            if (!payments.isEmpty()) {
                long totalDays = 0;
                int n = 0;
                for (SalePayment p : payments) {
                    LocalDate saleDate = saleDateById.get(p.getSale().getId());
                    if (saleDate == null) continue;
                    totalDays += ChronoUnit.DAYS.between(saleDate, p.getPaymentDate());
                    n++;
                }
                if (n > 0) avgDelay = BigDecimal.valueOf(totalDays).divide(BigDecimal.valueOf(n), 1, RoundingMode.HALF_UP);
            }

            lines.add(new CustomerPerformanceLineDto(accum.customer().getId(), accum.customer().getName(), accum.revenue(), accum.count(), avgDelay));
        }

        lines.sort(Comparator.comparing(CustomerPerformanceLineDto::totalRevenue).reversed());
        return new CustomerPerformanceReportDto(startDate, endDate, lines);
    }

    @Transactional(readOnly = true)
    public SupplierPerformanceReportDto supplierPerformance(LocalDate startDate, LocalDate endDate) {
        List<PurchaseOrder> pos = purchaseOrderRepository.findAll().stream()
                .filter(po -> !po.getOrderDate().isBefore(startDate) && !po.getOrderDate().isAfter(endDate))
                .toList();

        record Accum(String name, BigDecimal spend, int count, long totalLeadDays, int leadCount) {}
        Map<Long, Accum> bySupplier = new LinkedHashMap<>();
        for (PurchaseOrder po : pos) {
            Long supplierId = po.getSupplier().getId();
            BigDecimal poValue = po.getLines().stream()
                    .map(l -> l.getQuantityReceived().multiply(l.getUnitCost()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            poValue = poValue.add(VatConstants.vatOn(poValue));

            long leadDays = -1;
            if (po.getStatus() == PurchaseOrderStatus.RECEIVED) {
                leadDays = ChronoUnit.DAYS.between(po.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(), po.getUpdatedAt().atZone(ZoneOffset.UTC).toLocalDate());
            }

            Accum existing = bySupplier.get(supplierId);
            if (existing == null) {
                bySupplier.put(supplierId, new Accum(po.getSupplier().getName(), poValue, 1,
                        leadDays >= 0 ? leadDays : 0, leadDays >= 0 ? 1 : 0));
            } else {
                bySupplier.put(supplierId, new Accum(existing.name(), existing.spend().add(poValue), existing.count() + 1,
                        existing.totalLeadDays() + (leadDays >= 0 ? leadDays : 0), existing.leadCount() + (leadDays >= 0 ? 1 : 0)));
            }
        }

        List<SupplierPerformanceLineDto> lines = bySupplier.entrySet().stream()
                .map(e -> {
                    Accum a = e.getValue();
                    BigDecimal avgLead = a.leadCount() > 0
                            ? BigDecimal.valueOf(a.totalLeadDays()).divide(BigDecimal.valueOf(a.leadCount()), 1, RoundingMode.HALF_UP)
                            : null;
                    return new SupplierPerformanceLineDto(e.getKey(), a.name(), a.spend(), a.count(), avgLead, true);
                })
                .sorted(Comparator.comparing(SupplierPerformanceLineDto::totalSpend).reversed())
                .toList();

        return new SupplierPerformanceReportDto(startDate, endDate, lines);
    }

    // productId -> aggregated line, shared by profitByProduct() and profitByCategory() so both
    // reports are built from exactly one pass over the same Sale/SaleLine data.
    private Map<Long, ProfitByProductLineDto> aggregateProductProfit(LocalDate startDate, LocalDate endDate) {
        List<Sale> sales = saleRepository.findByStatusAndSaleDateBetweenOrderBySaleDateAscIdAsc(SaleStatus.COMPLETED, startDate, endDate);

        record Accum(Product product, BigDecimal qty, BigDecimal revenue) {}
        Map<Long, Accum> agg = new LinkedHashMap<>();
        for (Sale sale : sales) {
            for (SaleLine line : sale.getLines()) {
                Long productId = line.getProduct().getId();
                BigDecimal revenue = lineTotal(line);
                Accum existing = agg.get(productId);
                agg.put(productId, existing == null
                        ? new Accum(line.getProduct(), line.getQuantity(), revenue)
                        : new Accum(existing.product(), existing.qty().add(line.getQuantity()), existing.revenue().add(revenue)));
            }
        }

        Map<Long, ProfitByProductLineDto> result = new LinkedHashMap<>();
        for (Accum a : agg.values()) {
            BigDecimal cost = a.product().getCostPrice().multiply(a.qty());
            BigDecimal margin = a.revenue().subtract(cost);
            BigDecimal marginPercent = a.revenue().compareTo(BigDecimal.ZERO) > 0
                    ? margin.divide(a.revenue(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            Category category = a.product().getCategory();
            result.put(a.product().getId(), new ProfitByProductLineDto(
                    a.product().getId(), a.product().getName(), a.product().getSku(),
                    category != null ? category.getName() : null,
                    a.qty(), a.revenue(), cost, margin, marginPercent
            ));
        }
        return result;
    }

    private BigDecimal signedQuantity(StockAdjustment sa) {
        boolean increasing = sa.getAdjustmentType() == AdjustmentType.INCREASE
                || sa.getAdjustmentType() == AdjustmentType.TRANSFER_IN
                || sa.getAdjustmentType() == AdjustmentType.RECOUNT;
        return increasing ? sa.getQuantity() : sa.getQuantity().negate();
    }

    private LocalDate bucketStart(LocalDate date, String groupBy) {
        return switch (groupBy) {
            case "week" -> date.with(WeekFields.ISO.dayOfWeek(), 1);
            case "month" -> date.withDayOfMonth(1);
            default -> date;
        };
    }

    private String bucketLabel(LocalDate bucketStart, String groupBy) {
        return switch (groupBy) {
            case "week" -> "Week of " + bucketStart;
            case "month" -> bucketStart.getMonth() + " " + bucketStart.getYear();
            default -> bucketStart.toString();
        };
    }

    private BigDecimal grandTotal(Sale sale) {
        BigDecimal subtotal = sale.getLines().stream().map(this::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return subtotal.add(VatConstants.vatOn(subtotal));
    }

    private BigDecimal lineTotal(SaleLine line) {
        return line.getUnitPrice()
                .multiply(line.getQuantity())
                .multiply(BigDecimal.ONE.subtract(line.getDiscountPercent().divide(BigDecimal.valueOf(100))));
    }
}
