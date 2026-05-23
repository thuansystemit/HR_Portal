package com.demo.app.iam.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InternalApiKeyFilterTest {

    InternalApiKeyFilter filter;
    MockHttpServletRequest request;
    MockHttpServletResponse response;
    MockFilterChain chain;

    private static final String VALID_KEY = "secret-internal-key";
    private static final String HEADER = "X-Internal-Api-Key";

    @BeforeEach
    void setUp() {
        filter = new InternalApiKeyFilter();
        ReflectionTestUtils.setField(filter, "internalApiKey", VALID_KEY);
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
    void doFilter_setsServiceAuth_whenKeyMatches() throws ServletException, IOException {
        request.addHeader(HEADER, VALID_KEY);

        filter.doFilterInternal(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("internal-service");
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_SERVICE"));
    }

    @Test
    void doFilter_skips_whenKeyMissing() throws ServletException, IOException {
        // No header set
        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_skips_whenKeyWrong() throws ServletException, IOException {
        request.addHeader(HEADER, "wrong-key");

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_skips_whenKeyBlank() throws ServletException, IOException {
        // Configure blank internal key -- all requests should pass through without service auth
        ReflectionTestUtils.setField(filter, "internalApiKey", "");
        request.addHeader(HEADER, "any-value");

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_skips_whenAuthenticationAlreadySet() throws ServletException, IOException {
        // Set an authentication already in context -- filter should not replace it
        var existingAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "existing-user", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existingAuth);
        request.addHeader(HEADER, VALID_KEY);

        filter.doFilterInternal(request, response, chain);

        // Auth should remain unchanged (the existing auth, not internal-service)
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getPrincipal()).isEqualTo("existing-user");
    }
}
