package com.company.tai.dashboard.dto;

import java.math.BigDecimal;

// revenue is null when the caller (STAFF) shouldn't see monetary figures — quantitySold stays
// visible since it's operationally useful (restocking) regardless of role.
public record TopProductDto(
        Long productId,
        String productName,
        String productSku,
        BigDecimal quantitySold,
        BigDecimal revenue
) {}
