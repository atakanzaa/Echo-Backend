package com.echo.ai.gemini;

import com.echo.ai.AITranscriptionRequest;
import com.echo.config.AppProperties;
import com.echo.exception.TranscriptionFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GeminiTranscriptionProviderTest {

    @Test
    void rejectsPromptEchoWhenAudioTokensAreTooLow() {
        AppProperties props = new AppProperties();
        props.getAi().getGemini().setApiKey("gemini-key");

        GeminiClient geminiClient = mock(GeminiClient.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        GeminiTranscriptionProvider provider = new GeminiTranscriptionProvider(props, restTemplate, geminiClient);

        Map<?, ?> response = Map.of(
                "usageMetadata", Map.of(
                        "promptTokenCount", 20,
                        "candidatesTokenCount", 6
                ),
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of(
                                        "text", "Please transcribe this audio recording. Return only the spoken text."
                                ))
                        )
                ))
        );
        given(geminiClient.execute(any(), any(), any(), any(), eq("TRANSCRIPTION"), eq("transcription-v1")))
                .willAnswer(invocation -> response);
        given(geminiClient.extractText(response))
                .willReturn("Please transcribe this audio recording. Return only the spoken text.");

        assertThatThrownBy(() -> provider.transcribe(
                new AITranscriptionRequest(new byte[57_356], "echo.m4a", "audio/mp4", 18)))
                .isInstanceOf(TranscriptionFailedException.class)
                .hasMessageContaining("Ses kaydı çözümlenemedi");

    }
}
