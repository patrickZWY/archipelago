package com.archipelago.service.impl;

import com.archipelago.dto.request.UpdateProfileRequest;
import com.archipelago.dto.response.PublicUserResponse;
import com.archipelago.dto.response.UserProfileResponse;
import com.archipelago.exception.EmailAlreadyExistsException;
import com.archipelago.mapper.UserMapper;
import com.archipelago.model.User;
import com.archipelago.security.CurrentUserProvider;
import com.archipelago.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public UserProfileResponse getProfile() {
        return UserProfileResponse.from(currentUserProvider.getCurrentUser());
    }

    @Override
    public List<PublicUserResponse> searchUsers(String query) {
        User currentUser = currentUserProvider.getCurrentUser();
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        return userMapper.searchByUsername(query.trim(), currentUser.getId(), 15).stream()
                .map(PublicUserResponse::from)
                .toList();
    }

    @Override
    public void updateProfile(UpdateProfileRequest request) {
        User user = currentUserProvider.getCurrentUser();
        if (StringUtils.hasText(request.getUsername())
                && !request.getUsername().trim().equalsIgnoreCase(user.getUsername())
                && userMapper.countByUsernameIgnoreCaseExcludingId(request.getUsername().trim(), user.getId()) > 0) {
            throw new EmailAlreadyExistsException("Username already exists");
        }

        String username = StringUtils.hasText(request.getUsername()) ? request.getUsername().trim() : user.getUsername();
        String password = StringUtils.hasText(request.getPassword())
                ? passwordEncoder.encode(request.getPassword())
                : user.getPassword();

        userMapper.updateProfile(user.getId(), username, password);
    }

    @Override
    public void deleteCurrentUser() {
        User user = currentUserProvider.getCurrentUser();
        userMapper.softDeleteById(user.getId());
    }
}
