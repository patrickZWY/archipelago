package com.archipelago.service;

import com.archipelago.dto.request.UpdateProfileRequest;
import com.archipelago.dto.response.UserProfileResponse;

public interface UserService {
    UserProfileResponse getProfile();
    void updateProfile(UpdateProfileRequest request);
    void deleteCurrentUser();
}

