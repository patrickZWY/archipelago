package com.archipelago.auth;

import com.archipelago.dto.request.ForgotPasswordRequest;
import com.archipelago.dto.request.LoginRequest;
import com.archipelago.dto.request.RegisterRequest;
import com.archipelago.dto.request.ResetPasswordRequest;
import com.archipelago.dto.response.SessionResponse;
import com.archipelago.dto.response.UserSummaryResponse;
import com.archipelago.exception.InvalidCredentialsException;
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
        authService.register(request, httpServletRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(SessionResponse.anonymous(), "Check your email to finish signup"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<SessionResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest
    ) {
        User user = authService.authenticate(request, httpServletRequest);
        authService.startSession(user, httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(SessionResponse.authenticated(user), "Login successful"));
    }

    @PostMapping("/demo")
    public ResponseEntity<ApiResponse<SessionResponse>> demo(HttpServletRequest httpServletRequest) {
        User user = authService.authenticateDemoUser(httpServletRequest);
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
        try {
            User user = authService.getSessionUser();
            return ResponseEntity.ok(ApiResponse.success(
                    new SessionResponse(true, UserSummaryResponse.from(user)),
                    "Session loaded"
            ));
        } catch (InvalidCredentialsException exception) {
            return ResponseEntity.ok(ApiResponse.success(SessionResponse.anonymous(), "Session loaded"));
        }
    }

    @GetMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verify(@RequestParam String token, HttpServletRequest request) {
        authService.verifyAccount(token, request);
        return ResponseEntity.ok(ApiResponse.success("Account verified"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                            HttpServletRequest httpServletRequest) {
        authService.handleForgotPassword(request.getEmail(), httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success("If the account exists, a reset link has been issued"));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@Valid @RequestBody ForgotPasswordRequest request,
                                                                HttpServletRequest httpServletRequest) {
        authService.resendVerification(request.getEmail(), httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success("If the account is pending, a verification link has been issued"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                                           HttpServletRequest httpServletRequest) {
        authService.resetPassword(request.getToken(), request.getNewPassword(), httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success("Password updated"));
    }
}
