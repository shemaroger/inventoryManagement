package com.company.tai.common.config;

import com.company.tai.common.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Records every state-changing API call (POST/PUT/PATCH/DELETE) as an audit_log row — actor
 * (resolved from the JWT-authenticated principal, null for anonymous calls like login/register),
 * a semantic action label, target id parsed off the URL when present, response status, and
 * caller IP. Reads (GET) are intentionally not audited: at this system's scale they'd dwarf the
 * log with near-zero investigative value, whereas every create/update/delete/state-transition
 * is captured.
 */
@RequiredArgsConstructor
@Slf4j
public class AuditLoggingInterceptor implements HandlerInterceptor {

    private final AuditService auditService;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        String method = request.getMethod();
        if (!("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method))) {
            return;
        }

        try {
            String path = request.getRequestURI();
            String[] segments = path.replaceFirst("^/api/?", "").split("/");
            String targetType = segments.length > 0 ? segments[0] : null;
            Long targetId = extractTrailingId(segments);
            String action = deriveAction(method, segments);

            String query = request.getQueryString();
            String detail = method + " " + path + (query != null ? "?" + query : "") + " -> " + response.getStatus();
            if (detail.length() > 500) {
                detail = detail.substring(0, 500);
            }

            auditService.log(action, targetType, targetId, detail);
        } catch (Exception loggingError) {
            // An audit-trail bug must never break the actual request it's describing — the
            // response has already been written by this point anyway.
            log.warn("Failed to record audit log entry: {}", loggingError.getMessage());
        }
    }

    // Turns e.g. "PATCH /api/sales/12/confirm" into "CONFIRM" and "POST /api/products" into
    // "CREATE" instead of just the raw HTTP verb, so the log reads as a list of real activities
    // rather than a list of PATCH/POST/DELETE entries a reader has to decode by path.
    private String deriveAction(String method, String[] segments) {
        String last = segments.length > 0 ? segments[segments.length - 1] : "";
        boolean lastSegmentIsVerb = segments.length > 1 && !last.matches("\\d+");
        if (lastSegmentIsVerb) {
            return last.toUpperCase().replace('-', '_');
        }
        return switch (method) {
            case "POST" -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> method;
        };
    }

    private Long extractTrailingId(String[] segments) {
        for (int i = segments.length - 1; i >= 0; i--) {
            if (segments[i].matches("\\d+")) {
                try {
                    return Long.parseLong(segments[i]);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
