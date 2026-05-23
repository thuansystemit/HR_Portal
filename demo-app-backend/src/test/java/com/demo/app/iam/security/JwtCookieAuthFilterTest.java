package com.demo.app.iam.security;

import com.demo.app.iam.service.JwtService;
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
}
