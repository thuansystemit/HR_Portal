package com.demo.app.platform.exception;

public class WrongPasswordException extends DomainException {
    public WrongPasswordException() { super("Current password is incorrect"); }
}
