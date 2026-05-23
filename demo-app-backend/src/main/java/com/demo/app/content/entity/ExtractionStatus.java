package com.demo.app.content.entity;

public enum ExtractionStatus {
    PENDING, PROCESSING, SUCCESS, FAILED;

    public boolean canTransitionTo(ExtractionStatus next) {
        return switch (this) {
            case PENDING    -> next == PROCESSING || next == SUCCESS || next == FAILED;
            case PROCESSING -> next == SUCCESS || next == FAILED;
            case FAILED     -> next == PENDING;
            case SUCCESS    -> false;
        };
    }
}
