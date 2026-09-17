package com.company.tai.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AiQueryExampleRequest(
        @NotBlank String questionText,
        @NotBlank @Pattern(regexp = "PRODUCT_SEARCH|LOW_STOCK_SEARCH") String intent
) {}
