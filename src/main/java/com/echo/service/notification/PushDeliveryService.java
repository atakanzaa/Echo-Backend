package com.echo.service.notification;

import com.echo.domain.notification.Notification;
import com.echo.domain.notification.NotificationDelivery;
import com.echo.domain.notification.NotificationDeliveryStatus;
import com.echo.domain.notification.PushToken;
import com.echo.event.NotificationCreatedEvent;
import com.echo.repository.NotificationDeliveryRepository;
import com.echo.repository.NotificationRepository;
import com.echo.repository.PushTokenRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushDeliveryService {

    private static final int MAX_FAILURES_BEFORE_DEACTIVATE = 3;

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final PushTokenRepository pushTokenRepository;
    private final NotificationEligibilityService eligibilityService;
    private final NotificationRateLimiter rateLimiter;
    private final ApnsPushSender apnsPushSender;
    private final MeterRegistry meterRegistry;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        try {
            dispatch(event.notificationId());
        } catch (Exception ex) {
            log.warn("Push dispatch failed notificationId={}", event.notificationId(), ex);
        }
    }

    @Transactional
    public void dispatch(UUID notificationId) {
        Optional<Notification> maybe = notificationRepository.findById(notificationId);
        if (maybe.isEmpty()) {
            return;
        }
        Notification notification = maybe.get();
        String category = notification.getType().category().name();

        NotificationEligibilityService.Decision decision = eligibilityService.evaluate(notification);
        switch (decision.outcome()) {
            case SKIP_DISABLED -> {
                recordSkip(notification, null, NotificationDeliveryStatus.SKIPPED_DISABLED);
                metricSkip(category, "disabled");
                return;
            }
            case SKIP_QUIET_HOURS -> {
                recordSkip(notification, null, NotificationDeliveryStatus.SKIPPED_QUIET_HOURS);
                metricSkip(category, "quiet_hours");
                return;
            }
            case SKIP_NO_TOKEN -> {
                recordSkip(notification, null, NotificationDeliveryStatus.SKIPPED_NO_TOKEN);
                metricSkip(category, "no_token");
                return;
            }
            case DELIVER -> { /* fall through */ }
        }

        if (rateLimiter.shouldThrottle(notification.getUser().getId())) {
            recordSkip(notification, null, NotificationDeliveryStatus.SKIPPED_RATE_LIMITED);
            metricSkip(category, "rate_limited");
            return;
        }

        ApnsPushPayload payload = buildPayload(notification);
        for (PushToken token : decision.tokens()) {
            sendToToken(notification, token, payload, category);
        }
    }

    private void sendToToken(Notification notification, PushToken token, ApnsPushPayload payload, String category) {
        if (deliveryRepository.existsByNotificationIdAndPushTokenId(notification.getId(), token.getId())) {
            return;
        }

        NotificationDelivery delivery;
        try {
            delivery = deliveryRepository.save(NotificationDelivery.builder()
                    .notification(notification)
                    .pushToken(token)
                    .status(NotificationDeliveryStatus.PENDING)
                    .attemptCount(0)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            return;
        }

        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        Timer.Sample sample = Timer.start(meterRegistry);
        ApnsPushResult result = apnsPushSender.send(token, payload);
        sample.stop(Timer.builder("notifications.apns_request_duration")
                .description("APNs HTTP/2 request duration")
                .tag("category", category)
                .tag("outcome", result.accepted() ? "accepted" : (result.tokenInvalid() ? "rejected" : "failed"))
                .register(meterRegistry));

        if (result.accepted()) {
            delivery.setStatus(NotificationDeliveryStatus.SENT);
            delivery.setApnsId(result.apnsId());
            delivery.setSentAt(OffsetDateTime.now());
            token.setFailureCount(0);
            token.setLastUsedAt(OffsetDateTime.now());
            pushTokenRepository.save(token);
            metricSent(category);
        } else if (result.tokenInvalid()) {
            delivery.setStatus(NotificationDeliveryStatus.REJECTED);
            delivery.setErrorCode(result.errorCode());
            delivery.setErrorReason(result.errorReason());
            token.setActive(false);
            pushTokenRepository.save(token);
            metricFailed(category, "rejected:" + safeReason(result.errorCode()));
        } else {
            delivery.setStatus(NotificationDeliveryStatus.FAILED);
            delivery.setErrorCode(result.errorCode());
            delivery.setErrorReason(result.errorReason());
            int failures = token.getFailureCount() + 1;
            token.setFailureCount(failures);
            if (failures >= MAX_FAILURES_BEFORE_DEACTIVATE) {
                token.setActive(false);
            }
            pushTokenRepository.save(token);
            metricFailed(category, "failed:" + safeReason(result.errorCode()));
        }
        deliveryRepository.save(delivery);
    }

    private void recordSkip(Notification notification,
                            PushToken token,
                            NotificationDeliveryStatus status) {
        try {
            deliveryRepository.save(NotificationDelivery.builder()
                    .notification(notification)
                    .pushToken(token)
                    .status(status)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            // already recorded — ignore
        }
    }

    private ApnsPushPayload buildPayload(Notification notification) {
        Map<String, String> data = new HashMap<>();
        if (notification.getTargetType() != null) {
            data.put("target_type", notification.getTargetType());
        }
        if (notification.getTargetId() != null) {
            data.put("target_id", notification.getTargetId().toString());
        }
        if (notification.getGroupKey() != null) {
            data.put("group_key", notification.getGroupKey());
        }
        data.put("category", notification.getType().category().name());

        return new ApnsPushPayload(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getGroupKey(),
                data
        );
    }

    private void metricSent(String category) {
        Counter.builder("notifications.sent_total")
                .description("Push notifications sent successfully")
                .tag("category", category)
                .register(meterRegistry)
                .increment();
    }

    private void metricFailed(String category, String reason) {
        Counter.builder("notifications.failed_total")
                .description("Push notification delivery failures")
                .tag("category", category)
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    private void metricSkip(String category, String reason) {
        Counter.builder("notifications.skipped_total")
                .description("Push notifications skipped before delivery")
                .tag("category", category)
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    private String safeReason(String raw) {
        if (raw == null || raw.isBlank()) return "unknown";
        // Bound cardinality of the metric label
        return raw.length() > 40 ? raw.substring(0, 40) : raw;
    }
}
