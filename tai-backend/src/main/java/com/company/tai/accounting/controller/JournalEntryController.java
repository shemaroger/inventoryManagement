package com.company.tai.accounting.controller;

import com.company.tai.accounting.dto.CreateJournalEntryRequest;
import com.company.tai.accounting.dto.JournalEntryDto;
import com.company.tai.accounting.entity.JournalSourceType;
import com.company.tai.accounting.service.JournalService;
import com.company.tai.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/journal-entries")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class JournalEntryController {

    private final JournalService journalService;

    @GetMapping
    public ApiResponse<Page<JournalEntryDto>> search(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) JournalSourceType sourceType,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(journalService.search(accountId, sourceType, startDate, endDate, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<JournalEntryDto> getById(@PathVariable Long id) {
        return ApiResponse.ok(journalService.getById(id));
    }

    @PostMapping
    public ApiResponse<JournalEntryDto> create(@Valid @RequestBody CreateJournalEntryRequest request) {
        return ApiResponse.ok("Journal entry created", journalService.createManualEntry(request));
    }
}
