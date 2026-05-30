package com.demo.app.config;

import com.demo.app.iam.security.SamlAuthenticationSuccessHandler;
import com.demo.app.iam.security.SamlLogoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * IA-2(12) / IA-8(2): secondary SecurityFilterChain for SAML2 SP endpoints.
 * Only activated when app.saml.enabled=true so it does not interfere with
 * the stateless JWT chain in dev/test environments.
 *
 * Evaluated before the JWT chain (@Order(1)) and scoped to SAML2 URL patterns
 * only, so every other request falls through to the existing JWT chain.
 */
@Configuration
@ConditionalOnProperty(name = "app.saml.enabled", havingValue = "true")
@RequiredArgsConstructor
public class Saml2SecurityConfig {

    private final SamlAuthenticationSuccessHandler successHandler;
    private final SamlLogoutHandler logoutHandler;

    @Bean
    @Order(1)
    public SecurityFilterChain saml2FilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/login/saml2/**", "/logout/saml2/**", "/saml2/**")
            .saml2Login(saml -> saml.successHandler(successHandler))
            .saml2Logout(Customizer.withDefaults())
            .logout(logout -> logout.addLogoutHandler(logoutHandler));
        return http.build();
    }
}
