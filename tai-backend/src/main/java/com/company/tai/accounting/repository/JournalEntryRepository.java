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
           "(:endDate IS NULL OR je.entryDate <= :endDate) " +
           "ORDER BY je.entryDate DESC, je.id DESC")
    Page<JournalEntry> search(@Param("accountId") Long accountId,
                               @Param("sourceType") JournalSourceType sourceType,
                               @Param("startDate") LocalDate startDate,
                               @Param("endDate") LocalDate endDate,
                               Pageable pageable);
}
