package com.demo.app.iam.service;

import com.demo.app.platform.exception.PasswordPolicyException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * IA-5(3): NIST SP 800-63B password policy enforcement.
 * Rules: min 12 / max 128 chars; not in common-password blocklist;
 * does not contain the user's email local-part or name fragments.
 */
@Service
@Slf4j
public class PasswordPolicyService {

    @Value("${app.password.min-length:12}") private int minLength = 12;
    @Value("${app.password.max-length:128}") private int maxLength = 128;

    private Set<String> commonPasswords = Set.of();

    @PostConstruct
    void loadCommonPasswords() {
        var resource = new ClassPathResource("security/common-passwords.txt");
        var loaded = new HashSet<String>();
        try (var reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines()
                    .map(String::strip)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .map(String::toLowerCase)
                    .forEach(loaded::add);
            commonPasswords = Set.copyOf(loaded);
            log.debug("Loaded {} common passwords into blocklist", commonPasswords.size());
        } catch (Exception e) {
            log.warn("Could not load common-passwords.txt — blocklist check disabled: {}", e.getMessage());
        }
    }

    /**
     * Validates the candidate password against all policy rules.
     * Throws {@link PasswordPolicyException} listing every violation found.
     *
     * @param password   the candidate plain-text password
     * @param userEmail  the user's email address (used for attribute check)
     * @param userFullName the user's full name (used for attribute check)
     */
    public void validate(String password, String userEmail, String userFullName) {
        List<String> violations = new ArrayList<>();

        if (password == null || password.length() < minLength) {
            violations.add("Password must be at least " + minLength + " characters");
        }
        if (password != null && password.length() > maxLength) {
            violations.add("Password must be no more than " + maxLength + " characters");
        }

        if (password != null) {
            String lower = password.toLowerCase();

            if (commonPasswords.contains(lower)) {
                violations.add("Password is too common — choose a less predictable password");
            }

            if (userEmail != null) {
                String localPart = userEmail.contains("@")
                        ? userEmail.substring(0, userEmail.indexOf('@')).toLowerCase()
                        : userEmail.toLowerCase();
                if (localPart.length() >= 3 && lower.contains(localPart)) {
                    violations.add("Password must not contain your email address");
                }
            }

            if (userFullName != null) {
                for (String part : userFullName.split("\\s+")) {
                    if (part.length() >= 4 && lower.contains(part.toLowerCase())) {
                        violations.add("Password must not contain your name");
                        break;
                    }
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new PasswordPolicyException(violations);
        }
    }
}
