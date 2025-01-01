package com.archipelago.auth;

import com.archipelago.exception.EmailAlreadyExistsException;
import com.archipelago.exception.InvalidCredentialsException;
import com.archipelago.exception.InvalidTokenException;
import com.archipelago.exception.UserNotFoundException;
import com.archipelago.model.User;
import com.archipelago.model.enums.Role;
import com.archipelago.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public User register(String email, String password, String username) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("email used already: " + email);
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .username(username)
                .enabled(true)
                .build();

        String token = UUID.randomUUID().toString();
        user.setVerificationToken(token);
        user.setVerified(false);

        User saved = userRepository.save(user);
        // token for debugging, mail service not available yet
        System.out.println("Verification token: " + token);
        return saved;

    }

    public User authenticate(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        if (!user.isVerified()) {
            throw new InvalidCredentialsException("Account unverified. Check email.");
        }
        return user;
    }

    public void logout(String token) {
        jwtUtil.invalidateToken(token);
    }

    public String refreshToken(String oldToken) {
        if (!jwtUtil.validateToken(oldToken)) {
            throw new InvalidTokenException("Token is invalid or expired");
        }

        String email = jwtUtil.getEmailFromToken(oldToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found for email: " + email));

        return jwtUtil.generateToken(user);
    }

    public void handleForgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found for email: " + email));

        String token = UUID.randomUUID().toString();
        user.setPasswordResetToken(token);
        user.setPasswordResetTokenExpireTime(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        // mail service not available yet
    }

    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired reset token"));

        if (user.getPasswordResetTokenExpireTime().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Reset token expired");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpireTime(null);
        userRepository.save(user);
    }

























}
