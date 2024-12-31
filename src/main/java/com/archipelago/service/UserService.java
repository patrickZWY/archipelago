package com.archipelago.service;

import com.archipelago.dto.request.UpdateProfileRequest;
import com.archipelago.dto.response.UserProfileResponse;
import com.archipelago.exception.EmailAlreadyExistsException;
import com.archipelago.exception.UserNotFoundException;
import com.archipelago.model.User;
import com.archipelago.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserProfileResponse getProfile() {
        String email = getCurrentUserEmail();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
        return new UserProfileResponse(user.getUsername(), user.getEmail(), user.isEnabled());
    }

    public void updateProfile(UpdateProfileRequest request) {
        String email = getCurrentUserEmail();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsernameIgnoreCase(request.getUsername())) {
                throw new EmailAlreadyExistsException("Username already exists: " + request.getUsername());
            }
            user.setUsername(request.getUsername());
        }
        if (request.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        userRepository.save(user);
    }

    private String getCurrentUserEmail() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
