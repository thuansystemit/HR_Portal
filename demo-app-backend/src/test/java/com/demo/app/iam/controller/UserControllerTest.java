package com.demo.app.iam.controller;

import com.demo.app.iam.dto.CreateUserRequest;
import com.demo.app.iam.dto.PagedResponse;
import com.demo.app.iam.dto.UpdateUserRequest;
import com.demo.app.iam.dto.UserResponse;
import com.demo.app.iam.service.UserService;
import com.demo.app.platform.idempotency.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private UserController userController;

    private final UUID USER_ID = UUID.randomUUID();
    private final UUID ROLE_ID = UUID.randomUUID();

    private UserResponse buildUserResponse() {
        return new UserResponse(USER_ID, "Test User", "test@example.com",
                ROLE_ID, "Admin", "active", Instant.now());
    }

    @Test
    void list_delegatesToService_returnsOk() {
        var userResponse = buildUserResponse();
        var paged = new PagedResponse<>(List.of(userResponse), 0, 10, 1L, 1);

        when(userService.list(0, 10)).thenReturn(paged);

        var result = userController.list(0, 10, null);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(((PagedResponse<?>) result.getBody()).content()).hasSize(1);
    }

    @Test
    void create_noIdempotencyKey_returnsCreated() {
        var request = new CreateUserRequest("Test User", "test@example.com", ROLE_ID, "pass123", "active");
        var userResponse = buildUserResponse();

        when(userService.create(request)).thenReturn(userResponse);

        var result = userController.create(request, null, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getHeaders().get("Location")).isNotNull();
        assertThat(result.getHeaders().getFirst("Location")).contains(USER_ID.toString());
        verify(idempotencyService, never()).findCached(any(), anyString(), anyString(), any());
    }

    @Test
    void create_withIdempotencyKey_cacheHit_returnsOk_withReplayHeader() {
        var request = new CreateUserRequest("Test User", "test@example.com", ROLE_ID, "pass123", "active");
        var cachedResponse = buildUserResponse();
        var userId = UUID.randomUUID().toString();

        when(idempotencyService.findCached(UUID.fromString(userId), "user", "key", UserResponse.class))
                .thenReturn(Optional.of(cachedResponse));

        var result = userController.create(request, userId, "key");

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getHeaders().getFirst("X-Idempotent-Replayed")).isEqualTo("true");
        assertThat(result.getBody()).isEqualTo(cachedResponse);
        verify(userService, never()).create(any());
    }

    @Test
    void create_withIdempotencyKey_cacheMiss_returnsCreated_andStores() {
        var request = new CreateUserRequest("Test User", "test@example.com", ROLE_ID, "pass123", "active");
        var userResponse = buildUserResponse();
        var userId = UUID.randomUUID().toString();

        when(idempotencyService.findCached(UUID.fromString(userId), "user", "mykey", UserResponse.class))
                .thenReturn(Optional.empty());
        when(userService.create(request)).thenReturn(userResponse);

        var result = userController.create(request, userId, "mykey");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(idempotencyService).store(UUID.fromString(userId), "user", "mykey", userResponse);
    }

    @Test
    void create_withIdempotencyKey_butNullUserId_returnsCreated() {
        // idempotencyKey != null but userId == null -> skip idempotency check
        var request = new CreateUserRequest("Test User", "test@example.com", ROLE_ID, "pass123", "active");
        var userResponse = buildUserResponse();

        when(userService.create(request)).thenReturn(userResponse);

        var result = userController.create(request, null, "some-key");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(idempotencyService, never()).findCached(any(), anyString(), anyString(), any());
        verify(idempotencyService, never()).store(any(), anyString(), anyString(), any());
    }

    @Test
    void findById_delegatesToService_returnsOk() {
        var userResponse = buildUserResponse();
        when(userService.findById(USER_ID)).thenReturn(userResponse);

        var result = userController.findById(USER_ID);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(userResponse);
    }

    @Test
    void update_delegatesToService_returnsOk() {
        var request = new UpdateUserRequest("Updated Name", ROLE_ID, "active");
        var userResponse = buildUserResponse();

        when(userService.update(USER_ID, request)).thenReturn(userResponse);

        var result = userController.update(USER_ID, request);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(userResponse);
    }

    @Test
    void delete_delegatesToService_returns204() {
        var result = userController.delete(USER_ID);

        verify(userService).delete(USER_ID);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
