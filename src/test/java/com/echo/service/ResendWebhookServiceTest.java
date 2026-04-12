package com.echo.service;

import com.echo.config.AppProperties;
import com.echo.domain.user.User;
import com.echo.repository.ResendWebhookEventRepository;
import com.echo.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ResendWebhookServiceTest {

    @Mock
    ResendWebhookEventRepository resendWebhookEventRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    HttpServletRequest request;

    private final AppProperties appProperties = new AppProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    ResendWebhookService resendWebhookService;

    ResendWebhookServiceTest() {
        appProperties.getResend().setWebhookSecret("whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw");
    }

    @Test
    void handleWebhook_suppressesUserOnBounceEvent() throws Exception {
        resendWebhookService = new ResendWebhookService(
                appProperties,
                objectMapper,
                resendWebhookEventRepository,
                userRepository
        );

        String payload = """
                {
                  "type":"email.bounced",
                  "created_at":"2026-04-12T12:00:00Z",
                  "data":{
                    "email_id":"email_123",
                    "subject":"Reset your password",
                    "to":["user@echojournal.net"],
                    "bounce":{"type":"hard_bounce"}
                  }
                }
                """;

        String messageId = "msg_test_123";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = sign(messageId, timestamp, payload, appProperties.getResend().getWebhookSecret());

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@echojournal.net")
                .build();

        given(request.getHeader("svix-id")).willReturn(messageId);
        given(request.getHeader("svix-timestamp")).willReturn(timestamp);
        given(request.getHeader("svix-signature")).willReturn("v1," + signature);
        given(resendWebhookEventRepository.existsByWebhookMessageId(messageId)).willReturn(false);
        given(userRepository.findByEmail("user@echojournal.net")).willReturn(Optional.of(user));

        resendWebhookService.handleWebhook(payload, request);

        assertThat(user.isEmailSuppressed()).isTrue();
        assertThat(user.getEmailSuppressedReason()).isEqualTo("hard_bounce");
        then(resendWebhookEventRepository).should().save(any());
        then(userRepository).should().save(eq(user));
    }

    @Test
    void handleWebhook_rejectsInvalidSignature() {
        resendWebhookService = new ResendWebhookService(
                appProperties,
                objectMapper,
                resendWebhookEventRepository,
                userRepository
        );

        String payload = "{\"type\":\"email.delivered\",\"data\":{\"to\":[\"user@echojournal.net\"]}}";
        given(request.getHeader("svix-id")).willReturn("msg_invalid");
        given(request.getHeader("svix-timestamp")).willReturn(String.valueOf(Instant.now().getEpochSecond()));
        given(request.getHeader("svix-signature")).willReturn("v1,invalid");

        assertThatThrownBy(() -> resendWebhookService.handleWebhook(payload, request))
                .isInstanceOf(RuntimeException.class);
    }

    private String sign(String messageId, String timestamp, String payload, String webhookSecret) throws Exception {
        String secret = webhookSecret.substring("whsec_".length());
        byte[] secretBytes = Base64.getDecoder().decode(secret);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
        String signedContent = messageId + "." + timestamp + "." + payload;
        return Base64.getEncoder().encodeToString(mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8)));
    }
}
