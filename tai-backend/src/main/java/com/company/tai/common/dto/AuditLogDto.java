package com.company.tai.common.dto;

import java.time.Instant;

public record AuditLogDto(
        Long id,
        Long actorId,
        String actorName,
        String action,
        String targetType,
        Long targetId,
        String detail,
        String ipAddress,
        Instant createdAt
) {}
