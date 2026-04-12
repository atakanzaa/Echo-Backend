package com.echo.service;

import com.echo.config.AppProperties;
import com.echo.domain.token.PasswordResetToken;
import com.echo.domain.user.User;
import com.echo.repository.PasswordResetTokenRepository;
import com.echo.repository.RefreshTokenRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int MAX_RESETS_PER_HOUR = 3;
    private static final int MAX_ATTEMPTS = 5;
    private static final int OTP_LENGTH = 6;
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ResendEmailService resendEmailService;
    private final EmailTemplateService emailTemplateService;
    private final Environment environment;
    private final AppProperties appProperties;

    @Transactional
    public void requestReset(String email, String clientIp) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null || !user.isPasswordLoginEnabled()) {
            return;
        }

        OffsetDateTime oneHourAgo = OffsetDateTime.now().minusHours(1);
        long sentCount = passwordResetTokenRepository.countByUserIdAndCreatedAtAfter(user.getId(), oneHourAgo);
        if (sentCount >= MAX_RESETS_PER_HOUR) {
            log.warn("Password reset rate limit reached: userId={}, ip={}", user.getId(), clientIp);
            return;
        }

        String code = generateCode();
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .codeHash(hashValue(code))
                .expiresAt(OffsetDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .build();
        passwordResetTokenRepository.save(token);

        boolean delivered = resendEmailService.send(
                user.getEmail(),
                emailTemplateService.passwordResetSubject(user.getPreferredLanguage()),
                emailTemplateService.passwordResetHtml(code, user.getPreferredLanguage()),
                emailTemplateService.passwordResetText(code, user.getPreferredLanguage())
        );
        if (!appProperties.getResend().isEnabled() && environment.matchesProfiles("dev", "test")) {
            log.info("Password reset OTP generated for local development: email={}, code={}", user.getEmail(), code);
        }
        if (!delivered) {
            passwordResetTokenRepository.delete(token);
            log.warn("Password reset token discarded because email delivery failed: userId={}", user.getId());
        }
    }

    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(this::invalidResetRequest);

        if (!user.isPasswordLoginEnabled()) {
            throw invalidResetRequest();
        }

        PasswordResetToken token = passwordResetTokenRepository
                .findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId())
                .orElseThrow(this::invalidResetRequest);

        if (token.isExpired() || token.getAttempts() >= MAX_ATTEMPTS) {
            throw invalidResetRequest();
        }

        if (!hashMatches(code, token.getCodeHash())) {
            token.setAttempts(token.getAttempts() + 1);
            passwordResetTokenRepository.save(token);
            throw invalidResetRequest();
        }

        token.setUsed(true);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setEmailVerified(true);

        passwordResetTokenRepository.save(token);
        userRepository.save(user);
        refreshTokenRepository.deleteByUserId(user.getId());
    }

    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void purgeExpiredTokens() {
        passwordResetTokenRepository.deleteAllExpired(OffsetDateTime.now());
    }

    private IllegalArgumentException invalidResetRequest() {
        return new IllegalArgumentException("Invalid reset request");
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, OTP_LENGTH);
        return String.format(Locale.ROOT, "%0" + OTP_LENGTH + "d", SECURE_RANDOM.nextInt(bound));
    }

    private String hashValue(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private boolean hashMatches(String rawValue, String storedHash) {
        return MessageDigest.isEqual(
                hashValue(rawValue).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8)
        );
    }
}
