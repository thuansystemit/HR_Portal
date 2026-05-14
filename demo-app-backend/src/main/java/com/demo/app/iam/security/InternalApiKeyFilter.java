package com.demo.app.iam.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Api-Key";

    @Value("${app.internal.api-key:}")
    private String internalApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (!internalApiKey.isBlank() && internalApiKey.equals(key)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            var auth = new UsernamePasswordAuthenticationToken(
                    "internal-service", null,
                    List.of(new SimpleGrantedAuthority("ROLE_SERVICE")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }
}
