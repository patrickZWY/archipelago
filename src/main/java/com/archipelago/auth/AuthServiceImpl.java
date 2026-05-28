package com.archipelago.auth;

import com.archipelago.dto.request.LoginRequest;
import com.archipelago.dto.request.RegisterRequest;
import com.archipelago.exception.EmailAlreadyExistsException;
import com.archipelago.exception.InvalidCredentialsException;
import com.archipelago.exception.InvalidTokenException;
import com.archipelago.exception.TooManyLoginAttemptsException;
import com.archipelago.exception.UserNotFoundException;
import com.archipelago.mapper.UserMapper;
import com.archipelago.model.User;
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

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final CurrentUserProvider currentUserProvider;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Value("${app.auth.require-email-verification:false}")
    private boolean requireEmailVerification;

    @Override
    public User register(RegisterRequest request) {
        if (userMapper.countByEmail(request.getEmail()) > 0) {
            throw new EmailAlreadyExistsException("Email already exists");
        }
        if (userMapper.countByUsernameIgnoreCase(request.getUsername()) > 0) {
            throw new EmailAlreadyExistsException("Username already exists");
        }

        String verificationToken = requireEmailVerification ? UUID.randomUUID().toString() : null;
        User user = User.builder()
                .email(request.getEmail().trim().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .username(request.getUsername().trim())
                .role(Role.USER)
                .enabled(true)
                .verified(!requireEmailVerification)
                .verificationToken(verificationToken)
                .failedLoginAttempts(0)
                .deleted(false)
                .build();
        userMapper.insert(user);

        if (requireEmailVerification) {
            emailService.sendEmail(
                    user.getEmail(),
                    "Verify your Archipelago account",
                    "<p>Open this link to verify your account:</p><p><a href=\"" +
                            frontendBaseUrl + "/verify?token=" + verificationToken + "\">Verify account</a></p>"
            );
        }
        return user;
    }

    @Override
    public User authenticate(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userMapper.findActiveByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (user.getLockoutTime() != null && user.getLockoutTime().isAfter(LocalDateTime.now())) {
            throw new TooManyLoginAttemptsException("Account locked until " + user.getLockoutTime());
        }
        if (!user.isVerified()) {
            throw new InvalidCredentialsException("Account verification is still required");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            int failedAttempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(failedAttempts);
            if (failedAttempts >= 5) {
                user.setLockoutTime(LocalDateTime.now().plusMinutes(10));
            }
            userMapper.update(user);
            if (failedAttempts >= 5) {
                throw new TooManyLoginAttemptsException("Too many failed login attempts");
            }
            throw new InvalidCredentialsException("Invalid email or password");
        }

        user.setFailedLoginAttempts(0);
        user.setLockoutTime(null);
        userMapper.update(user);
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
    public void verifyAccount(String token) {
        User user = userMapper.findByVerificationToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid verification token"));
        user.setVerified(true);
        user.setVerificationToken(null);
        userMapper.update(user);
    }

    @Override
    public void handleForgotPassword(String email) {
        userMapper.findActiveByEmail(email.trim().toLowerCase()).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            user.setPasswordResetToken(token);
            user.setPasswordResetTokenExpireTime(LocalDateTime.now().plusHours(1));
            userMapper.update(user);
            emailService.sendEmail(
                    user.getEmail(),
                    "Reset your Archipelago password",
                    "<p>Open this link to reset your password:</p><p><a href=\"" +
                            frontendBaseUrl + "/reset-password?token=" + token + "\">Reset password</a></p>"
            );
        });
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        if (!StringUtils.hasText(token)) {
            throw new InvalidTokenException("Missing reset token");
        }
        User user = userMapper.findByPasswordResetToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired reset token"));
        if (user.getPasswordResetTokenExpireTime() == null ||
                user.getPasswordResetTokenExpireTime().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Invalid or expired reset token");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpireTime(null);
        user.setFailedLoginAttempts(0);
        user.setLockoutTime(null);
        userMapper.update(user);
    }
}
