package com.demo.app.platform.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String title,
        String body,
        @JsonProperty("isRead") boolean isRead,
        Instant createdAt
) {}
