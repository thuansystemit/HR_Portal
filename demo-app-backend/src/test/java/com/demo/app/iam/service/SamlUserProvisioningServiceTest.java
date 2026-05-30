package com.demo.app.iam.service;

import com.demo.app.compliance.service.AuditService;
import com.demo.app.iam.entity.Credential;
import com.demo.app.iam.entity.FederatedIdentity;
import com.demo.app.iam.entity.Role;
import com.demo.app.iam.entity.User;
import com.demo.app.iam.entity.UserRole;
import com.demo.app.iam.repository.CredentialRepository;
import com.demo.app.iam.repository.FederatedIdentityRepository;
import com.demo.app.iam.repository.RoleRepository;
import com.demo.app.iam.repository.UserRepository;
import com.demo.app.iam.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SamlUserProvisioningServiceTest {

    @Mock UserRepository userRepository;
    @Mock FederatedIdentityRepository federatedIdentityRepository;
    @Mock CredentialRepository credentialRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditService auditService;

    SamlUserProvisioningService service;

    private static final String PROVIDER  = "logingov";
    private static final String NAME_ID   = "urn:gov:test:sub:12345";
    private static final String NAME_FORMAT = "urn:oasis:names:tc:SAML:2.0:nameid-format:persistent";

    @BeforeEach
    void setUp() {
        service = new SamlUserProvisioningService(
                userRepository, federatedIdentityRepository, credentialRepository,
                userRoleRepository, roleRepository, passwordEncoder, auditService);
    }

    // ── path 1: existing FederatedIdentity ───────────────────────────────────

    @Test
    void provisionOrLink_existingIdentity_refreshesAndReturnsUser() {
        var userId = UUID.randomUUID();
        var user   = User.builder().id(userId).email("jane@agency.gov").fullName("Jane Doe").build();
        var fi     = FederatedIdentity.builder().userId(userId).provider(PROVIDER).nameId(NAME_ID).build();
        var attrs  = Map.of("email", "jane@agency.gov");

        when(federatedIdentityRepository.findByProviderAndNameId(PROVIDER, NAME_ID))
                .thenReturn(Optional.of(fi));
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(federatedIdentityRepository.save(fi)).thenReturn(fi);

        var result = service.provisionOrLink(PROVIDER, NAME_ID, NAME_FORMAT, attrs);

        assertThat(result).isEqualTo(user);
        verify(federatedIdentityRepository).save(fi);
        verify(auditService).log(eq(SamlUserProvisioningService.SYSTEM_ACTOR),
                eq("SAML_USER_LINKED"), eq("User"), eq(userId),
                isNull(), any(), eq("success"));
    }

    @Test
    void provisionOrLink_existingIdentity_deletedUser_throwsIllegalState() {
        var userId = UUID.randomUUID();
        var fi     = FederatedIdentity.builder().userId(userId).provider(PROVIDER).nameId(NAME_ID).build();

        when(federatedIdentityRepository.findByProviderAndNameId(PROVIDER, NAME_ID))
                .thenReturn(Optional.of(fi));
        when(federatedIdentityRepository.save(fi)).thenReturn(fi);
        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.provisionOrLink(PROVIDER, NAME_ID, NAME_FORMAT, Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IA-2(12)");
    }

    // ── path 2: link by email to existing user ───────────────────────────────

    @Test
    void provisionOrLink_emailMatchesExistingUser_linksAndReturnsUser() {
        var userId = UUID.randomUUID();
        var user   = User.builder().id(userId).email("jane@agency.gov").fullName("Jane Doe").build();
        var attrs  = Map.of("email", "jane@agency.gov");

        when(federatedIdentityRepository.findByProviderAndNameId(PROVIDER, NAME_ID))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("jane@agency.gov"))
                .thenReturn(Optional.of(user));
        when(federatedIdentityRepository.save(any(FederatedIdentity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = service.provisionOrLink(PROVIDER, NAME_ID, NAME_FORMAT, attrs);

        assertThat(result).isEqualTo(user);
        var fiCaptor = ArgumentCaptor.forClass(FederatedIdentity.class);
        verify(federatedIdentityRepository).save(fiCaptor.capture());
        assertThat(fiCaptor.getValue().getProvider()).isEqualTo(PROVIDER);
        assertThat(fiCaptor.getValue().getNameId()).isEqualTo(NAME_ID);
        assertThat(fiCaptor.getValue().getUserId()).isEqualTo(userId);
        verify(auditService).log(eq(SamlUserProvisioningService.SYSTEM_ACTOR),
                eq("SAML_USER_LINKED"), eq("User"), eq(userId),
                isNull(), any(), eq("success"));
    }

    @Test
    void provisionOrLink_emailIsBlank_throwsIllegalArgument() {
        var attrs = Map.of("email", "   ");

        when(federatedIdentityRepository.findByProviderAndNameId(PROVIDER, NAME_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.provisionOrLink(PROVIDER, NAME_ID, NAME_FORMAT, attrs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IA-2(12)");
    }

    // ── path 3: full JIT provisioning ────────────────────────────────────────

    @Test
    void provisionOrLink_noMatchAnywhere_provisionsNewUser() {
        var attrs  = Map.of("email", "new@agency.gov", "name", "New User");
        var saved  = User.builder().id(UUID.randomUUID()).email("new@agency.gov").fullName("New User").build();

        when(federatedIdentityRepository.findByProviderAndNameId(PROVIDER, NAME_ID))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("new@agency.gov"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any())).thenReturn(saved);
        when(credentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("{pbkdf2}hash");
        when(roleRepository.findByName(SamlUserProvisioningService.DEFAULT_ROLE_NAME))
                .thenReturn(Optional.empty());
        when(federatedIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.provisionOrLink(PROVIDER, NAME_ID, NAME_FORMAT, attrs);

        assertThat(result).isEqualTo(saved);
        verify(userRepository).save(any(User.class));
        verify(credentialRepository).save(any(Credential.class));
        verify(auditService).log(eq(SamlUserProvisioningService.SYSTEM_ACTOR),
                eq("SAML_USER_PROVISIONED"), eq("User"), eq(saved.getId()),
                isNull(), any(), eq("success"));
    }

    @Test
    void provisionOrLink_provisionsNewUser_withDefaultRole() {
        var roleId = UUID.randomUUID();
        var role   = Role.builder().id(roleId).name(SamlUserProvisioningService.DEFAULT_ROLE_NAME).build();
        var attrs  = Map.of("email", "new@agency.gov", "name", "New User");
        var saved  = User.builder().id(UUID.randomUUID()).email("new@agency.gov").fullName("New User").build();

        when(federatedIdentityRepository.findByProviderAndNameId(any(), any()))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(any()))
                .thenReturn(Optional.empty());
        when(userRepository.save(any())).thenReturn(saved);
        when(credentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("{pbkdf2}hash");
        when(roleRepository.findByName(SamlUserProvisioningService.DEFAULT_ROLE_NAME))
                .thenReturn(Optional.of(role));
        when(userRoleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(federatedIdentityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.provisionOrLink(PROVIDER, NAME_ID, NAME_FORMAT, attrs);

        var urCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(urCaptor.capture());
        assertThat(urCaptor.getValue().getRoleId()).isEqualTo(roleId);
        assertThat(urCaptor.getValue().getUserId()).isEqualTo(saved.getId());
    }

    @Test
    void provisionOrLink_nullEmail_throws() {
        when(federatedIdentityRepository.findByProviderAndNameId(any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.provisionOrLink(PROVIDER, NAME_ID, NAME_FORMAT, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IA-2(12)");
    }

    // ── buildFullName ─────────────────────────────────────────────────────────

    @Test
    void buildFullName_usesNameAttribute() {
        assertThat(service.buildFullName(Map.of("name", "Jane A. Doe")))
                .isEqualTo("Jane A. Doe");
    }

    @Test
    void buildFullName_combinesGivenAndFamily() {
        assertThat(service.buildFullName(Map.of("given_name", "Jane", "family_name", "Doe")))
                .isEqualTo("Jane Doe");
    }

    @Test
    void buildFullName_blankNameAttribute_fallsBackToGivenFamily() {
        assertThat(service.buildFullName(Map.of("name", "  ", "given_name", "Jane", "family_name", "Doe")))
                .isEqualTo("Jane Doe");
    }

    @Test
    void buildFullName_noAttributes_returnsUnknown() {
        assertThat(service.buildFullName(Map.of())).isEqualTo("Unknown");
    }

    @Test
    void buildFullName_onlyBlankComponents_returnsUnknown() {
        assertThat(service.buildFullName(Map.of("given_name", " ", "family_name", " ")))
                .isEqualTo("Unknown");
    }
}
