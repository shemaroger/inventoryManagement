package com.company.tai.common.controller;

import com.company.tai.common.dto.ApiResponse;
import com.company.tai.common.dto.AuditLogDto;
import com.company.tai.common.service.AuditLogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;

// ADMIN-only: this is the system-wide activity trail (who did what, when, from where), not
// something any other role should be able to browse.
@RestController
@RequestMapping("/api/audit-log")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "action", "targetType", "actorId");

    private final AuditLogQueryService auditLogQueryService;

    @GetMapping
    public ApiResponse<Page<AuditLogDto>> search(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        String safeSortBy = SORTABLE_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort sort = Sort.by("desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC, safeSortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        return ApiResponse.ok(auditLogQueryService.search(actorId, action, targetType, from, to, search, pageable));
    }

    @GetMapping("/target-types")
    public ApiResponse<List<String>> targetTypes() {
        return ApiResponse.ok(auditLogQueryService.listTargetTypes());
    }

    @GetMapping("/actions")
    public ApiResponse<List<String>> actions() {
        return ApiResponse.ok(auditLogQueryService.listActions());
    }
}
