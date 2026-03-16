package com.echo.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @Valid private Jwt jwt = new Jwt();
    @Valid private AI ai = new AI();
    @Valid private Storage storage = new Storage();

    @Getter @Setter
    public static class Jwt {
        @NotBlank private String secret;
        @Positive private long accessTokenExpirySeconds = 900;
        @Positive private long refreshTokenExpirySeconds = 2592000;
    }

    @Getter @Setter
    public static class AI {
        @NotBlank private String provider = "openai";
        @Positive private int timeoutSeconds = 60;
        private OpenAI  openai  = new OpenAI();
        private Gemini  gemini  = new Gemini();
        private Claude  claude  = new Claude();
        private Ollama  ollama  = new Ollama();

        @Getter @Setter
        public static class OpenAI {
            private String apiKey;
            private String transcribeModel = "whisper-1";
            private String analysisModel   = "gpt-4o-mini";
            private String coachModel      = "gpt-4o-mini";
        }

        @Getter @Setter
        public static class Gemini {
            private String apiKey;
            private String analysisModel   = "gemini-2.5-flash";
            private String coachModel      = "gemini-2.5-flash";
            private String transcribeModel = "gemini-2.5-flash";  // Multimodal ses transkripsiyon
        }

        @Getter @Setter
        public static class Claude {
            private String apiKey;
            private String analysisModel = "claude-haiku-4-5-20251001";
            private String coachModel    = "claude-haiku-4-5-20251001";
        }

        @Getter @Setter
        public static class Ollama {
            private String baseUrl       = "http://localhost:11434";
            private String analysisModel = "llama3.1:8b";
            private String coachModel    = "llama3.1:8b";
        }
    }

    @Getter @Setter
    public static class Storage {
        private String type      = "local";
        private String localPath = "/tmp/echo-audio";
    }
}
