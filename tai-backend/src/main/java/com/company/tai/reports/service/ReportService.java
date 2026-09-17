package com.company.tai.reports.service;

import com.company.tai.accounting.entity.Account;
import com.company.tai.accounting.entity.AccountType;
import com.company.tai.accounting.entity.JournalEntryLine;
import com.company.tai.accounting.repository.JournalEntryLineRepository;
import com.company.tai.accounting.service.AccountService;
import com.company.tai.common.tax.VatConstants;
import com.company.tai.inventory.entity.AdjustmentType;
import com.company.tai.inventory.entity.StockAdjustment;
import com.company.tai.inventory.repository.StockAdjustmentRepository;
import com.company.tai.purchasing.entity.PurchaseOrder;
import com.company.tai.purchasing.entity.PurchaseOrderLine;
import com.company.tai.purchasing.entity.PurchasePaymentType;
import com.company.tai.purchasing.repository.PurchaseOrderRepository;
import com.company.tai.reports.dto.*;
import com.company.tai.sales.entity.PaymentType;
import com.company.tai.sales.entity.Sale;
import com.company.tai.sales.entity.SaleLine;
import com.company.tai.sales.entity.SaleStatus;
import com.company.tai.sales.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Compiles the existing Sales/Purchasing data into the two statutory records Rwanda's Tax
// Procedures Law requires (a daily record of sales, and of purchases, each split cash vs.
// credit) — this is reporting over data that's already written elsewhere, not new business
// logic, so everything here is read-only.
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final String RECEIPT_REASON_PREFIX = "Received against PO #";
    private static final Pattern RECEIPT_REASON_PATTERN = Pattern.compile("Received against PO #(\\d+)");

    private static final String VAT_PAYABLE_CODE = "2100";
    private static final String INPUT_VAT_CODE = "1150";

    private final SaleRepository saleRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final JournalEntryLineRepository journalEntryLineRepository;
    private final AccountService accountService;

    @Transactional(readOnly = true)
    public DailySalesReportDto dailySales(LocalDate date) {
        DailySalesRangeReportDto range = dailySalesRange(date, date);
        return new DailySalesReportDto(date, range.cashSales(), range.creditSales(), range.cashTotal(), range.creditTotal(), range.grandTotal());
    }

    // Only COMPLETED sales are real sales for this record — a QUOTATION never happened and a
    // CANCELLED one didn't either, so both are excluded at the query level, not filtered out
    // after the fact where it'd be easy to forget.
    @Transactional(readOnly = true)
    public DailySalesRangeReportDto dailySalesRange(LocalDate startDate, LocalDate endDate) {
        List<Sale> sales = saleRepository.findByStatusAndSaleDateBetweenOrderBySaleDateAscIdAsc(SaleStatus.COMPLETED, startDate, endDate);

        List<DailySalesEntryDto> cash = new ArrayList<>();
        List<DailySalesEntryDto> credit = new ArrayList<>();
        for (Sale sale : sales) {
            // VAT-inclusive: this is what actually changed hands, which is what a daily cash/
            // credit sales record needs to show — not the pre-VAT revenue figure.
            BigDecimal subtotal = saleTotal(sale);
            BigDecimal grandTotal = subtotal.add(VatConstants.vatOn(subtotal));
            DailySalesEntryDto entry = new DailySalesEntryDto(
                    sale.getId(), sale.getSaleDate(), sale.getCustomer().getName(), sale.getPaymentType(), grandTotal);
            (sale.getPaymentType() == PaymentType.CASH ? cash : credit).add(entry);
        }

        BigDecimal cashTotal = cash.stream().map(DailySalesEntryDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal creditTotal = credit.stream().map(DailySalesEntryDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DailySalesRangeReportDto(startDate, endDate, cash, credit, cashTotal, creditTotal, cashTotal.add(creditTotal));
    }

    @Transactional(readOnly = true)
    public DailyPurchaseReportDto dailyPurchases(LocalDate date) {
        DailyPurchaseRangeReportDto range = dailyPurchasesRange(date, date);
        return new DailyPurchaseReportDto(date, range.cashPurchases(), range.creditPurchases(), range.cashTotal(), range.creditTotal(), range.grandTotal());
    }

    // "Purchases" here means goods actually received on the given day — not when the PO was
    // placed — per the law caring about when the cost was actually incurred. Receipt events are
    // recorded as StockAdjustment rows by PurchaseOrderService.receive(); the PO id is only
    // carried in that row's reason text (no dedicated GRN table), so it's parsed back out here.
    // Rows for the same PO/day are merged into one line so a multi-line receipt reads as one
    // purchase, matching how a real ledger entry would look.
    @Transactional(readOnly = true)
    public DailyPurchaseRangeReportDto dailyPurchasesRange(LocalDate startDate, LocalDate endDate) {
        Instant start = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<StockAdjustment> receipts = stockAdjustmentRepository
                .findByAdjustmentTypeAndReasonStartingWithAndCreatedAtBetween(AdjustmentType.INCREASE, RECEIPT_REASON_PREFIX, start, end);

        record Key(Long poId, LocalDate date) {}
        Map<Key, BigDecimal> valueByKey = new LinkedHashMap<>();
        Map<Long, PurchaseOrder> poCache = new HashMap<>();

        for (StockAdjustment sa : receipts) {
            Matcher m = RECEIPT_REASON_PATTERN.matcher(sa.getReason());
            if (!m.find()) continue;
            Long poId = Long.valueOf(m.group(1));
            PurchaseOrder po = poCache.computeIfAbsent(poId, id -> purchaseOrderRepository.findById(id).orElse(null));
            if (po == null) continue;

            BigDecimal unitCost = po.getLines().stream()
                    .filter(l -> l.getProduct().getId().equals(sa.getProduct().getId()))
                    .map(PurchaseOrderLine::getUnitCost)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);
            // VAT-inclusive, same reasoning as Daily Sales — this is the actual invoiced amount,
            // matching what JournalService.postPurchaseReceiptEntry() posts to Cash/Accounts Payable.
            BigDecimal netValue = sa.getQuantity().multiply(unitCost);
            BigDecimal value = netValue.add(VatConstants.vatOn(netValue));
            LocalDate receivedDate = sa.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();

            valueByKey.merge(new Key(poId, receivedDate), value, BigDecimal::add);
        }

        List<DailyPurchaseEntryDto> cash = new ArrayList<>();
        List<DailyPurchaseEntryDto> credit = new ArrayList<>();
        for (Map.Entry<Key, BigDecimal> e : valueByKey.entrySet()) {
            PurchaseOrder po = poCache.get(e.getKey().poId());
            DailyPurchaseEntryDto entry = new DailyPurchaseEntryDto(
                    po.getId(), e.getKey().date(), po.getSupplier().getName(), po.getPaymentType(), e.getValue());
            (po.getPaymentType() == PurchasePaymentType.CASH ? cash : credit).add(entry);
        }

        BigDecimal cashTotal = cash.stream().map(DailyPurchaseEntryDto::amountReceived).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal creditTotal = credit.stream().map(DailyPurchaseEntryDto::amountReceived).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DailyPurchaseRangeReportDto(startDate, endDate, cash, credit, cashTotal, creditTotal, cashTotal.add(creditTotal));
    }

    // Derived from the actual General Ledger control accounts (VAT Payable, Input VAT) rather
    // than recomputed independently from Sales/Purchasing data — a VAT return is filed off the
    // books, so this reflects exactly what those accounts show for the period, manual entries
    // included. VAT Payable is credit-normal (output VAT collected increases it); Input VAT is
    // debit-normal (VAT paid to suppliers increases it, since it's a reclaimable asset).
    @Transactional(readOnly = true)
    public VatReportDto vatReport(LocalDate startDate, LocalDate endDate) {
        Account vatPayable = accountService.findByCode(VAT_PAYABLE_CODE);
        Account inputVat = accountService.findByCode(INPUT_VAT_CODE);

        BigDecimal outputVat = netMovement(vatPayable.getId(), startDate, endDate, false);
        BigDecimal inputVatAmount = netMovement(inputVat.getId(), startDate, endDate, true);

        return new VatReportDto(startDate, endDate, outputVat, inputVatAmount, outputVat.subtract(inputVatAmount));
    }

    private BigDecimal netMovement(Long accountId, LocalDate startDate, LocalDate endDate, boolean debitNormal) {
        List<JournalEntryLine> lines = journalEntryLineRepository.findByAccountAndDateRange(accountId, startDate, endDate);
        return signedSum(lines, debitNormal);
    }

    private BigDecimal cumulativeBalance(Long accountId, LocalDate asOfDate, boolean debitNormal) {
        List<JournalEntryLine> lines = journalEntryLineRepository.findForLedger(accountId, asOfDate);
        return signedSum(lines, debitNormal);
    }

    private BigDecimal signedSum(List<JournalEntryLine> lines, boolean debitNormal) {
        BigDecimal totalDebit = lines.stream().map(JournalEntryLine::getDebitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = lines.stream().map(JournalEntryLine::getCreditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return debitNormal ? totalDebit.subtract(totalCredit) : totalCredit.subtract(totalDebit);
    }

    // Revenue accounts' credit-normal movement in the period minus Expense accounts'
    // debit-normal movement — the standard P&L shape. Built entirely from the General Ledger,
    // same as the VAT report, so it reflects manual corrections too, not just automatic postings.
    @Transactional(readOnly = true)
    public ProfitLossReportDto profitAndLoss(LocalDate startDate, LocalDate endDate) {
        List<Account> revenueAccounts = accountService.listByType(AccountType.REVENUE);
        List<Account> expenseAccounts = accountService.listByType(AccountType.EXPENSE);

        List<FinancialLineDto> revenueLines = revenueAccounts.stream()
                .map(a -> new FinancialLineDto(a.getCode(), a.getName(), netMovement(a.getId(), startDate, endDate, false)))
                .toList();
        List<FinancialLineDto> expenseLines = expenseAccounts.stream()
                .map(a -> new FinancialLineDto(a.getCode(), a.getName(), netMovement(a.getId(), startDate, endDate, true)))
                .toList();

        BigDecimal totalRevenue = revenueLines.stream().map(FinancialLineDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpenses = expenseLines.stream().map(FinancialLineDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ProfitLossReportDto(startDate, endDate, revenueLines, expenseLines, totalRevenue, totalExpenses,
                totalRevenue.subtract(totalExpenses));
    }

    // Assets/Liabilities/Equity as of a single date, each account's balance accumulated since
    // inception. Equity also folds in "current earnings" (all-time Revenue minus Expenses up to
    // asOfDate) as a computed line, since there's no period-closing entry that actually moves
    // those balances into Retained Earnings — without it, Assets would not equal Liabilities +
    // Equity, which should never happen given every journal entry is balanced by construction.
    @Transactional(readOnly = true)
    public BalanceSheetReportDto balanceSheet(LocalDate asOfDate) {
        List<Account> assetAccounts = accountService.listByType(AccountType.ASSET);
        List<Account> liabilityAccounts = accountService.listByType(AccountType.LIABILITY);
        List<Account> equityAccounts = accountService.listByType(AccountType.EQUITY);
        List<Account> revenueAccounts = accountService.listByType(AccountType.REVENUE);
        List<Account> expenseAccounts = accountService.listByType(AccountType.EXPENSE);

        List<FinancialLineDto> assetLines = assetAccounts.stream()
                .map(a -> new FinancialLineDto(a.getCode(), a.getName(), cumulativeBalance(a.getId(), asOfDate, true)))
                .toList();
        List<FinancialLineDto> liabilityLines = liabilityAccounts.stream()
                .map(a -> new FinancialLineDto(a.getCode(), a.getName(), cumulativeBalance(a.getId(), asOfDate, false)))
                .toList();
        List<FinancialLineDto> equityLines = equityAccounts.stream()
                .map(a -> new FinancialLineDto(a.getCode(), a.getName(), cumulativeBalance(a.getId(), asOfDate, false)))
                .toList();

        BigDecimal allTimeRevenue = revenueAccounts.stream()
                .map(a -> cumulativeBalance(a.getId(), asOfDate, false)).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal allTimeExpenses = expenseAccounts.stream()
                .map(a -> cumulativeBalance(a.getId(), asOfDate, true)).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal currentEarnings = allTimeRevenue.subtract(allTimeExpenses);

        BigDecimal totalAssets = assetLines.stream().map(FinancialLineDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLiabilities = liabilityLines.stream().map(FinancialLineDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEquity = equityLines.stream().map(FinancialLineDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add).add(currentEarnings);

        return new BalanceSheetReportDto(asOfDate, assetLines, liabilityLines, equityLines, currentEarnings,
                totalAssets, totalLiabilities, totalEquity);
    }

    // Same formula as SaleService/CustomerService — kept duplicated in this small form rather
    // than introducing a cross-service dependency just for one line of arithmetic.
    private BigDecimal saleTotal(Sale sale) {
        return sale.getLines().stream()
                .map(this::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal lineTotal(SaleLine line) {
        return line.getUnitPrice()
                .multiply(line.getQuantity())
                .multiply(BigDecimal.ONE.subtract(line.getDiscountPercent().divide(BigDecimal.valueOf(100))));
    }
}
