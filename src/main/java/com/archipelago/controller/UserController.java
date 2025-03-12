package com.archipelago.controller;

import com.archipelago.dto.request.UpdateProfileRequest;
import com.archipelago.dto.response.UserProfileResponse;
import com.archipelago.service.UserService;
import com.archipelago.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final Logger logger = LoggerFactory.getLogger(UserController.class);

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile() {
        logger.info("Get profile");
        UserProfileResponse profile = userService.getProfile();
        logger.info(profile.toString());
        return ResponseEntity.ok(new ApiResponse<>(true, profile, "Profile retrieved successfully"));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<Void>> updateProfile(@RequestBody UpdateProfileRequest request) {
        logger.info("Update profile");
        userService.updateProfile(request);
        logger.info(request.toString());
        return ResponseEntity.ok(ApiResponse.success("Profile updated success"));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<ApiResponse<Void>> deleteAccount() {
        logger.info("Delete account");
        userService.deleteCurrentUser();
        logger.info("Account deleted successfully");
        return ResponseEntity.ok(ApiResponse.success("Account deletion success"));
    }
}
