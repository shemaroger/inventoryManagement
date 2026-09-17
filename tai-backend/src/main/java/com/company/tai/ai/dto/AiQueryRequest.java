package com.company.tai.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record AiQueryRequest(@NotBlank String question) {}
