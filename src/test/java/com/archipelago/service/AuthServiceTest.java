package com.archipelago.service;

import com.archipelago.auth.AuthServiceImpl;
import com.archipelago.auth.JwtUtil;
import com.archipelago.exception.EmailAlreadyExistsException;
import com.archipelago.exception.InvalidCredentialsException;
import com.archipelago.exception.InvalidTokenException;
import com.archipelago.mapper.UserMapper;
import com.archipelago.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        lenient().when(passwordEncoder.encode(anyString()))
                .thenAnswer(invocation -> "encoded-" + invocation.getArgument(0));

        lenient().when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(false);

        lenient().when(jwtUtil.validateToken(anyString()))
                .thenReturn(false);

        lenient().when(jwtUtil.generateToken(any(User.class)))
                .thenReturn("mockedToken");

        lenient().when(jwtUtil.getEmailFromToken(anyString()))
                .thenReturn("default@example.com");
    }

    @Test
    void register_shouldThrowEmailAlreadyExistsException() {
        String email = "used@example.com";

        when(userMapper.countByEmail(email) > 0).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () ->
                authService.register(email, "password", "testUser")
        );

        verify(userMapper, never()).update(any(User.class));
    }

    @Test
    void register_shouldCreateUser_whenEmailNotUsed() {
        String email = "new@example.com";
        when(userMapper.countByEmail(email)).thenReturn(0);

        User created = authService.register(email, "somePass", "myUser");
        assertNotNull(created);
        assertEquals(email, created.getEmail());
        assertTrue(created.getPassword().startsWith("encoded-"));
    }

    @Test
    void authenticate_shouldThrowInvalidCredentials_ifUserNotFound() {
        String email = "notfound@example.com";
        when(userMapper.findByEmail(email)).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () ->
                authService.authenticate(email, "anyPass")
        );
    }

    @Test
    void authenticate_shouldThrowInvalidCredentials_ifPasswordMismatch() {
        String email = "test@example.com";

        User existing = new User();
        existing.setEmail(email);
        existing.setPassword("encoded-correctPassword");
        existing.setVerified(true);

        when(userMapper.findByEmail(email)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("wrongPassword", "encoded-correctPassword")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () ->
                authService.authenticate(email, "wrongPassword")
        );
    }

    @Test
    void authenticate_shouldReturnUser_whenCredentialsAreCorrect() {
        String email = "test@example.com";
        String rawPass = "correctPass";

        User existing = new User();
        existing.setEmail(email);
        existing.setPassword("encoded-correctPass");
        existing.setVerified(true);

        when(userMapper.findByEmail(email)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches(rawPass, "encoded-correctPass")).thenReturn(true);

        User authenticated = authService.authenticate(email, rawPass);
        assertNotNull(authenticated);
        assertEquals(email, authenticated.getEmail());
    }

    @Test
    void refreshToken_shouldThrowInvalidTokenException_ifTokenInvalid() {
        String oldToken = "invalidToken";
        when(jwtUtil.validateToken(oldToken)).thenReturn(false);

        assertThrows(InvalidTokenException.class, () ->
                authService.refreshToken(oldToken)
        );
    }

    @Test
    void refreshToken_shouldReturnNewToken_whenTokenIsValid() {
        String oldToken = "validToken";
        String email = "test@example.com";

        when(jwtUtil.validateToken(oldToken)).thenReturn(true);
        when(jwtUtil.getEmailFromToken(oldToken)).thenReturn(email);

        User user = new User();
        user.setEmail(email);
        when(userMapper.findByEmail(email)).thenReturn(Optional.of(user));

        when(jwtUtil.generateToken(user)).thenReturn("newToken456");

        String newToken = authService.refreshToken(oldToken);
        assertEquals("newToken456", newToken);
    }
}

