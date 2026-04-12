package com.echo.service;

import com.echo.config.AppProperties;
import com.echo.domain.token.PasswordResetToken;
import com.echo.domain.user.User;
import com.echo.repository.PasswordResetTokenRepository;
import com.echo.repository.RefreshTokenRepository;
import com.echo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    RefreshTokenRepository refreshTokenRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    ResendEmailService resendEmailService;
    @Mock
    EmailTemplateService emailTemplateService;
    @Mock
    Environment environment;
    @Mock
    AppProperties appProperties;

    @InjectMocks
    PasswordResetService passwordResetService;

    @Test
    void requestReset_discardsTokenWhenEmailDeliveryFails() {
        User user = buildUser();
        given(userRepository.findByEmail(user.getEmail())).willReturn(Optional.of(user));
        given(passwordResetTokenRepository.countByUserIdAndCreatedAtAfter(eq(user.getId()), any())).willReturn(0L);
        given(passwordResetTokenRepository.save(any(PasswordResetToken.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(emailTemplateService.passwordResetSubject("en")).willReturn("subject");
        given(emailTemplateService.passwordResetHtml(anyString(), eq("en"))).willReturn("html");
        given(emailTemplateService.passwordResetText(anyString(), eq("en"))).willReturn("text");
        given(appProperties.getResend()).willReturn(new AppProperties.Resend());
        given(resendEmailService.send(eq(user.getEmail()), eq("subject"), eq("html"), eq("text"))).willReturn(false);

        passwordResetService.requestReset(user.getEmail(), "127.0.0.1");

        then(passwordResetTokenRepository).should().delete(any(PasswordResetToken.class));
    }

    @Test
    void resetPassword_withWrongCode_incrementsAttempts() throws Exception {
        User user = buildUser();
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .codeHash(hash("123456"))
                .expiresAt(OffsetDateTime.now().plusMinutes(10))
                .attempts(0)
                .used(false)
                .build();

        given(userRepository.findByEmail(user.getEmail())).willReturn(Optional.of(user));
        given(passwordResetTokenRepository.findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId()))
                .willReturn(Optional.of(token));

        assertThatThrownBy(() -> passwordResetService.resetPassword(user.getEmail(), "654321", "NewPass123!"))
                .isInstanceOf(IllegalArgumentException.class);

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        then(passwordResetTokenRepository).should().save(captor.capture());
        assertThat(captor.getValue().getAttempts()).isEqualTo(1);
    }

    @Test
    void resetPassword_success_updatesPasswordAndRevokesRefreshTokens() throws Exception {
        User user = buildUser();
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .codeHash(hash("123456"))
                .expiresAt(OffsetDateTime.now().plusMinutes(10))
                .attempts(0)
                .used(false)
                .build();

        given(userRepository.findByEmail(user.getEmail())).willReturn(Optional.of(user));
        given(passwordResetTokenRepository.findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId()))
                .willReturn(Optional.of(token));
        given(passwordEncoder.encode("NewPass123!")).willReturn("encoded");

        passwordResetService.resetPassword(user.getEmail(), "123456", "NewPass123!");

        assertThat(user.getPasswordHash()).isEqualTo("encoded");
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(token.isUsed()).isTrue();
        then(refreshTokenRepository).should().deleteByUserId(user.getId());
    }

    private User buildUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("user@echojournal.net")
                .preferredLanguage("en")
                .passwordLoginEnabled(true)
                .build();
    }

    private String hash(String rawValue) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return Base64.getEncoder().encodeToString(digest.digest(rawValue.getBytes(StandardCharsets.UTF_8)));
    }
}
