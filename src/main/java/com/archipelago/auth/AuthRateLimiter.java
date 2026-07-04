package com.archipelago.auth;

import com.archipelago.exception.TooManyLoginAttemptsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthRateLimiter {
    private static final String UNKNOWN = "unknown";

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final SecureTokenService secureTokenService;
    private final Duration window;
    private final int loginLimit;
    private final int registerLimit;
    private final int forgotPasswordLimit;
    private final int resetPasswordLimit;
    private final int demoLimit;
    private final int verifyLimit;

    public AuthRateLimiter(
            SecureTokenService secureTokenService,
            @Value("${app.auth.rate-limit.window:PT15M}") Duration window,
            @Value("${app.auth.rate-limit.login:6}") int loginLimit,
            @Value("${app.auth.rate-limit.register:5}") int registerLimit,
            @Value("${app.auth.rate-limit.forgot-password:5}") int forgotPasswordLimit,
            @Value("${app.auth.rate-limit.reset-password:5}") int resetPasswordLimit,
            @Value("${app.auth.rate-limit.demo:30}") int demoLimit,
            @Value("${app.auth.rate-limit.verify:20}") int verifyLimit
    ) {
        this.secureTokenService = secureTokenService;
        this.window = window;
        this.loginLimit = loginLimit;
        this.registerLimit = registerLimit;
        this.forgotPasswordLimit = forgotPasswordLimit;
        this.resetPasswordLimit = resetPasswordLimit;
        this.demoLimit = demoLimit;
        this.verifyLimit = verifyLimit;
    }

    public void check(String action, String accountKey, HttpServletRequest request) {
        cleanup();
        int limit = limitFor(action);
        Instant now = Instant.now();
        checkBucket(action + ":ip:" + hash(remoteAddress(request)), limit, now);
        if (accountKey != null && !accountKey.isBlank()) {
            checkBucket(action + ":account:" + hash(accountKey.trim().toLowerCase(Locale.ROOT)), limit, now);
        }
    }

    private void checkBucket(String key, int limit, Instant now) {
        Bucket bucket = buckets.compute(key, (ignored, existing) -> {
            if (existing == null || existing.windowStart.plus(window).isBefore(now)) {
                return new Bucket(now, 1);
            }
            existing.attempts++;
            return existing;
        });
        if (bucket.attempts > limit) {
            throw new TooManyLoginAttemptsException("Too many attempts. Try again later.");
        }
    }

    private int limitFor(String action) {
        return switch (action) {
            case "login" -> loginLimit;
            case "register" -> registerLimit;
            case "forgot-password" -> forgotPasswordLimit;
            case "reset-password" -> resetPasswordLimit;
            case "demo" -> demoLimit;
            case "verify" -> verifyLimit;
            default -> registerLimit;
        };
    }

    private void cleanup() {
        Instant cutoff = Instant.now().minus(window.multipliedBy(2));
        Iterator<Map.Entry<String, Bucket>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().windowStart.isBefore(cutoff)) {
                iterator.remove();
            }
        }
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

    private String hash(String value) {
        return secureTokenService.hashToken(value);
    }

    private static final class Bucket {
        private final Instant windowStart;
        private int attempts;

        private Bucket(Instant windowStart, int attempts) {
            this.windowStart = windowStart;
            this.attempts = attempts;
        }
    }
}
