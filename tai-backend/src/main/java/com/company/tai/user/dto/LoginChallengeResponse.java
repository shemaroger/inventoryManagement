package com.company.tai.user.dto;

public record LoginChallengeResponse(
        String challengeToken,
        String maskedEmail,
        int expiresInSeconds,
        String debugCode
) {}
