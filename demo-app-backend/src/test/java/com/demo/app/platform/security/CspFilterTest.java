package com.demo.app.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CspFilterTest {

    private final CspFilter filter = new CspFilter();

    @Test
    void doFilterInternal_setsCspHeaderWithNonce() throws Exception {
        var request  = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var chain    = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String csp = response.getHeader("Content-Security-Policy");
        assertThat(csp).isNotNull();
        assertThat(csp).contains("default-src 'self'");
        assertThat(csp).contains("script-src 'self' 'nonce-");
        assertThat(csp).doesNotContain("script-src 'unsafe-inline'");
        assertThat(csp).contains("style-src 'self' 'unsafe-inline'");
        assertThat(csp).contains("frame-ancestors 'none'");
    }

    @Test
    void doFilterInternal_setsNonceRequestAttribute() throws Exception {
        var request  = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        var nonce = request.getAttribute(CspFilter.NONCE_ATTRIBUTE);
        assertThat(nonce).isNotNull().isInstanceOf(String.class);
        assertThat((String) nonce).isNotBlank();
    }

    @Test
    void doFilterInternal_setsOtherSecurityHeaders() throws Exception {
        var request  = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
    }

    @Test
    void doFilterInternal_proceedsThroughChain() throws Exception {
        var request  = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var chain    = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void doFilterInternal_generatesDifferentNoncePerRequest() throws Exception {
        var r1 = new MockHttpServletRequest();
        var r2 = new MockHttpServletRequest();

        filter.doFilter(r1, new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(r2, new MockHttpServletResponse(), new MockFilterChain());

        var nonce1 = (String) r1.getAttribute(CspFilter.NONCE_ATTRIBUTE);
        var nonce2 = (String) r2.getAttribute(CspFilter.NONCE_ATTRIBUTE);
        assertThat(nonce1).isNotEqualTo(nonce2);
    }
}
