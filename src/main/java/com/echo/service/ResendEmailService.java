package com.echo.service;

import com.echo.config.AppProperties;
import com.echo.domain.user.User;
import com.echo.repository.UserRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResendEmailService {

    private static final String RESEND_URL = "https://api.resend.com/emails";

    private final RestTemplate restTemplate;
    private final AppProperties appProperties;
    private final UserRepository userRepository;

    public boolean send(String to, String subject, String htmlBody) {
        return send(to, subject, htmlBody, null);
    }

    @CircuitBreaker(name = "resend-email", fallbackMethod = "sendFallback")
    public boolean send(String to, String subject, String htmlBody, String textBody) {
        AppProperties.Resend resend = appProperties.getResend();
        if (!StringUtils.hasText(to)) {
            log.warn("Email skipped because recipient is blank: subject={}", subject);
            return false;
        }
        User targetUser = userRepository.findByEmail(to.trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (targetUser != null && targetUser.isEmailSuppressed()) {
            log.warn("Email skipped because recipient is suppressed: userId={}, email={}, reason={}",
                    targetUser.getId(), targetUser.getEmail(), targetUser.getEmailSuppressedReason());
            return false;
        }
        if (!resend.isEnabled()) {
            log.info("Resend disabled, simulating email delivery: to={}, subject={}", to, subject);
            return true;
        }
        if (!StringUtils.hasText(resend.getApiKey())) {
            log.warn("Resend API key missing, email skipped: to={}, subject={}", to, subject);
            return false;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(resend.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new HashMap<>();
        payload.put("from", formatFromAddress(resend));
        payload.put("to", List.of(to.trim()));
        payload.put("subject", subject);
        payload.put("html", htmlBody);
        if (StringUtils.hasText(textBody)) {
            payload.put("text", textBody);
        }
        if (StringUtils.hasText(resend.getReplyToAddress())) {
            payload.put("reply_to", resend.getReplyToAddress().trim());
        }

        Map<?, ?> response = restTemplate.postForObject(RESEND_URL, new HttpEntity<>(payload, headers), Map.class);
        Object emailId = response == null ? null : response.get("id");
        log.info("Email delivered via Resend: to={}, subject={}, emailId={}", to, subject, emailId);
        return true;
    }

    @SuppressWarnings("unused")
    public boolean sendFallback(String to, String subject, String htmlBody, String textBody, Throwable throwable) {
        log.error("Email delivery fallback triggered: to={}, subject={}, error={}",
                to, subject, throwable.getMessage());
        return false;
    }

    private String formatFromAddress(AppProperties.Resend resend) {
        if (!StringUtils.hasText(resend.getFromName())) {
            return resend.getFromAddress();
        }
        return resend.getFromName() + " <" + resend.getFromAddress() + ">";
    }
}
