package com.demo.app.iam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FederatedIdentityTest {

    @Test
    void builder_setsAllFields() {
        var userId = UUID.randomUUID();
        var now = Instant.now();
        var attrs = Map.of("email", "user@agency.gov", "given_name", "Jane");

        var fi = FederatedIdentity.builder()
                .userId(userId)
                .provider("logingov")
                .nameId("urn:test:subject:12345")
                .nameIdFormat("urn:oasis:names:tc:SAML:2.0:nameid-format:persistent")
                .attributes(attrs)
                .createdAt(now)
                .lastSeenAt(now)
                .build();

        assertThat(fi.getUserId()).isEqualTo(userId);
        assertThat(fi.getProvider()).isEqualTo("logingov");
        assertThat(fi.getNameId()).isEqualTo("urn:test:subject:12345");
        assertThat(fi.getNameIdFormat()).isEqualTo("urn:oasis:names:tc:SAML:2.0:nameid-format:persistent");
        assertThat(fi.getAttributes()).isEqualTo(attrs);
        assertThat(fi.getCreatedAt()).isEqualTo(now);
        assertThat(fi.getLastSeenAt()).isEqualTo(now);
    }

    @Test
    void builder_defaults_createdAtAndLastSeenAt() {
        var before = Instant.now();
        var fi = FederatedIdentity.builder()
                .userId(UUID.randomUUID())
                .provider("piv")
                .nameId("CN=JANE DOE,OU=PEOPLE,O=AGENCY,C=US")
                .build();

        assertThat(fi.getCreatedAt()).isNotNull().isAfterOrEqualTo(before);
        assertThat(fi.getLastSeenAt()).isNotNull().isAfterOrEqualTo(before);
    }

    @Test
    void entity_hasUniqueConstraintOnProviderAndNameId() {
        var tableAnnotation = FederatedIdentity.class.getAnnotation(Table.class);
        assertThat(tableAnnotation).isNotNull();
        assertThat(tableAnnotation.name()).isEqualTo("federated_identities");

        UniqueConstraint[] constraints = tableAnnotation.uniqueConstraints();
        assertThat(constraints).isNotEmpty();

        boolean found = Arrays.stream(constraints)
                .anyMatch(c -> Arrays.asList(c.columnNames()).containsAll(
                        java.util.List.of("provider", "name_id")));
        assertThat(found).as("unique constraint on (provider, name_id) must exist").isTrue();
    }

    @Test
    void nameId_columnLength_is512() throws Exception {
        var col = FederatedIdentity.class
                .getDeclaredField("nameId")
                .getAnnotation(Column.class);

        assertThat(col).isNotNull();
        assertThat(col.length()).isEqualTo(512);
        assertThat(col.nullable()).isFalse();
    }

    @Test
    void onPreUpdate_updatesLastSeenAt() throws Exception {
        var fi = FederatedIdentity.builder()
                .userId(UUID.randomUUID())
                .provider("logingov")
                .nameId("sub:123")
                .lastSeenAt(Instant.EPOCH)
                .build();

        var before = Instant.now();
        fi.onUpdate();

        assertThat(fi.getLastSeenAt()).isAfterOrEqualTo(before);
    }

    @Test
    void noArgsConstructor_initializesLastSeenAtFromFieldDefault() {
        var before = Instant.now();
        var fi = new FederatedIdentity();

        // @Builder.Default keeps the field initializer for all constructors,
        // so lastSeenAt is never null even without the builder.
        assertThat(fi.getLastSeenAt()).isNotNull().isAfterOrEqualTo(before);
    }
}
