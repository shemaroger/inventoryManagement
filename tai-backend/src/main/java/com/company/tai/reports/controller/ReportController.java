package com.company.tai.reports.controller;

import com.company.tai.common.dto.ApiResponse;
import com.company.tai.reports.dto.*;
import com.company.tai.reports.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/daily-sales")
    public ApiResponse<DailySalesReportDto> dailySales(@RequestParam(required = false) LocalDate date) {
        return ApiResponse.ok(reportService.dailySales(date != null ? date : LocalDate.now()));
    }

    @GetMapping("/daily-sales/range")
    public ApiResponse<DailySalesRangeReportDto> dailySalesRange(
            @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return ApiResponse.ok(reportService.dailySalesRange(startDate, endDate));
    }

    @GetMapping("/daily-purchases")
    public ApiResponse<DailyPurchaseReportDto> dailyPurchases(@RequestParam(required = false) LocalDate date) {
        return ApiResponse.ok(reportService.dailyPurchases(date != null ? date : LocalDate.now()));
    }

    @GetMapping("/daily-purchases/range")
    public ApiResponse<DailyPurchaseRangeReportDto> dailyPurchasesRange(
            @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return ApiResponse.ok(reportService.dailyPurchasesRange(startDate, endDate));
    }

    @GetMapping("/vat")
    public ApiResponse<VatReportDto> vatReport(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();
        LocalDate effectiveStart = startDate != null ? startDate : effectiveEnd.withDayOfMonth(1);
        return ApiResponse.ok(reportService.vatReport(effectiveStart, effectiveEnd));
    }

    @GetMapping("/vat/export")
    public ResponseEntity<byte[]> exportVatReport(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();
        LocalDate effectiveStart = startDate != null ? startDate : effectiveEnd.withDayOfMonth(1);
        VatReportDto report = reportService.vatReport(effectiveStart, effectiveEnd);

        StringBuilder csv = new StringBuilder();
        csv.append("Period,Output VAT (on sales),Input VAT (on purchases),Net VAT Payable\n");
        csv.append(report.startDate()).append(" to ").append(report.endDate()).append(',')
                .append(report.outputVat()).append(',')
                .append(report.inputVat()).append(',')
                .append(report.netVatPayable()).append('\n');

        return csvResponse(csv.toString(), "vat-report-" + effectiveStart + "-to-" + effectiveEnd + ".csv");
    }

    @GetMapping("/profit-loss")
    public ApiResponse<ProfitLossReportDto> profitAndLoss(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();
        LocalDate effectiveStart = startDate != null ? startDate : effectiveEnd.withDayOfMonth(1);
        return ApiResponse.ok(reportService.profitAndLoss(effectiveStart, effectiveEnd));
    }

    @GetMapping("/profit-loss/export")
    public ResponseEntity<byte[]> exportProfitAndLoss(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();
        LocalDate effectiveStart = startDate != null ? startDate : effectiveEnd.withDayOfMonth(1);
        ProfitLossReportDto report = reportService.profitAndLoss(effectiveStart, effectiveEnd);

        StringBuilder csv = new StringBuilder();
        csv.append("Section,Account Code,Account Name,Amount\n");
        for (FinancialLineDto l : report.revenue()) appendFinancialRow(csv, "Revenue", l);
        csv.append(",,Total Revenue,").append(report.totalRevenue()).append('\n');
        for (FinancialLineDto l : report.expenses()) appendFinancialRow(csv, "Expense", l);
        csv.append(",,Total Expenses,").append(report.totalExpenses()).append('\n');
        csv.append(",,Net Income,").append(report.netIncome()).append('\n');

        return csvResponse(csv.toString(), "profit-loss-" + effectiveStart + "-to-" + effectiveEnd + ".csv");
    }

    @GetMapping("/balance-sheet")
    public ApiResponse<BalanceSheetReportDto> balanceSheet(@RequestParam(required = false) LocalDate asOfDate) {
        return ApiResponse.ok(reportService.balanceSheet(asOfDate != null ? asOfDate : LocalDate.now()));
    }

    @GetMapping("/balance-sheet/export")
    public ResponseEntity<byte[]> exportBalanceSheet(@RequestParam(required = false) LocalDate asOfDate) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        BalanceSheetReportDto report = reportService.balanceSheet(effectiveDate);

        StringBuilder csv = new StringBuilder();
        csv.append("Section,Account Code,Account Name,Amount\n");
        for (FinancialLineDto l : report.assets()) appendFinancialRow(csv, "Asset", l);
        csv.append(",,Total Assets,").append(report.totalAssets()).append('\n');
        for (FinancialLineDto l : report.liabilities()) appendFinancialRow(csv, "Liability", l);
        csv.append(",,Total Liabilities,").append(report.totalLiabilities()).append('\n');
        for (FinancialLineDto l : report.equity()) appendFinancialRow(csv, "Equity", l);
        csv.append(",,Current Earnings,").append(report.currentEarnings()).append('\n');
        csv.append(",,Total Equity,").append(report.totalEquity()).append('\n');

        return csvResponse(csv.toString(), "balance-sheet-" + effectiveDate + ".csv");
    }

    private void appendFinancialRow(StringBuilder csv, String section, FinancialLineDto line) {
        csv.append(section).append(',')
                .append(line.accountCode()).append(',')
                .append(csvEscape(line.accountName())).append(',')
                .append(line.amount()).append('\n');
    }

    @GetMapping("/daily-sales/export")
    public ResponseEntity<byte[]> exportDailySales(@RequestParam(required = false) LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        DailySalesReportDto report = reportService.dailySales(effectiveDate);

        StringBuilder csv = new StringBuilder();
        csv.append("Date,Transaction Reference,Customer,Cash/Credit,Amount\n");
        for (DailySalesEntryDto e : report.cashSales()) appendSalesRow(csv, e);
        for (DailySalesEntryDto e : report.creditSales()) appendSalesRow(csv, e);
        csv.append(",,,Cash Total,").append(report.cashTotal()).append('\n');
        csv.append(",,,Credit Total,").append(report.creditTotal()).append('\n');
        csv.append(",,,Grand Total,").append(report.grandTotal()).append('\n');

        return csvResponse(csv.toString(), "daily-sales-" + effectiveDate + ".csv");
    }

    @GetMapping("/daily-purchases/export")
    public ResponseEntity<byte[]> exportDailyPurchases(@RequestParam(required = false) LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        DailyPurchaseReportDto report = reportService.dailyPurchases(effectiveDate);

        StringBuilder csv = new StringBuilder();
        csv.append("Date,Transaction Reference,Supplier,Cash/Credit,Amount\n");
        for (DailyPurchaseEntryDto e : report.cashPurchases()) appendPurchaseRow(csv, e);
        for (DailyPurchaseEntryDto e : report.creditPurchases()) appendPurchaseRow(csv, e);
        csv.append(",,,Cash Total,").append(report.cashTotal()).append('\n');
        csv.append(",,,Credit Total,").append(report.creditTotal()).append('\n');
        csv.append(",,,Grand Total,").append(report.grandTotal()).append('\n');

        return csvResponse(csv.toString(), "daily-purchases-" + effectiveDate + ".csv");
    }

    private void appendSalesRow(StringBuilder csv, DailySalesEntryDto e) {
        csv.append(e.saleDate()).append(',')
                .append("Sale #").append(e.saleId()).append(',')
                .append(csvEscape(e.customerName())).append(',')
                .append(e.paymentType()).append(',')
                .append(e.amount()).append('\n');
    }

    private void appendPurchaseRow(StringBuilder csv, DailyPurchaseEntryDto e) {
        csv.append(e.receivedDate()).append(',')
                .append("PO #").append(e.purchaseOrderId()).append(',')
                .append(csvEscape(e.supplierName())).append(',')
                .append(e.paymentType()).append(',')
                .append(e.amountReceived()).append('\n');
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
