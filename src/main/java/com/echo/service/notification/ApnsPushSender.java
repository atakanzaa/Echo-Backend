package com.echo.service.notification;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.echo.config.AppProperties;
import com.echo.domain.notification.ApnsEnvironment;
import com.echo.domain.notification.PushToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class ApnsPushSender {

    private static final String ERROR_DISABLED = "APNS_DISABLED";
    private static final String ERROR_ENV_MISMATCH = "APNS_ENV_MISMATCH";
    private static final String ERROR_TIMEOUT = "APNS_TIMEOUT";
    private static final String ERROR_TRANSPORT = "APNS_TRANSPORT";

    private final Optional<ApnsClient> apnsClient;
    private final ApnsPayloadBuilder payloadBuilder;
    private final AppProperties props;

    public ApnsPushSender(Optional<ApnsClient> apnsClient,
                          ApnsPayloadBuilder payloadBuilder,
                          AppProperties props) {
        this.apnsClient = apnsClient;
        this.payloadBuilder = payloadBuilder;
        this.props = props;
    }

    public ApnsPushResult send(PushToken token, ApnsPushPayload payload) {
        if (apnsClient.isEmpty() || !props.getApns().isEnabled()) {
            return ApnsPushResult.rejected(ERROR_DISABLED, "APNs not enabled in configuration", false);
        }

        ApnsEnvironment configured = ApnsEnvironment.fromClient(props.getApns().getEnvironment());
        if (token.getEnvironment() != configured) {
            return ApnsPushResult.rejected(
                    ERROR_ENV_MISMATCH,
                    "Token environment " + token.getEnvironment()
                            + " does not match configured server " + configured,
                    false);
        }

        String topic = props.getApns().getTopic();
        String json = payloadBuilder.build(payload);
        Instant deadline = Instant.now().plusSeconds(props.getApns().getResponseTimeoutSeconds());

        SimpleApnsPushNotification apnsNotification = new SimpleApnsPushNotification(
                token.getToken(),
                topic,
                json,
                Instant.now().plus(Duration.ofDays(7)),
                com.eatthepath.pushy.apns.DeliveryPriority.IMMEDIATE,
                com.eatthepath.pushy.apns.PushType.ALERT
        );

        try {
            PushNotificationResponse<SimpleApnsPushNotification> response = apnsClient.get()
                    .sendNotification(apnsNotification)
                    .get(Math.max(1, Duration.between(Instant.now(), deadline).getSeconds()), TimeUnit.SECONDS);

            if (response.isAccepted()) {
                return ApnsPushResult.accepted(response.getApnsId() == null ? null : response.getApnsId().toString());
            }

            String reason = response.getRejectionReason().orElse("UNKNOWN");
            boolean tokenInvalid = isInvalidTokenReason(reason)
                    || response.getTokenInvalidationTimestamp().isPresent();
            return ApnsPushResult.rejected(reason, reason, tokenInvalid);

        } catch (TimeoutException ex) {
            log.warn("APNs send timed out token={}", maskToken(token.getToken()));
            return ApnsPushResult.rejected(ERROR_TIMEOUT, "APNs response timed out", false);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ApnsPushResult.rejected(ERROR_TRANSPORT, "Interrupted while waiting for APNs", false);
        } catch (ExecutionException ex) {
            String message = ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage();
            log.warn("APNs send failed token={} error={}", maskToken(token.getToken()), message);
            return ApnsPushResult.rejected(ERROR_TRANSPORT, message, false);
        }
    }

    private boolean isInvalidTokenReason(String reason) {
        if (reason == null) return false;
        return "BadDeviceToken".equals(reason)
                || "Unregistered".equals(reason)
                || "DeviceTokenNotForTopic".equals(reason);
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "***";
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }
}
