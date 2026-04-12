package com.echo.service;

import com.echo.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EmailTemplateService {

    private final AppProperties appProperties;

    public String passwordResetSubject(String language) {
        return isEnglish(language)
                ? "Your Echo password reset code"
                : "Echo şifre sıfırlama kodunuz";
    }

    public String passwordResetHtml(String code, String language) {
        if (isEnglish(language)) {
            return """
                    <html>
                      <body style="margin:0;padding:24px;background:#f6f1ff;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:#241b35;">
                        <div style="display:none;max-height:0;overflow:hidden;opacity:0;">Use this 6-digit code to reset your Echo password. The code expires in 10 minutes.</div>
                        <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:24px;padding:32px;border:1px solid #eadfff;box-shadow:0 10px 30px rgba(63,24,108,0.08);">
                          <div style="font-size:28px;font-weight:700;margin-bottom:16px;">Echo Journal</div>
                          <div style="font-size:22px;font-weight:700;margin-bottom:12px;">Reset your password</div>
                          <div style="font-size:16px;line-height:1.6;margin-bottom:12px;">We received a request to reset your password. Enter the 6-digit verification code below in the app.</div>
                          <div style="font-size:34px;letter-spacing:10px;font-weight:800;text-align:center;background:#f4ecff;border-radius:16px;padding:20px 16px;margin:24px 0;">%s</div>
                          <div style="font-size:14px;line-height:1.7;color:#5d4f76;margin-bottom:12px;">This code is valid for 10 minutes. For your security, do not share it with anyone.</div>
                          <div style="font-size:14px;line-height:1.7;color:#5d4f76;">If you did not request this change, you can safely ignore this email.%s</div>
                        </div>
                      </body>
                    </html>
                    """.formatted(code, supportFooterHtml(language));
        }

        return """
                <html>
                  <body style="margin:0;padding:24px;background:#f6f1ff;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:#241b35;">
                    <div style="display:none;max-height:0;overflow:hidden;opacity:0;">Echo şifrenizi sıfırlamak için bu 6 haneli kodu kullanın. Kod 10 dakika içinde sona erer.</div>
                    <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:24px;padding:32px;border:1px solid #eadfff;box-shadow:0 10px 30px rgba(63,24,108,0.08);">
                      <div style="font-size:28px;font-weight:700;margin-bottom:16px;">Echo Journal</div>
                      <div style="font-size:22px;font-weight:700;margin-bottom:12px;">Şifrenizi sıfırlayın</div>
                      <div style="font-size:16px;line-height:1.6;margin-bottom:12px;">Şifrenizi sıfırlamak için bir istek aldık. Aşağıdaki 6 haneli doğrulama kodunu uygulamada girin.</div>
                      <div style="font-size:34px;letter-spacing:10px;font-weight:800;text-align:center;background:#f4ecff;border-radius:16px;padding:20px 16px;margin:24px 0;">%s</div>
                      <div style="font-size:14px;line-height:1.7;color:#5d4f76;margin-bottom:12px;">Bu kod 10 dakika boyunca geçerlidir. Güvenliğiniz için kodu kimseyle paylaşmayın.</div>
                      <div style="font-size:14px;line-height:1.7;color:#5d4f76;">Bu isteği siz yapmadıysanız bu e-postayı güvenle yok sayabilirsiniz.%s</div>
                    </div>
                  </body>
                </html>
                """.formatted(code, supportFooterHtml(language));
    }

    public String passwordResetText(String code, String language) {
        if (isEnglish(language)) {
            return """
                    Echo Journal

                    Reset your password

                    Use this 6-digit verification code in the app:
                    %s

                    This code is valid for 10 minutes. Do not share it with anyone.
                    If you did not request this change, you can safely ignore this email.%s
                    """.formatted(code, supportFooterText(language));
        }

        return """
                Echo Journal

                Şifrenizi sıfırlayın

                Uygulamada bu 6 haneli doğrulama kodunu kullanın:
                %s

                Bu kod 10 dakika boyunca geçerlidir. Kimseyle paylaşmayın.
                Bu isteği siz yapmadıysanız bu e-postayı güvenle yok sayabilirsiniz.%s
                """.formatted(code, supportFooterText(language));
    }

    public String purchaseConfirmationSubject(String language) {
        return isEnglish(language)
                ? "Your Echo Premium is active"
                : "Echo Premium üyeliğiniz aktif";
    }

    public String purchaseConfirmationHtml(String productId, String language, OffsetDateTime activatedAt) {
        String productName = productLabel(productId, language);
        String date = formatDate(activatedAt, language);

        if (isEnglish(language)) {
            return """
                    <html>
                      <body style="margin:0;padding:24px;background:#eef7f3;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:#163126;">
                        <div style="display:none;max-height:0;overflow:hidden;opacity:0;">Your Echo Premium membership is active and ready to use.</div>
                        <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:24px;padding:32px;border:1px solid #d7eee3;box-shadow:0 10px 30px rgba(16,76,52,0.08);">
                          <div style="font-size:28px;font-weight:700;margin-bottom:16px;">Echo Journal</div>
                          <div style="font-size:22px;font-weight:700;margin-bottom:12px;">Premium activated</div>
                          <div style="font-size:16px;line-height:1.6;margin-bottom:18px;">Your Premium membership is now active. You can continue with the full Echo experience.</div>
                          <div style="background:#f1faf6;border-radius:16px;padding:18px 20px;margin-bottom:20px;">
                            <div style="font-size:14px;color:#557668;margin-bottom:6px;">Plan</div>
                            <div style="font-size:18px;font-weight:700;margin-bottom:12px;">%s</div>
                            <div style="font-size:14px;color:#557668;margin-bottom:6px;">Activated on</div>
                            <div style="font-size:16px;font-weight:600;">%s</div>
                          </div>
                          <div style="font-size:14px;line-height:1.7;color:#557668;">Thank you for supporting Echo.%s</div>
                        </div>
                      </body>
                    </html>
                    """.formatted(productName, date, supportFooterHtml(language));
        }

        return """
                <html>
                  <body style="margin:0;padding:24px;background:#eef7f3;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:#163126;">
                    <div style="display:none;max-height:0;overflow:hidden;opacity:0;">Echo Premium üyeliğiniz aktif edildi ve kullanıma hazır.</div>
                    <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:24px;padding:32px;border:1px solid #d7eee3;box-shadow:0 10px 30px rgba(16,76,52,0.08);">
                      <div style="font-size:28px;font-weight:700;margin-bottom:16px;">Echo Journal</div>
                      <div style="font-size:22px;font-weight:700;margin-bottom:12px;">Premium aktif edildi</div>
                      <div style="font-size:16px;line-height:1.6;margin-bottom:18px;">Premium üyeliğiniz aktif edildi. Echo deneyiminin tüm avantajlarını kullanmaya başlayabilirsiniz.</div>
                      <div style="background:#f1faf6;border-radius:16px;padding:18px 20px;margin-bottom:20px;">
                        <div style="font-size:14px;color:#557668;margin-bottom:6px;">Paket</div>
                        <div style="font-size:18px;font-weight:700;margin-bottom:12px;">%s</div>
                        <div style="font-size:14px;color:#557668;margin-bottom:6px;">Aktivasyon tarihi</div>
                        <div style="font-size:16px;font-weight:600;">%s</div>
                      </div>
                      <div style="font-size:14px;line-height:1.7;color:#557668;">Echo'yu desteklediğiniz için teşekkür ederiz.%s</div>
                    </div>
                  </body>
                </html>
                """.formatted(productName, date, supportFooterHtml(language));
    }

    public String purchaseConfirmationText(String productId, String language, OffsetDateTime activatedAt) {
        String productName = productLabel(productId, language);
        String date = formatDate(activatedAt, language);

        if (isEnglish(language)) {
            return """
                    Echo Journal

                    Premium activated

                    Your Premium membership is now active.
                    Plan: %s
                    Activated on: %s

                    Thank you for supporting Echo.%s
                    """.formatted(productName, date, supportFooterText(language));
        }

        return """
                Echo Journal

                Premium aktif edildi

                Premium üyeliğiniz artık aktif.
                Paket: %s
                Aktivasyon tarihi: %s

                Echo'yu desteklediğiniz için teşekkür ederiz.%s
                """.formatted(productName, date, supportFooterText(language));
    }

    private boolean isEnglish(String language) {
        String normalized = normalizeLanguage(language);
        return normalized.startsWith("en");
    }

    private String normalizeLanguage(String language) {
        if (!StringUtils.hasText(language)) {
            return "tr";
        }
        return language.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    }

    private String productLabel(String productId, String language) {
        boolean english = isEnglish(language);
        return switch (productId) {
            case "echo_premium_yearly" -> english ? "Echo Premium Yearly" : "Echo Premium Yıllık";
            case "echo_premium_monthly" -> english ? "Echo Premium Monthly" : "Echo Premium Aylık";
            default -> "Echo Premium";
        };
    }

    private String formatDate(OffsetDateTime activatedAt, String language) {
        Locale locale = isEnglish(language) ? Locale.ENGLISH : Locale.forLanguageTag("tr");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMMM uuuu", locale);
        return formatter.format(activatedAt.toLocalDate());
    }

    private String supportFooterHtml(String language) {
        String supportAddress = appProperties.getResend().getSupportAddress();
        if (!StringUtils.hasText(supportAddress)) {
            return "";
        }

        if (isEnglish(language)) {
            return "<br><br>Need help? Contact <a href=\"mailto:%s\" style=\"color:#4a60d3;text-decoration:none;\">%s</a>."
                    .formatted(supportAddress, supportAddress);
        }

        return "<br><br>Yardıma ihtiyacınız olursa <a href=\"mailto:%s\" style=\"color:#4a60d3;text-decoration:none;\">%s</a> adresine yazabilirsiniz."
                .formatted(supportAddress, supportAddress);
    }

    private String supportFooterText(String language) {
        String supportAddress = appProperties.getResend().getSupportAddress();
        if (!StringUtils.hasText(supportAddress)) {
            return "";
        }

        if (isEnglish(language)) {
            return "\n\nNeed help? Contact " + supportAddress + ".";
        }

        return "\n\nYardıma ihtiyacınız olursa " + supportAddress + " adresine yazabilirsiniz.";
    }
}
