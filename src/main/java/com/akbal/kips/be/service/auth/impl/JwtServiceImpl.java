package com.akbal.kips.be.service.auth.impl;

import com.akbal.kips.be.service.auth.JwtService;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtServiceImpl implements JwtService {
    private final SecretKey signingKey;
    private final long accessExpiration;
    private final long refreshExpiration;
    private final JwtParser accessParser;
    private final JwtParser refreshParser;

    public JwtServiceImpl(
            @Value("${application.security.jwt.secret-key:}") String secretKey,
            @Value("${application.security.jwt.expiration}") long accessExpiration,
            @Value("${application.security.jwt.refresh-token.expiration}") long refreshExpiration) {
        this.signingKey = secretKey.isBlank()
                ? Jwts.SIG.HS256.key().build()
                : Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
        this.accessParser = Jwts.parser().verifyWith(signingKey).require("token_use", "access").build();
        this.refreshParser = Jwts.parser().verifyWith(signingKey).require("token_use", "refresh").build();
    }

    @Override
    public String accessSubject(String token) {
        return accessParser.parseSignedClaims(token).getPayload().getSubject();
    }

    @Override
    public String refreshSubject(String token) {
        return refreshParser.parseSignedClaims(token).getPayload().getSubject();
    }

    @Override
    public String generateToken(UserDetails user) {
        return buildToken(user, "access", accessExpiration);
    }

    @Override
    public String generateRefreshToken(UserDetails user) {
        return buildToken(user, "refresh", refreshExpiration);
    }

    private String buildToken(UserDetails user, String tokenUse, long expiration) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("token_use", tokenUse)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expiration)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }
}
