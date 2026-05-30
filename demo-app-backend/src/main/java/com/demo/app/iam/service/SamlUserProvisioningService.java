package com.demo.app.iam.service;

import com.demo.app.compliance.service.AuditService;
import com.demo.app.iam.entity.Credential;
import com.demo.app.iam.entity.FederatedIdentity;
import com.demo.app.iam.entity.User;
import com.demo.app.iam.entity.UserRole;
import com.demo.app.iam.repository.CredentialRepository;
import com.demo.app.iam.repository.FederatedIdentityRepository;
import com.demo.app.iam.repository.RoleRepository;
import com.demo.app.iam.repository.UserRepository;
import com.demo.app.iam.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * IA-2(12) / IA-8(2): Just-in-time (JIT) SAML user provisioning.
 *
 * On each successful SAML assertion:
 *   1. If a FederatedIdentity already exists for (provider, nameId) → refresh
 *      lastSeenAt and return the linked User.
 *   2. Else if the assertion carries an email that matches an active local User
 *      → create a new FederatedIdentity and link it to that User.
 *   3. Else → provision a new User + locked Credential + default role assignment
 *      and create a new FederatedIdentity.
 *
 * All three paths emit an audit event so the SAML login appears in the trail.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SamlUserProvisioningService {

    static final UUID SYSTEM_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final String DEFAULT_ROLE_NAME = "EMPLOYEE";

    private final UserRepository userRepository;
    private final FederatedIdentityRepository federatedIdentityRepository;
    private final CredentialRepository credentialRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional
    public User provisionOrLink(String provider, String nameId, String nameIdFormat,
                                Map<String, String> attributes) {
        var existing = federatedIdentityRepository.findByProviderAndNameId(provider, nameId);
        if (existing.isPresent()) {
            return handleExistingIdentity(existing.get(), attributes);
        }

        String email = attributes.get("email");
        if (email != null) {
            if (!email.isBlank()) {
                var existingUser = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email.toLowerCase());
                if (existingUser.isPresent()) {
                    return linkExistingUser(existingUser.get(), provider, nameId, nameIdFormat, attributes);
                }
            }
        }

        return provisionNewUser(provider, nameId, nameIdFormat, attributes, email);
    }

    User handleExistingIdentity(FederatedIdentity fi, Map<String, String> attributes) {
        fi.setAttributes(attributes);
        federatedIdentityRepository.save(fi);

        var user = userRepository.findByIdAndDeletedAtIsNull(fi.getUserId())
                .orElseThrow(() -> new IllegalStateException(
                        "IA-2(12): federated identity references deleted user " + fi.getUserId()));

        auditService.log(SYSTEM_ACTOR, "SAML_USER_LINKED", "User", user.getId(),
                null, Map.of("provider", fi.getProvider(), "nameId", fi.getNameId()), "success");

        return user;
    }

    User linkExistingUser(User user, String provider, String nameId,
                          String nameIdFormat, Map<String, String> attributes) {
        var fi = FederatedIdentity.builder()
                .userId(user.getId())
                .provider(provider)
                .nameId(nameId)
                .nameIdFormat(nameIdFormat)
                .attributes(attributes)
                .build();
        federatedIdentityRepository.save(fi);

        auditService.log(SYSTEM_ACTOR, "SAML_USER_LINKED", "User", user.getId(),
                null, Map.of("provider", provider, "nameId", nameId), "success");

        return user;
    }

    User provisionNewUser(String provider, String nameId, String nameIdFormat,
                          Map<String, String> attributes, String email) {
        if (email == null) {
            throw new IllegalArgumentException(
                    "IA-2(12): cannot provision SAML user without email attribute");
        }
        if (email.isBlank()) {
            throw new IllegalArgumentException(
                    "IA-2(12): cannot provision SAML user without email attribute");
        }

        final User savedUser = userRepository.save(User.builder()
                .email(email.toLowerCase())
                .fullName(buildFullName(attributes))
                .status("active")
                .build());

        // SAML-only account: random inaccessible password so the credential row exists
        // but the account can never be accessed via the password login path.
        credentialRepository.save(Credential.builder()
                .userId(savedUser.getId())
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .mustChangePassword(false)
                .build());

        // Assign default role; skip silently if EMPLOYEE role has not been seeded yet
        roleRepository.findByName(DEFAULT_ROLE_NAME).ifPresent(role ->
                userRoleRepository.save(UserRole.builder()
                        .userId(savedUser.getId())
                        .roleId(role.getId())
                        .build()));

        var fi = FederatedIdentity.builder()
                .userId(savedUser.getId())
                .provider(provider)
                .nameId(nameId)
                .nameIdFormat(nameIdFormat)
                .attributes(attributes)
                .build();
        federatedIdentityRepository.save(fi);

        auditService.log(SYSTEM_ACTOR, "SAML_USER_PROVISIONED", "User", savedUser.getId(),
                null, Map.of("provider", provider, "nameId", nameId, "email", email), "success");

        log.info("IA-2(12): JIT-provisioned new user {} via SAML provider {}", savedUser.getId(), provider);
        return savedUser;
    }

    String buildFullName(Map<String, String> attributes) {
        String full = attributes.get("name");
        if (full != null) {
            if (!full.isBlank()) {
                return full;
            }
        }
        String given = attributes.getOrDefault("given_name", "");
        String family = attributes.getOrDefault("family_name", "");
        String combined = (given + " " + family).strip();
        if (combined.isBlank()) {
            return "Unknown";
        }
        return combined;
    }
}
