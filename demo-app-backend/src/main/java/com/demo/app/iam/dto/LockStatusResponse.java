package com.demo.app.iam.dto;

import java.time.Instant;

public record LockStatusResponse(boolean locked, Instant lockedUntil, int failedAttempts) {}
