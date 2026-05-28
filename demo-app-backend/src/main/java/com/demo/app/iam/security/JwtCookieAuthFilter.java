package com.demo.app.iam.security;

import com.demo.app.iam.service.SessionActivityService;
import com.demo.app.iam.service.TokenDenylistService;
import com.demo.app.iam.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtCookieAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenDenylistService tokenDenylistService;
    private final SessionActivityService sessionActivityService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var token = extractCookie(request, "access-token");
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                var claims = jwtService.validateAndParse(token);
                var jti = claims.getId();

                // AC-12: reject explicitly revoked tokens
                if (jti != null && tokenDenylistService.isDenied(jti)) {
                    log.debug("Denied token jti={}", jti);
                    chain.doFilter(request, response);
                    return;
                }

                // AC-11: reject idle sessions (no activity within configured timeout)
                if (jti != null && sessionActivityService.isIdle(jti)) {
                    log.debug("Idle session jti={}", jti);
                    tokenDenylistService.deny(jti, claims.getExpiration().toInstant());
                    try {
                        sessionActivityService.deregister(jti, java.util.UUID.fromString(claims.getSubject()));
                    } catch (Exception ignored) {}
                    chain.doFilter(request, response);
                    return;
                }

                var userId = claims.getSubject();
                @SuppressWarnings("unchecked")
                var perms = (List<String>) claims.get("permissions", List.class);
                var authorities = Optional.ofNullable(perms).orElse(List.of()).stream()
                        .map(p -> new SimpleGrantedAuthority("PERM_" + p))
                        .collect(Collectors.toList());
                var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                var roleId = claims.get("roleId", String.class);
                auth.setDetails(Map.of("roleId", roleId != null ? roleId : "NONE"));
                SecurityContextHolder.getContext().setAuthentication(auth);

                // Extend idle timeout on each authenticated request
                if (jti != null) {
                    sessionActivityService.touch(jti);
                    MDC.put("sessionId", jti);
                }
            } catch (Exception e) {
                log.debug("JWT validation failed: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst().orElse(null);
    }
}
