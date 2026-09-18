package com.company.tai.accounting.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// Automatically closes the previous calendar month's Revenue/Expense activity into Retained
// Earnings — the "automatic accounting book recording" for period closes, same role as
// AnomalyDetectionService's nightly job and ReorderEvaluationService's periodic run.
@Component
@RequiredArgsConstructor
public class PeriodClosingScheduler {

    private static final Logger log = LoggerFactory.getLogger(PeriodClosingScheduler.class);

    private final JournalService journalService;

    // Runs at 03:00 on the 1st of each month, closing the month that just ended. Deliberately
    // after AnomalyDetectionService's 02:00 run so a full night's adjustments are captured
    // first, and well clear of business hours in Africa/Kigali.
    @Scheduled(cron = "0 0 3 1 * *")
    public void closePreviousMonth() {
        LocalDate firstOfThisMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate startDate = firstOfThisMonth.minusMonths(1);
        LocalDate endDate = firstOfThisMonth.minusDays(1);

        try {
            var entry = journalService.closePeriod(startDate, endDate);
            log.info("Closed accounting period {} to {}: {}", startDate, endDate, entry.description());
        } catch (Exception e) {
            // Already closed, or nothing to close (e.g. no Sales/Expense activity that month)
            // are both expected outcomes, not failures — log at info/debug rather than raising
            // an alert for what is very often a no-op month.
            log.info("Skipped closing period {} to {}: {}", startDate, endDate, e.getMessage());
        }
    }
}
