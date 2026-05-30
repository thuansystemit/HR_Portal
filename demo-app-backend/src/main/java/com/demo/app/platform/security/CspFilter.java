package com.demo.app.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Sets per-request security response headers (SC-7, SC-8(1), SC-18, SC-28).
 * script-src intentionally omits 'unsafe-inline' — all JS must be from 'self'.
 * style-src retains 'unsafe-inline' because Angular Material injects component styles at runtime.
 */
@Component
public class CspFilter extends OncePerRequestFilter {

    private static final SecureRandom RANDOM = new SecureRandom();

    // Exposed as a request attribute so server-rendered templates can inject the nonce value.
    public static final String NONCE_ATTRIBUTE = "cspNonce";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var nonce = generateNonce();
        request.setAttribute(NONCE_ATTRIBUTE, nonce);

        response.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                "script-src 'self' 'nonce-" + nonce + "'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: blob:; " +
                "font-src 'self' data:; " +
                "connect-src 'self'; " +
                "frame-ancestors 'none'; " +
                "base-uri 'self'; " +
                "form-action 'self'");

        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // SC-8(1): force HTTPS for 1 year; prevents protocol-downgrade and MITM attacks
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        // SC-7 / SC-18: restrict browser feature APIs with no business use in this application
        response.setHeader("Permissions-Policy",
                "camera=(), microphone=(), geolocation=(), payment=(), usb=(), interest-cohort=()");

        // SC-7: prevents cross-origin window handle sharing (clickjacking variant via opener)
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");

        // SC-28: prevent browsers from caching sensitive API responses (tokens, user data, PII)
        if (request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store, max-age=0");
        }

        chain.doFilter(request, response);
    }

    private String generateNonce() {
        var bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
