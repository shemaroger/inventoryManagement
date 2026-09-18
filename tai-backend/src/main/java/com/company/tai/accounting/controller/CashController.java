package com.company.tai.accounting.controller;

import com.company.tai.accounting.dto.CashExpenditureRequest;
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

@RestController
@RequestMapping("/api/cash")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class CashController {

    private final JournalService journalService;

    @GetMapping("/expenditures")
    public ApiResponse<Page<JournalEntryDto>> listExpenditures(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(journalService.search(null, JournalSourceType.EXPENSE, null, null, null, pageable));
    }

    @PostMapping("/expenditures")
    public ApiResponse<JournalEntryDto> recordExpenditure(@Valid @RequestBody CashExpenditureRequest request) {
        return ApiResponse.ok("Cash expenditure recorded", journalService.postCashExpenditure(request));
    }
}
