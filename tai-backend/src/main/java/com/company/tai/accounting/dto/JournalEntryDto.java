package com.company.tai.accounting.dto;

import com.company.tai.accounting.entity.JournalSourceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record JournalEntryDto(
        Long id,
        LocalDate entryDate,
        String description,
        String reference,
        JournalSourceType sourceType,
        Long sourceId,
        List<JournalEntryLineDto> lines,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        String createdByName,
        Instant createdAt
) {}
