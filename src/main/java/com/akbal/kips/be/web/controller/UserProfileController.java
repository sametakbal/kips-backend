package com.akbal.kips.be.web.controller;

import com.akbal.kips.be.dto.product.response.ApiResponse;
import com.akbal.kips.be.dto.user.UserDtos.ChangePasswordRequest;
import com.akbal.kips.be.dto.user.UserDtos.ProfileResponse;
import com.akbal.kips.be.dto.user.UserDtos.UpdateProfileRequest;
import com.akbal.kips.be.service.user.UserProfileService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/user")
public class UserProfileController extends BaseController {
    private final UserProfileService service;

    @GetMapping("/profile")
    public ApiResponse<ProfileResponse> getProfile(Principal user) {
        return respond(service.getProfile(user.getName()));
    }

    @PutMapping("/profile")
    public ApiResponse<ProfileResponse> updateProfile(Principal user, @Valid @RequestBody UpdateProfileRequest request) {
        return respond(service.updateProfile(user.getName(), request));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(Principal user, @Valid @RequestBody ChangePasswordRequest request) {
        service.changePassword(user.getName(), request);
        return respond(null);
    }
}
