package com.echo.ai;

import com.echo.ai.claude.ClaudeAnalysisProvider;
import com.echo.ai.claude.ClaudeCoachProvider;
import com.echo.ai.claude.ClaudeTranscriptionProvider;
import com.echo.ai.gemini.GeminiAnalysisProvider;
import com.echo.ai.gemini.GeminiCoachProvider;
import com.echo.ai.gemini.GeminiSynthesisProvider;
import com.echo.ai.gemini.GeminiTranscriptionProvider;
import com.echo.ai.ollama.OllamaAnalysisProvider;
import com.echo.ai.ollama.OllamaCoachProvider;
import com.echo.ai.ollama.OllamaTranscriptionProvider;
import com.echo.ai.openai.OpenAIAnalysisProvider;
import com.echo.ai.openai.OpenAICoachProvider;
import com.echo.ai.openai.OpenAISynthesisProvider;
import com.echo.ai.openai.OpenAITranscriptionProvider;
import com.echo.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime'da restart olmadan AI provider değiştirmeyi sağlar.
 * AtomicReference kullanımı thread-safe olduğu için concurrent request'lerde sorun olmaz.
 *
 * Desteklenen provider'lar: openai, gemini, claude, ollama (self-hosted)
 * Kullanım: POST /api/v1/admin/ai-config {"provider": "ollama"}
 */
@Slf4j
@Component
public class AIProviderRouter {

    private static final Set<String> VALID_PROVIDERS =
            Set.of("openai", "gemini", "claude", "ollama");

    private final AtomicReference<AITranscriptionProvider> transcriptionRef;
    private final AtomicReference<AIAnalysisProvider>      analysisRef;
    private final AtomicReference<AICoachProvider>         coachRef;
    private final AtomicReference<AISynthesisProvider>     synthesisRef;
    private volatile String activeProvider;

    // Tüm provider'lar Spring tarafından inject edilir
    private final OpenAITranscriptionProvider openAITranscription;
    private final OpenAIAnalysisProvider      openAIAnalysis;
    private final OpenAICoachProvider         openAICoach;
    private final GeminiTranscriptionProvider geminiTranscription;
    private final GeminiAnalysisProvider      geminiAnalysis;
    private final GeminiCoachProvider         geminiCoach;
    private final ClaudeTranscriptionProvider claudeTranscription;
    private final ClaudeAnalysisProvider      claudeAnalysis;
    private final ClaudeCoachProvider         claudeCoach;
    private final OllamaTranscriptionProvider ollamaTranscription;
    private final OllamaAnalysisProvider      ollamaAnalysis;
    private final OllamaCoachProvider         ollamaCoach;
    private final GeminiSynthesisProvider     geminiSynthesis;
    private final OpenAISynthesisProvider     openAISynthesis;

    public AIProviderRouter(AppProperties props,
                            OpenAITranscriptionProvider openAITranscription,
                            OpenAIAnalysisProvider openAIAnalysis,
                            OpenAICoachProvider openAICoach,
                            GeminiTranscriptionProvider geminiTranscription,
                            GeminiAnalysisProvider geminiAnalysis,
                            GeminiCoachProvider geminiCoach,
                            ClaudeTranscriptionProvider claudeTranscription,
                            ClaudeAnalysisProvider claudeAnalysis,
                            ClaudeCoachProvider claudeCoach,
                            OllamaTranscriptionProvider ollamaTranscription,
                            OllamaAnalysisProvider ollamaAnalysis,
                            OllamaCoachProvider ollamaCoach,
                            GeminiSynthesisProvider geminiSynthesis,
                            OpenAISynthesisProvider openAISynthesis) {

        this.openAITranscription = openAITranscription;
        this.openAIAnalysis      = openAIAnalysis;
        this.openAICoach         = openAICoach;
        this.geminiTranscription = geminiTranscription;
        this.geminiAnalysis      = geminiAnalysis;
        this.geminiCoach         = geminiCoach;
        this.claudeTranscription = claudeTranscription;
        this.claudeAnalysis      = claudeAnalysis;
        this.claudeCoach         = claudeCoach;
        this.ollamaTranscription = ollamaTranscription;
        this.ollamaAnalysis      = ollamaAnalysis;
        this.ollamaCoach         = ollamaCoach;
        this.geminiSynthesis     = geminiSynthesis;
        this.openAISynthesis     = openAISynthesis;

        String provider = props.getAi().getProvider();
        this.activeProvider   = provider;
        this.transcriptionRef = new AtomicReference<>(resolveTranscription(provider));
        this.analysisRef      = new AtomicReference<>(resolveAnalysis(provider));
        this.coachRef         = new AtomicReference<>(resolveCoach(provider));
        this.synthesisRef     = new AtomicReference<>(resolveSynthesis(provider));

        log.info("AI Provider başlatıldı: {}", provider);
    }

    /**
     * Tüm provider'ları atomik olarak değiştirir. Thread-safe.
     */
    public synchronized void switchProvider(String provider) {
        if (!VALID_PROVIDERS.contains(provider)) {
            throw new IllegalArgumentException(
                    "Geçersiz provider: " + provider + ". Geçerli: openai, gemini, claude, ollama");
        }
        transcriptionRef.set(resolveTranscription(provider));
        analysisRef.set(resolveAnalysis(provider));
        coachRef.set(resolveCoach(provider));
        synthesisRef.set(resolveSynthesis(provider));
        this.activeProvider = provider;
        log.info("AI Provider değiştirildi: {}", provider);
    }

    public AITranscriptionProvider transcription() { return transcriptionRef.get(); }
    public AIAnalysisProvider      analysis()      { return analysisRef.get(); }
    public AICoachProvider         coach()         { return coachRef.get(); }
    public AISynthesisProvider     synthesis()     { return synthesisRef.get(); }
    public String                  activeProvider(){ return activeProvider; }

    // ── Private ─────────────────────────────────────────────────────────────

    private AITranscriptionProvider resolveTranscription(String provider) {
        return switch (provider) {
            case "openai" -> openAITranscription;
            case "gemini" -> geminiTranscription;
            case "claude" -> claudeTranscription;  // UnsupportedOperationException fırlatır
            case "ollama" -> ollamaTranscription;  // UnsupportedOperationException fırlatır — /transcript endpoint kullan
            default       -> throw new IllegalArgumentException("Bilinmeyen provider: " + provider);
        };
    }

    private AIAnalysisProvider resolveAnalysis(String provider) {
        return switch (provider) {
            case "openai" -> openAIAnalysis;
            case "gemini" -> geminiAnalysis;
            case "claude" -> claudeAnalysis;
            case "ollama" -> ollamaAnalysis;
            default       -> throw new IllegalArgumentException("Bilinmeyen provider: " + provider);
        };
    }

    private AICoachProvider resolveCoach(String provider) {
        return switch (provider) {
            case "openai" -> openAICoach;
            case "gemini" -> geminiCoach;
            case "claude" -> claudeCoach;
            case "ollama" -> ollamaCoach;
            default       -> throw new IllegalArgumentException("Bilinmeyen provider: " + provider);
        };
    }

    private AISynthesisProvider resolveSynthesis(String provider) {
        return switch (provider) {
            case "openai" -> openAISynthesis;
            case "gemini" -> geminiSynthesis;
            case "claude", "ollama" -> req -> {
                throw new UnsupportedOperationException(
                        "AI sentez için " + provider + " desteklenmiyor. openai veya gemini kullanın.");
            };
            default -> throw new IllegalArgumentException("Bilinmeyen provider: " + provider);
        };
    }
}
