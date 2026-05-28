package com.archipelago.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters long")
    private String username;

    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters long")
    private String password;
}
