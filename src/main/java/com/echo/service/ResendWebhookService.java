package com.echo.service;

import com.echo.config.AppProperties;
import com.echo.domain.email.ResendWebhookEvent;
import com.echo.domain.user.User;
import com.echo.exception.ServiceUnavailableException;
import com.echo.exception.UnauthorizedException;
import com.echo.repository.ResendWebhookEventRepository;
import com.echo.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResendWebhookService {

    private static final long TIMESTAMP_TOLERANCE_SECONDS = 300;

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final ResendWebhookEventRepository resendWebhookEventRepository;
    private final UserRepository userRepository;

    @Transactional
    public void handleWebhook(String payload, HttpServletRequest request) {
        String webhookSecret = appProperties.getResend().getWebhookSecret();
        if (!StringUtils.hasText(webhookSecret)) {
            throw new ServiceUnavailableException("Resend webhook secret is not configured");
        }

        String messageId = firstHeader(request, "svix-id", "webhook-id");
        String timestamp = firstHeader(request, "svix-timestamp", "webhook-timestamp");
        String signature = firstHeader(request, "svix-signature", "webhook-signature");

        verifyRequest(payload, messageId, timestamp, signature, webhookSecret);

        if (resendWebhookEventRepository.existsByWebhookMessageId(messageId)) {
            log.info("Resend webhook ignored as duplicate: messageId={}", messageId);
            return;
        }

        JsonNode root = parsePayload(payload);
        String eventType = root.path("type").asText("");
        JsonNode data = root.path("data");
        List<String> recipients = extractRecipients(data.path("to"));
        String primaryRecipient = recipients.isEmpty() ? null : normalizeEmail(recipients.get(0));

        resendWebhookEventRepository.save(ResendWebhookEvent.builder()
                .webhookMessageId(messageId)
                .eventType(eventType)
                .emailId(text(data, "email_id"))
                .recipientEmail(primaryRecipient)
                .subject(text(data, "subject"))
                .createdAt(parseOffsetDateTime(root.path("created_at").asText(null)))
                .rawPayload(payload)
                .build());

        if (isSuppressionEvent(eventType)) {
            for (String recipient : recipients) {
                suppressRecipient(recipient, suppressionReason(eventType, data));
            }
        }

        log.info("Resend webhook processed: messageId={}, eventType={}, recipients={}",
                messageId, eventType, recipients.size());
    }

    private void verifyRequest(String payload,
                               String messageId,
                               String timestamp,
                               String signatureHeader,
                               String webhookSecret) {
        if (!StringUtils.hasText(messageId) || !StringUtils.hasText(timestamp) || !StringUtils.hasText(signatureHeader)) {
            throw new UnauthorizedException("Missing webhook signature headers");
        }

        long webhookTimestamp;
        try {
            webhookTimestamp = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            throw new UnauthorizedException("Invalid webhook timestamp");
        }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - webhookTimestamp) > TIMESTAMP_TOLERANCE_SECONDS) {
            throw new UnauthorizedException("Webhook timestamp is outside the allowed tolerance");
        }

        String signedContent = messageId + "." + timestamp + "." + payload;
        byte[] secretBytes = decodeSecret(webhookSecret);
        String expected = sign(signedContent, secretBytes);

        boolean matched = extractSignatures(signatureHeader).stream()
                .map(value -> value.getBytes(StandardCharsets.UTF_8))
                .anyMatch(candidate -> MessageDigest.isEqual(candidate, expected.getBytes(StandardCharsets.UTF_8)));

        if (!matched) {
            throw new UnauthorizedException("Invalid webhook signature");
        }
    }

    private byte[] decodeSecret(String webhookSecret) {
        String secret = webhookSecret.startsWith("whsec_")
                ? webhookSecret.substring("whsec_".length())
                : webhookSecret;
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            throw new ServiceUnavailableException("Resend webhook secret is invalid");
        }
    }

    private String sign(String signedContent, byte[] secretBytes) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to verify webhook signature", ex);
        }
    }

    private List<String> extractSignatures(String signatureHeader) {
        List<String> signatures = new ArrayList<>();
        for (String part : signatureHeader.trim().split("\\s+")) {
            int commaIndex = part.indexOf(',');
            if (commaIndex > 0 && commaIndex + 1 < part.length()) {
                signatures.add(part.substring(commaIndex + 1));
            }
        }
        return signatures;
    }

    private JsonNode parsePayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid Resend webhook payload");
        }
    }

    private void suppressRecipient(String email, String reason) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        userRepository.findByEmail(normalizeEmail(email)).ifPresent(user -> {
            user.setEmailSuppressed(true);
            user.setEmailSuppressedReason(reason);
            user.setEmailSuppressedAt(OffsetDateTime.now());
            userRepository.save(user);
            log.warn("User email suppressed after Resend webhook: userId={}, email={}, reason={}",
                    user.getId(), user.getEmail(), reason);
        });
    }

    private boolean isSuppressionEvent(String eventType) {
        return "email.bounced".equals(eventType)
                || "email.complained".equals(eventType)
                || "email.suppressed".equals(eventType);
    }

    private String suppressionReason(String eventType, JsonNode data) {
        if ("email.bounced".equals(eventType)) {
            return text(data.path("bounce"), "type", "BOUNCED");
        }
        if ("email.suppressed".equals(eventType)) {
            return text(data.path("suppressed"), "type", "SUPPRESSED");
        }
        if ("email.complained".equals(eventType)) {
            return "COMPLAINED";
        }
        return "UNKNOWN";
    }

    private List<String> extractRecipients(JsonNode recipientsNode) {
        List<String> recipients = new ArrayList<>();
        if (recipientsNode == null || recipientsNode.isMissingNode() || recipientsNode.isNull()) {
            return recipients;
        }
        if (recipientsNode.isTextual()) {
            recipients.add(recipientsNode.asText());
            return recipients;
        }
        if (recipientsNode.isArray()) {
            recipientsNode.forEach(node -> {
                if (node.isTextual()) {
                    recipients.add(node.asText());
                }
            });
        }
        return recipients;
    }

    private OffsetDateTime parseOffsetDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        return text(node, field, null);
    }

    private String text(JsonNode node, String field, String defaultValue) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }
        String value = node.path(field).asText(null);
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String firstHeader(HttpServletRequest request, String preferredName, String fallbackName) {
        String value = request.getHeader(preferredName);
        if (StringUtils.hasText(value)) {
            return value;
        }
        return request.getHeader(fallbackName);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
