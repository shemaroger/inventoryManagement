package com.company.tai.accounting.dto;

import com.company.tai.accounting.entity.AccountType;

public record AccountDto(
        Long id,
        String code,
        String name,
        AccountType accountType,
        Long parentAccountId,
        String parentAccountName,
        boolean active
) {}
