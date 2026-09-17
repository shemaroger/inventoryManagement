package com.company.tai.accounting.controller;

import com.company.tai.accounting.dto.AccountDto;
import com.company.tai.accounting.dto.AccountRequest;
import com.company.tai.accounting.dto.LedgerResponseDto;
import com.company.tai.accounting.service.AccountService;
import com.company.tai.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ApiResponse<List<AccountDto>> listAll() {
        return ApiResponse.ok(accountService.listAll());
    }

    @GetMapping("/{id}/ledger")
    public ApiResponse<LedgerResponseDto> ledger(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();
        return ApiResponse.ok(accountService.getLedger(id, startDate, effectiveEnd));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AccountDto> create(@Valid @RequestBody AccountRequest request) {
        return ApiResponse.ok("Account created", accountService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AccountDto> update(@PathVariable Long id, @Valid @RequestBody AccountRequest request) {
        return ApiResponse.ok("Account updated", accountService.update(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deactivate(@PathVariable Long id) {
        accountService.deactivate(id);
        return ApiResponse.ok("Account deactivated", null);
    }

    @PatchMapping("/ledger-lines/{lineId}/reconcile")
    public ApiResponse<Void> reconcile(@PathVariable Long lineId) {
        accountService.setReconciled(lineId, true);
        return ApiResponse.ok("Marked reconciled", null);
    }

    @PatchMapping("/ledger-lines/{lineId}/unreconcile")
    public ApiResponse<Void> unreconcile(@PathVariable Long lineId) {
        accountService.setReconciled(lineId, false);
        return ApiResponse.ok("Marked unreconciled", null);
    }
}
