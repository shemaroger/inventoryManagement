package com.company.tai.common.service;

import com.company.tai.common.entity.AuditLog;
import com.company.tai.common.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void log(String action, String targetType, Long targetId, String detail) {
        Long actorId = currentUserId();
        String ip = currentIp();

        AuditLog entry = AuditLog.builder()
                .actorId(actorId)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .detail(detail)
                .ipAddress(ip)
                .build();

        auditLogRepository.save(entry);
    }

    private Long currentUserId() {
        // Actor is resolved by email→id lookup in the caller where needed;
        // here we just don't have direct access to UserRepository to avoid a circular
        // dependency, so callers pass the id explicitly via the overload below if known.
        return null;
    }

    private String currentIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }

    public void log(Long actorId, String action, String targetType, Long targetId, String detail) {
        AuditLog entry = AuditLog.builder()
                .actorId(actorId)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .detail(detail)
                .ipAddress(currentIp())
                .build();

        auditLogRepository.save(entry);
    }
}
