package com.archipelago.auth;

import com.archipelago.exception.EmailAlreadyExistsException;
import com.archipelago.exception.InvalidCredentialsException;
import com.archipelago.exception.InvalidTokenException;
import com.archipelago.exception.UserNotFoundException;
import com.archipelago.model.User;
import com.archipelago.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
        return userRepository.save(user);
    }

    public User authenticate(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
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

}
