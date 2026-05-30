package com.demo.app.iam.service;

import com.demo.app.platform.exception.PasswordPolicyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyServiceTest {

    private PasswordPolicyService service;

    @BeforeEach
    void setUp() {
        service = new PasswordPolicyService();
        service.loadCommonPasswords();
    }

    // --- Length rules ---

    @Test
    void valid_password_passes() {
        assertThatCode(() -> service.validate("C0rrect!Horse#Battery", "user@example.com", "Alice Smith"))
                .doesNotThrowAnyException();
    }

    @Test
    void tooShort_throwsWithLengthViolation() {
        assertThatThrownBy(() -> service.validate("Short1!", "u@x.com", "Alice"))
                .isInstanceOf(PasswordPolicyException.class)
                .satisfies(ex -> assertThat(((PasswordPolicyException) ex).getViolations())
                        .anyMatch(v -> v.contains("at least 12")));
    }

    @Test
    void exactly12Chars_passes() {
        assertThatCode(() -> service.validate("Abcdef123456", "u@x.com", "Alice"))
                .doesNotThrowAnyException();
    }

    @Test
    void tooLong_throwsWithLengthViolation() {
        String over128 = "A".repeat(129);
        assertThatThrownBy(() -> service.validate(over128, "u@x.com", "Alice"))
                .isInstanceOf(PasswordPolicyException.class)
                .satisfies(ex -> assertThat(((PasswordPolicyException) ex).getViolations())
                        .anyMatch(v -> v.contains("no more than 128")));
    }

    @Test
    void exactly128Chars_passes() {
        String exactly128 = "Aa1!".repeat(32); // 128 chars
        assertThatCode(() -> service.validate(exactly128, "u@x.com", "Alice"))
                .doesNotThrowAnyException();
    }

    // --- Common password blocklist ---

    @Test
    void commonPassword_throws() {
        assertThatThrownBy(() -> service.validate("password12345", "u@x.com", "Alice"))
                .isInstanceOf(PasswordPolicyException.class)
                .satisfies(ex -> assertThat(((PasswordPolicyException) ex).getViolations())
                        .anyMatch(v -> v.contains("too common")));
    }

    @Test
    void commonPassword_caseInsensitive() {
        assertThatThrownBy(() -> service.validate("PASSWORD12345", "u@x.com", "Alice"))
                .isInstanceOf(PasswordPolicyException.class)
                .satisfies(ex -> assertThat(((PasswordPolicyException) ex).getViolations())
                        .anyMatch(v -> v.contains("too common")));
    }

    // --- User attribute checks ---

    @Test
    void passwordContainsEmailLocalPart_throws() {
        // email local-part is "alice" (>= 3 chars)
        assertThatThrownBy(() -> service.validate("alice!Secure9999", "alice@example.com", "Alice Smith"))
                .isInstanceOf(PasswordPolicyException.class)
                .satisfies(ex -> assertThat(((PasswordPolicyException) ex).getViolations())
                        .anyMatch(v -> v.contains("email")));
    }

    @Test
    void passwordContainsEmailLocalPart_caseInsensitive() {
        assertThatThrownBy(() -> service.validate("ALICE!Secure9999", "alice@example.com", "Alice Smith"))
                .isInstanceOf(PasswordPolicyException.class)
                .satisfies(ex -> assertThat(((PasswordPolicyException) ex).getViolations())
                        .anyMatch(v -> v.contains("email")));
    }

    @Test
    void shortEmailLocalPart_notChecked() {
        // local part "ab" is < 3 chars — should be ignored
        assertThatCode(() -> service.validate("abSecure!Pass999", "ab@example.com", "Alice"))
                .doesNotThrowAnyException();
    }

    @Test
    void passwordContainsFullNamePart_throws() {
        // "Smith" is >= 4 chars
        assertThatThrownBy(() -> service.validate("SmithSecure!999", "u@x.com", "Alice Smith"))
                .isInstanceOf(PasswordPolicyException.class)
                .satisfies(ex -> assertThat(((PasswordPolicyException) ex).getViolations())
                        .anyMatch(v -> v.contains("name")));
    }

    @Test
    void shortNamePart_notChecked() {
        // name "Bo" is < 4 chars
        assertThatCode(() -> service.validate("BoSecure!Pass999", "u@x.com", "Bo Li"))
                .doesNotThrowAnyException();
    }

    @Test
    void nullEmail_skipsEmailCheck() {
        assertThatCode(() -> service.validate("SecurePass!999x", null, "Alice"))
                .doesNotThrowAnyException();
    }

    @Test
    void nullFullName_skipsNameCheck() {
        assertThatCode(() -> service.validate("SecurePass!999x", "u@x.com", null))
                .doesNotThrowAnyException();
    }

    @Test
    void multipleViolations_allReported() {
        // Too short AND common password
        assertThatThrownBy(() -> service.validate("password", "u@x.com", "Alice"))
                .isInstanceOf(PasswordPolicyException.class)
                .satisfies(ex -> {
                    var violations = ((PasswordPolicyException) ex).getViolations();
                    assertThat(violations).hasSizeGreaterThanOrEqualTo(2);
                    assertThat(violations).anyMatch(v -> v.contains("at least 12"));
                    assertThat(violations).anyMatch(v -> v.contains("too common"));
                });
    }

    @Test
    void nullPassword_throwsLengthViolation() {
        assertThatThrownBy(() -> service.validate(null, "u@x.com", "Alice"))
                .isInstanceOf(PasswordPolicyException.class)
                .satisfies(ex -> assertThat(((PasswordPolicyException) ex).getViolations())
                        .anyMatch(v -> v.contains("at least 12")));
    }
}
