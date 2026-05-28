package com.demo.app.iam.service;

import com.demo.app.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtConfig jwtConfig;

    public String generateAccessToken(UUID userId, UUID roleId, Set<String> permissions, String jti) {
        var now = System.currentTimeMillis();
        var exp = now + jwtConfig.getAccessExpirySeconds() * 1000L;
        return Jwts.builder()
                .id(jti)                                 // jti — required for denylist (AC-12) and session tracking
                .subject(userId.toString())
                .claim("roleId", roleId != null ? roleId.toString() : null)
                .claim("permissions", permissions)
                .issuedAt(new Date(now))
                .expiration(new Date(exp))
                .signWith(jwtConfig.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    public Claims validateAndParse(String token) {
        return Jwts.parser()
                .verifyWith(jwtConfig.getPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
