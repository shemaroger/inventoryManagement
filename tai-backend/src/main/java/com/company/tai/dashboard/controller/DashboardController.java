package com.company.tai.dashboard.controller;

import com.company.tai.common.dto.ApiResponse;
import com.company.tai.dashboard.dto.*;
import com.company.tai.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryDto> summary() {
        return ApiResponse.ok(dashboardService.summary());
    }

    // Financial chart — gated server-side, not just hidden in the frontend, same as the rest of
    // the app's revenue-sensitive endpoints (Reports, Accounting).
    @GetMapping("/sales-trend")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<SalesTrendDto> salesTrend(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(dashboardService.salesTrend(days));
    }

    @GetMapping("/top-products")
    public ApiResponse<List<TopProductDto>> topProducts(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "30") int period) {
        return ApiResponse.ok(dashboardService.topProducts(limit, period));
    }

    @GetMapping("/slow-moving")
    public ApiResponse<List<SlowMovingProductDto>> slowMoving(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "30") int period) {
        return ApiResponse.ok(dashboardService.slowMoving(limit, period));
    }

    @GetMapping("/recent-activity")
    public ApiResponse<List<RecentActivityDto>> recentActivity(@RequestParam(defaultValue = "15") int limit) {
        return ApiResponse.ok(dashboardService.recentActivity(limit));
    }
}
