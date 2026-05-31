package com.archipelago.service;

import com.archipelago.dto.request.UpdateProfileRequest;
import com.archipelago.dto.response.PublicUserResponse;
import com.archipelago.dto.response.UserProfileResponse;

import java.util.List;

public interface UserService {
    UserProfileResponse getProfile();
    List<PublicUserResponse> searchUsers(String query);
    void updateProfile(UpdateProfileRequest request);
    void deleteCurrentUser();
}
