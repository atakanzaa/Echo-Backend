package com.echo.service;

import com.echo.domain.subscription.Subscription;
import com.echo.domain.subscription.SubscriptionEvent;
import com.echo.domain.subscription.SubscriptionStatus;
import com.echo.domain.subscription.SubscriptionTier;
import com.echo.domain.user.User;
import com.echo.dto.response.SubscriptionResponse;
import com.echo.exception.ResourceNotFoundException;
import com.echo.event.PurchaseConfirmedEvent;
import com.echo.repository.SubscriptionEventRepository;
import com.echo.repository.SubscriptionRepository;
import com.echo.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionEventRepository subscriptionEventRepository;
    private final UserRepository userRepository;
    private final AppleStoreKitService appleStoreKitService;
    private final EntitlementService entitlementService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SubscriptionResponse verifyAndActivate(UUID userId, String signedTransaction) {
        AppleStoreKitService.AppleTransactionPayload tx =
                appleStoreKitService.verifyAndDecodeTransaction(signedTransaction);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String originalTransactionId = firstNonBlank(tx.originalTransactionId(), tx.transactionId());
        if (!StringUtils.hasText(originalTransactionId)) {
            throw new IllegalArgumentException("Apple transaction missing originalTransactionId");
        }

        Subscription subscription = subscriptionRepository.findByOriginalTransactionId(originalTransactionId)
                .orElseGet(() -> subscriptionRepository.findByUserId(userId).orElseGet(Subscription::new));

        boolean isNew = subscription.getId() == null;
        upsertSubscriptionFromTransaction(subscription, user, tx, signedTransaction, SubscriptionStatus.ACTIVE);
        Subscription saved = subscriptionRepository.save(subscription);

        syncUserTier(user, saved);
        userRepository.save(user);

        String eventType = isNew ? "SUBSCRIBED" : "RENEWED";
        saveEvent(saved, user, eventType, tx.rawClaims());
        entitlementService.invalidateCache(userId);
        eventPublisher.publishEvent(new PurchaseConfirmedEvent(
                user.getId(),
                user.getEmail(),
                user.getPreferredLanguage(),
                saved.getProductId(),
                OffsetDateTime.now()
        ));

        return toResponse(user, saved);
    }

    @Transactional
    public SubscriptionResponse restore(UUID userId, String signedTransaction) {
        AppleStoreKitService.AppleTransactionPayload tx =
                appleStoreKitService.verifyAndDecodeTransaction(signedTransaction);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String originalTransactionId = firstNonBlank(tx.originalTransactionId(), tx.transactionId());
        if (!StringUtils.hasText(originalTransactionId)) {
            throw new IllegalArgumentException("Apple transaction missing originalTransactionId");
        }

        Subscription subscription = subscriptionRepository.findByOriginalTransactionId(originalTransactionId)
                .orElseGet(Subscription::new);

        upsertSubscriptionFromTransaction(subscription, user, tx, signedTransaction, SubscriptionStatus.ACTIVE);
        Subscription saved = subscriptionRepository.save(subscription);

        syncUserTier(user, saved);
        userRepository.save(user);

        saveEvent(saved, user, "RESTORED", tx.rawClaims());
        entitlementService.invalidateCache(userId);

        return toResponse(user, saved);
    }

    @Transactional
    public void handleAppleNotification(String signedPayload) {
        AppleStoreKitService.AppleNotificationPayload notification =
                appleStoreKitService.decodeNotification(signedPayload);

        AppleStoreKitService.AppleTransactionPayload tx = notification.transaction();
        if (tx == null || !StringUtils.hasText(firstNonBlank(tx.originalTransactionId(), tx.transactionId()))) {
            log.warn("Apple notification ignored: missing signedTransactionInfo or originalTransactionId");
            return;
        }

        String originalTransactionId = firstNonBlank(tx.originalTransactionId(), tx.transactionId());
        Subscription subscription = subscriptionRepository.findByOriginalTransactionId(originalTransactionId)
                .orElse(null);

        if (subscription == null) {
            log.warn("Apple notification ignored: subscription not linked yet, originalTransactionId={}",
                    originalTransactionId);
            return;
        }

        User user = subscription.getUser();
        String type = upper(notification.notificationType());
        String subtype = upper(notification.subtype());

        switch (type) {
            case "SUBSCRIBED", "INITIAL_BUY" -> {
                upsertSubscriptionFromTransaction(subscription, user, tx, subscription.getLatestReceipt(), SubscriptionStatus.ACTIVE);
                saveEvent(subscription, user, "SUBSCRIBED", notification.rawPayload());
            }
            case "DID_RENEW" -> {
                upsertSubscriptionFromTransaction(subscription, user, tx, subscription.getLatestReceipt(), SubscriptionStatus.ACTIVE);
                saveEvent(subscription, user, "RENEWED", notification.rawPayload());
            }
            case "DID_FAIL_TO_RENEW" -> {
                upsertSubscriptionFromTransaction(subscription, user, tx, subscription.getLatestReceipt(), SubscriptionStatus.BILLING_RETRY);
                saveEvent(subscription, user, "BILLING_RETRY", notification.rawPayload());
            }
            case "GRACE_PERIOD_EXPIRED", "EXPIRED" -> {
                upsertSubscriptionFromTransaction(subscription, user, tx, subscription.getLatestReceipt(), SubscriptionStatus.EXPIRED);
                saveEvent(subscription, user, "EXPIRED", notification.rawPayload());
            }
            case "REVOKE", "REVOKED" -> {
                upsertSubscriptionFromTransaction(subscription, user, tx, subscription.getLatestReceipt(), SubscriptionStatus.REVOKED);
                saveEvent(subscription, user, "REVOKED", notification.rawPayload());
            }
            case "DID_CHANGE_RENEWAL_STATUS" -> {
                if ("AUTO_RENEW_DISABLED".equals(subtype)) {
                    subscription.setAutoRenewEnabled(false);
                    saveEvent(subscription, user, "CANCELLED", notification.rawPayload());
                } else if ("AUTO_RENEW_ENABLED".equals(subtype)) {
                    subscription.setAutoRenewEnabled(true);
                    saveEvent(subscription, user, "RESTORED", notification.rawPayload());
                } else {
                    saveEvent(subscription, user, "RENEWED", notification.rawPayload());
                }
            }
            default -> {
                // Some notifications are informative and do not require entitlement changes.
                log.info("Unhandled Apple notification type: type={}, subtype={}", type, subtype);
                return;
            }
        }

        subscriptionRepository.save(subscription);
        syncUserTier(user, subscription);
        userRepository.save(user);
        entitlementService.invalidateCache(user.getId());
    }

    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void processExpiredSubscriptions() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Subscription> expiredCandidates = subscriptionRepository.findByStatusInAndExpiresDateBefore(
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.GRACE_PERIOD, SubscriptionStatus.BILLING_RETRY),
                now
        );

        if (expiredCandidates.isEmpty()) {
            return;
        }

        // batch-update all subscriptions and users, then save events
        List<SubscriptionEvent> events = new ArrayList<>();
        for (Subscription subscription : expiredCandidates) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            User user = subscription.getUser();
            syncUserTier(user, subscription);
            events.add(SubscriptionEvent.builder()
                    .subscription(subscription)
                    .user(user)
                    .eventType("EXPIRED")
                    .originalTransactionId(subscription.getOriginalTransactionId())
                    .productId(subscription.getProductId())
                    .environment(subscription.getEnvironment())
                    .rawPayload(toJson(Map.of("source", "scheduler", "expiredAt", now.toString())))
                    .build());
            entitlementService.invalidateCache(user.getId());
        }
        subscriptionRepository.saveAll(expiredCandidates);
        subscriptionEventRepository.saveAll(events);

        log.info("Expired subscription sweep completed: count={}", expiredCandidates.size());
    }

    private void upsertSubscriptionFromTransaction(Subscription subscription,
                                                   User user,
                                                   AppleStoreKitService.AppleTransactionPayload tx,
                                                   String latestReceipt,
                                                   SubscriptionStatus status) {
        String originalTransactionId = firstNonBlank(tx.originalTransactionId(), tx.transactionId());

        subscription.setUser(user);
        subscription.setOriginalTransactionId(originalTransactionId);
        subscription.setProductId(tx.productId());
        subscription.setStatus(status);
        subscription.setPurchaseDate(firstNonNull(tx.purchaseDate(), OffsetDateTime.now()));
        subscription.setExpiresDate(firstNonNull(tx.expiresDate(), OffsetDateTime.now().plusMonths(1)));
        subscription.setGracePeriodExpiresDate(tx.gracePeriodExpiresDate());
        subscription.setEnvironment(firstNonBlank(tx.environment(), "Production"));

        if (StringUtils.hasText(latestReceipt)) {
            subscription.setLatestReceipt(latestReceipt);
        }

        if (subscription.getAutoRenewProductId() == null) {
            subscription.setAutoRenewProductId(tx.productId());
        }
    }

    private void syncUserTier(User user, Subscription subscription) {
        boolean hasPremiumAccess = subscription.getStatus() != null
                && subscription.getStatus().grantsAccess()
                && SubscriptionTier.fromProductId(subscription.getProductId()) == SubscriptionTier.PREMIUM;

        if (hasPremiumAccess) {
            user.setSubscriptionTier(SubscriptionTier.PREMIUM.name());
            user.setAdsEnabled(false);
        } else {
            user.setSubscriptionTier(SubscriptionTier.FREE.name());
            user.setAdsEnabled(true);
        }
    }

    private void saveEvent(Subscription subscription, User user, String eventType, Object rawPayload) {
        subscriptionEventRepository.save(SubscriptionEvent.builder()
                .subscription(subscription)
                .user(user)
                .eventType(eventType)
                .originalTransactionId(subscription != null ? subscription.getOriginalTransactionId() : null)
                .productId(subscription != null ? subscription.getProductId() : null)
                .environment(subscription != null ? subscription.getEnvironment() : null)
                .rawPayload(toJson(rawPayload))
                .build());
    }

    private String toJson(Object rawPayload) {
        if (rawPayload == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(rawPayload);
        } catch (Exception ex) {
            log.warn("Failed to serialize subscription payload: {}", ex.getMessage());
            return String.valueOf(rawPayload);
        }
    }

    private SubscriptionResponse toResponse(User user, Subscription subscription) {
        return new SubscriptionResponse(
                user.getSubscriptionTier(),
                subscription.getStatus().name(),
                subscription.getProductId(),
                subscription.getOriginalTransactionId(),
                subscription.getPurchaseDate(),
                subscription.getExpiresDate(),
                subscription.isAutoRenewEnabled(),
                user.isAdsEnabled()
        );
    }

    private String upper(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return second;
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }
}
