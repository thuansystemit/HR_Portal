package com.demo.app.platform.exception;

import java.util.UUID;

public class ResourceNotFoundException extends DomainException {
    public ResourceNotFoundException(String resource, UUID id) {
        super(resource + " not found: " + id);
    }
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
