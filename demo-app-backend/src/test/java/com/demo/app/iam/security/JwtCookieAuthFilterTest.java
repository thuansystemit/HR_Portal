package com.demo.app.iam.security;

import com.demo.app.iam.service.JwtService;
import com.demo.app.iam.service.SessionActivityService;
import com.demo.app.iam.service.TokenDenylistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtCookieAuthFilterTest {

    @Mock JwtService jwtService;
    @Mock TokenDenylistService tokenDenylistService;
    @Mock SessionActivityService sessionActivityService;

    @InjectMocks
    JwtCookieAuthFilter filter;

    MockHttpServletRequest request;
    MockHttpServletResponse response;
    MockFilterChain chain;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_setsAuthentication_whenValidCookiePresent() throws ServletException, IOException {
        String userId = UUID.randomUUID().toString();
        request.setCookies(new Cookie("access-token", "valid.jwt.token"));

        Claims claims = new DefaultClaims(Map.of(
                "sub", userId,
                "permissions", List.of("users.view", "roles.view")
        ));
        when(jwtService.validateAndParse("valid.jwt.token")).thenReturn(claims);

        filter.doFilterInternal(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(userId);
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("PERM_users.view"));
    }

    @Test
    void doFilter_skips_whenNoCookie() throws ServletException, IOException {
        // No cookies set
        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).validateAndParse(anyString());
    }

    @Test
    void doFilter_skips_whenJwtInvalid() throws ServletException, IOException {
        request.setCookies(new Cookie("access-token", "invalid.token"));
        when(jwtService.validateAndParse("invalid.token")).thenThrow(new RuntimeException("Invalid JWT"));

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_setsRoleIdInDetails_whenRoleIdClaimPresent() throws ServletException, IOException {
        String userId = UUID.randomUUID().toString();
        String roleId = UUID.randomUUID().toString();
        request.setCookies(new Cookie("access-token", "token.with.role"));

        Claims claims = new DefaultClaims(Map.of(
                "sub", userId,
                "roleId", roleId
        ));
        when(jwtService.validateAndParse("token.with.role")).thenReturn(claims);

        filter.doFilterInternal(request, response, chain);

        var auth = (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        @SuppressWarnings("unchecked")
        var details = (java.util.Map<String, String>) auth.getDetails();
        assertThat(details.get("roleId")).isEqualTo(roleId);
    }

    @Test
    void doFilter_skips_whenAuthenticationAlreadySet() throws ServletException, IOException {
        // Pre-set an existing authentication
        var existingAuth = new UsernamePasswordAuthenticationToken("existing-user", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        request.setCookies(new Cookie("access-token", "some.token"));

        filter.doFilterInternal(request, response, chain);

        // Existing auth should not be overwritten
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo("existing-user");
        verify(jwtService, never()).validateAndParse(anyString());
    }

    @Test
    void doFilter_setsAuth_andTouches_whenJtiPresent() throws ServletException, IOException {
        String userId = UUID.randomUUID().toString();
        String jti = java.util.UUID.randomUUID().toString();
        request.setCookies(new Cookie("access-token", "token.with.jti"));

        Claims claims = new DefaultClaims(Map.of("sub", userId, "jti", jti));
        when(jwtService.validateAndParse("token.with.jti")).thenReturn(claims);
        when(tokenDenylistService.isDenied(jti)).thenReturn(false);
        when(sessionActivityService.isIdle(jti)).thenReturn(false);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(sessionActivityService).touch(jti);
    }

    @Test
    void doFilter_rejectsRequest_whenTokenDenied() throws ServletException, IOException {
        String userId = UUID.randomUUID().toString();
        String jti = java.util.UUID.randomUUID().toString();
        request.setCookies(new Cookie("access-token", "denied.token"));

        Claims claims = new DefaultClaims(Map.of("sub", userId, "jti", jti));
        when(jwtService.validateAndParse("denied.token")).thenReturn(claims);
        when(tokenDenylistService.isDenied(jti)).thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(sessionActivityService, never()).touch(any());
    }

    @Test
    void doFilter_rejectsRequest_whenSessionIdle() throws ServletException, IOException {
        String userId = UUID.randomUUID().toString();
        String jti = java.util.UUID.randomUUID().toString();
        request.setCookies(new Cookie("access-token", "idle.token"));

        var expiry = new java.util.Date(System.currentTimeMillis() + 900_000L);
        Claims claims = new DefaultClaims(Map.of("sub", userId, "jti", jti, "exp", expiry));
        when(jwtService.validateAndParse("idle.token")).thenReturn(claims);
        when(tokenDenylistService.isDenied(jti)).thenReturn(false);
        when(sessionActivityService.isIdle(jti)).thenReturn(true);

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tokenDenylistService).deny(eq(jti), any());
        verify(sessionActivityService).deregister(eq(jti), any());
    }
}
