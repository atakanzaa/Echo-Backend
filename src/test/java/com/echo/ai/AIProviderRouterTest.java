package com.echo.ai;

import com.echo.ai.claude.ClaudeAnalysisProvider;
import com.echo.ai.claude.ClaudeCoachProvider;
import com.echo.ai.claude.ClaudeTranscriptionProvider;
import com.echo.ai.gemini.GeminiAnalysisProvider;
import com.echo.ai.gemini.GeminiCoachProvider;
import com.echo.ai.gemini.GeminiSynthesisProvider;
import com.echo.ai.gemini.GeminiTranscriptionProvider;
import com.echo.ai.openai.OpenAIAnalysisProvider;
import com.echo.ai.openai.OpenAICoachProvider;
import com.echo.ai.openai.OpenAISynthesisProvider;
import com.echo.ai.openai.OpenAITranscriptionProvider;
import com.echo.config.AppProperties;
import com.echo.exception.TranscriptionFailedException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AIProviderRouterTest {

    @Test
    void usesExplicitTranscriptionProviderOverride() {
        OpenAITranscriptionProvider openAITranscription = mock(OpenAITranscriptionProvider.class);
        GeminiTranscriptionProvider geminiTranscription = mock(GeminiTranscriptionProvider.class);
        AIProviderRouter router = router(
                props("gemini", "openai", "openai", "gemini"),
                openAITranscription,
                geminiTranscription
        );

        AITranscriptionRequest request = new AITranscriptionRequest(new byte[] {1, 2}, "voice.m4a", "audio/mp4", 18);
        AITranscriptionResult expected = new AITranscriptionResult("Merhaba", "openai", null, null, "audio/mp4", List.of());
        given(openAITranscription.transcribe(request)).willReturn(expected);

        AITranscriptionResult actual = router.transcription().transcribe(request);

        assertThat(router.activeProvider()).isEqualTo("gemini");
        assertThat(router.activeTranscriptionProvider()).isEqualTo("openai");
        assertThat(actual).isEqualTo(expected);
        verify(openAITranscription).transcribe(request);
        verifyNoInteractions(geminiTranscription);
    }

    @Test
    void fallsBackForTranscriptionWhenPrimaryRejectsAudio() {
        OpenAITranscriptionProvider openAITranscription = mock(OpenAITranscriptionProvider.class);
        GeminiTranscriptionProvider geminiTranscription = mock(GeminiTranscriptionProvider.class);
        AIProviderRouter router = router(
                props("gemini", "openai", null, null),
                openAITranscription,
                geminiTranscription
        );

        AITranscriptionRequest request = new AITranscriptionRequest(new byte[] {1, 2}, "voice.m4a", "audio/mp4", 18);
        given(geminiTranscription.transcribe(request)).willThrow(
                new TranscriptionFailedException("TRANSCRIPTION_AUDIO_NOT_PARSED", "Ses kaydi cozumlenemedi."));
        AITranscriptionResult fallbackResult =
                new AITranscriptionResult("Fallback transcript", "openai", null, null, "audio/mp4", List.of());
        given(openAITranscription.transcribe(request)).willReturn(fallbackResult);

        AITranscriptionResult actual = router.transcription().transcribe(request);

        assertThat(actual).isEqualTo(fallbackResult);
        verify(geminiTranscription).transcribe(request);
        verify(openAITranscription).transcribe(request);
    }

    private AppProperties props(String provider,
                                String fallbackProvider,
                                String transcriptionProvider,
                                String transcriptionFallbackProvider) {
        AppProperties props = new AppProperties();
        props.getAi().getOpenai().setApiKey("openai-key");
        props.getAi().getGemini().setApiKey("gemini-key");
        props.getAi().getClaude().setApiKey("claude-key");
        props.getAi().setProvider(provider);
        props.getAi().setFallbackProvider(fallbackProvider);
        props.getAi().setTranscriptionProvider(transcriptionProvider);
        props.getAi().setTranscriptionFallbackProvider(transcriptionFallbackProvider);
        return props;
    }

    private AIProviderRouter router(AppProperties props,
                                    OpenAITranscriptionProvider openAITranscription,
                                    GeminiTranscriptionProvider geminiTranscription) {
        return new AIProviderRouter(
                props,
                openAITranscription,
                mock(OpenAIAnalysisProvider.class),
                mock(OpenAICoachProvider.class),
                mock(OpenAISynthesisProvider.class),
                geminiTranscription,
                mock(GeminiAnalysisProvider.class),
                mock(GeminiCoachProvider.class),
                mock(GeminiSynthesisProvider.class),
                mock(ClaudeTranscriptionProvider.class),
                mock(ClaudeAnalysisProvider.class),
                mock(ClaudeCoachProvider.class)
        );
    }
}
