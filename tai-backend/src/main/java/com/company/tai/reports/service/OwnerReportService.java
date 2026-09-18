package com.company.tai.reports.service;

import com.company.tai.accounting.entity.Account;
import com.company.tai.accounting.service.AccountService;
import com.company.tai.analytics.dto.CustomerPerformanceLineDto;
import com.company.tai.analytics.dto.ProfitByProductLineDto;
import com.company.tai.analytics.dto.SupplierPerformanceLineDto;
import com.company.tai.analytics.service.AnalyticsService;
import com.company.tai.inventory.entity.StockItem;
import com.company.tai.inventory.entity.Warehouse;
import com.company.tai.inventory.repository.StockItemRepository;
import com.company.tai.purchasing.entity.PurchaseOrder;
import com.company.tai.purchasing.entity.SupplierPayment;
import com.company.tai.purchasing.repository.PurchaseOrderRepository;
import com.company.tai.purchasing.repository.SupplierPaymentRepository;
import com.company.tai.reports.dto.*;
import com.company.tai.sales.entity.Customer;
import com.company.tai.sales.repository.CustomerRepository;
import com.company.tai.common.tax.VatConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// A single, consolidated "everything a business owner needs" view over data every other domain
// already owns and computes correctly (Reports/Analytics/Dashboard/Accounting) — this service
// deliberately does no new business-logic computation of its own, only aggregation, so it can
// never drift out of sync with the individual reports it's built from.
@Service
@RequiredArgsConstructor
public class OwnerReportService {

    private static final String COGS_CODE = "5000";
    private static final String CASH_ACCOUNT_CODE = "1000";
    private static final int TOP_N = 5;

    private final ReportService reportService;
    private final AnalyticsService analyticsService;
    private final AccountService accountService;
    private final StockItemRepository stockItemRepository;
    private final CustomerRepository customerRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierPaymentRepository supplierPaymentRepository;

    @Transactional(readOnly = true)
    public OwnerReportDto generate(LocalDate startDate, LocalDate endDate) {
        return new OwnerReportDto(
                startDate, endDate,
                financial(startDate, endDate),
                sales(startDate, endDate),
                inventory(),
                purchasing(startDate, endDate)
        );
    }

    private OwnerReportFinancialDto financial(LocalDate startDate, LocalDate endDate) {
        ProfitLossReportDto pnl = reportService.profitAndLoss(startDate, endDate);
        BigDecimal cogs = pnl.expenses().stream()
                .filter(l -> COGS_CODE.equals(l.accountCode()))
                .map(FinancialLineDto::amount)
                .findFirst().orElse(BigDecimal.ZERO);
        BigDecimal grossMargin = pnl.totalRevenue().subtract(cogs);

        VatReportDto vat = reportService.vatReport(startDate, endDate);

        Account cashAccount = accountService.findByCode(CASH_ACCOUNT_CODE);
        BigDecimal cashBalance = accountService.getLedger(cashAccount.getId(), null, LocalDate.now()).closingBalance();

        return new OwnerReportFinancialDto(
                pnl.totalRevenue(), pnl.totalExpenses(), pnl.netIncome(), grossMargin,
                vat.netVatPayable(), cashBalance
        );
    }

    private OwnerReportSalesDto sales(LocalDate startDate, LocalDate endDate) {
        var profitByProduct = analyticsService.profitByProduct(startDate, endDate);
        List<ProfitByProductLineDto> topProducts = profitByProduct.lines().stream()
                .sorted(Comparator.comparing(ProfitByProductLineDto::revenue).reversed())
                .limit(TOP_N)
                .toList();

        var customerPerf = analyticsService.customerPerformance(startDate, endDate);
        List<CustomerPerformanceLineDto> topCustomers = customerPerf.lines().stream()
                .sorted(Comparator.comparing(CustomerPerformanceLineDto::totalRevenue).reversed())
                .limit(TOP_N)
                .toList();

        int saleCount = customerPerf.lines().stream().mapToInt(CustomerPerformanceLineDto::saleCount).sum();

        BigDecimal totalReceivables = customerRepository.findAll().stream()
                .map(Customer::getCurrentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OwnerReportSalesDto(profitByProduct.totalRevenue(), saleCount, topProducts, topCustomers, totalReceivables);
    }

    private OwnerReportInventoryDto inventory() {
        List<StockItem> allStock = stockItemRepository.findAll();

        BigDecimal totalStockValue = allStock.stream()
                .map(si -> si.getQuantity().multiply(si.getProduct().getCostPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<StockItem> lowStock = stockItemRepository.findLowStock();
        List<OwnerReportLowStockItemDto> lowStockItems = lowStock.stream()
                .map(si -> new OwnerReportLowStockItemDto(
                        si.getProduct().getId(), si.getProduct().getName(), si.getWarehouse().getName(),
                        si.getQuantity(), si.getProduct().getReorderLevel()))
                .limit(10)
                .toList();

        Map<Warehouse, BigDecimal> byWarehouse = allStock.stream()
                .collect(Collectors.groupingBy(StockItem::getWarehouse,
                        Collectors.reducing(BigDecimal.ZERO, si -> si.getQuantity().multiply(si.getProduct().getCostPrice()), BigDecimal::add)));
        List<OwnerReportWarehouseValueDto> stockByWarehouse = byWarehouse.entrySet().stream()
                .map(e -> new OwnerReportWarehouseValueDto(e.getKey().getId(), e.getKey().getName(), e.getValue()))
                .sorted(Comparator.comparing(OwnerReportWarehouseValueDto::stockValue).reversed())
                .toList();

        return new OwnerReportInventoryDto(totalStockValue, lowStock.size(), lowStockItems, stockByWarehouse);
    }

    private OwnerReportPurchasingDto purchasing(LocalDate startDate, LocalDate endDate) {
        var supplierPerf = analyticsService.supplierPerformance(startDate, endDate);
        List<SupplierPerformanceLineDto> topSuppliers = supplierPerf.lines().stream()
                .sorted(Comparator.comparing(SupplierPerformanceLineDto::totalSpend).reversed())
                .limit(TOP_N)
                .toList();
        BigDecimal totalPurchases = supplierPerf.lines().stream()
                .map(SupplierPerformanceLineDto::totalSpend)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Same outstanding-balance formula as SupplierPaymentService.recordForPurchaseOrder():
        // (received qty × unit cost) + VAT, less payments already made, summed across every PO
        // that has ever had anything received against it.
        BigDecimal totalPayables = purchaseOrderRepository.findAll().stream()
                .map(this::outstandingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OwnerReportPurchasingDto(totalPurchases, topSuppliers, totalPayables);
    }

    private BigDecimal outstandingBalance(PurchaseOrder po) {
        BigDecimal receivedValue = po.getLines().stream()
                .map(l -> l.getQuantityReceived().multiply(l.getUnitCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (receivedValue.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;

        BigDecimal invoiceTotal = receivedValue.add(VatConstants.vatOn(receivedValue));
        BigDecimal alreadyPaid = supplierPaymentRepository.findByPurchaseOrderIdOrderByPaymentDateDesc(po.getId())
                .stream().map(SupplierPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = invoiceTotal.subtract(alreadyPaid);
        return outstanding.compareTo(BigDecimal.ZERO) > 0 ? outstanding : BigDecimal.ZERO;
    }
}
