package com.archipelago.exception;

import com.archipelago.util.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllUncaughtExceptions(Exception ex) {
        log.error(ex.getMessage(), ex);
        ApiResponse<Void> response = new ApiResponse<>(
                false,
                null,
                "An unexpected error happened: " + ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailAlreadyExistsException(Exception ex) {
        log.error(ex.getMessage(), ex);
        ApiResponse<Void> response = new ApiResponse<>(
                false,
                null,
                "email already exists: " + ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(Exception ex) {
        log.error(ex.getMessage(), ex);
        ApiResponse<Void> response = new ApiResponse<>(
                false,
                null,
                "Illegal state: " + ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidTokenException(Exception ex) {
        log.error(ex.getMessage(), ex);
        ApiResponse<Void> response = new ApiResponse<>(
                false,
                null,
                "Invalid token: " + ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentialsException(Exception ex) {
        log.error(ex.getMessage(), ex);
        ApiResponse<Void> response = new ApiResponse<>(
                false,
                null,
                "Invalid credentials: " + ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(Exception ex) {
        log.error(ex.getMessage(), ex);
        ApiResponse<Void> response = new ApiResponse<>(
                false,
                null,
                "Resource not found: " + ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooManyLoginAttemptsException(Exception ex) {
        log.error(ex.getMessage(), ex);
        ApiResponse<Void> response = new ApiResponse<>(
                false,
                null,
                "Too many login attempts: " + ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFoundException(Exception ex) {
        log.error(ex.getMessage(), ex);
        ApiResponse<Void> response = new ApiResponse<>(
                false,
                null,
                "User not found: " + ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

}
