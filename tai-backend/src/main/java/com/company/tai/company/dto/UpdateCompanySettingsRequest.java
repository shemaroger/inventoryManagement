package com.company.tai.company.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCompanySettingsRequest(
        @NotBlank @Size(max = 150) String legalName,
        @Size(max = 150) String tradingName,
        @Size(max = 30) String tinNumber,
        @Size(max = 50) String registrationNumber,
        @Size(max = 200) String addressLine,
        @Size(max = 100) String city,
        @Size(max = 100) String country,
        @Size(max = 30) String phone,
        @Email @Size(max = 150) String email,
        @Size(max = 150) String website,
        @Size(max = 255) String logoUrl
) {}
