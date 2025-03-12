package com.archipelago.auth;

import com.archipelago.exception.*;
import com.archipelago.mapper.UserMapper;
import com.archipelago.model.User;
import com.archipelago.model.enums.Role;
import com.archipelago.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;

    @Override
    public User register(String email, String password, String username) {
        logger.debug("Registering user with email: {}", email);

        if (userMapper.countByEmail(email) > 0) {
            logger.warn("Registration failed, email already in use: {}", email);
            throw new EmailAlreadyExistsException("email used already: " + email);
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .username(username)
                .role(Role.USER)
                .enabled(true)
                .build();
        logger.debug("Password encoded for user with email: {}", email);

        String token = UUID.randomUUID().toString();
        user.setVerificationToken(token);
        user.setVerified(false);

        userMapper.insert(user);
        logger.info("User saved in DB with email: {}", user.getEmail());
        // verification email
        String verificationLink = "http://localhost:8080/api/auth/verify?token=" + user.getVerificationToken();
        emailService.sendEmail(email, "Verify your account",
                "<h1>Welcome to Archipelago!</h1>" +
                "<p>Click me to verify your account</p>" +
                "<a href=\"" + verificationLink + "\">Verify Account</a>");
        System.out.println("Verification token: " + token);
        return user;
    }

    @Override
    public User authenticate(String email, String password) {
        logger.debug("Authenticating user with email: {}", email);

        User user = userMapper.findByEmail(email)
                .orElseThrow(() -> {
                    logger.error("No user found with email: {}", email);
                    return new InvalidCredentialsException("Invalid email or password");
                });
        if (user.getLockoutTime() != null && user.getLockoutTime().isAfter(LocalDateTime.now())) {
            logger.warn("Too many login attempts for email: {}", email);
            throw new TooManyLoginAttemptsException("User locked until:" + user.getLockoutTime());
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);

            if (user.getFailedLoginAttempts() >= 5) {
                user.setLockoutTime(LocalDateTime.now().plusMinutes(10));
                userMapper.update(user);
                logger.warn("User locked until: {}", user.getLockoutTime());
                throw new TooManyLoginAttemptsException("Account locked for 10 minutes");
            }
            userMapper.update(user);
            logger.warn("Invalid password or email for email: {}", email);
            throw new InvalidCredentialsException("Invalid email or password");
        }
        if (!user.isVerified()) {
            logger.warn("User is not verified with email: {}", email);
            throw new InvalidCredentialsException("Account unverified. Check email.");
        }
        // successful login and reset
        user.setFailedLoginAttempts(0);
        user.setLockoutTime(null);
        userMapper.update(user);
        logger.info("User authenticated with email: {}", user.getEmail());

        return user;
    }

    @Override
    public void logout(String token) {
        logger.debug("Logout user attempt with token: {}", token);
        jwtUtil.invalidateToken(token);
        logger.info("User logged out with token: {}", token);
    }

    @Override
    public String refreshToken(String oldToken) {
        logger.debug("Refreshing token: {}", oldToken);
        if (!jwtUtil.validateToken(oldToken)) {
            logger.warn("Token invalid or expired: {}", oldToken);
            throw new InvalidTokenException("Token is invalid or expired");
        }

        String email = jwtUtil.getEmailFromToken(oldToken);
        logger.debug("Retrieved email from old token: {}", email);
        User user = userMapper.findByEmail(email)
                .orElseThrow(() ->
                {
                    logger.error("No user found with email: {}", email);
                    return new UserNotFoundException("User not found for email: " + email);
                });

        String newToken = jwtUtil.generateToken(user);
        logger.info("Generated new token for user: {}", email);
        return newToken;
    }

    @Override
    public void handleForgotPassword(String email) {
        logger.info("Handle forgot password attempt for email {}", email);
        User user = userMapper.findByEmail(email)
                .orElseThrow(() ->
                {
                    logger.error("No user found with email: {}", email);
                    return new UserNotFoundException("User not found for email: " + email);
                });

        String token = UUID.randomUUID().toString();
        logger.info("New token generated for user: {}", email);
        user.setPasswordResetToken(token);
        user.setPasswordResetTokenExpireTime(LocalDateTime.now().plusHours(1));
        logger.info("New password reset token generated for user: {}", email);
        userMapper.update(user);
        logger.info("Handle forgot password success for user: {}", email);

        String resetLink = "http://localhost:8080/api/auth/reset-password?token=" + token;
        emailService.sendEmail(email, "Reset Your Password",
                "<h1>Reset Password Request</h1>" +
                        "<p>Click the link below to reset your password:</p>" +
                        "<a href=\"" + resetLink + "\">Reset Password</a>");
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        logger.info("Reset password attempt for user: {}", token);
        User user = userMapper.findByPasswordResetToken(token)
                .orElseThrow(() ->
                {
                    logger.error("No user found with token: {}", token);
                    return new InvalidTokenException("Invalid or expired reset token");
                });

        if (user.getPasswordResetTokenExpireTime().isBefore(LocalDateTime.now())) {
            logger.error("Password reset token expired");
            throw new InvalidTokenException("Reset token expired");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpireTime(null);
        userMapper.update(user);
        logger.info("Password reset success");
    }
}
