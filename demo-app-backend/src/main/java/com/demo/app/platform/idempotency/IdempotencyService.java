package com.demo.app.platform.idempotency;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyService {
    <T> Optional<T> findCached(UUID userId, String resource, String idempotencyKey, Class<T> responseType);
    <T> void store(UUID userId, String resource, String idempotencyKey, T response);
}
