package com.company.tai.accounting.repository;

import com.company.tai.accounting.entity.JournalEntry;
import com.company.tai.accounting.entity.JournalSourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    @Query("SELECT DISTINCT je FROM JournalEntry je LEFT JOIN je.lines l WHERE " +
           "(:accountId IS NULL OR l.account.id = :accountId) AND " +
           "(:sourceType IS NULL OR je.sourceType = :sourceType) AND " +
           "(:startDate IS NULL OR je.entryDate >= :startDate) AND " +
           "(:endDate IS NULL OR je.entryDate <= :endDate) AND " +
           "(:search IS NULL OR LOWER(je.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "OR LOWER(je.reference) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "ORDER BY je.entryDate DESC, je.id DESC")
    Page<JournalEntry> search(@Param("accountId") Long accountId,
                               @Param("sourceType") JournalSourceType sourceType,
                               @Param("startDate") LocalDate startDate,
                               @Param("endDate") LocalDate endDate,
                               @Param("search") String search,
                               Pageable pageable);

    // Idempotency guard for period-end closing — reference is a distinctive, deterministic
    // string per period ("Period close YYYY-MM-DD to YYYY-MM-DD"), so re-running the scheduled
    // job (or a manual backfill) for an already-closed period is a no-op rather than a duplicate.
    boolean existsByReference(String reference);
}
