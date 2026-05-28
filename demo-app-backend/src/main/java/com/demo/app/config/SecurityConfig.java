package com.demo.app.config;

import com.demo.app.iam.security.InternalApiKeyFilter;
import com.demo.app.iam.security.JwtCookieAuthFilter;
import com.demo.app.platform.logging.MdcUserFilter;
import com.demo.app.platform.security.CspFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

import java.util.Map;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtCookieAuthFilter jwtCookieAuthFilter;
    private final InternalApiKeyFilter internalApiKeyFilter;
    private final CorsConfigurationSource corsConfigurationSource;
    private final MdcUserFilter mdcUserFilter;
    private final CspFilter cspFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/v1/auth/banner").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/knowledge/ingest").hasRole("SERVICE")
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            .addFilterBefore(cspFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(internalApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtCookieAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(mdcUserFilter, AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // FIPS-compliant PBKDF2-HMAC-SHA256 is the default for new passwords (IA-5(1), SC-13).
        // Legacy {bcrypt} prefix allows existing hashes to verify until users log in and trigger re-encode.
        var pbkdf2 = new Pbkdf2PasswordEncoder("", 16, 310_000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        return new DelegatingPasswordEncoder("pbkdf2@sha256",
                Map.of("pbkdf2@sha256", pbkdf2,
                       "bcrypt",        new BCryptPasswordEncoder(12)));
    }
}
