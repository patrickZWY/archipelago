package com.archipelago.auth;

import com.archipelago.mapper.AuthAuditEventMapper;
import com.archipelago.model.AuthAuditEvent;
import com.archipelago.model.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class AuthAuditService {
    private static final Logger logger = LoggerFactory.getLogger(AuthAuditService.class);
    private static final String UNKNOWN = "unknown";

    private final AuthAuditEventMapper authAuditEventMapper;
    private final SecureTokenService secureTokenService;

    @Value("${app.auth.audit-hash-salt:archipelago-local-audit}")
    private String auditHashSalt;

    public void record(String eventType, String outcome, User user, HttpServletRequest request) {
        try {
            HttpServletRequest currentRequest = request != null ? request : currentRequest();
            authAuditEventMapper.insert(AuthAuditEvent.builder()
                    .userId(user == null ? null : user.getId())
                    .eventType(eventType)
                    .outcome(outcome)
                    .ipHash(hash(remoteAddress(currentRequest)))
                    .userAgentHash(hash(userAgent(currentRequest)))
                    .build());
        } catch (RuntimeException exception) {
            logger.warn("Failed to write auth audit event {} {}", eventType, outcome, exception);
        }
    }

    public void record(String eventType, String outcome, User user) {
        record(eventType, outcome, user, null);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String remoteAddress(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? UNKNOWN : request.getRemoteAddr();
    }

    private String userAgent(HttpServletRequest request) {
        if (request == null || request.getHeader("User-Agent") == null) {
            return UNKNOWN;
        }
        return request.getHeader("User-Agent");
    }

    private String hash(String value) {
        return secureTokenService.hashToken(auditHashSalt + ":" + value);
    }
}
