package com.archipelago.auth;

import com.archipelago.dto.request.ForgotPasswordRequest;
import com.archipelago.dto.request.LoginRequest;
import com.archipelago.dto.request.RegisterRequest;
import com.archipelago.dto.request.ResetPasswordRequest;
import com.archipelago.dto.response.AuthResponse;
import com.archipelago.exception.UserNotFoundException;
import com.archipelago.model.User;
import com.archipelago.repository.UserRepository;
import com.archipelago.util.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        logger.info("Received registration request for email: {}", request.getEmail());
        User user = authService.register(request.getEmail(), request.getPassword(), request.getUsername());
        String token = jwtUtil.generateToken(user);
        logger.debug("Generated token for user with email: {}", user.getEmail());
        ApiResponse<AuthResponse> response = ApiResponse.success(new AuthResponse(token), "user registration success");
        logger.info("Registration success for email: {}", user.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        logger.info("Login attempt for email: {}", request.getEmail());
        User user = authService.authenticate(request.getEmail(), request.getPassword());
        logger.debug("Generated token for user with email: {}", user.getEmail());
        String token = jwtUtil.generateToken(user);
        ApiResponse<AuthResponse> response = ApiResponse.success(new AuthResponse(token), "login success");
        logger.info("Login success for email: {}", user.getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader("Authorization") String token) {
        logger.info("Logout attempt with token: {}", token);
        authService.logout(token);
        logger.info("Logout successful for token: {}", token);
        return ResponseEntity.ok(ApiResponse.success(null, "logout success"));

    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@RequestHeader("Authorization") String token) {
        logger.info("Token refresh attempt for token: {}", token);
        String newToken = authService.refreshToken(token);
        logger.debug("New token generated after refresh: {}", newToken);
        ApiResponse<AuthResponse> response = ApiResponse.success(new AuthResponse(newToken), "Token refresh success");
        logger.info("Token refresh success");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verify(@RequestParam String token) {
        logger.info("Verify attempt for token: {}", token);
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new UserNotFoundException("Invalid verification token"));
        user.setVerified(true);
        user.setVerificationToken(null);
        userRepository.save(user);
        logger.info("Verify successful for token: {}", token);
        return ResponseEntity.ok(ApiResponse.success("Account verification success"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        logger.info("Forgot password attempt for email: {}", request.getEmail());
        authService.handleForgotPassword(request.getEmail());
        logger.info("Forgot password handled for email: {}", request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("Reset link sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@RequestBody ResetPasswordRequest request) {
        logger.info("Reset password attempt for token: {}", request.getToken());
        authService.resetPassword(request.getToken(), request.getNewPassword());
        logger.info("Reset password success for token: {}", request.getToken());
        return ResponseEntity.ok(ApiResponse.success("Password reset success"));
    }
}
