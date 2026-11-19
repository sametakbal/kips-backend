package com.akbal.kips.be.service.auth;

import org.springframework.security.core.userdetails.UserDetails;

public interface JwtService {
    String accessSubject(String token);
    String refreshSubject(String token);
    String generateToken(UserDetails user);
    String generateRefreshToken(UserDetails user);
}
