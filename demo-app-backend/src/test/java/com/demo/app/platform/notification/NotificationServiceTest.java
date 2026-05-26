package com.demo.app.platform.notification;

import com.demo.app.platform.exception.ForbiddenException;
import com.demo.app.platform.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationRepository notificationRepository;

    @InjectMocks
    NotificationService notificationService;

    private final UUID RECIPIENT_ID = UUID.randomUUID();
    private final UUID NOTIF_ID     = UUID.randomUUID();

    // ── send ──────────────────────────────────────────────────────────────────

    @Test
    void send_savesAndReturnsNotification() {
        var saved = buildNotification(RECIPIENT_ID, false);
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

        Notification result = notificationService.send(RECIPIENT_ID, "Test Title", "Test body");

        assertThat(result).isNotNull();
        assertThat(result.getRecipientId()).isEqualTo(RECIPIENT_ID);
        assertThat(result.getTitle()).isEqualTo("Test Title");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientId()).isEqualTo(RECIPIENT_ID);
        assertThat(captor.getValue().getTitle()).isEqualTo("Test Title");
        assertThat(captor.getValue().getBody()).isEqualTo("Test body");
    }

    // ── listForUser ───────────────────────────────────────────────────────────

    @Test
    void listForUser_returnsOrderedList() {
        var n1 = buildNotification(RECIPIENT_ID, false);
        var n2 = buildNotification(RECIPIENT_ID, true);
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(RECIPIENT_ID))
                .thenReturn(List.of(n1, n2));

        List<Notification> result = notificationService.listForUser(RECIPIENT_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRecipientId()).isEqualTo(RECIPIENT_ID);
        verify(notificationRepository).findByRecipientIdOrderByCreatedAtDesc(RECIPIENT_ID);
    }

    @Test
    void listForUser_returnsEmptyList_whenNoNotifications() {
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(RECIPIENT_ID))
                .thenReturn(List.of());

        List<Notification> result = notificationService.listForUser(RECIPIENT_ID);

        assertThat(result).isEmpty();
    }

    // ── countUnread ───────────────────────────────────────────────────────────

    @Test
    void countUnread_returnsCorrectCount() {
        when(notificationRepository.countByRecipientIdAndIsReadFalse(RECIPIENT_ID)).thenReturn(5);

        int count = notificationService.countUnread(RECIPIENT_ID);

        assertThat(count).isEqualTo(5);
        verify(notificationRepository).countByRecipientIdAndIsReadFalse(RECIPIENT_ID);
    }

    @Test
    void countUnread_returnsZero_whenAllRead() {
        when(notificationRepository.countByRecipientIdAndIsReadFalse(RECIPIENT_ID)).thenReturn(0);

        int count = notificationService.countUnread(RECIPIENT_ID);

        assertThat(count).isZero();
    }

    // ── markRead ─────────────────────────────────────────────────────────────

    @Test
    void markRead_setsIsReadTrue_whenUserIsRecipient() {
        var notification = buildNotification(RECIPIENT_ID, false);
        when(notificationRepository.findById(NOTIF_ID)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.markRead(NOTIF_ID, RECIPIENT_ID);

        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isNotNull();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markRead_throws_whenNotificationNotFound() {
        when(notificationRepository.findById(NOTIF_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(NOTIF_ID, RECIPIENT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(NOTIF_ID.toString());

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markRead_throws_whenUserDoesNotOwnNotification() {
        UUID otherUser = UUID.randomUUID();
        var notification = buildNotification(RECIPIENT_ID, false);
        when(notificationRepository.findById(NOTIF_ID)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markRead(NOTIF_ID, otherUser))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not allowed");

        assertThat(notification.isRead()).isFalse();
        verify(notificationRepository, never()).save(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Notification buildNotification(UUID recipientId, boolean isRead) {
        return Notification.builder()
                .id(NOTIF_ID)
                .recipientId(recipientId)
                .title("Test Title")
                .body("Test body")
                .isRead(isRead)
                .createdAt(Instant.now())
                .build();
    }
}
