package com.echo.service;

import com.echo.config.AppProperties;
import com.echo.domain.notification.NotificationDeliveryStatus;
import com.echo.domain.subscription.SubscriptionStatus;
import com.echo.dto.response.AdminErrorResponse;
import com.echo.dto.response.AdminSummaryResponse;
import com.echo.dto.response.PagedResponse;
import com.echo.repository.AiJobDlqRepository;
import com.echo.repository.JournalEntryRepository;
import com.echo.repository.NotificationDeliveryRepository;
import com.echo.repository.PushTokenRepository;
import com.echo.repository.SubscriptionRepository;
import com.echo.repository.SystemErrorLogRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMonitorService {

    private static final Duration WINDOW_24H = Duration.ofHours(24);
    private static final Duration WINDOW_7D = Duration.ofDays(7);
    private static final int STUCK_THRESHOLD_MINUTES = 20;

    private static final List<NotificationDeliveryStatus> FAILED_STATUSES = List.of(
            NotificationDeliveryStatus.FAILED,
            NotificationDeliveryStatus.REJECTED);
    private static final List<NotificationDeliveryStatus> SKIPPED_STATUSES = List.of(
            NotificationDeliveryStatus.SKIPPED_DISABLED,
            NotificationDeliveryStatus.SKIPPED_QUIET_HOURS,
            NotificationDeliveryStatus.SKIPPED_RATE_LIMITED,
            NotificationDeliveryStatus.SKIPPED_NO_TOKEN);

    private final UserRepository userRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final AiJobDlqRepository aiJobDlqRepository;
    private final PushTokenRepository pushTokenRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SystemErrorLogRepository errorLogRepository;
    private final AppProperties props;

    public AdminSummaryResponse getSummary() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime since24h = now.minus(WINDOW_24H);
        OffsetDateTime since7d = now.minus(WINDOW_7D);
        OffsetDateTime startOfToday = now.truncatedTo(ChronoUnit.DAYS);
        OffsetDateTime stuckBefore = now.minusMinutes(STUCK_THRESHOLD_MINUTES);

        return new AdminSummaryResponse(
                userRepository.count(),
                userRepository.countByCreatedAtAfter(since24h),
                userRepository.countByCreatedAtAfter(since7d),
                journalEntryRepository.countDistinctActiveUsersSince(startOfToday),
                journalEntryRepository.count(),
                journalEntryRepository.countCreatedSince(since24h),
                journalEntryRepository.countStuck(stuckBefore),
                journalEntryRepository.countFailed(),
                deliveryRepository.countByStatusAndCreatedAtAfter(NotificationDeliveryStatus.SENT, since24h),
                deliveryRepository.countByStatusInAndCreatedAtAfter(FAILED_STATUSES, since24h),
                deliveryRepository.countByStatusInAndCreatedAtAfter(SKIPPED_STATUSES, since24h),
                deliveryRepository.countByStatus(NotificationDeliveryStatus.PENDING),
                pushTokenRepository.countByActiveTrue(),
                errorLogRepository.countByOccurredAtAfter(since24h),
                aiJobDlqRepository.countByResolvedAtIsNull(),
                subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE),
                props.getVersion(),
                true,
                now
        );
    }

    public PagedResponse<AdminErrorResponse> getRecentErrors(Pageable pageable) {
        return PagedResponse.from(
                errorLogRepository.findAllByOrderByOccurredAtDesc(pageable),
                AdminErrorResponse::from);
    }
}
