package com.akbal.kips.be.service.auth.impl;

import com.akbal.kips.be.domain.user.User;
import com.akbal.kips.be.dto.auth.request.AuthenticationRequest;
import com.akbal.kips.be.dto.auth.request.RegisterRequest;
import com.akbal.kips.be.dto.auth.response.AuthenticationResponse;
import com.akbal.kips.be.repository.RoleRepository;
import com.akbal.kips.be.repository.UserRepository;
import com.akbal.kips.be.service.auth.AuthService;
import com.akbal.kips.be.service.auth.JwtService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Override
    public AuthenticationResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already in use");
        }
        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes");
        }

        var role = roleRepository.findByName("ROLE_USER").orElseThrow();
        var user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(Set.of(role))
                .build();
        var savedUser = userRepository.save(user);
        return tokenResponse(savedUser, jwtService.generateRefreshToken(savedUser));
    }

    @Override
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        var credentials = UsernamePasswordAuthenticationToken.unauthenticated(
                request.getUsername(), request.getPassword());
        var authenticated = authenticationManager.authenticate(credentials);
        var user = (User) authenticated.getPrincipal();
        return tokenResponse(user, jwtService.generateRefreshToken(user));
    }

    @Override
    public AuthenticationResponse refresh(String refreshToken) {
        final String username;
        try {
            username = jwtService.refreshSubject(refreshToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadCredentialsException("Invalid refresh token", ex);
        }
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        return tokenResponse(user, refreshToken);
    }

    private AuthenticationResponse tokenResponse(User user, String refreshToken) {
        return AuthenticationResponse.builder()
                .accessToken(jwtService.generateToken(user))
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .username(user.getUsername())
                .email(user.getEmail())
                .build();
    }
}
