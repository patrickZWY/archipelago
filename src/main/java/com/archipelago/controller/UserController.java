package com.archipelago.controller;

import com.archipelago.dto.request.UpdateProfileRequest;
import com.archipelago.dto.response.UserProfileResponse;
import com.archipelago.service.UserService;
import com.archipelago.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile() {
        UserProfileResponse profile = userService.getProfile();
        return ResponseEntity.ok(new ApiResponse<>(true, profile, "Profile retrieved successfully"));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<Void>> updateProfile(@RequestBody UpdateProfileRequest request) {
        userService.updateProfile(request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated success"));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteAccount() {
        userService.deleteCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("Account deletion success"));
    }
}
