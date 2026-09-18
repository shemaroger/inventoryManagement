package com.company.tai.company.dto;

import java.time.Instant;

public record CompanySettingsDto(
        String legalName,
        String tradingName,
        String tinNumber,
        String registrationNumber,
        String addressLine,
        String city,
        String country,
        String phone,
        String email,
        String website,
        String logoUrl,
        Instant updatedAt
) {}
