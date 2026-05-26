package com.demo.app.platform.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String title,
        String body,
        boolean isRead,
        Instant createdAt
) {}
