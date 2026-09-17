package com.company.tai.accounting.dto;

import com.company.tai.accounting.entity.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AccountRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull AccountType accountType,
        Long parentAccountId
) {}
