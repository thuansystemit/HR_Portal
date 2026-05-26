package com.demo.app.platform.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock NotificationService notificationService;
    @Mock Authentication authentication;

    @InjectMocks
    NotificationController controller;

    private final UUID USER_ID  = UUID.randomUUID();
    private final UUID NOTIF_ID = UUID.randomUUID();

    // ── list ──────────────────────────────────────────────────────────────────

    @Test
    void list_returns200_withNotifications() {
        var notification = buildNotification(false);
        when(authentication.getName()).thenReturn(USER_ID.toString());
        when(notificationService.listForUser(USER_ID)).thenReturn(List.of(notification));

        var result = controller.list(authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).id()).isEqualTo(NOTIF_ID);
        assertThat(result.getBody().get(0).title()).isEqualTo("Test Title");
        assertThat(result.getBody().get(0).body()).isEqualTo("Test body");
        assertThat(result.getBody().get(0).isRead()).isFalse();
    }

    @Test
    void list_returns200_withEmptyList_whenNoNotifications() {
        when(authentication.getName()).thenReturn(USER_ID.toString());
        when(notificationService.listForUser(USER_ID)).thenReturn(List.of());

        var result = controller.list(authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEmpty();
    }

    // ── unreadCount ───────────────────────────────────────────────────────────

    @Test
    void unreadCount_returns200_withCount() {
        when(authentication.getName()).thenReturn(USER_ID.toString());
        when(notificationService.countUnread(USER_ID)).thenReturn(3);

        var result = controller.unreadCount(authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).containsEntry("count", 3);
    }

    @Test
    void unreadCount_returns200_withZero_whenAllRead() {
        when(authentication.getName()).thenReturn(USER_ID.toString());
        when(notificationService.countUnread(USER_ID)).thenReturn(0);

        var result = controller.unreadCount(authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).containsEntry("count", 0);
    }

    // ── markRead ─────────────────────────────────────────────────────────────

    @Test
    void markRead_returns204_whenSuccessful() {
        when(authentication.getName()).thenReturn(USER_ID.toString());

        var result = controller.markRead(NOTIF_ID, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(notificationService).markRead(NOTIF_ID, USER_ID);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Notification buildNotification(boolean isRead) {
        return Notification.builder()
                .id(NOTIF_ID)
                .recipientId(USER_ID)
                .title("Test Title")
                .body("Test body")
                .isRead(isRead)
                .createdAt(Instant.now())
                .build();
    }
}
