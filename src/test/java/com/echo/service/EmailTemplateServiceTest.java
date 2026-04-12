package com.echo.service;

import com.echo.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateServiceTest {

    private final AppProperties appProperties = new AppProperties();
    private final EmailTemplateService service = new EmailTemplateService(appProperties);

    EmailTemplateServiceTest() {
        appProperties.getResend().setSupportAddress("support@echojournal.net");
    }

    @Test
    void passwordResetTemplate_usesEnglishForRegionalLocale() {
        String subject = service.passwordResetSubject("en-US");
        String html = service.passwordResetHtml("123456", "en-US");
        String text = service.passwordResetText("123456", "en-US");

        assertThat(subject).isEqualTo("Your Echo password reset code");
        assertThat(html).contains("Reset your password");
        assertThat(text).contains("support@echojournal.net");
    }

    @Test
    void passwordResetTemplate_defaultsToTurkish() {
        String subject = service.passwordResetSubject("tr-TR");
        String html = service.passwordResetHtml("123456", "tr-TR");

        assertThat(subject).isEqualTo("Echo şifre sıfırlama kodunuz");
        assertThat(html).contains("Şifrenizi sıfırlayın");
        assertThat(html).contains("123456");
    }

    @Test
    void purchaseConfirmationText_usesLocalizedPlanName() {
        String text = service.purchaseConfirmationText(
                "echo_premium_yearly",
                "tr",
                OffsetDateTime.parse("2026-04-12T12:00:00Z")
        );

        assertThat(text).contains("Echo Premium Yıllık");
        assertThat(text).contains("support@echojournal.net");
    }
}
