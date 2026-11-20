package com.akbal.kips.be.service.user;

import com.akbal.kips.be.domain.user.Role;
import com.akbal.kips.be.domain.user.User;
import com.akbal.kips.be.dto.user.UserDtos.ChangePasswordRequest;
import com.akbal.kips.be.dto.user.UserDtos.ProfileResponse;
import com.akbal.kips.be.dto.user.UserDtos.UpdateProfileRequest;
import com.akbal.kips.be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class UserProfileService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(String username) {
        return view(find(username));
    }

    @Transactional
    public ProfileResponse updateProfile(String username, UpdateProfileRequest request) {
        var user = find(username);
        if (!user.getEmail().equalsIgnoreCase(request.email()) && userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already in use");
        }
        user.setEmail(request.email());
        return view(user);
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        var user = find(username);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
    }

    private User find(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private ProfileResponse view(User user) {
        var roles = user.getRoles().stream().map(Role::getName).sorted().toList();
        return new ProfileResponse(user.getId(), user.getUsername(), user.getEmail(), roles, user.getCreatedAt());
    }
}
