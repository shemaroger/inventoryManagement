package com.company.tai.anomaly.dto;

import jakarta.validation.constraints.Pattern;

public record AnomalyReviewRequest(
        @Pattern(regexp = "REVIEWED|DISMISSED") String status,
        String reviewNote
) {}
