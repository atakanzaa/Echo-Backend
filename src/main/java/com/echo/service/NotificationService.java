package com.echo.service;

import com.echo.domain.capsule.CapsuleStatus;
import com.echo.domain.capsule.TimeCapsule;
import com.echo.domain.notification.ApnsEnvironment;
import com.echo.domain.notification.Notification;
import com.echo.domain.notification.NotificationType;
import com.echo.domain.notification.PushPlatform;
import com.echo.domain.notification.PushToken;
import com.echo.domain.user.User;
import com.echo.dto.response.NotificationResponse;
import com.echo.dto.response.PagedResponse;
import com.echo.event.NotificationCreatedEvent;
import com.echo.exception.ResourceNotFoundException;
import com.echo.repository.NotificationRepository;
import com.echo.repository.PushTokenRepository;
import com.echo.repository.TimeCapsuleRepository;
import com.echo.repository.UserRepository;
import com.echo.service.notification.NotificationTemplateService;
import com.echo.service.notification.RenderedNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String LIKE_GROUP_BODY_TEMPLATE = "%d";
    private static final int LIKE_COLLAPSE_WINDOW_HOURS = 1;

    private final NotificationRepository notificationRepository;
    private final PushTokenRepository pushTokenRepository;
    private final UserRepository userRepository;
    private final TimeCapsuleRepository timeCapsuleRepository;
    private final NotificationTemplateService templateService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void notifyTemplated(UUID userId,
                                NotificationType type,
                                Map<String, String> templateVars,
                                String targetType,
                                UUID targetId,
                                String eventId,
                                String groupKey) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        RenderedNotification rendered = templateService.render(type, user.getPreferredLanguage(), templateVars);
        notify(userId, type, rendered.title(), rendered.body(), targetType, targetId, eventId, groupKey);
    }

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
            Notification saved = saveNotification(userId, type, title, body, targetType, targetId, eventId, groupKey);
            eventPublisher.publishEvent(new NotificationCreatedEvent(saved.getId()));
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
    public void registerPushToken(UUID userId, String token, String platformRaw, String environmentRaw) {
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
        pushToken.setPlatform(PushPlatform.fromClient(platformRaw));
        pushToken.setEnvironment(ApnsEnvironment.fromClient(environmentRaw));
        pushToken.setActive(true);
        pushToken.setFailureCount(0);
        pushToken.setLastUsedAt(OffsetDateTime.now());
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
        for (TimeCapsule capsule : timeCapsuleRepository.findByStatusAndUnlockAtLessThanEqual(CapsuleStatus.SEALED, now)) {
            capsule.setStatus(CapsuleStatus.UNLOCKED);
            timeCapsuleRepository.save(capsule);

            notifyTemplated(
                    capsule.getUser().getId(),
                    NotificationType.CAPSULE_UNLOCKED,
                    Map.of(),
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
        OffsetDateTime windowStart = OffsetDateTime.now().minusHours(LIKE_COLLAPSE_WINDOW_HOURS);
        var existing = notificationRepository
                .findFirstByUserIdAndGroupKeyAndCreatedAtAfterOrderByCreatedAtDesc(userId, groupKey, windowStart);

        if (existing.isPresent()) {
            Notification notification = existing.get();
            notification.setRead(false);
            notification.setReadAt(null);
            notificationRepository.save(notification);
            // Within the collapse window: refresh the in-app row but do not
            // fire another push to avoid spamming the user during like bursts.
            return;
        }

        try {
            Notification saved = saveNotification(userId, type, title, body, targetType, targetId, eventId, groupKey);
            eventPublisher.publishEvent(new NotificationCreatedEvent(saved.getId()));
        } catch (DataIntegrityViolationException ex) {
            log.debug("Notification deduplicated by unique event_id: {}", eventId);
        }
    }

    private Notification saveNotification(UUID userId,
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
        return notificationRepository.save(notification);
    }
}
