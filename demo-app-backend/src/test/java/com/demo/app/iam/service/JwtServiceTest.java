package com.demo.app.iam.service;

import com.demo.app.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    static JwtService jwtService;
    static JwtConfig jwtConfig;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ROLE_ID = UUID.randomUUID();

    @BeforeAll
    static void setUp() throws NoSuchAlgorithmException {
        jwtConfig = new JwtConfig();
        // access expiry = 900s, refresh expiry = 604800s
        ReflectionTestUtils.setField(jwtConfig, "accessExpirySeconds", 900L);
        ReflectionTestUtils.setField(jwtConfig, "refreshExpirySeconds", 604800L);
        jwtService = new JwtService(jwtConfig);
    }

    @Test
    void generateAccessToken_containsSubjectAndClaims() {
        Set<String> permissions = Set.of("users.view", "roles.view");

        String token = jwtService.generateAccessToken(USER_ID, ROLE_ID, permissions);

        assertThat(token).isNotBlank();
        Claims claims = jwtService.validateAndParse(token);
        assertThat(claims.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(claims.get("roleId", String.class)).isEqualTo(ROLE_ID.toString());
        assertThat(claims.get("permissions")).isNotNull();
    }

    @Test
    void validateAndParse_returnsClaimsForValidToken() {
        Set<String> permissions = Set.of("docs.upload");

        String token = jwtService.generateAccessToken(USER_ID, ROLE_ID, permissions);
        Claims claims = jwtService.validateAndParse(token);

        assertThat(claims.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(claims.getExpiration()).isAfter(new Date());
    }

    @Test
    void validateAndParse_throwsForExpiredToken() throws NoSuchAlgorithmException {
        // Build a token with -1s expiry (already expired)
        var gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        var keyPair = gen.generateKeyPair();

        // Use the same private key from jwtConfig but build an expired token manually
        long now = System.currentTimeMillis();
        String expiredToken = Jwts.builder()
                .subject(USER_ID.toString())
                .issuedAt(new Date(now - 2000))
                .expiration(new Date(now - 1000))
                .signWith(jwtConfig.getPrivateKey(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> jwtService.validateAndParse(expiredToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void validateAndParse_throwsForTamperedToken() {
        String token = jwtService.generateAccessToken(USER_ID, ROLE_ID, Set.of("perm1"));
        // tamper with the signature
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "invalidsig";

        assertThatThrownBy(() -> jwtService.validateAndParse(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void generateAccessToken_withNullRoleId_claimIsNull() {
        String token = jwtService.generateAccessToken(USER_ID, null, Set.of("perm1"));

        assertThat(token).isNotBlank();
        Claims claims = jwtService.validateAndParse(token);
        assertThat(claims.get("roleId")).isNull();
    }
}
