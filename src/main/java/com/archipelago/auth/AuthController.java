package com.archipelago.auth;

import com.archipelago.dto.request.ForgotPasswordRequest;
import com.archipelago.dto.request.LoginRequest;
import com.archipelago.dto.request.RegisterRequest;
import com.archipelago.dto.request.ResetPasswordRequest;
import com.archipelago.dto.response.SessionResponse;
import com.archipelago.dto.response.UserSummaryResponse;
import com.archipelago.model.User;
import com.archipelago.util.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<SessionResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpServletRequest
    ) {
        User user = authService.register(request);
        authService.startSession(user, httpServletRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(SessionResponse.authenticated(user), "Registration successful"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<SessionResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest
    ) {
        User user = authService.authenticate(request);
        authService.startSession(user, httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(SessionResponse.authenticated(user), "Login successful"));
    }

    @PostMapping("/demo")
    public ResponseEntity<ApiResponse<SessionResponse>> demo(HttpServletRequest httpServletRequest) {
        User user = authService.authenticateDemoUser();
        authService.startSession(user, httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(SessionResponse.authenticated(user), "Demo session opened"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success("Logout successful"));
    }

    @GetMapping("/session")
    public ResponseEntity<ApiResponse<SessionResponse>> session(Authentication authentication, CsrfToken csrfToken) {
        csrfToken.getToken();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.ok(ApiResponse.success(SessionResponse.anonymous(), "Session loaded"));
        }
        User user = authService.getSessionUser();
        return ResponseEntity.ok(ApiResponse.success(
                new SessionResponse(true, UserSummaryResponse.from(user)),
                "Session loaded"
        ));
    }

    @GetMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verify(@RequestParam String token) {
        authService.verifyAccount(token);
        return ResponseEntity.ok(ApiResponse.success("Account verified"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.handleForgotPassword(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("If the account exists, a reset link has been issued"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success("Password updated"));
    }
}
