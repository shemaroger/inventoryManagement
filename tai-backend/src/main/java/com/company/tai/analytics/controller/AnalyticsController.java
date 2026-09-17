package com.company.tai.analytics.controller;

import com.company.tai.analytics.dto.*;
import com.company.tai.analytics.service.AnalyticsService;
import com.company.tai.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/sales")
    public ApiResponse<SalesAnalyticsReportDto> sales(
            @RequestParam LocalDate startDate, @RequestParam LocalDate endDate,
            @RequestParam(defaultValue = "day") String groupBy) {
        return ApiResponse.ok(analyticsService.salesAnalysis(startDate, endDate, groupBy));
    }

    @GetMapping("/sales/export")
    public ResponseEntity<byte[]> exportSales(
            @RequestParam LocalDate startDate, @RequestParam LocalDate endDate,
            @RequestParam(defaultValue = "day") String groupBy) {
        SalesAnalyticsReportDto report = analyticsService.salesAnalysis(startDate, endDate, groupBy);
        StringBuilder csv = new StringBuilder("Period,Sales Count,Total\n");
        report.points().forEach(p -> csv.append(csvEscape(p.bucketLabel())).append(',').append(p.saleCount()).append(',').append(p.total()).append('\n'));
        csv.append(",,").append(report.grandTotal()).append('\n');
        return csvResponse(csv.toString(), "sales-analysis-" + startDate + "-to-" + endDate + ".csv");
    }

    @GetMapping("/profit-by-product")
    public ApiResponse<ProfitByProductReportDto> profitByProduct(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return ApiResponse.ok(analyticsService.profitByProduct(startDate, endDate));
    }

    @GetMapping("/profit-by-product/export")
    public ResponseEntity<byte[]> exportProfitByProduct(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        ProfitByProductReportDto report = analyticsService.profitByProduct(startDate, endDate);
        StringBuilder csv = new StringBuilder("Product,SKU,Category,Quantity Sold,Revenue,Cost (approx.),Margin,Margin %\n");
        report.lines().forEach(l -> csv.append(csvEscape(l.productName())).append(',').append(l.productSku()).append(',')
                .append(csvEscape(l.categoryName())).append(',').append(l.quantitySold()).append(',').append(l.revenue()).append(',')
                .append(l.cost()).append(',').append(l.margin()).append(',').append(l.marginPercent()).append('\n'));
        csv.append(",,,,").append(report.totalRevenue()).append(',').append(report.totalCost()).append(',').append(report.totalMargin()).append(",\n");
        return csvResponse(csv.toString(), "profit-by-product-" + startDate + "-to-" + endDate + ".csv");
    }

    @GetMapping("/profit-by-category")
    public ApiResponse<ProfitByCategoryReportDto> profitByCategory(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return ApiResponse.ok(analyticsService.profitByCategory(startDate, endDate));
    }

    @GetMapping("/profit-by-category/export")
    public ResponseEntity<byte[]> exportProfitByCategory(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        ProfitByCategoryReportDto report = analyticsService.profitByCategory(startDate, endDate);
        StringBuilder csv = new StringBuilder("Category,Revenue,Cost (approx.),Margin,Margin %\n");
        report.lines().forEach(l -> csv.append(csvEscape(l.categoryName())).append(',').append(l.revenue()).append(',')
                .append(l.cost()).append(',').append(l.margin()).append(',').append(l.marginPercent()).append('\n'));
        return csvResponse(csv.toString(), "profit-by-category-" + startDate + "-to-" + endDate + ".csv");
    }

    @GetMapping("/inventory-turnover")
    public ApiResponse<InventoryTurnoverReportDto> inventoryTurnover(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return ApiResponse.ok(analyticsService.inventoryTurnover(startDate, endDate));
    }

    @GetMapping("/inventory-turnover/export")
    public ResponseEntity<byte[]> exportInventoryTurnover(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        InventoryTurnoverReportDto report = analyticsService.inventoryTurnover(startDate, endDate);
        StringBuilder csv = new StringBuilder("Product,SKU,Units Sold,Average Stock,Turnover Ratio,Days of Stock on Hand\n");
        report.lines().forEach(l -> csv.append(csvEscape(l.productName())).append(',').append(l.productSku()).append(',')
                .append(l.unitsSold()).append(',').append(l.averageStock()).append(',')
                .append(l.turnoverRatio() != null ? l.turnoverRatio() : "").append(',')
                .append(l.daysOfStockOnHand() != null ? l.daysOfStockOnHand() : "").append('\n'));
        return csvResponse(csv.toString(), "inventory-turnover-" + startDate + "-to-" + endDate + ".csv");
    }

    @GetMapping("/customer-performance")
    public ApiResponse<CustomerPerformanceReportDto> customerPerformance(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return ApiResponse.ok(analyticsService.customerPerformance(startDate, endDate));
    }

    @GetMapping("/customer-performance/export")
    public ResponseEntity<byte[]> exportCustomerPerformance(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        CustomerPerformanceReportDto report = analyticsService.customerPerformance(startDate, endDate);
        StringBuilder csv = new StringBuilder("Customer,Revenue,Sale Count,Avg Payment Delay (days)\n");
        report.lines().forEach(l -> csv.append(csvEscape(l.customerName())).append(',').append(l.totalRevenue()).append(',')
                .append(l.saleCount()).append(',').append(l.averagePaymentDelayDays() != null ? l.averagePaymentDelayDays() : "").append('\n'));
        return csvResponse(csv.toString(), "customer-performance-" + startDate + "-to-" + endDate + ".csv");
    }

    @GetMapping("/supplier-performance")
    public ApiResponse<SupplierPerformanceReportDto> supplierPerformance(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return ApiResponse.ok(analyticsService.supplierPerformance(startDate, endDate));
    }

    @GetMapping("/supplier-performance/export")
    public ResponseEntity<byte[]> exportSupplierPerformance(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        SupplierPerformanceReportDto report = analyticsService.supplierPerformance(startDate, endDate);
        StringBuilder csv = new StringBuilder("Supplier,Total Spend,PO Count,Avg Lead Time (days, approx.)\n");
        report.lines().forEach(l -> csv.append(csvEscape(l.supplierName())).append(',').append(l.totalSpend()).append(',')
                .append(l.purchaseOrderCount()).append(',').append(l.averageLeadTimeDays() != null ? l.averageLeadTimeDays() : "").append('\n'));
        return csvResponse(csv.toString(), "supplier-performance-" + startDate + "-to-" + endDate + ".csv");
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private ResponseEntity<byte[]> csvResponse(String csv, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
