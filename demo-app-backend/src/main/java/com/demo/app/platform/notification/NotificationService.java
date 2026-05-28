package com.demo.app.platform.notification;

import com.demo.app.platform.exception.ForbiddenException;
import com.demo.app.platform.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public Notification send(UUID recipientId, String title, String body) {
        Notification notification = Notification.builder()
                .recipientId(recipientId)
                .title(title)
                .body(body)
                .build();
        return notificationRepository.save(notification);
    }

    public void markRead(UUID notificationId, UUID requestingUserId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));

        if (!notification.getRecipientId().equals(requestingUserId)) {
            throw new ForbiddenException("You are not allowed to mark this notification as read");
        }

        notification.setRead(true);
        notification.setReadAt(Instant.now());
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> listForUser(UUID userId) {
        return notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public int countUnread(UUID userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }
}
