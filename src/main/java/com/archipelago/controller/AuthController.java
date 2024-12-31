package com.archipelago.controller;

import com.archipelago.auth.AuthService;
import com.archipelago.auth.JwtUtil;
import com.archipelago.dto.request.LoginRequest;
import com.archipelago.dto.request.RegisterRequest;
import com.archipelago.dto.response.AuthResponse;
import com.archipelago.model.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
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
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.getEmail(), request.getPassword(), request.getUsername());
        String token = jwtUtil.generateToken(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(token));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = authService.authenticate(request.getEmail(), request.getPassword());
        String token = jwtUtil.generateToken(user);
        return ResponseEntity.ok(new AuthResponse(token));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String token) {
        authService.logout(token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@RequestHeader("Authorization") String token) {
        String newToken = authService.refreshToken(token);
        return ResponseEntity.ok(new AuthResponse(newToken));
    }




















}
