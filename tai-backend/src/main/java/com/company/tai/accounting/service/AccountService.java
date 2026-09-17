package com.company.tai.accounting.service;

import com.company.tai.accounting.dto.AccountDto;
import com.company.tai.accounting.dto.AccountRequest;
import com.company.tai.accounting.dto.LedgerLineDto;
import com.company.tai.accounting.dto.LedgerResponseDto;
import com.company.tai.accounting.entity.Account;
import com.company.tai.accounting.entity.AccountType;
import com.company.tai.accounting.entity.JournalEntryLine;
import com.company.tai.accounting.repository.AccountRepository;
import com.company.tai.accounting.repository.JournalEntryLineRepository;
import com.company.tai.common.exception.BusinessRuleException;
import com.company.tai.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final JournalEntryLineRepository journalEntryLineRepository;

    public List<AccountDto> listAll() {
        return accountRepository.findAllByOrderByCodeAsc().stream().map(this::toDto).toList();
    }

    // Used by ReportService for P&L/Balance Sheet, which need the raw Account entities (for
    // account.getId()) grouped by type, not the DTO shape listAll() returns.
    public List<Account> listByType(AccountType type) {
        return accountRepository.findAllByOrderByCodeAsc().stream()
                .filter(a -> a.getAccountType() == type && a.isActive())
                .toList();
    }

    @Transactional
    public AccountDto create(AccountRequest request) {
        if (accountRepository.findByCode(request.code()).isPresent()) {
            throw new BusinessRuleException("An account with code " + request.code() + " already exists");
        }
        Account account = Account.builder()
                .code(request.code())
                .name(request.name())
                .accountType(request.accountType())
                .parentAccount(resolveParent(request.parentAccountId()))
                .active(true)
                .build();
        return toDto(accountRepository.save(account));
    }

    @Transactional
    public AccountDto update(Long id, AccountRequest request) {
        Account account = findOrThrow(id);
        if (!account.getCode().equals(request.code()) && accountRepository.findByCode(request.code()).isPresent()) {
            throw new BusinessRuleException("An account with code " + request.code() + " already exists");
        }
        account.setCode(request.code());
        account.setName(request.name());
        account.setAccountType(request.accountType());
        account.setParentAccount(resolveParent(request.parentAccountId()));
        return toDto(account);
    }

    @Transactional
    public void deactivate(Long id) {
        findOrThrow(id).setActive(false);
    }

    // No delete endpoint exists at all for accounts — correction happens by deactivating, never
    // by removing history an audit trail depends on. This method exists only so JournalService
    // and other callers can resolve well-known accounts by code without duplicating the lookup.
    public Account findByCode(String code) {
        return accountRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException(
                        "Required system account with code " + code + " is missing from the Chart of Accounts"));
    }

    @Transactional(readOnly = true)
    public LedgerResponseDto getLedger(Long accountId, LocalDate startDate, LocalDate endDate) {
        Account account = findOrThrow(accountId);
        boolean debitNormal = account.getAccountType().isDebitNormal();

        List<JournalEntryLine> allLines = journalEntryLineRepository.findForLedger(accountId, endDate);

        BigDecimal openingBalance = BigDecimal.ZERO;
        List<LedgerLineDto> ledgerLines = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        boolean pastOpeningPeriod = false;

        for (JournalEntryLine line : allLines) {
            LocalDate entryDate = line.getJournalEntry().getEntryDate();
            BigDecimal delta = debitNormal
                    ? line.getDebitAmount().subtract(line.getCreditAmount())
                    : line.getCreditAmount().subtract(line.getDebitAmount());

            if (startDate != null && entryDate.isBefore(startDate)) {
                openingBalance = openingBalance.add(delta);
                continue;
            }

            if (!pastOpeningPeriod) {
                running = openingBalance;
                pastOpeningPeriod = true;
            }
            running = running.add(delta);

            ledgerLines.add(new LedgerLineDto(
                    line.getId(),
                    entryDate,
                    line.getJournalEntry().getDescription(),
                    line.getJournalEntry().getReference(),
                    line.getDebitAmount(),
                    line.getCreditAmount(),
                    delta,
                    line.isReconciled(),
                    running
            ));
        }

        BigDecimal closingBalance = ledgerLines.isEmpty() ? openingBalance : ledgerLines.get(ledgerLines.size() - 1).runningBalance();
        return new LedgerResponseDto(account.getId(), account.getName(), openingBalance, ledgerLines, closingBalance);
    }

    // Manual match-and-flag reconciliation: no bank-statement import, just marking individual
    // ledger lines as reconciled once the accountant has checked them against the physical
    // statement. Kept intentionally simple — no matching heuristics, no statement parsing.
    @Transactional
    public void setReconciled(Long lineId, boolean reconciled) {
        JournalEntryLine line = journalEntryLineRepository.findById(lineId)
                .orElseThrow(() -> new ResourceNotFoundException("Journal entry line not found with id: " + lineId));
        line.setReconciled(reconciled);
        line.setReconciledDate(reconciled ? LocalDate.now() : null);
    }

    private Account resolveParent(Long parentAccountId) {
        return parentAccountId != null ? findOrThrow(parentAccountId) : null;
    }

    Account findOrThrow(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + id));
    }

    private AccountDto toDto(Account a) {
        return new AccountDto(
                a.getId(), a.getCode(), a.getName(), a.getAccountType(),
                a.getParentAccount() != null ? a.getParentAccount().getId() : null,
                a.getParentAccount() != null ? a.getParentAccount().getName() : null,
                a.isActive()
        );
    }
}
