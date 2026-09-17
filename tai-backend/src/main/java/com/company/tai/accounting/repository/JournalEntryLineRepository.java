package com.company.tai.accounting.repository;

import com.company.tai.accounting.entity.JournalEntryLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface JournalEntryLineRepository extends JpaRepository<JournalEntryLine, Long> {

    // Used by the General Ledger endpoint: every line ever posted to this account up to and
    // including endDate, ordered chronologically. The service splits this into "before
    // startDate" (folded into the opening balance) and "on/after startDate" (the displayed
    // lines) rather than issuing two separate queries.
    @Query("SELECT l FROM JournalEntryLine l JOIN FETCH l.journalEntry je WHERE " +
           "l.account.id = :accountId AND je.entryDate <= :endDate " +
           "ORDER BY je.entryDate ASC, je.id ASC")
    List<JournalEntryLine> findForLedger(@Param("accountId") Long accountId, @Param("endDate") LocalDate endDate);

    // Used by the VAT Report: every line posted to a given control account (VAT Payable or
    // Input VAT) within a period, so the report reflects the actual General Ledger movement
    // rather than recomputing VAT independently from Sales/Purchasing data.
    @Query("SELECT l FROM JournalEntryLine l JOIN FETCH l.journalEntry je WHERE " +
           "l.account.id = :accountId AND je.entryDate BETWEEN :startDate AND :endDate")
    List<JournalEntryLine> findByAccountAndDateRange(@Param("accountId") Long accountId,
                                                       @Param("startDate") LocalDate startDate,
                                                       @Param("endDate") LocalDate endDate);
}
