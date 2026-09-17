package com.company.tai.anomaly.controller;

import com.company.tai.anomaly.dto.AnomalyDto;
import com.company.tai.anomaly.dto.AnomalyReviewRequest;
import com.company.tai.anomaly.dto.DetectionRunResultDto;
import com.company.tai.anomaly.entity.AnomalyStatus;
import com.company.tai.anomaly.service.AnomalyDetectionService;
import com.company.tai.anomaly.service.AnomalyReviewService;
import com.company.tai.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// Every endpoint here is ADMIN-only, enforced server-side (not just hidden from the frontend
// nav) — this is sensitive review tooling, not visible to MANAGER or STAFF, and never visible
// to the person whose adjustment was flagged, per this feature's framing requirements.
@RestController
@RequestMapping("/api/anomalies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AnomalyController {

    private final AnomalyReviewService anomalyReviewService;
    private final AnomalyDetectionService anomalyDetectionService;

    @GetMapping
    public ApiResponse<Page<AnomalyDto>> search(
            @RequestParam(required = false) AnomalyStatus status,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(anomalyReviewService.search(status, severity, pageable));
    }

    @PatchMapping("/{id}/review")
    public ApiResponse<AnomalyDto> review(@PathVariable Long id, @Valid @RequestBody AnomalyReviewRequest request) {
        return ApiResponse.ok("Anomaly updated", anomalyReviewService.review(id, request));
    }

    // Manual trigger, in addition to the nightly schedule — useful for admins who want to
    // check outside the nightly cycle, or to verify the feature during setup.
    @PostMapping("/detect")
    public ApiResponse<DetectionRunResultDto> runDetection() {
        return ApiResponse.ok("Detection run complete", anomalyDetectionService.runDetection());
    }
}
