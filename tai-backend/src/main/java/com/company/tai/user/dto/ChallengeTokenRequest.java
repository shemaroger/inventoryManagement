package com.company.tai.user.dto;

import jakarta.validation.constraints.NotBlank;

public record ChallengeTokenRequest(
        @NotBlank String challengeToken
) {}
