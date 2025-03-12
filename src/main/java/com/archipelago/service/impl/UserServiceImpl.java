package com.archipelago.service.impl;

import com.archipelago.dto.request.UpdateProfileRequest;
import com.archipelago.dto.response.UserProfileResponse;
import com.archipelago.exception.EmailAlreadyExistsException;
import com.archipelago.exception.UserNotFoundException;
import com.archipelago.mapper.UserMapper;
import com.archipelago.model.User;
import com.archipelago.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserProfileResponse getProfile() {
        String email = getCurrentUserEmail();
        User user = userMapper.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
        return new UserProfileResponse(user.getUsername(), user.getEmail(), user.isEnabled());
    }

    @Override
    public void updateProfile(UpdateProfileRequest request) {
        String email = getCurrentUserEmail();
        User user = userMapper.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            if (userMapper.countByUsernameIgnoreCase(request.getUsername()) > 0) {
                throw new EmailAlreadyExistsException("Username already exists: " + request.getUsername());
            }
            user.setUsername(request.getUsername());
        }
        if (request.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        userMapper.updateProfile(email, request.getUsername(), request.getPassword());
    }

    @Override
    public void deleteCurrentUser() {
        String email = getCurrentUserEmail();
        User user = userMapper.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
        user.setDeleted(true);
        userMapper.update(user);
    }

    private String getCurrentUserEmail() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
