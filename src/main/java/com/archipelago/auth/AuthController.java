package com.archipelago.auth;

import com.archipelago.dto.request.LoginRequest;
import com.archipelago.dto.request.RegisterRequest;
import com.archipelago.dto.response.AuthResponse;
import com.archipelago.model.User;
import com.archipelago.util.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.getEmail(), request.getPassword(), request.getUsername());
        String token = jwtUtil.generateToken(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, new AuthResponse(token), "user registration success"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        User user = authService.authenticate(request.getEmail(), request.getPassword());
        String token = jwtUtil.generateToken(user);
        return ResponseEntity.ok(new ApiResponse<>(true, new AuthResponse(token), "login success"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader("Authorization") String token) {
        authService.logout(token);
        return ResponseEntity.ok(new ApiResponse<>(true, null, "logout success"));

    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@RequestHeader("Authorization") String token) {
        String newToken = authService.refreshToken(token);
        return ResponseEntity.ok(new ApiResponse<>(true, new AuthResponse(newToken), "Token refresh success"));
    }


}
