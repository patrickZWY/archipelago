package com.archipelago.auth;

import com.archipelago.dto.request.LoginRequest;
import com.archipelago.dto.request.RegisterRequest;
import com.archipelago.exception.InvalidCredentialsException;
import com.archipelago.exception.InvalidTokenException;
import com.archipelago.exception.TooManyLoginAttemptsException;
import com.archipelago.exception.UserNotFoundException;
import com.archipelago.mapper.UserMapper;
import com.archipelago.model.User;
import com.archipelago.model.enums.AccountStatus;
import com.archipelago.model.enums.Role;
import com.archipelago.security.AuthenticatedUser;
import com.archipelago.security.CurrentUserProvider;
import com.archipelago.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private static final String DEMO_EMAIL = "demo@archipelago.local";
    private static final String GENERIC_LOGIN_FAILURE = "Invalid email or password";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final CurrentUserProvider currentUserProvider;
    private final SecureTokenService secureTokenService;
    private final AuthRateLimiter authRateLimiter;
    private final AuthAuditService authAuditService;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Value("${app.auth.signup-mode:approval}")
    private String signupModeValue;

    @Value("${app.auth.verification-token-ttl:PT24H}")
    private Duration verificationTokenTtl;

    @Value("${app.auth.reset-token-ttl:PT1H}")
    private Duration resetTokenTtl;

    @Override
    public RegistrationResult register(RegisterRequest request, HttpServletRequest httpServletRequest) {
        String email = normalizeEmail(request.getEmail());
        String username = request.getUsername().trim();
        checkRateLimit("register", email, "REGISTER", httpServletRequest);

        SignupMode signupMode = signupMode();
        if (signupMode == SignupMode.INVITE) {
            authAuditService.record("REGISTER", "BLOCKED", null, httpServletRequest);
            return new RegistrationResult(null, false);
        }

        Optional<User> existingUser = userMapper.findActiveByEmail(email);
        if (existingUser.isPresent() || userMapper.countByUsernameIgnoreCase(username) > 0) {
            authAuditService.record("REGISTER", "NEUTRAL", existingUser.orElse(null), httpServletRequest);
            return new RegistrationResult(existingUser.orElse(null), false);
        }

        String verificationToken = secureTokenService.generateToken();
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .username(username)
                .role(Role.USER)
                .enabled(true)
                .verified(false)
                .accountStatus(AccountStatus.PENDING_VERIFICATION)
                .verificationTokenHash(secureTokenService.hashToken(verificationToken))
                .verificationTokenExpireTime(LocalDateTime.now().plus(verificationTokenTtl))
                .failedLoginAttempts(0)
                .deleted(false)
                .build();
        userMapper.insert(user);

        sendVerificationEmail(user, verificationToken);
        authAuditService.record("REGISTER", "SUCCESS", user, httpServletRequest);
        return new RegistrationResult(user, true);
    }

    @Override
    public User authenticate(LoginRequest request, HttpServletRequest httpServletRequest) {
        String email = normalizeEmail(request.getEmail());
        checkRateLimit("login", email, "LOGIN", httpServletRequest);
        User user = userMapper.findActiveByEmail(email)
                .orElseThrow(() -> {
                    authAuditService.record("LOGIN", "FAILURE", null, httpServletRequest);
                    return new InvalidCredentialsException(GENERIC_LOGIN_FAILURE);
                });

        if (user.getLockoutTime() != null && user.getLockoutTime().isAfter(LocalDateTime.now())) {
            authAuditService.record("LOGIN", "FAILURE", user, httpServletRequest);
            throw new TooManyLoginAttemptsException("Too many failed login attempts");
        }
        if (!canAuthenticate(user)) {
            authAuditService.record("LOGIN", "FAILURE", user, httpServletRequest);
            throw new InvalidCredentialsException(GENERIC_LOGIN_FAILURE);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            int failedAttempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(failedAttempts);
            if (failedAttempts >= 5) {
                user.setLockoutTime(LocalDateTime.now().plusMinutes(10));
            }
            userMapper.update(user);
            if (failedAttempts >= 5) {
                authAuditService.record("LOGIN", "FAILURE", user, httpServletRequest);
                throw new TooManyLoginAttemptsException("Too many failed login attempts");
            }
            authAuditService.record("LOGIN", "FAILURE", user, httpServletRequest);
            throw new InvalidCredentialsException(GENERIC_LOGIN_FAILURE);
        }

        user.setFailedLoginAttempts(0);
        user.setLockoutTime(null);
        userMapper.update(user);
        authAuditService.record("LOGIN", "SUCCESS", user, httpServletRequest);
        return user;
    }

    @Override
    public User authenticateDemoUser(HttpServletRequest httpServletRequest) {
        checkRateLimit("demo", DEMO_EMAIL, "DEMO_LOGIN", httpServletRequest);
        User user = userMapper.findActiveByEmail(DEMO_EMAIL)
                .orElseThrow(() -> new UserNotFoundException("Demo account is unavailable"));
        if (!canAuthenticate(user)) {
            authAuditService.record("DEMO_LOGIN", "FAILURE", user, httpServletRequest);
            throw new UserNotFoundException("Demo account is unavailable");
        }
        authAuditService.record("DEMO_LOGIN", "SUCCESS", user, httpServletRequest);
        return user;
    }

    @Override
    public void startSession(User user, HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        AuthenticatedUser principal = AuthenticatedUser.from(user);
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        SecurityContext context = new SecurityContextImpl(authentication);
        SecurityContextHolder.setContext(context);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    @Override
    public void logout(HttpServletRequest request) {
        try {
            authAuditService.record("LOGOUT", "SUCCESS", currentUserProvider.getCurrentUser(), request);
        } catch (RuntimeException exception) {
            authAuditService.record("LOGOUT", "SUCCESS", null, request);
        }
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @Override
    public User getSessionUser() {
        return currentUserProvider.getCurrentUser();
    }

    @Override
    public void verifyAccount(String token, HttpServletRequest httpServletRequest) {
        checkRateLimit("verify", token, "VERIFY", httpServletRequest);
        if (!StringUtils.hasText(token)) {
            authAuditService.record("VERIFY", "FAILURE", null, httpServletRequest);
            throw new InvalidTokenException("Missing verification token");
        }
        User user = userMapper.findByVerificationToken(secureTokenService.hashToken(token))
                .orElseThrow(() -> {
                    authAuditService.record("VERIFY", "FAILURE", null, httpServletRequest);
                    return new InvalidTokenException("Invalid verification token");
                });
        if (user.getVerificationTokenExpireTime() == null ||
                user.getVerificationTokenExpireTime().isBefore(LocalDateTime.now())) {
            user.setVerificationTokenHash(null);
            user.setVerificationTokenExpireTime(null);
            userMapper.update(user);
            authAuditService.record("VERIFY", "FAILURE", user, httpServletRequest);
            throw new InvalidTokenException("Verification token has expired");
        }
        user.setVerified(true);
        user.setVerificationTokenHash(null);
        user.setVerificationTokenExpireTime(null);
        user.setAccountStatus(signupMode() == SignupMode.PUBLIC ? AccountStatus.ACTIVE : AccountStatus.PENDING_APPROVAL);
        userMapper.update(user);
        authAuditService.record("VERIFY", "SUCCESS", user, httpServletRequest);
    }

    @Override
    public void handleForgotPassword(String email, HttpServletRequest httpServletRequest) {
        String normalizedEmail = normalizeEmail(email);
        checkRateLimit("forgot-password", normalizedEmail, "FORGOT_PASSWORD_REQUESTED", httpServletRequest);
        Optional<User> maybeUser = userMapper.findActiveByEmail(normalizedEmail);
        maybeUser.filter(this::canResetPassword).ifPresent(user -> {
            String token = secureTokenService.generateToken();
            user.setPasswordResetTokenHash(secureTokenService.hashToken(token));
            user.setPasswordResetTokenExpireTime(LocalDateTime.now().plus(resetTokenTtl));
            userMapper.update(user);
            emailService.sendEmail(
                    user.getEmail(),
                    "Reset your Archipelago password",
                    "<p>Open this link to reset your password:</p><p><a href=\"" +
                            frontendBaseUrl + "/reset-password?token=" + token + "\">Reset password</a></p>"
            );
        });
        authAuditService.record("FORGOT_PASSWORD_REQUESTED", maybeUser.filter(this::canResetPassword).isPresent() ? "SUCCESS" : "NEUTRAL",
                maybeUser.orElse(null), httpServletRequest);
    }

    @Override
    public void resendVerification(String email, HttpServletRequest httpServletRequest) {
        String normalizedEmail = normalizeEmail(email);
        checkRateLimit("forgot-password", normalizedEmail, "RESEND_VERIFICATION", httpServletRequest);
        Optional<User> maybeUser = userMapper.findActiveByEmail(normalizedEmail);
        maybeUser.filter(user -> user.getAccountStatus() == AccountStatus.PENDING_VERIFICATION)
                .ifPresent(user -> {
                    String token = secureTokenService.generateToken();
                    user.setVerificationTokenHash(secureTokenService.hashToken(token));
                    user.setVerificationTokenExpireTime(LocalDateTime.now().plus(verificationTokenTtl));
                    userMapper.update(user);
                    sendVerificationEmail(user, token);
                });
        authAuditService.record("RESEND_VERIFICATION", maybeUser
                .filter(user -> user.getAccountStatus() == AccountStatus.PENDING_VERIFICATION)
                .isPresent() ? "SUCCESS" : "NEUTRAL", maybeUser.orElse(null), httpServletRequest);
    }

    @Override
    public void resetPassword(String token, String newPassword, HttpServletRequest httpServletRequest) {
        checkRateLimit("reset-password", token, "PASSWORD_RESET", httpServletRequest);
        if (!StringUtils.hasText(token)) {
            authAuditService.record("PASSWORD_RESET", "FAILURE", null, httpServletRequest);
            throw new InvalidTokenException("Missing reset token");
        }
        User user = userMapper.findByPasswordResetToken(secureTokenService.hashToken(token))
                .orElseThrow(() -> {
                    authAuditService.record("PASSWORD_RESET", "FAILURE", null, httpServletRequest);
                    return new InvalidTokenException("Invalid reset token");
                });
        if (user.getPasswordResetTokenExpireTime() == null ||
                user.getPasswordResetTokenExpireTime().isBefore(LocalDateTime.now())) {
            authAuditService.record("PASSWORD_RESET", "FAILURE", user, httpServletRequest);
            throw new InvalidTokenException("Reset token has expired");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetTokenExpireTime(null);
        user.setFailedLoginAttempts(0);
        user.setLockoutTime(null);
        user.setSessionRevokedBefore(LocalDateTime.now());
        userMapper.update(user);
        authAuditService.record("PASSWORD_RESET", "SUCCESS", user, httpServletRequest);
    }

    private boolean canAuthenticate(User user) {
        return user.isEnabled()
                && !user.isDeleted()
                && user.isVerified()
                && user.getAccountStatus() == AccountStatus.ACTIVE;
    }

    private boolean canResetPassword(User user) {
        return canAuthenticate(user);
    }

    private SignupMode signupMode() {
        return SignupMode.fromConfig(signupModeValue);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private void sendVerificationEmail(User user, String verificationToken) {
        emailService.sendEmail(
                user.getEmail(),
                "Verify your Archipelago account",
                "<p>Open this link to verify your account:</p><p><a href=\"" +
                        frontendBaseUrl + "/verify?token=" + verificationToken + "\">Verify account</a></p>"
        );
    }

    private void checkRateLimit(String action, String accountKey, String eventType, HttpServletRequest request) {
        try {
            authRateLimiter.check(action, accountKey, request);
        } catch (TooManyLoginAttemptsException exception) {
            authAuditService.record(eventType, "RATE_LIMITED", null, request);
            throw exception;
        }
    }
}
