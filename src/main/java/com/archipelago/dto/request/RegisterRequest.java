package com.archipelago.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {
    @NotBlank(message = "Email required")
    @Email(message = "Invalid email format")
    private String email;

    @Getter
    @NotBlank(message = "Password required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    private String password;

    @NotBlank(message = "Username required")
    @Size(max = 50, message = "Username must not exceed 50 characters")
    private String username;

    public String getEmail() {
        return this.email;
    }

}
