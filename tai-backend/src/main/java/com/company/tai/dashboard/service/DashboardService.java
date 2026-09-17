package com.company.tai.dashboard.service;

import com.company.tai.dashboard.dto.*;
import com.company.tai.inventory.entity.Product;
import com.company.tai.inventory.entity.StockItem;
import com.company.tai.inventory.repository.ProductRepository;
import com.company.tai.inventory.repository.StockAdjustmentRepository;
import com.company.tai.inventory.repository.StockItemRepository;
import com.company.tai.purchasing.entity.PurchaseOrderStatus;
import com.company.tai.purchasing.repository.PurchaseOrderRepository;
import com.company.tai.reorder.service.ReorderSuggestionService;
import com.company.tai.reports.dto.FinancialLineDto;
import com.company.tai.reports.dto.ProfitLossReportDto;
import com.company.tai.reports.service.ReportService;
import com.company.tai.sales.entity.Sale;
import com.company.tai.sales.entity.SaleLine;
import com.company.tai.sales.entity.SaleStatus;
import com.company.tai.sales.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

// Read-only aggregation over data Sales/Purchasing/Inventory/Accounting already own — nothing
// here writes anything. Financial figures (money amounts) are nulled out for STAFF callers at
// the point each DTO is built, rather than trusting the frontend to hide them.
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final String COGS_CODE = "5000";

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final StockItemRepository stockItemRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ReorderSuggestionService reorderSuggestionService;
    private final ReportService reportService;

    @Transactional(readOnly = true)
    public DashboardSummaryDto summary() {
        boolean showFinancials = currentUserCanSeeFinancials();
        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);

        List<Sale> todaySales = saleRepository.findByStatusAndSaleDateBetweenOrderBySaleDateAscIdAsc(SaleStatus.COMPLETED, today, today);
        List<Sale> monthSales = saleRepository.findByStatusAndSaleDateBetweenOrderBySaleDateAscIdAsc(SaleStatus.COMPLETED, startOfMonth, today);

        BigDecimal todayTotal = todaySales.stream().map(this::grandTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthTotal = monthSales.stream().map(this::grandTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        long activeProducts = productRepository.countByActiveTrue();
        BigDecimal stockValue = stockItemRepository.findAll().stream()
                .map(this::stockItemValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int lowStockCount = stockItemRepository.findLowStock().size();
        long openReorderCount = reorderSuggestionService.countOpen();

        BigDecimal grossMargin = null;
        if (showFinancials) {
            ProfitLossReportDto pnl = reportService.profitAndLoss(startOfMonth, today);
            BigDecimal cogs = pnl.expenses().stream()
                    .filter(l -> COGS_CODE.equals(l.accountCode()))
                    .map(FinancialLineDto::amount)
                    .findFirst().orElse(BigDecimal.ZERO);
            grossMargin = pnl.totalRevenue().subtract(cogs);
        }

        return new DashboardSummaryDto(
                showFinancials ? todayTotal : null,
                todaySales.size(),
                showFinancials ? monthTotal : null,
                activeProducts,
                stockValue,
                lowStockCount,
                openReorderCount,
                grossMargin
        );
    }

    // Includes zero-sale days deliberately — the gaps are meaningful, not noise to skip.
    @Transactional(readOnly = true)
    public SalesTrendDto salesTrend(int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1L);
        List<Sale> sales = saleRepository.findByStatusAndSaleDateBetweenOrderBySaleDateAscIdAsc(SaleStatus.COMPLETED, startDate, endDate);

        Map<LocalDate, BigDecimal> byDate = new HashMap<>();
        for (Sale sale : sales) {
            byDate.merge(sale.getSaleDate(), grandTotal(sale), BigDecimal::add);
        }

        List<SalesTrendPointDto> points = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            points.add(new SalesTrendPointDto(d, byDate.getOrDefault(d, BigDecimal.ZERO)));
        }

        return new SalesTrendDto(days, byDate.size(), points);
    }

    @Transactional(readOnly = true)
    public List<TopProductDto> topProducts(int limit, int periodDays) {
        boolean showFinancials = currentUserCanSeeFinancials();
        Map<Long, Object[]> agg = aggregateSaleLines(periodDays); // productId -> [Product, qty, revenue]

        return agg.values().stream()
                .sorted((a, b) -> ((BigDecimal) b[1]).compareTo((BigDecimal) a[1]))
                .limit(limit)
                .map(row -> {
                    Product product = (Product) row[0];
                    BigDecimal qty = (BigDecimal) row[1];
                    BigDecimal revenue = (BigDecimal) row[2];
                    return new TopProductDto(product.getId(), product.getName(), product.getSku(), qty, showFinancials ? revenue : null);
                })
                .toList();
    }

    // "Slow moving" = active products currently in stock that sold the least (or nothing) in
    // the period — a genuinely actionable list, not just "everything with zero stock".
    @Transactional(readOnly = true)
    public List<SlowMovingProductDto> slowMoving(int limit, int periodDays) {
        Map<Long, Object[]> agg = aggregateSaleLines(periodDays);

        Map<Long, BigDecimal> stockByProduct = stockItemRepository.findAll().stream()
                .collect(Collectors.groupingBy(si -> si.getProduct().getId(),
                        Collectors.reducing(BigDecimal.ZERO, StockItem::getQuantity, BigDecimal::add)));

        List<Product> activeProducts = productRepository.findAll().stream().filter(Product::isActive).toList();

        return activeProducts.stream()
                .map(p -> {
                    BigDecimal stock = stockByProduct.getOrDefault(p.getId(), BigDecimal.ZERO);
                    BigDecimal sold = agg.containsKey(p.getId()) ? (BigDecimal) agg.get(p.getId())[1] : BigDecimal.ZERO;
                    return new SlowMovingProductDto(p.getId(), p.getName(), p.getSku(), stock, sold);
                })
                .filter(d -> d.currentStock().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(SlowMovingProductDto::quantitySoldInPeriod)
                        .thenComparing(Comparator.comparing(SlowMovingProductDto::currentStock).reversed()))
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RecentActivityDto> recentActivity(int limit) {
        List<RecentActivityDto> activity = new ArrayList<>();

        stockAdjustmentRepository.search(null, null, PageRequest.of(0, limit)).forEach(sa ->
                activity.add(new RecentActivityDto(
                        "STOCK_ADJUSTMENT",
                        sa.getAdjustmentType() + " " + sa.getQuantity() + " × " + sa.getProduct().getName()
                                + " (" + sa.getWarehouse().getName() + ")" + (sa.getReason() != null ? " — " + sa.getReason() : ""),
                        sa.getCreatedAt()
                )));

        saleRepository.search(null, SaleStatus.COMPLETED, PageRequest.of(0, limit)).forEach(sale ->
                activity.add(new RecentActivityDto(
                        "SALE",
                        "Sale #" + sale.getId() + " completed for " + sale.getCustomer().getName(),
                        sale.getUpdatedAt()
                )));

        purchaseOrderRepository.search(null, null, PageRequest.of(0, limit)).stream()
                .filter(po -> po.getStatus() == PurchaseOrderStatus.RECEIVED || po.getStatus() == PurchaseOrderStatus.PARTIALLY_RECEIVED)
                .forEach(po -> activity.add(new RecentActivityDto(
                        "PURCHASE",
                        "PO #" + po.getId() + " (" + po.getStatus() + ") from " + po.getSupplier().getName(),
                        po.getUpdatedAt()
                )));

        return activity.stream()
                .sorted(Comparator.comparing(RecentActivityDto::timestamp).reversed())
                .limit(limit)
                .toList();
    }

    // productId -> {Product, totalQuantity, totalRevenue}, aggregated in Java rather than a JPQL
    // group-by — at this business's current data volume a period's worth of Sale/SaleLine rows
    // fits comfortably in memory, and it keeps top-products and slow-moving sharing one pass
    // over the same data instead of two divergent queries.
    private Map<Long, Object[]> aggregateSaleLines(int periodDays) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(periodDays - 1L);
        List<Sale> sales = saleRepository.findByStatusAndSaleDateBetweenOrderBySaleDateAscIdAsc(SaleStatus.COMPLETED, startDate, endDate);

        Map<Long, Object[]> agg = new HashMap<>();
        for (Sale sale : sales) {
            for (SaleLine line : sale.getLines()) {
                Long productId = line.getProduct().getId();
                BigDecimal qty = line.getQuantity();
                BigDecimal revenue = lineTotal(line);
                agg.merge(productId, new Object[]{line.getProduct(), qty, revenue}, (existing, added) -> new Object[]{
                        existing[0], ((BigDecimal) existing[1]).add((BigDecimal) added[1]), ((BigDecimal) existing[2]).add((BigDecimal) added[2])
                });
            }
        }
        return agg;
    }

    private BigDecimal grandTotal(Sale sale) {
        BigDecimal subtotal = sale.getLines().stream().map(this::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return subtotal.add(com.company.tai.common.tax.VatConstants.vatOn(subtotal));
    }

    private BigDecimal lineTotal(SaleLine line) {
        return line.getUnitPrice()
                .multiply(line.getQuantity())
                .multiply(BigDecimal.ONE.subtract(line.getDiscountPercent().divide(BigDecimal.valueOf(100))));
    }

    private BigDecimal stockItemValue(StockItem item) {
        return item.getQuantity().multiply(item.getProduct().getCostPrice());
    }

    private boolean currentUserCanSeeFinancials() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MANAGER"));
    }
}
