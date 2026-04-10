package com.echo.service;

import com.echo.domain.user.User;
import com.echo.domain.user.UserConsentLog;
import com.echo.dto.request.UpdateConsentRequest;
import com.echo.dto.response.ConsentStatusResponse;
import com.echo.exception.ResourceNotFoundException;
import com.echo.repository.UserConsentLogRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentService {

    private static final String CURRENT_PRIVACY_VERSION = "1.0";

    private final UserRepository          userRepository;
    private final UserConsentLogRepository consentLogRepository;

    @Transactional(readOnly = true)
    public ConsentStatusResponse getConsent(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ConsentStatusResponse.from(user);
    }

    @Transactional
    public ConsentStatusResponse updateConsent(UUID userId, UpdateConsentRequest request,
                                               String ipAddress, String userAgent) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (request.aiTrainingConsent() != null) {
            boolean wasGranted = user.isAiTrainingConsent();
            user.setAiTrainingConsent(request.aiTrainingConsent());
            user.setAiTrainingConsentAt(request.aiTrainingConsent() ? OffsetDateTime.now() : null);

            if (wasGranted != request.aiTrainingConsent()) {
                logConsent(user, "ai_training", request.aiTrainingConsent(), ipAddress, userAgent);
                log.info("AI training consent {} by user {}", request.aiTrainingConsent() ? "GRANTED" : "REVOKED", userId);
            }
        }

        if (request.kvkkExplicitConsent() != null) {
            boolean wasGranted = user.isKvkkExplicitConsent();
            user.setKvkkExplicitConsent(request.kvkkExplicitConsent());
            user.setKvkkConsentAt(request.kvkkExplicitConsent() ? OffsetDateTime.now() : null);
            user.setPrivacyPolicyVersion(request.kvkkExplicitConsent() ? CURRENT_PRIVACY_VERSION : null);

            if (wasGranted != request.kvkkExplicitConsent()) {
                logConsent(user, "kvkk", request.kvkkExplicitConsent(), ipAddress, userAgent);
            }
        }

        userRepository.save(user);
        return ConsentStatusResponse.from(user);
    }

    @Transactional
    public void requestAccountDeletion(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getAccountDeletionRequestedAt() != null) return;

        user.setAccountDeletionRequestedAt(OffsetDateTime.now());
        // auto-revoke AI training consent on deletion request
        if (user.isAiTrainingConsent()) {
            user.setAiTrainingConsent(false);
            user.setAiTrainingConsentAt(null);
            logConsent(user, "ai_training", false, null, "account_deletion_request");
        }
        userRepository.save(user);
        log.info("Account deletion requested by user {}", userId);
    }

    private void logConsent(User user, String type, boolean granted, String ip, String userAgent) {
        consentLogRepository.save(UserConsentLog.builder()
                .user(user)
                .consentType(type)
                .granted(granted)
                .ipAddress(ip)
                .userAgent(userAgent)
                .build());
    }
}
