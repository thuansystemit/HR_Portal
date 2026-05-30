package com.demo.app.iam.repository;

import com.demo.app.iam.entity.FederatedIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * IA-2(12) / IA-8(2): data access for federated SAML identities.
 * Used by SamlUserProvisioningService to look up or create the local User
 * linked to an incoming SAML assertion.
 */
public interface FederatedIdentityRepository extends JpaRepository<FederatedIdentity, UUID> {

    /** Primary JIT lookup: find existing link for a (provider, nameId) pair. */
    Optional<FederatedIdentity> findByProviderAndNameId(String provider, String nameId);

    /** Return all federated identities linked to a given local user. */
    List<FederatedIdentity> findAllByUserId(UUID userId);

    boolean existsByProviderAndNameId(String provider, String nameId);
}
