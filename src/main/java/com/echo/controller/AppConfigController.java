package com.echo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * App Store gereksinimleri için statik uygulama yapılandırması.
 * iOS uygulaması bu endpoint'i kullanarak:
 * - Gizlilik politikası URL'ini alır
 * - Yasal feragat metnini gösterir
 * - Minimum sürüm kontrolü yapar
 */
@RestController
@RequestMapping("/api/v1/app")
public class AppConfigController {

    private static final String PRIVACY_POLICY_URL   = "https://echo-app.com/privacy";
    private static final String TERMS_URL             = "https://echo-app.com/terms";
    private static final String MIN_APP_VERSION       = "1.0.0";
    private static final String CURRENT_APP_VERSION   = "1.0.0";
    private static final String CRISIS_LINE_TR        = "182";
    private static final String CRISIS_LINE_NAME      = "Türkiye Ruh Sağlığı Hattı";

    /**
     * iOS uygulaması ilk açılışta bu endpoint'i çağırır.
     * Public — authentication gerektirmez.
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(Map.of(
                "privacy_policy_url",    PRIVACY_POLICY_URL,
                "terms_url",             TERMS_URL,
                "min_app_version",       MIN_APP_VERSION,
                "current_app_version",   CURRENT_APP_VERSION,
                "ai_disclaimer",         buildAiDisclaimer(),
                "crisis_resources",      buildCrisisResources(),
                "ai_training_consent_required", true,
                "kvkk_consent_required", true,
                "ads", buildAdsConfig()
        ));
    }

    private Map<String, String> buildAiDisclaimer() {
        return Map.of(
                "tr", "Echo, bir yansıma koçudur. Tıbbi veya psikolojik tavsiye vermez. " +
                      "Profesyonel destek için lütfen bir uzmanla görüşün.",
                "en", "Echo is a reflection coach, not a therapist. " +
                      "It does not provide medical or psychological advice. " +
                      "Please consult a professional for mental health support."
        );
    }

    private Map<String, String> buildCrisisResources() {
        return Map.of(
                "line", CRISIS_LINE_TR,
                "name", CRISIS_LINE_NAME,
                "availability", "7/24",
                "cost", "Ücretsiz"
        );
    }

    private Map<String, Object> buildAdsConfig() {
        return Map.of(
                "enabled", true,
                "placements", Map.of(
                        "communityFeed", Map.of("enabled", true, "frequency", 5),
                        "homeBanner", Map.of("enabled", true),
                        "postAnalysisInterstitial", Map.of("enabled", true),
                        "profileBanner", Map.of("enabled", true)
                )
        );
    }
}
