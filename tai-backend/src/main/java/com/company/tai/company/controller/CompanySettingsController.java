package com.company.tai.company.controller;

import com.company.tai.common.dto.ApiResponse;
import com.company.tai.company.dto.CompanySettingsDto;
import com.company.tai.company.dto.UpdateCompanySettingsRequest;
import com.company.tai.company.service.CompanySettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/company-settings")
@RequiredArgsConstructor
public class CompanySettingsController {

    private final CompanySettingsService companySettingsService;

    // Readable by any authenticated user — invoices, statutory exports, and report headers all
    // need this, not just admins.
    @GetMapping
    public ApiResponse<CompanySettingsDto> get() {
        return ApiResponse.ok(companySettingsService.get());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CompanySettingsDto> update(@Valid @RequestBody UpdateCompanySettingsRequest request) {
        return ApiResponse.ok("Company profile updated", companySettingsService.update(request));
    }
}
