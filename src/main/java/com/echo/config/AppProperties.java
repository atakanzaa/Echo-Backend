package com.echo.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @Valid private Jwt jwt = new Jwt();
    @Valid private AI ai = new AI();
    @Valid private Storage storage = new Storage();
    @Valid private Cors cors = new Cors();
    private Prompts prompts = new Prompts();

    @PostConstruct
    public void validate() {
        if (jwt.getSecret() == null || jwt.getSecret().length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters");
        }

        String provider = normalizeProvider(ai.getProvider());
        validateProviderKey(provider);

        String fallbackProvider = ai.getFallbackProvider();
        if (StringUtils.hasText(fallbackProvider)) {
            validateProviderKey(normalizeProvider(fallbackProvider));
        }
    }

    @Getter @Setter
    public static class Jwt {
        @NotBlank private String secret;
        @Positive private long accessTokenExpirySeconds = 900;
        @Positive private long refreshTokenExpirySeconds = 2592000;
    }

    @Getter @Setter
    public static class AI {
        @NotBlank private String provider = "openai";
        private String fallbackProvider = "openai";
        @Positive private int timeoutSeconds = 60;
        private OpenAI openai = new OpenAI();
        private Gemini gemini = new Gemini();
        private Claude claude = new Claude();

        @Getter @Setter
        public static class OpenAI {
            private String apiKey;
            private String transcribeModel = "whisper-1";
            private String analysisModel = "gpt-4o-mini";
            private String coachModel = "gpt-4o-mini";
        }

        @Getter @Setter
        public static class Gemini {
            private String apiKey;
            private String analysisModel = "gemini-2.5-flash";
            private String coachModel = "gemini-2.5-flash";
            // multimodal audio transcription model
            private String transcribeModel = "gemini-2.5-flash";
        }

        @Getter @Setter
        public static class Claude {
            private String apiKey;
            private String analysisModel = "claude-haiku-4-5-20251001";
            private String coachModel = "claude-haiku-4-5-20251001";
        }

    }

    @Getter @Setter
    public static class Storage {
        private String type = "local";
        /** Community image files — persistent local path (not /tmp) */
        private String localImagesPath = System.getProperty("user.home") + "/echo-uploads/images";
        /** R2 / S3 endpoint, e.g. https://<account_id>.r2.cloudflarestorage.com */
        private String s3Endpoint = "";
        private String s3AccessKey = "";
        private String s3SecretKey = "";
        /** "auto" for Cloudflare R2; use a real region (e.g. "us-east-1") for AWS S3 */
        private String s3Region = "auto";
        private String imagesBucket = "echo-community-images";
        /** Base URL of the public R2 CDN, e.g. https://pub-<hash>.r2.dev */
        private String imagesPublicBaseUrl = "";
    }

    @Getter @Setter
    public static class Cors {
        // Backward compatibility for existing config keys (allowed-origins)
        private String[] allowedOrigins = {"*"};
        private List<String> allowedOriginPatterns = List.of("*");
        private List<String> allowedMethods =
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        private List<String> allowedHeaders =
                List.of("Content-Type", "Authorization", "Accept", "X-Request-ID");
        private boolean allowCredentials = false;
        @Positive private long maxAgeSeconds = 3600;

        public List<String> resolveOriginPatterns() {
            if (allowedOriginPatterns != null && !allowedOriginPatterns.isEmpty()) {
                return allowedOriginPatterns;
            }
            return Arrays.asList(allowedOrigins);
        }
    }

    @Getter @Setter
    public static class Prompts {
        private String analysisVersion      = "analysis-v4";
        private String coachVersion         = "coach-v5";
        private String synthesisVersion     = "synthesis-v2";
        private String transcriptionVersion = "transcription-v1";
    }

    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            throw new IllegalStateException("AI provider must not be blank");
        }
        return provider.toLowerCase(Locale.ROOT).trim();
    }

    private void validateProviderKey(String provider) {
        switch (provider) {
            case "gemini" -> requireApiKey("GEMINI_API_KEY", ai.getGemini().getApiKey());
            case "openai" -> requireApiKey("OPENAI_API_KEY", ai.getOpenai().getApiKey());
            case "claude" -> requireApiKey("CLAUDE_API_KEY", ai.getClaude().getApiKey());
            default -> throw new IllegalStateException(
                    "Unsupported AI provider: " + provider + ". Valid values: openai, gemini, claude");
        }
    }

    private void requireApiKey(String envName, String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(envName + " is required for selected AI provider");
        }
    }
}
