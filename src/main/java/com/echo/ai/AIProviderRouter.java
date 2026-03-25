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
import com.echo.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime'da restart olmadan AI provider değiştirmeyi sağlar.
 * AtomicReference kullanımı thread-safe olduğu için concurrent request'lerde sorun olmaz.
 *
 * Desteklenen provider'lar: openai, gemini, claude
 * Fallback: primary CB açılırsa OpenAI'ya şeffaf geçiş yapar (kullanıcı 503 görmez).
 * Kullanım: POST /api/v1/admin/ai-config {"provider": "gemini"}
 */
@Slf4j
@Component
public class AIProviderRouter {

    private static final Set<String> VALID_PROVIDERS = Set.of("openai", "gemini", "claude");

    private final AtomicReference<AITranscriptionProvider> transcriptionRef;
    private final AtomicReference<AIAnalysisProvider>      analysisRef;
    private final AtomicReference<AICoachProvider>         coachRef;
    private final AtomicReference<AISynthesisProvider>     synthesisRef;
    private volatile String activeProvider;

    private final AppProperties props;

    // All concrete provider beans — held for resolve() and fallback wiring
    private final OpenAITranscriptionProvider openAITranscription;
    private final OpenAIAnalysisProvider      openAIAnalysis;
    private final OpenAICoachProvider         openAICoach;
    private final OpenAISynthesisProvider     openAISynthesis;
    private final GeminiTranscriptionProvider geminiTranscription;
    private final GeminiAnalysisProvider      geminiAnalysis;
    private final GeminiCoachProvider         geminiCoach;
    private final GeminiSynthesisProvider     geminiSynthesis;
    private final ClaudeTranscriptionProvider claudeTranscription;
    private final ClaudeAnalysisProvider      claudeAnalysis;
    private final ClaudeCoachProvider         claudeCoach;

    public AIProviderRouter(AppProperties props,
                            OpenAITranscriptionProvider openAITranscription,
                            OpenAIAnalysisProvider openAIAnalysis,
                            OpenAICoachProvider openAICoach,
                            OpenAISynthesisProvider openAISynthesis,
                            GeminiTranscriptionProvider geminiTranscription,
                            GeminiAnalysisProvider geminiAnalysis,
                            GeminiCoachProvider geminiCoach,
                            GeminiSynthesisProvider geminiSynthesis,
                            ClaudeTranscriptionProvider claudeTranscription,
                            ClaudeAnalysisProvider claudeAnalysis,
                            ClaudeCoachProvider claudeCoach) {

        this.props               = props;
        this.openAITranscription = openAITranscription;
        this.openAIAnalysis      = openAIAnalysis;
        this.openAICoach         = openAICoach;
        this.openAISynthesis     = openAISynthesis;
        this.geminiTranscription = geminiTranscription;
        this.geminiAnalysis      = geminiAnalysis;
        this.geminiCoach         = geminiCoach;
        this.geminiSynthesis     = geminiSynthesis;
        this.claudeTranscription = claudeTranscription;
        this.claudeAnalysis      = claudeAnalysis;
        this.claudeCoach         = claudeCoach;

        String provider       = props.getAi().getProvider();
        this.activeProvider   = provider;
        this.transcriptionRef = new AtomicReference<>(resolveTranscription(provider));
        this.analysisRef      = new AtomicReference<>(resolveAnalysis(provider));
        this.coachRef         = new AtomicReference<>(resolveCoach(provider));
        this.synthesisRef     = new AtomicReference<>(resolveSynthesis(provider));

        log.info("AI Provider started: primary={} fallback={}", provider, props.getAi().getFallbackProvider());
    }

    /** Switches all providers atomically. Thread-safe. */
    public synchronized void switchProvider(String provider) {
        if (!VALID_PROVIDERS.contains(provider)) {
            throw new IllegalArgumentException(
                    "Invalid provider: " + provider + ". Valid: openai, gemini, claude");
        }
        transcriptionRef.set(resolveTranscription(provider));
        analysisRef.set(resolveAnalysis(provider));
        coachRef.set(resolveCoach(provider));
        synthesisRef.set(resolveSynthesis(provider));
        this.activeProvider = provider;
        log.info("AI Provider switched to: {}", provider);
    }

    public AITranscriptionProvider transcription() { return transcriptionRef.get(); }
    public AIAnalysisProvider      analysis()      { return analysisRef.get(); }
    public AICoachProvider         coach()         { return coachRef.get(); }
    public AISynthesisProvider     synthesis()     { return synthesisRef.get(); }
    public String                  activeProvider(){ return activeProvider; }

    // ── Private resolve methods ──────────────────────────────────────────────

    private AITranscriptionProvider resolveTranscription(String provider) {
        AITranscriptionProvider primary = switch (provider) {
            case "openai" -> openAITranscription;
            case "gemini" -> geminiTranscription;
            case "claude" -> claudeTranscription;  // throws UnsupportedOperationException
            default       -> throw new IllegalArgumentException("Unknown provider: " + provider);
        };
        return needsFallback(provider)
                ? wrappedTranscription(primary, provider)
                : primary;
    }

    private AIAnalysisProvider resolveAnalysis(String provider) {
        AIAnalysisProvider primary = switch (provider) {
            case "openai" -> openAIAnalysis;
            case "gemini" -> geminiAnalysis;
            case "claude" -> claudeAnalysis;
            default       -> throw new IllegalArgumentException("Unknown provider: " + provider);
        };
        return needsFallback(provider)
                ? wrappedAnalysis(primary, provider)
                : primary;
    }

    private AICoachProvider resolveCoach(String provider) {
        AICoachProvider primary = switch (provider) {
            case "openai" -> openAICoach;
            case "gemini" -> geminiCoach;
            case "claude" -> claudeCoach;
            default       -> throw new IllegalArgumentException("Unknown provider: " + provider);
        };
        return needsFallback(provider)
                ? wrappedCoach(primary, provider)
                : primary;
    }

    private AISynthesisProvider resolveSynthesis(String provider) {
        AISynthesisProvider primary = switch (provider) {
            case "openai" -> openAISynthesis;
            case "gemini" -> geminiSynthesis;
            case "claude" -> req -> {
                throw new UnsupportedOperationException(
                        "Synthesis not supported for claude. Use openai or gemini.");
            };
            default -> throw new IllegalArgumentException("Unknown provider: " + provider);
        };
        return needsFallback(provider)
                ? wrappedSynthesis(primary, provider)
                : primary;
    }

    // ── Fallback helpers ─────────────────────────────────────────────────────

    /**
     * Returns true when the active provider differs from the fallback provider.
     * When primary == fallback (both openai), no wrapper is needed.
     */
    private boolean needsFallback(String provider) {
        String fb = props.getAi().getFallbackProvider();
        return fb != null && !fb.isBlank() && !fb.equalsIgnoreCase(provider);
    }

    /**
     * Wraps a coach provider with transparent OpenAI fallback.
     * The lambda is NOT a Spring bean — but primary.chat() goes through the Spring AOP proxy
     * on the concrete bean, so the @CircuitBreaker on that bean is still active.
     */
    private AICoachProvider wrappedCoach(AICoachProvider primary, String primaryName) {
        return request -> {
            try {
                return primary.chat(request);
            } catch (ServiceUnavailableException e) {
                log.warn("ai_fallback op=COACH primary={} switching_to=openai reason={}",
                         primaryName, e.getMessage());
                return openAICoach.chat(request);
            }
        };
    }

    private AIAnalysisProvider wrappedAnalysis(AIAnalysisProvider primary, String primaryName) {
        return request -> {
            try {
                return primary.analyze(request);
            } catch (ServiceUnavailableException e) {
                log.warn("ai_fallback op=ANALYSIS primary={} switching_to=openai reason={}",
                         primaryName, e.getMessage());
                return openAIAnalysis.analyze(request);
            }
        };
    }

    private AISynthesisProvider wrappedSynthesis(AISynthesisProvider primary, String primaryName) {
        return request -> {
            try {
                return primary.synthesize(request);
            } catch (ServiceUnavailableException e) {
                log.warn("ai_fallback op=SYNTHESIS primary={} switching_to=openai reason={}",
                         primaryName, e.getMessage());
                return openAISynthesis.synthesize(request);
            }
        };
    }

    private AITranscriptionProvider wrappedTranscription(AITranscriptionProvider primary, String primaryName) {
        return (audioBytes, filename) -> {
            try {
                return primary.transcribe(audioBytes, filename);
            } catch (ServiceUnavailableException e) {
                log.warn("ai_fallback op=TRANSCRIPTION primary={} switching_to=openai reason={}",
                         primaryName, e.getMessage());
                return openAITranscription.transcribe(audioBytes, filename);
            }
        };
    }
}
