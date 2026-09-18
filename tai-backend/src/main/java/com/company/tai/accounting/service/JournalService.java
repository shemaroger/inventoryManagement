package com.company.tai.accounting.service;

import com.company.tai.accounting.dto.*;
import com.company.tai.accounting.entity.Account;
import com.company.tai.accounting.entity.AccountType;
import com.company.tai.accounting.entity.JournalEntry;
import com.company.tai.accounting.entity.JournalEntryLine;
import com.company.tai.accounting.entity.JournalSourceType;
import com.company.tai.accounting.repository.JournalEntryLineRepository;
import com.company.tai.accounting.repository.JournalEntryRepository;
import com.company.tai.common.exception.BusinessRuleException;
import com.company.tai.common.exception.ResourceNotFoundException;
import com.company.tai.common.tax.VatConstants;
import com.company.tai.inventory.entity.AdjustmentType;
import com.company.tai.inventory.entity.StockAdjustment;
import com.company.tai.purchasing.entity.PurchaseOrder;
import com.company.tai.purchasing.entity.PurchasePaymentType;
import com.company.tai.purchasing.entity.SupplierPayment;
import com.company.tai.sales.entity.PaymentType;
import com.company.tai.sales.entity.Sale;
import com.company.tai.sales.entity.SaleLine;
import com.company.tai.sales.entity.SalePayment;
import com.company.tai.sales.entity.SalePaymentMethod;
import com.company.tai.user.entity.User;
import com.company.tai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// The single place double-entry postings happen — SaleService, PurchaseOrderService,
// SalePaymentService, and SupplierPaymentService call into this rather than building journal
// entries themselves, so the balanced-entry invariant only has to be enforced in one place.
@Service
@RequiredArgsConstructor
public class JournalService {

    private static final String CASH = "1000";
    private static final String BANK = "1010";
    private static final String ACCOUNTS_RECEIVABLE = "1100";
    private static final String INPUT_VAT = "1150";
    private static final String INVENTORY = "1200";
    private static final String ACCOUNTS_PAYABLE = "2000";
    private static final String VAT_PAYABLE = "2100";
    private static final String SALES_REVENUE = "4000";
    private static final String COST_OF_GOODS_SOLD = "5000";
    private static final String INVENTORY_SHRINKAGE = "5200";
    private static final String RETAINED_EARNINGS = "3100";

    // These two adjustment types move stock between warehouses but never change the total
    // quantity Mapleco owns — so they have zero net accounting effect and are never posted.
    private static final java.util.Set<AdjustmentType> NO_LEDGER_EFFECT =
            java.util.Set.of(AdjustmentType.TRANSFER_IN, AdjustmentType.TRANSFER_OUT);
    private static final java.util.Set<AdjustmentType> INCREASING_TYPES =
            java.util.Set.of(AdjustmentType.INCREASE, AdjustmentType.RECOUNT);

    private final JournalEntryRepository journalEntryRepository;
    private final JournalEntryLineRepository journalEntryLineRepository;
    private final AccountService accountService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<JournalEntryDto> search(Long accountId, JournalSourceType sourceType, LocalDate startDate, LocalDate endDate, String searchText, Pageable pageable) {
        return journalEntryRepository.search(accountId, sourceType, startDate, endDate, searchText, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public JournalEntryDto getById(Long id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public JournalEntryDto createManualEntry(CreateJournalEntryRequest request) {
        List<JournalEntryLine> lines = buildLines(request.lines());
        JournalEntry entry = persistBalanced(request.entryDate(), request.description(), request.reference(),
                JournalSourceType.MANUAL, null, lines);
        return toDto(entry);
    }

    // The "Cash Management" quick-expense entry point: staff pick an expense account and where
    // the money came from, without needing to know this is really a Debit Expense / Credit
    // Cash-or-Bank double-entry underneath. Still funnels through persistBalanced() like
    // everything else, so it's just as protected by the balanced-entry invariant.
    @Transactional
    public JournalEntryDto postCashExpenditure(CashExpenditureRequest request) {
        Account expenseAccount = accountService.findOrThrow(request.expenseAccountId());
        if (expenseAccount.getAccountType() != AccountType.EXPENSE) {
            throw new BusinessRuleException("Account " + expenseAccount.getCode() + " (" + expenseAccount.getName()
                    + ") is not an EXPENSE account");
        }
        if (!expenseAccount.isActive()) {
            throw new BusinessRuleException("Account " + expenseAccount.getCode() + " (" + expenseAccount.getName()
                    + ") is inactive and cannot be posted to");
        }
        Account sourceAccount = accountService.findByCode("BANK".equalsIgnoreCase(request.paymentSource()) ? BANK : CASH);

        List<JournalEntryLine> lines = List.of(
                debitLine(expenseAccount, request.amount()),
                creditLine(sourceAccount, request.amount())
        );
        JournalEntry entry = persistBalanced(request.date(), request.description(), null, JournalSourceType.EXPENSE, null, lines);
        return toDto(entry);
    }

    // Period-end closing: zeroes every Revenue/Expense account's movement for the period into
    // Retained Earnings, so "current earnings" becomes a real posted balance instead of a live
    // recomputation — this is what ReportService.balanceSheet()'s "currentEarnings" line
    // approximates on the fly for periods that haven't been closed yet. Idempotent: re-running
    // this for an already-closed period is rejected rather than double-posting.
    @Transactional
    public JournalEntryDto closePeriod(LocalDate startDate, LocalDate endDate) {
        String reference = "Period close " + startDate + " to " + endDate;
        if (journalEntryRepository.existsByReference(reference)) {
            throw new BusinessRuleException("Period " + startDate + " to " + endDate + " has already been closed");
        }

        List<JournalEntryLine> lines = new ArrayList<>();
        BigDecimal netIncome = BigDecimal.ZERO;

        for (Account account : accountService.listByType(AccountType.REVENUE)) {
            BigDecimal balance = accountPeriodBalance(account.getId(), startDate, endDate, false);
            if (balance.compareTo(BigDecimal.ZERO) == 0) continue;
            lines.add(balance.compareTo(BigDecimal.ZERO) > 0 ? debitLine(account, balance) : creditLine(account, balance.abs()));
            netIncome = netIncome.add(balance);
        }
        for (Account account : accountService.listByType(AccountType.EXPENSE)) {
            BigDecimal balance = accountPeriodBalance(account.getId(), startDate, endDate, true);
            if (balance.compareTo(BigDecimal.ZERO) == 0) continue;
            lines.add(balance.compareTo(BigDecimal.ZERO) > 0 ? creditLine(account, balance) : debitLine(account, balance.abs()));
            netIncome = netIncome.subtract(balance);
        }

        if (lines.isEmpty()) {
            throw new BusinessRuleException("No Revenue or Expense activity to close for " + startDate + " to " + endDate);
        }

        Account retainedEarnings = accountService.findByCode(RETAINED_EARNINGS);
        if (netIncome.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(creditLine(retainedEarnings, netIncome));
        } else if (netIncome.compareTo(BigDecimal.ZERO) < 0) {
            lines.add(debitLine(retainedEarnings, netIncome.abs()));
        }

        JournalEntry entry = persistBalanced(endDate,
                "Period close " + startDate + " to " + endDate + " (net income " + netIncome + ")",
                reference, JournalSourceType.CLOSING, null, lines);
        return toDto(entry);
    }

    private BigDecimal accountPeriodBalance(Long accountId, LocalDate startDate, LocalDate endDate, boolean debitNormal) {
        List<JournalEntryLine> lines = journalEntryLineRepository.findByAccountAndDateRange(accountId, startDate, endDate);
        BigDecimal totalDebit = lines.stream().map(JournalEntryLine::getDebitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = lines.stream().map(JournalEntryLine::getCreditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return debitNormal ? totalDebit.subtract(totalCredit) : totalCredit.subtract(totalDebit);
    }

    // Debit Cash/AR for the sale total, credit Sales Revenue; separately debit COGS and credit
    // Inventory for the cost basis of goods sold — all four lines in ONE journal entry, so the
    // balanced-entry check naturally covers both halves of the transaction together. Called from
    // within SaleService.complete()'s own @Transactional so a posting failure rolls back the
    // sale completion too, rather than leaving stock moved with no matching accounting record.
    public void postSaleEntry(Sale sale) {
        Account revenueAccount = accountService.findByCode(SALES_REVENUE);
        Account receivingAccount = accountService.findByCode(sale.getPaymentType() == PaymentType.CASH ? CASH : ACCOUNTS_RECEIVABLE);

        // VAT-exclusive subtotal is what's actually recognized as revenue; the 18% on top is
        // never Mapleco's income — it's collected on RRA's behalf, so it credits VAT Payable
        // instead of Sales Revenue. Cash/AR is still debited for the full VAT-inclusive amount,
        // since that's what the customer actually pays/owes.
        BigDecimal subtotal = sale.getLines().stream().map(this::saleLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vatAmount = VatConstants.vatOn(subtotal);
        BigDecimal grandTotal = subtotal.add(vatAmount);
        BigDecimal cogsTotal = sale.getLines().stream()
                .map(l -> l.getProduct().getCostPrice().multiply(l.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<JournalEntryLine> lines = new ArrayList<>();
        lines.add(debitLine(receivingAccount, grandTotal));
        lines.add(creditLine(revenueAccount, subtotal));
        if (vatAmount.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(creditLine(accountService.findByCode(VAT_PAYABLE), vatAmount));
        }
        if (cogsTotal.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(debitLine(accountService.findByCode(COST_OF_GOODS_SOLD), cogsTotal));
            lines.add(creditLine(accountService.findByCode(INVENTORY), cogsTotal));
        }

        persistBalanced(sale.getSaleDate(), "Sale #" + sale.getId(), "Sale #" + sale.getId(), JournalSourceType.SALE, sale.getId(), lines);
    }

    // Debit Inventory for the net value actually received in this receive() call, and debit
    // Input VAT for the 18% Mapleco paid the supplier and can reclaim — the supplier invoice
    // covers both, so the credit side (Cash or Accounts Payable) is the VAT-inclusive total.
    // One entry per receive() call, not per PO, since a PO can be received in multiple batches
    // and each batch is its own dated accounting event — mirrors how the Daily Purchases report
    // already groups receipts.
    public void postPurchaseReceiptEntry(PurchaseOrder po, BigDecimal receivedValue) {
        if (receivedValue.compareTo(BigDecimal.ZERO) <= 0) return;

        Account inventoryAccount = accountService.findByCode(INVENTORY);
        Account inputVatAccount = accountService.findByCode(INPUT_VAT);
        Account creditAccount = accountService.findByCode(po.getPaymentType() == PurchasePaymentType.CASH ? CASH : ACCOUNTS_PAYABLE);

        BigDecimal vatAmount = VatConstants.vatOn(receivedValue);
        BigDecimal invoiceTotal = receivedValue.add(vatAmount);

        List<JournalEntryLine> lines = List.of(
                debitLine(inventoryAccount, receivedValue),
                debitLine(inputVatAccount, vatAmount),
                creditLine(creditAccount, invoiceTotal)
        );
        persistBalanced(LocalDate.now(), "Goods received — PO #" + po.getId(), "PO #" + po.getId(),
                JournalSourceType.PURCHASE, po.getId(), lines);
    }

    // Covers every stock movement NOT already accounted for by a Sale completion or a PO
    // receipt — DAMAGE, LOST, RECOUNT, and ad-hoc manual corrections made via
    // POST /api/stock/adjustments. Without this, those movements change StockItem.quantity
    // (and therefore the Dashboard's/Analytics' stock-value figures) with no matching ledger
    // entry, silently drifting Inventory's book balance away from real stock value. Transfers
    // are deliberately excluded — moving stock between warehouses doesn't change how much
    // Mapleco owns in total, so there is nothing to post.
    public void postStockAdjustmentEntry(StockAdjustment adjustment) {
        if (NO_LEDGER_EFFECT.contains(adjustment.getAdjustmentType())) return;

        BigDecimal value = adjustment.getQuantity().multiply(adjustment.getProduct().getCostPrice());
        if (value.compareTo(BigDecimal.ZERO) <= 0) return;

        Account inventoryAccount = accountService.findByCode(INVENTORY);
        Account shrinkageAccount = accountService.findByCode(INVENTORY_SHRINKAGE);
        boolean increasing = INCREASING_TYPES.contains(adjustment.getAdjustmentType());

        List<JournalEntryLine> lines = increasing
                ? List.of(debitLine(inventoryAccount, value), creditLine(shrinkageAccount, value))
                : List.of(debitLine(shrinkageAccount, value), creditLine(inventoryAccount, value));

        String description = adjustment.getAdjustmentType() + " — " + adjustment.getProduct().getName()
                + " (" + adjustment.getWarehouse().getName() + ")"
                + (adjustment.getReason() != null ? ": " + adjustment.getReason() : "");

        persistBalanced(LocalDate.now(), description, null, JournalSourceType.ADJUSTMENT, adjustment.getId(), lines);
    }

    // Only meaningful for a CREDIT sale — a CASH sale already recognized the full amount to
    // Cash at completion, so there is no Accounts Receivable balance left to clear.
    public void postSalePaymentEntry(SalePayment payment) {
        Sale sale = payment.getSale();
        if (sale.getPaymentType() != PaymentType.CREDIT) return;

        Account receivableAccount = accountService.findByCode(ACCOUNTS_RECEIVABLE);
        Account cashOrBankAccount = accountService.findByCode(payment.getPaymentMethod() == SalePaymentMethod.CASH ? CASH : BANK);

        List<JournalEntryLine> lines = List.of(
                debitLine(cashOrBankAccount, payment.getAmount()),
                creditLine(receivableAccount, payment.getAmount())
        );
        persistBalanced(payment.getPaymentDate(), "Payment received — Sale #" + sale.getId(), "Sale #" + sale.getId(),
                JournalSourceType.PAYMENT, sale.getId(), lines);
    }

    // Only meaningful when the originating PO was on CREDIT terms — a CASH PO never created an
    // Accounts Payable balance, so there is nothing here to clear. SupplierPayment has no
    // payment-method field (unlike SalePayment), so this always clears against Cash.
    public void postSupplierPaymentEntry(SupplierPayment payment) {
        PurchaseOrder po = payment.getPurchaseOrder();
        if (po == null || po.getPaymentType() != PurchasePaymentType.CREDIT) return;

        Account payableAccount = accountService.findByCode(ACCOUNTS_PAYABLE);
        Account cashAccount = accountService.findByCode(CASH);

        List<JournalEntryLine> lines = List.of(
                debitLine(payableAccount, payment.getAmount()),
                creditLine(cashAccount, payment.getAmount())
        );
        persistBalanced(payment.getPaymentDate(), "Payment to supplier — PO #" + po.getId(), "PO #" + po.getId(),
                JournalSourceType.PAYMENT, po.getId(), lines);
    }

    private BigDecimal saleLineTotal(SaleLine line) {
        return line.getUnitPrice()
                .multiply(line.getQuantity())
                .multiply(BigDecimal.ONE.subtract(line.getDiscountPercent().divide(BigDecimal.valueOf(100))));
    }

    private JournalEntryLine debitLine(Account account, BigDecimal amount) {
        return JournalEntryLine.builder().account(account).debitAmount(amount).creditAmount(BigDecimal.ZERO).build();
    }

    private JournalEntryLine creditLine(Account account, BigDecimal amount) {
        return JournalEntryLine.builder().account(account).debitAmount(BigDecimal.ZERO).creditAmount(amount).build();
    }

    private List<JournalEntryLine> buildLines(List<JournalEntryLineRequest> requests) {
        List<JournalEntryLine> lines = new ArrayList<>();
        for (JournalEntryLineRequest req : requests) {
            boolean hasDebit = req.debitAmount().compareTo(BigDecimal.ZERO) > 0;
            boolean hasCredit = req.creditAmount().compareTo(BigDecimal.ZERO) > 0;
            if (hasDebit == hasCredit) {
                throw new BusinessRuleException("Each journal entry line must have exactly one of debit or credit set (account id " + req.accountId() + ")");
            }
            Account account = accountService.findOrThrow(req.accountId());
            if (!account.isActive()) {
                throw new BusinessRuleException("Account " + account.getCode() + " (" + account.getName()
                        + ") is inactive and cannot be posted to");
            }
            lines.add(JournalEntryLine.builder()
                    .account(account)
                    .debitAmount(req.debitAmount())
                    .creditAmount(req.creditAmount())
                    .build());
        }
        return lines;
    }

    // The one place the balanced-entry invariant is enforced — every path that creates a
    // JournalEntry (manual or automatic) funnels through here, and nothing is saved to the
    // database until debits and credits are confirmed equal.
    @Transactional
    JournalEntry persistBalanced(LocalDate entryDate, String description, String reference,
                                  JournalSourceType sourceType, Long sourceId, List<JournalEntryLine> lines) {
        BigDecimal totalDebit = lines.stream().map(JournalEntryLine::getDebitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = lines.stream().map(JournalEntryLine::getCreditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new BusinessRuleException(
                    "Journal entry does not balance: debits " + totalDebit + " vs credits " + totalCredit
                            + " (difference " + totalDebit.subtract(totalCredit).abs() + ")");
        }

        JournalEntry entry = JournalEntry.builder()
                .entryDate(entryDate)
                .description(description)
                .reference(reference)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .createdBy(currentUser())
                .build();
        for (JournalEntryLine line : lines) {
            line.setJournalEntry(entry);
            entry.getLines().add(line);
        }

        return journalEntryRepository.save(entry);
    }

    private User currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private JournalEntry findOrThrow(Long id) {
        return journalEntryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Journal entry not found with id: " + id));
    }

    private JournalEntryDto toDto(JournalEntry entry) {
        List<JournalEntryLineDto> lineDtos = entry.getLines().stream()
                .map(l -> new JournalEntryLineDto(
                        l.getId(), l.getAccount().getId(), l.getAccount().getCode(), l.getAccount().getName(),
                        l.getDebitAmount(), l.getCreditAmount()
                ))
                .toList();
        BigDecimal totalDebit = lineDtos.stream().map(JournalEntryLineDto::debitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = lineDtos.stream().map(JournalEntryLineDto::creditAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new JournalEntryDto(
                entry.getId(), entry.getEntryDate(), entry.getDescription(), entry.getReference(),
                entry.getSourceType(), entry.getSourceId(), lineDtos, totalDebit, totalCredit,
                entry.getCreatedBy() != null ? entry.getCreatedBy().getFullName() : null,
                entry.getCreatedAt()
        );
    }
}
