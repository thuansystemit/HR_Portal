package com.demo.app.platform.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
public class MdcUserFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        populateUserMdc();
        chain.doFilter(request, response);
        // No MDC cleanup here — MdcRequestFilter.MDC.clear() handles full cleanup
        // after emitting the access log entry with userId/roleId included.
    }

    private void populateUserMdc() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            MDC.put("userId", "anonymous");
            MDC.put("roleId", "NONE");
            return;
        }
        Object principal = auth.getPrincipal();
        MDC.put("userId", principal instanceof String s ? s : principal.toString());

        Object details = auth.getDetails();
        if (details instanceof Map<?, ?> map && map.get("roleId") instanceof String roleId) {
            MDC.put("roleId", roleId);
        } else {
            MDC.put("roleId", "UNKNOWN");
        }
    }
}
