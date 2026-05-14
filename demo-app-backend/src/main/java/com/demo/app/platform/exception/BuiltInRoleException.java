package com.demo.app.platform.exception;

public class BuiltInRoleException extends DomainException {
    public BuiltInRoleException() { super("Built-in roles cannot be modified or deleted"); }
}
