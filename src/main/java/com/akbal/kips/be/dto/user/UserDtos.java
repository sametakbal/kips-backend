package com.akbal.kips.be.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class UserDtos {
    private UserDtos() {
    }

    public record ProfileResponse(Long id, String username, String email, List<String> roles,
                                  LocalDateTime createdAt) {
    }

    public record UpdateProfileRequest(@NotBlank @Email @Size(max = 255) String email) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 6, message = "Password must be at least 6 characters") String newPassword) {
    }
}
