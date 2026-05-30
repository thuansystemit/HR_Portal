package com.demo.app.platform.exception;

import java.util.List;

public class PasswordPolicyException extends DomainException {

    private final List<String> violations;

    public PasswordPolicyException(List<String> violations) {
        super("Password does not meet policy requirements");
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
