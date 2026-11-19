package com.akbal.kips.be.service.auth;

import com.akbal.kips.be.dto.auth.request.AuthenticationRequest;
import com.akbal.kips.be.dto.auth.request.RegisterRequest;
import com.akbal.kips.be.dto.auth.response.AuthenticationResponse;

public interface AuthService {
    AuthenticationResponse register(RegisterRequest request);
    AuthenticationResponse authenticate(AuthenticationRequest request);
    AuthenticationResponse refresh(String refreshToken);
}
