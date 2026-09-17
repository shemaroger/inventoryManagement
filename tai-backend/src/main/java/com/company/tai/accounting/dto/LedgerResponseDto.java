package com.company.tai.accounting.dto;

import java.math.BigDecimal;
import java.util.List;

public record LedgerResponseDto(
        Long accountId,
        String accountName,
        BigDecimal openingBalance,
        List<LedgerLineDto> lines,
        BigDecimal closingBalance
) {}
