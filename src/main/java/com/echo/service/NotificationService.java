package com.echo.service;

import com.echo.domain.capsule.TimeCapsule;
import com.echo.domain.notification.Notification;
import com.echo.domain.notification.NotificationType;
import com.echo.domain.notification.PushToken;
import com.echo.domain.user.User;
import com.echo.dto.response.NotificationResponse;
import com.echo.dto.response.PagedResponse;
import com.echo.exception.ResourceNotFoundException;
import com.echo.repository.NotificationRepository;
import com.echo.repository.PushTokenRepository;
import com.echo.repository.TimeCapsuleRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Pattern LIKE_COUNT_PATTERN = Pattern.compile("^(\\d+) people liked your post.*$");

    private final NotificationRepository notificationRepository;
    private final PushTokenRepository pushTokenRepository;
    private final UserRepository userRepository;
    private final TimeCapsuleRepository timeCapsuleRepository;

    @Transactional
    public void notify(UUID userId,
                       NotificationType type,
                       String title,
                       String body,
                       String targetType,
                       UUID targetId,
                       String eventId,
                       String groupKey) {
        if (eventId != null && !eventId.isBlank() && notificationRepository.existsByEventId(eventId)) {
            return;
        }
        if (type == NotificationType.POST_LIKED && groupKey != null && !groupKey.isBlank()) {
            notifyOrCollapse(userId, type, title, body, targetType, targetId, eventId, groupKey);
            return;
        }

        try {
            saveNotification(userId, type, title, body, targetType, targetId, eventId, groupKey);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Notification deduplicated by unique event_id: {}", eventId);
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> getNotifications(UUID userId, Pageable pageable) {
        return PagedResponse.from(
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable),
                NotificationResponse::from
        );
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAsRead(UUID userId, UUID notificationId) {
        int updated = notificationRepository.markAsRead(userId, notificationId, OffsetDateTime.now());
        if (updated == 0 && notificationRepository.findByIdAndUserId(notificationId, userId).isEmpty()) {
            throw new ResourceNotFoundException("Notification not found");
        }
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsRead(userId, OffsetDateTime.now());
    }

    @Transactional
    public void registerPushToken(UUID userId, String token, String platform) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String normalizedToken = token == null ? null : token.trim();
        if (normalizedToken == null || normalizedToken.isBlank()) {
            throw new IllegalArgumentException("Push token is required");
        }

        PushToken pushToken = pushTokenRepository.findByUserIdAndToken(userId, normalizedToken)
                .orElseGet(() -> PushToken.builder()
                        .user(user)
                        .token(normalizedToken)
                        .build());
        pushToken.setPlatform(platform == null || platform.isBlank() ? "ios" : platform.toLowerCase());
        pushToken.setActive(true);
        pushTokenRepository.save(pushToken);
    }

    @Transactional
    public void deactivatePushToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        pushTokenRepository.findByToken(token.trim()).ifPresent(pushToken -> {
            pushToken.setActive(false);
            pushTokenRepository.save(pushToken);
        });
    }

    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void notifyUnlockedCapsules() {
        OffsetDateTime now = OffsetDateTime.now();
        for (TimeCapsule capsule : timeCapsuleRepository.findByStatusAndUnlockAtLessThanEqual("sealed", now)) {
            capsule.setStatus("unlocked");
            timeCapsuleRepository.save(capsule);

            notify(
                    capsule.getUser().getId(),
                    NotificationType.CAPSULE_UNLOCKED,
                    "Time Capsule Unlocked",
                    "One of your time capsules is now ready to open.",
                    "CAPSULE",
                    capsule.getId(),
                    "capsule_unlocked:" + capsule.getId(),
                    null
            );
        }
    }

    private void notifyOrCollapse(UUID userId,
                                  NotificationType type,
                                  String title,
                                  String body,
                                  String targetType,
                                  UUID targetId,
                                  String eventId,
                                  String groupKey) {
        OffsetDateTime oneHourAgo = OffsetDateTime.now().minusHours(1);
        var existing = notificationRepository
                .findFirstByUserIdAndGroupKeyAndCreatedAtAfterOrderByCreatedAtDesc(userId, groupKey, oneHourAgo);

        if (existing.isPresent()) {
            Notification notification = existing.get();
            int count = extractLikeCount(notification.getBody());
            int next = count + 1;
            notification.setBody(next + " people liked your post");
            notification.setRead(false);
            notification.setReadAt(null);
            notificationRepository.save(notification);
            return;
        }

        String collapsedBody = body == null || body.isBlank() ? "1 person liked your post" : body;
        saveNotification(userId, type, title, collapsedBody, targetType, targetId, eventId, groupKey);
    }

    private void saveNotification(UUID userId,
                                  NotificationType type,
                                  String title,
                                  String body,
                                  String targetType,
                                  UUID targetId,
                                  String eventId,
                                  String groupKey) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .body(body)
                .targetType(targetType)
                .targetId(targetId)
                .eventId(eventId)
                .groupKey(groupKey)
                .build();
        notificationRepository.save(notification);
    }

    private int extractLikeCount(String body) {
        if (body == null) {
            return 1;
        }
        Matcher matcher = LIKE_COUNT_PATTERN.matcher(body);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 1;
    }
}
