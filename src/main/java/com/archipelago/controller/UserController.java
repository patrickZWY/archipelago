package com.archipelago.controller;

import com.archipelago.dto.request.UpdateProfileRequest;
import com.archipelago.dto.response.PublicUserResponse;
import com.archipelago.dto.response.UserProfileResponse;
import com.archipelago.service.UserService;
import com.archipelago.util.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile() {
        return ResponseEntity.ok(ApiResponse.success(userService.getProfile(), "Profile retrieved"));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<PublicUserResponse>>> searchUsers(@RequestParam("q") String query) {
        return ResponseEntity.ok(ApiResponse.success(userService.searchUsers(query), "Users retrieved"));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<Void>> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        userService.updateProfile(request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated"));
    }

    @DeleteMapping("/profile")
    public ResponseEntity<ApiResponse<Void>> deleteAccount() {
        userService.deleteCurrentUser();
        return ResponseEntity.ok(ApiResponse.success("Account deleted"));
    }
}
